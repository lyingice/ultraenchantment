package com.lyingice.ultraenchantment.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
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
 * <h2>基础阶也要「见过」才解锁（v4 起）</h2>
 *
 * <p>v3 时基础阶恒为已解锁，理由是「原版附魔人人可见」。作者指出这<b>太便宜</b>了：
 * 进阶台的基础档可以<b>凭空</b>给任何附魔写等级，玩家不需要先拥有过它。
 *
 * <p>现在基础阶占掩码的<b>第 4 位</b>（{@link #NATIVE_BIT}），
 * 与三个进阶阶级同一套存储。解锁入口见 {@code LibraryMenu.onDeposited}：
 * 存入一本<b>基础阶</b>的书即视为「见过」。
 */
public record CodexData(Map<ResourceLocation, Integer> unlocked) {

    public static final CodexData EMPTY = new CodexData(Map.of());

    /**
     * 基础阶在掩码里的位。
     *
     * <p>{@link AscensionTier} 只有三个值，占 0/1/2 位；第 3 位空着，基础阶放这里，
     * 于是「基础 / 高阶 / 超级 / 究极」四档共用一套掩码与存储格式。
     */
    public static final int NATIVE_BIT = 1 << 3;

    public static final Codec<CodexData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT)
                    .optionalFieldOf("unlocked", Map.of())
                    .forGetter(CodexData::unlocked)
    ).apply(instance, CodexData::new));

    /**
     * 网络同步用的编解码器（附件 {@code .sync()} 需要）。
     *
     * <p>与 {@link #CODEC} 同构，但走二进制：{@code ResourceLocation → VAR_INT} 的映射表。
     * 数据量很小（30 条谱系封顶），整份同步的开销可以忽略。
     */
    public static final StreamCodec<ByteBuf, CodexData> STREAM_CODEC =
            // ⚠️ 必须写显式类型见证把 M 固定成 Map。让编译器自己推会推成 HashMap，
            //    于是 .map(...) 的 from 函数要求「返回 HashMap」，而 unlocked() 返回 Map —— 编译不过。
            ByteBufCodecs.<ByteBuf, ResourceLocation, Integer, Map<ResourceLocation, Integer>>map(
                            HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.VAR_INT)
                    .map(CodexData::new, CodexData::unlocked);

    public CodexData {
        unlocked = Map.copyOf(unlocked);
    }

    /** 某条谱系的某个进阶阶级是否已解锁。 */
    public boolean isUnlocked(ResourceLocation root, AscensionTier tier) {
        Integer mask = this.unlocked.get(root);
        return mask != null && (mask & (1 << tier.ordinal())) != 0;
    }

    /**
     * 谱系状态口径。
     *
     * <p>基础阶读 {@link #NATIVE_BIT}；三个进阶阶级读 {@link AscensionTier#ordinal()} 位。
     */
    public boolean isUnlocked(ResourceLocation root, LineageTier tier) {
        if (tier == LineageTier.NATIVE) {
            Integer mask = this.unlocked.get(root);
            return mask != null && (mask & NATIVE_BIT) != 0;
        }
        return AscensionTier.of(tier).map(ascent -> this.isUnlocked(root, ascent)).orElse(false);
    }

    /**
     * 解锁某条谱系的<b>基础阶</b>——玩家「见过」这条原版附魔了。
     *
     * <p>与 {@link #with} 同样：已解锁时返回<b>自身</b>，调用方据此判断有无真变化。
     */
    public CodexData withNative(ResourceLocation root) {
        int before = this.unlocked.getOrDefault(root, 0);
        int after = before | NATIVE_BIT;
        if (after == before) {
            return this;
        }
        Map<ResourceLocation, Integer> next = new HashMap<>(this.unlocked);
        next.put(root, after);
        return new CodexData(next);
    }

    /**
     * 解锁一个 (谱系, 阶级)。
     *
     * <p>已解锁时<b>返回自身</b>（同一个实例）——调用方据此判断「有没有真的变化」，
     * 避免每次存入都写一次玩家数据并触发一次同步。
     */
    public CodexData with(ResourceLocation root, AscensionTier tier) {
        int before = this.unlocked.getOrDefault(root, 0);
        int after = before | maskUpTo(tier);
        if (after == before) {
            return this;
        }
        Map<ResourceLocation, Integer> next = new HashMap<>(this.unlocked);
        next.put(root, after);
        return new CodexData(next);
    }

    /**
     * 某个阶级<b>及其下级全部阶级</b>的位。
     *
     * <h2>为什么阶级是「包含关系」</h2>
     *
     * <p>拿到一本「究极锋利」的书，意味着你当然也知道「超级锋利 / 高阶锋利 / 锋利」是什么 ——
     * 它们是同一条谱系的前几段。只点亮究极那一格，会让玩家碰到荒谬的情形：
     * <b>物品带着究极锋利，却选不了它的高阶档</b>。
     *
     * <p>所以解锁是<b>向下闭包</b>的：`with(root, ULTRA)` 同时点亮
     * 基础（{@link #NATIVE_BIT}）、高阶、超级、究极。
     * 「基础阶」也算在内 —— 见过这条谱系的任何一档，就没道理还说「没见过它本身」。
     */
    public static int maskUpTo(AscensionTier tier) {
        int mask = NATIVE_BIT;
        for (AscensionTier t : AscensionTier.values()) {
            if (t.ordinal() <= tier.ordinal()) {
                mask |= 1 << t.ordinal();
            }
        }
        return mask;
    }

    /**
     * 已解锁的全部 {@code (谱系, 阶级)}，按<b>谱系字典序 → 阶级枚举序</b>稳定排序。
     *
     * <p>图书馆界面用它当列表来源：<b>图鉴决定「能取什么」</b>（设计文档 §8）。
     * 排序是必需的——不排序会让行位置在每次刷新时随机跳动。
     */
    public List<UnlockedTier> unlockedTiers() {
        List<UnlockedTier> out = new java.util.ArrayList<>();
        for (Map.Entry<ResourceLocation, Integer> entry : this.unlocked.entrySet()) {
            for (AscensionTier tier : AscensionTier.values()) {
                if ((entry.getValue() & (1 << tier.ordinal())) != 0) {
                    out.add(new UnlockedTier(entry.getKey(), tier));
                }
            }
            // 基础阶单独处理：它不在 AscensionTier 里，图书馆列表也按 AscensionTier 组织，
            // 所以这里只负责让它「被记录」，不产出 UnlockedTier。
        }
        out.sort(java.util.Comparator
                .comparing((UnlockedTier u) -> u.root().toString())
                .thenComparingInt(u -> u.tier().ordinal()));
        return out;
    }

    /** 图鉴里的一条已解锁组合。 */
    public record UnlockedTier(ResourceLocation root, AscensionTier tier) {}

    /** 已解锁的谱系条数（不是「阶级数」）。 */
    public int lineageCount() {
        return this.unlocked.size();
    }

    public boolean isEmpty() {
        return this.unlocked.isEmpty();
    }
}
