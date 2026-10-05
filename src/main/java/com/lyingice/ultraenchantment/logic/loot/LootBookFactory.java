package com.lyingice.ultraenchantment.logic.loot;

import com.lyingice.ultraenchantment.content.AscensionTier;
import com.lyingice.ultraenchantment.content.BookSpecs;
import com.lyingice.ultraenchantment.content.BookSubject;
import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.event.ReloadEvents;
import com.lyingice.ultraenchantment.logic.BookFactory;
import com.lyingice.ultraenchantment.registry.UEComponents;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * <b>战利品用</b>的进阶附魔书生成——决定「掉哪一阶、哪条谱系、什么等级」。
 *
 * <h2>为什么必须用代码而不是静态战利品表 JSON</h2>
 *
 * <p>战利品表 JSON 是<b>静态</b>的：{@code set_components} 只能写<b>固定</b>组件值。
 * 而我们要的是「随机一条谱系 + 随机等级」——静态 JSON 表达不了。
 *
 * <p>原版 {@code EnchantRandomlyFunction} 确实能随机挑附魔，但它写的是
 * {@code minecraft:enchantments}（原版附魔注册表），
 * <b>填不了我们的自定义载荷组件</b>（{@code ascension_spec} 等）。
 *
 * <p>因此随机必须在代码里做 ⇒ 由 {@link ULTBookLootModifier} 调用本类。
 *
 * <h2>池子来自数据包，不硬编码</h2>
 *
 * <p>与 {@code LineageTradePool} 同源：走 {@link ReloadEvents#roots()} 与
 * {@link ReloadEvents#hasTier}，所以<b>以后新增谱系会自动进池</b>。
 * 把谱系写死成常量正是 {@code ReloadEvents} 类文档反复强调的坑。
 *
 * <h2>阶级怎么选</h2>
 *
 * <p>权重由 {@link LootTierWeights} 决定（含来源修正），本类只负责「按权重掷一次」
 * 与「用该阶造一本合法的书」。
 */
public final class LootBookFactory {
    private LootBookFactory() {}

    /**
     * 掷出一本书；不满足条件时返回空。
     *
     * <p>返回的是<b>成品书</b>（已写载荷，捡到即可用），不是空白书。
     *
     * @param random      随机源（来自 LootContext）
     * @param lookup      附魔注册表查询，用于把附魔 id 解析成 holder
     * @param tier        已由权重选定的阶级
     */
    public static Optional<ItemStack> roll(RandomSource random, HolderLookup.Provider lookup, AscensionTier tier) {
        List<ResourceLocation> pool = candidates(tier);
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation rootId = pool.get(random.nextInt(pool.size()));

        Optional<Holder<Enchantment>> ench = lookup.lookup(Registries.ENCHANTMENT)
                .flatMap(reg -> reg.get(ResourceKey.create(Registries.ENCHANTMENT, rootId)));
        if (ench.isEmpty()) {
            return Optional.empty();
        }

        // 等级随机 1..该阶该谱系的上限（逐谱系，见 P1-28）。
        int cap = Math.max(1, ReloadEvents.maxLevelOf(rootId, tier, 1));
        int level = 1 + random.nextInt(cap);

        return Optional.of(build(tier, rootId, level));
    }

    /**
     * 当前可作为掉落对象的谱系——<b>只取定义了该阶级阶段的谱系</b>。
     *
     * <p>数据包没铺某一阶时，产出那一阶的书是不合法的（与创造栏、
     * {@code LineageTradePool} 的判据一致）。
     */
    public static List<ResourceLocation> candidates(AscensionTier tier) {
        List<ResourceLocation> roots = ReloadEvents.roots();
        if (roots.isEmpty()) {
            return List.of();
        }
        return roots.stream().filter(root -> ReloadEvents.hasTier(root, tier)).toList();
    }

    /**
     * 按阶级造一本带载荷的书。
     *
     * <h2>为什么三阶都用铭刻型（载体书）</h2>
     *
     * <p>铭刻型是<b>进阶形态的实物载体</b>（见 docs/book-system-spec.md）：
     * 它像原版附魔书一样直接承载「某阶级的某条进阶附魔」，
     * 捡到就能贴装备——这正是「战利品」应有的语义。
     *
     * <p>进阶书（进化型）与升级书描述的是「操作」而非「形态」，
     * 作为掉落会让玩家拿到需要另凑材料才能用的半成品，不符合战利品直觉。
     */
    private static ItemStack build(AscensionTier tier, ResourceLocation rootId, int level) {
        ItemStack book = BookFactory.create(BookSubject.INSCRIPTION, tier.asViewTier());
        book.set(UEComponents.INSCRIPTION_SPEC.get(),
                new BookSpecs.Inscription(tier, List.of(new BookSpecs.Inscription.Entry(rootId, level))));
        return book;
    }

    /**
     * 造一本「基础 → 指定阶」的定向进阶书（备用出口）。
     *
     * <p>当前未被 {@link ULTBookLootModifier} 使用——保留它是为了让「战利品也可以
     * 掉进阶书而非成品」这件事有现成入口；若将来要调整掉落口味，改一行即可。
     */
    public static Optional<ItemStack> rollAscension(RandomSource random, HolderLookup.Provider lookup,
                                                    AscensionTier toTier) {
        List<ResourceLocation> pool = candidates(toTier);
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation rootId = pool.get(random.nextInt(pool.size()));
        ItemStack book = BookFactory.create(BookSubject.ASCENSION, toTier.asViewTier());
        book.set(UEComponents.ASCENSION_SPEC.get(),
                new BookSpecs.Ascension(LineageTier.NATIVE, toTier.asLineageTier(), Optional.of(rootId)));
        return Optional.of(book);
    }
}
