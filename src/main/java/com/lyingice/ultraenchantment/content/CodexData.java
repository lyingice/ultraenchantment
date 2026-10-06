package com.lyingice.ultraenchantment.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * <b>图鉴</b>——玩家「见过」哪些 (谱系, 阶级)。
 *
 * <h2>为什么存玩家身上</h2>
 *
 * <p>规格原话是「<b>玩家</b>把一本铭刻书放进图书馆，就<b>永久</b>解锁这条附魔的这个阶级」——
 * 这天然是 <b>per-player</b> 的语义。存在方块里会「换个图书馆就重新开始」，
 * 存在存档全局则没有个人进度可言。三个选项的取舍见 AGENTS 变更记录。
 *
 * <h2>数据结构：位掩码，不是集合</h2>
 *
 * <p>每条形如 {@code 谱系根源 → 3 bit 掩码}，bit 依次对应
 * {@link AscensionTier#ordinal()}（高阶/超级/究极）。
 *
 * <p>用掩码而不是 {@code Set<(root, tier)>}：一条谱系最多 3 个阶级，
 * 掩码把「一条谱系」压成<b>一个 int</b>，NBT 里就是一条 {@code "minecraft:sharpness": 5}，
 * 既省空间也一眼能读。30 条谱系总共 30 个条目。
 *
 * <h2>原生阶恒为已解锁</h2>
 *
 * <p>{@link LineageTier#NATIVE} 不在掩码里——原版附魔人人可见，
 * 把它也纳入解锁状态只会让「新玩家能不能用原版附魔」变成一个荒谬的问题。
 * {@link #isUnlocked(ResourceLocation, LineageTier)} 对原生阶直接返回 {@code true}。
 */
public record CodexData(Map<ResourceLocation, Integer> unlocked) {

    public static final CodexData EMPTY = new CodexData(Map.of());

    public static final Codec<CodexData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT)
                    .optionalFieldOf("unlocked", Map.of())
                    .forGetter(CodexData::unlocked)
    ).apply(instance, CodexData::new));

    public CodexData {
        unlocked = Map.copyOf(unlocked);
    }

    /** 某条谱系的某个进阶阶级是否已解锁。 */
    public boolean isUnlocked(ResourceLocation root, AscensionTier tier) {
        Integer mask = this.unlocked.get(root);
        return mask != null && (mask & (1 << tier.ordinal())) != 0;
    }

    /** 谱系状态口径：原生阶<b>恒为已解锁</b>。 */
    public boolean isUnlocked(ResourceLocation root, LineageTier tier) {
        return tier == LineageTier.NATIVE
                || AscensionTier.of(tier).map(ascent -> this.isUnlocked(root, ascent)).orElse(false);
    }

    /**
     * 解锁一个 (谱系, 阶级)。
     *
     * <p>已解锁时<b>返回自身</b>（同一个实例）——调用方据此判断「有没有真的变化」，
     * 避免每次存入都写一次玩家数据并触发一次同步。
     */
    public CodexData with(ResourceLocation root, AscensionTier tier) {
        int before = this.unlocked.getOrDefault(root, 0);
        int after = before | (1 << tier.ordinal());
        if (after == before) {
            return this;
        }
        Map<ResourceLocation, Integer> next = new HashMap<>(this.unlocked);
        next.put(root, after);
        return new CodexData(next);
    }

    /** 已解锁的谱系条数（不是「阶级数」）。 */
    public int lineageCount() {
        return this.unlocked.size();
    }

    public boolean isEmpty() {
        return this.unlocked.isEmpty();
    }
}
