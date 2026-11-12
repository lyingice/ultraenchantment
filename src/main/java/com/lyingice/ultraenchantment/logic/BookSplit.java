package com.lyingice.ultraenchantment.logic;

import com.lyingice.ultraenchantment.content.AscensionData;
import com.lyingice.ultraenchantment.content.AscensionTier;
import com.lyingice.ultraenchantment.content.BookSpecs;
import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.content.StageDefinition;
import com.lyingice.ultraenchantment.registry.UEComponents;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * <b>普通附魔书 / 进阶附魔书的拆分</b>——消灭「原版附魔书 + 隐形 ascension」这种混合态。
 *
 * <h2>要解决的问题</h2>
 *
 * <p>本模组的进阶形态有<b>两个宿主</b>：装备和载体书。原版附魔书是第三个东西——
 * 它只能装 {@code minecraft:stored_enchantments}，装不下「某条谱系已进阶到某阶」。
 * 但历史上为了「至少不丢身份」，宝典拆解、附魔编辑台导出等路径会把进阶记录写进
 * 原版附魔书的 {@code ultraenchantment:ascension} 组件，形成：
 *
 * <pre>
 *   minecraft:enchanted_book
 *     + stored_enchantments: [锋利 V（普通）, 抢夺 III（普通）]
 *     + ultraenchantment:ascension: {sharpness: ultra/sharpness}
 * </pre>
 *
 * <p>这种书看起来是「普通书」，进图书馆会被按基础阶吸收、进阶身份蒸发；
 * 拿到铁砧上又只能走原版书分支。玩家看到的就是「书上的高阶附魔废了」。
 *
 * <h2>拆分规则</h2>
 *
 * <ul>
 *   <li>没有 ascension 记录 → 原样返回（普通书不拆）。</li>
 *   <li>有记录但某条附魔<b>不在</b> stored_enchantments 里 → 该记录是死数据，忽略。</li>
 *   <li>stored 里有记录、且记录指向合法阶级 → 进<b>载体书</b>（按阶级分组，
 *       因为载体书的 tier 是书级单值）。</li>
 *   <li>其余 stored 附魔 → 留在<b>普通附魔书</b>里，ascension 组件被移除。</li>
 * </ul>
 *
 * <p>于是「普通附魔书」与「进阶附魔书」从源头分开：普通书里不再夹带进阶记录，
 * 进阶记录全部落到真正的载体书上，任何一条路径都不会再把它们当成基础阶吞掉。
 */
public final class BookSplit {
    private BookSplit() {}

    /**
     * 拆分结果。
     *
     * @param vanilla  只含基础阶附魔的原版附魔书；没有基础阶条目时为空栈
     * @param advanced 每阶一本载体书（最多三本）；没有进阶条目时为空表
     */
    public record Split(ItemStack vanilla, List<ItemStack> advanced) {
        public Split {
            advanced = List.copyOf(advanced);
        }

        public boolean hasAdvanced() {
            return !this.advanced.isEmpty();
        }

        public boolean isEmpty() {
            return this.vanilla.isEmpty() && this.advanced.isEmpty();
        }

        /** 普通书 + 所有载体书（普通书在前，保持「基础 → 进阶」的阅读顺序）。 */
        public List<ItemStack> all() {
            List<ItemStack> out = new ArrayList<>(1 + this.advanced.size());
            if (!this.vanilla.isEmpty()) {
                out.add(this.vanilla);
            }
            out.addAll(this.advanced);
            return List.copyOf(out);
        }
    }

    /** 一本原版附魔书的拆分（非原版附魔书返回空拆分）。 */
    public static Split of(ItemStack book) {
        if (book.isEmpty() || !book.is(Items.ENCHANTED_BOOK)) {
            return new Split(ItemStack.EMPTY, List.of());
        }
        ItemEnchantments stored = EnchantmentHelper.getEnchantmentsForCrafting(book);
        AscensionData data = UEComponents.ascensionOf(book);
        if (data.stages().isEmpty()) {
            return new Split(book.copy(), List.of());
        }

        ItemEnchantments.Mutable base = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        boolean anyBase = false;
        Map<AscensionTier, List<BookSpecs.Inscription.Entry>> groups = new LinkedHashMap<>();
        HolderLookup.RegistryLookup<StageDefinition> stages = StageLookup.lookup();

        for (var entry : stored.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            ResourceLocation root = UERoots.rootOf(holder.value()).orElse(null);
            ResourceLocation stageId = root == null ? null : data.stages().get(root);
            AscensionTier tier = tierOf(stageId);
            if (root == null || tier == null) {
                // 没有记录 / 记录指向基础阶或非法阶段 ⇒ 这条仍是普通附魔。
                base.set(holder, entry.getIntValue());
                anyBase = true;
                continue;
            }
            int level = Math.max(1, data.tierLevelOf(root));
            int cap = StageLookup.maxLevelOf(stages, tier.asLineageTier(), root, level);
            groups.computeIfAbsent(tier, key -> new ArrayList<>())
                    .add(new BookSpecs.Inscription.Entry(root, Math.min(level, Math.max(1, cap))));
        }

        List<ItemStack> advanced = new ArrayList<>(3);
        for (AscensionTier tier : AscensionTier.values()) {
            List<BookSpecs.Inscription.Entry> entries = groups.get(tier);
            if (entries != null && !entries.isEmpty()) {
                advanced.add(BookFactory.inscription(new BookSpecs.Inscription(tier, entries)));
            }
        }

        ItemStack vanilla = ItemStack.EMPTY;
        if (anyBase) {
            vanilla = book.copy();
            EnchantmentHelper.setEnchantments(vanilla, base.toImmutable());
            vanilla.remove(UEComponents.ASCENSION.get());
        }
        return new Split(vanilla, advanced);
    }

    /** 只取普通书那一半；没有基础阶条目时返回空栈。 */
    public static ItemStack baseOnly(ItemStack book) {
        return of(book).vanilla();
    }

    /** 只取载体书那一半。 */
    public static List<ItemStack> advancedOnly(ItemStack book) {
        return of(book).advanced();
    }

    /**
     * 把<b>装备</b>上的附魔拆成「普通书 + 载体书」。
     *
     * <p>用于附魔编辑台的「拿下」：装备上的附魔本来就是 {@code minecraft:enchantments}，
     * 这里先临时装进一本原版附魔书的形状，再复用 {@link #of(ItemStack)} 的同一套规则。
     */
    public static List<ItemStack> splitEquipment(ItemStack item) {
        if (item.isEmpty()) {
            return List.of();
        }
        AscensionData data = UEComponents.ascensionOf(item);
        if (data.stages().isEmpty()) {
            return List.of();
        }
        ItemStack temp = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.setEnchantments(temp, EnchantmentHelper.getEnchantmentsForCrafting(item));
        UEComponents.setAscension(temp, data);
        return of(temp).all();
    }

    /** 把一本书按「普通 / 进阶」拆成多本后交给玩家；背包满则掉在脚下，绝不静默吞掉。 */
    public static void give(Player player, List<ItemStack> books) {
        for (ItemStack book : books) {
            if (book.isEmpty()) {
                continue;
            }
            if (!player.getInventory().add(book)) {
                player.drop(book, false);
            }
        }
    }

    /** 阶段 id → 进阶档位；基础阶 / 非法 id 返回 {@code null}。 */
    private static AscensionTier tierOf(ResourceLocation stageId) {
        if (stageId == null) {
            return null;
        }
        LineageTier lineage = UERoots.lineageTier(UERoots.tierOfStage(stageId)).orElse(null);
        if (lineage == null || lineage == LineageTier.NATIVE) {
            return null;
        }
        return AscensionTier.values()[lineage.ordinal() - 1];
    }
}
