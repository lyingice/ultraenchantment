package com.lyingice.ultraenchantment.logic.trade;

import com.lyingice.ultraenchantment.content.AscensionTier;
import com.lyingice.ultraenchantment.event.ReloadEvents;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 交易用的谱系随机池。
 *
 * <h2>池子来源必须是数据包，不能是常量</h2>
 *
 * <p>用 {@code ReloadEvents.roots()}——它就是「数据包里实际存在哪些谱系」快照
 * （{@code matrix} 的键集）。这样<b>以后新增谱系会自动进池</b>，
 * 不需要回来改这里。把谱系硬编码成常量，正是 {@code ReloadEvents} 类文档里
 * 反复强调过的坑。
 *
 * <p>⚠️ 池子只取「定义了 {@link AscensionTier#ADVANCED} 阶段条目」的谱系：
 * 高阶是低等级交易的前提，没有高阶条目就产不出合法的定向书。
 *
 * <h2>随机范围仅限高阶形态</h2>
 *
 * <p>需求明确「随机范围仅限高阶」。因此这里只暴露满级上限的查询，
 * 且调用方一律用 {@link AscensionTier#ADVANCED}。
 */
public final class LineageTradePool {
    private LineageTradePool() {}

    /** 当前可作为交易对象的谱系（已排序，稳定顺序）。 */
    public static List<ResourceLocation> candidates() {
        List<ResourceLocation> roots = ReloadEvents.roots();
        if (roots.isEmpty()) {
            return List.of();
        }
        return roots.stream()
                .filter(root -> ReloadEvents.hasTier(root, AscensionTier.ADVANCED))
                .toList();
    }

    /** 随机抽一条谱系；池子为空时返回空。 */
    public static Optional<ResourceLocation> pick(RandomSource random) {
        List<ResourceLocation> pool = candidates();
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(pool.get(random.nextInt(pool.size())));
    }

    /**
     * 取某条谱系在高阶的等级上限。
     *
     * <p>用 {@code ReloadEvents.maxLevelOf}（逐谱系）而不是全局最大值——
     * 全局最大值会产出「永远无法写入」的等级（例如拿 5 去造耐久书，而耐久上限是 3）。
     */
    public static int advancedMaxLevel(ResourceLocation root, int fallback) {
        return ReloadEvents.maxLevelOf(root, AscensionTier.ADVANCED, fallback);
    }

    /** 把某个附魔 id 解析成注册表 Holder；注册表不可用时返回空。 */
    public static Optional<net.minecraft.core.Holder<Enchantment>> enchantment(
            HolderLookup.Provider registries, ResourceLocation id) {
        return registries.lookup(Registries.ENCHANTMENT)
                .flatMap(lookup -> lookup.get(ResourceKey.create(Registries.ENCHANTMENT, id)))
                .map(holder -> (net.minecraft.core.Holder<Enchantment>) holder);
    }
}
