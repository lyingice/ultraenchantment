package com.lyingice.ultraenchantment.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 物品上的「阶段标识」载荷——记录**哪些谱系被进阶到了哪一阶**。
 *
 * <p>这是整个系统的存储层核心。用 map 而不是单值，因为一把剑可以同时有多条谱系
 * （锋利 + 耐久 + 抢夺），各走各的进度。
 *
 * <p>物品上**同时**还保留着原本的 {@code minecraft:enchantments}（例如
 * {@code minecraft:sharpness: 5}）——身份永不改变，本组件只追加「它现在处于哪一阶」。
 * 结算时由 {@code GetEnchantmentLevelEvent} 读本组件，屏蔽原版附魔并注入阶段定义的效果。
 *
 * <h2>为什么需要 {@code tierLevel}（进阶后等级曲线）</h2>
 *
 * <p>规格要求「升阶后显示为 1 级，再用升级书可以往上提」。但原版附魔的存储等级
 * 在升阶时<b>不变</b>（锋利 5 升阶后存储仍是 5），于是「显示 1 级」这个新曲线的进度
 * <b>无处安放</b>——若按存储的 5 去判定升级书，3 级升级书会因为 {@code 5 >= 3} 而无效。
 *
 * <p>因此本组件为每条谱系额外记一个 {@code tierLevel}：<b>进阶后曲线上的当前等级</b>。
 * <ul>
 *   <li>升阶瞬间：{@code tierLevel = 1}（重新起算）</li>
 *   <li>升级书生效：{@code tierLevel = 书的目标等级}</li>
 *   <li>未进阶的谱系：无记录</li>
 * </ul>
 *
 * <p><b>tierLevel 就是「这个进阶形态自己的附魔等级」，效果强度按它结算</b>
 * （{@code EnchantmentLevelEvents} 注入的就是它）。存储等级升阶后不变，
 * 只留给原版机制读（铁砧合并、附魔台、村民交易）——拿它当效果等级会让显示与实强脱节，
 * 升级书也会变成纯装饰。
 *
 * <p>⚠️ 早期文档写的是「存储等级管效果强度，tierLevel 管对外呈现」，那是错的，已更正
 * （见 AGENT.md P1-20）。
 */
public record AscensionData(Map<ResourceLocation, Record> lineages) {

    public static final AscensionData EMPTY = new AscensionData(Map.of());

    /**
     * 一条谱系的进阶记录。
     *
     * @param stage     当前阶段条目 id（{@code ultraenchantment:super/sharpness}）
     * @param tierLevel 进阶后曲线上的等级，从 1 起算
     */
    public record Record(ResourceLocation stage, int tierLevel) {
        public static final Codec<Record> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("stage").forGetter(Record::stage),
                Codec.intRange(1, 255).fieldOf("tier_level").forGetter(Record::tierLevel)
        ).apply(instance, Record::new));
    }

    /**
     * 序列化格式。
     *
     * <p>为兼容旧存档，读入时允许<b>纯字符串</b>形式（早期版本只存阶段 id，无 tierLevel）——
     * 遇到就按 {@code tierLevel = 1} 补全。写出一律用完整对象形式。
     */
    public static final Codec<AscensionData> CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, Record.CODEC)
                    .xmap(AscensionData::new, AscensionData::lineages);

    /**
     * 网络同步用。
     *
     * <p>用 {@code fromCodecWithRegistries} 而非 {@code ByteBufCodecs.map(...)}：
     * 后者的返回类型是 {@code StreamCodec<ByteBuf, ...>}，与组件要求的
     * {@code RegistryFriendlyByteBuf} 不匹配。走 codec 派生最简单也最不易错。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, AscensionData> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public AscensionData {
        lineages = Map.copyOf(lineages);
    }

    /** 谱系根源（原版附魔）→ 当前阶段条目。 */
    public Optional<ResourceLocation> stageOf(ResourceLocation rootEnchantment) {
        Record rec = this.lineages.get(rootEnchantment);
        return rec == null ? Optional.empty() : Optional.of(rec.stage());
    }

    /**
     * 谱系根源 → 进阶后曲线上的等级。
     *
     * <p>未进阶的谱系返回 0，表示「不使用进阶曲线」。
     */
    public int tierLevelOf(ResourceLocation rootEnchantment) {
        Record rec = this.lineages.get(rootEnchantment);
        return rec == null ? 0 : rec.tierLevel();
    }

    /** 兼容旧调用点的原始 map 视图（root → stage）。 */
    public Map<ResourceLocation, ResourceLocation> stages() {
        Map<ResourceLocation, ResourceLocation> out = new LinkedHashMap<>();
        this.lineages.forEach((root, rec) -> out.put(root, rec.stage()));
        return out;
    }

    public boolean isEmpty() {
        return this.lineages.isEmpty();
    }

    /**
     * 返回一个把 {@code root} 定位到 {@code stage} 的新实例（不可变，原实例不受影响）。
     *
     * <p>{@code tierLevel} 归 1——升阶是新的成长曲线。
     */
    public AscensionData with(ResourceLocation root, ResourceLocation stage) {
        return with(root, stage, 1);
    }

    /** 指定进阶后等级的版本（升级书使用）。 */
    public AscensionData with(ResourceLocation root, ResourceLocation stage, int tierLevel) {
        Map<ResourceLocation, Record> copy = new LinkedHashMap<>(this.lineages);
        copy.put(root, new Record(stage, Math.max(1, tierLevel)));
        return new AscensionData(copy);
    }

    /** 在保持阶段不变的前提下更新进阶后等级。 */
    public AscensionData withTierLevel(ResourceLocation root, int tierLevel) {
        Record rec = this.lineages.get(root);
        if (rec == null) {
            return this;
        }
        Map<ResourceLocation, Record> copy = new LinkedHashMap<>(this.lineages);
        copy.put(root, new Record(rec.stage(), Math.max(1, tierLevel)));
        return new AscensionData(copy);
    }

    /** 返回一个移除 {@code root} 记录的新实例（祛咒石解除保护时用）。 */
    public AscensionData without(ResourceLocation root) {
        if (!this.lineages.containsKey(root)) {
            return this;
        }
        Map<ResourceLocation, Record> copy = new LinkedHashMap<>(this.lineages);
        copy.remove(root);
        return new AscensionData(copy);
    }
}
