package com.lyingice.ultraenchantment.compat.jei;

import com.lyingice.ultraenchantment.content.AscensionData;
import com.lyingice.ultraenchantment.content.AscensionTier;
import com.lyingice.ultraenchantment.content.BookSpecs;
import com.lyingice.ultraenchantment.content.BookSubject;
import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.content.StageDefinition;
import com.lyingice.ultraenchantment.event.ReloadEvents;
import com.lyingice.ultraenchantment.logic.BookFactory;
import com.lyingice.ultraenchantment.logic.InscriptionLogic;
import com.lyingice.ultraenchantment.logic.StageLookup;
import com.lyingice.ultraenchantment.logic.UELookups;
import com.lyingice.ultraenchantment.registry.UEComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * 从<b>数据包动态生成</b>铁砧配方显示——不硬编码任何谱系名。
 *
 * <h2>三类内容</h2>
 * <ol>
 *   <li><b>附魔进阶</b>：物品 + 进阶书 → 推进一步。左槽展示的是<b>刚好够门槛</b>的等级
 *       （进阶要求取 {@code min(目标阶 required_level, 源阶段上限)}，与铁砧判定同源），
 *       悬停给出「要求满级」或「要求达到 X 级」。</li>
 *   <li><b>附魔合并</b>：书 + 书。两本载体书合并、载体书 + 进阶书推阶、载体书 + 升级书提升、
 *       原版附魔书 + 进阶书转印、两本升级书合并——<b>结果一律调用铁砧那套纯函数算</b>
 *       （{@link InscriptionLogic}），不手写近似值，逻辑改了这里跟着变。</li>
 *   <li><b>附魔升级</b>：物品 + 升级书 → 同阶把曲线等级提到目标值。<b>中间等级轮播</b>：
 *       一条配方把 2..上限 的升级书与对应结果都放进槽里，JEI 自己轮播。</li>
 * </ol>
 *
 * <h2>为什么必须用客户端注册表</h2>
 * <p>这些 {@link ItemStack} 是客户端生成并展示的，JEI 作弊模式还能把它们直接塞到玩家手上——
 * 一旦编码就用客户端注册表。所以取附魔 Holder 必须走
 * {@link UELookups#enchantmentsForItemWrites}(true)，否则在单机上会拿到服务端 Holder，
 * 玩家一点就掉线（P0-17）。
 */
final class UEJeiRecipes {
    private UEJeiRecipes() {}

    static List<AnvilDisplay> build() {
        List<AnvilDisplay> out = new ArrayList<>();

        HolderLookup.RegistryLookup<StageDefinition> stages = StageLookup.lookup();
        HolderLookup.RegistryLookup<Enchantment> enchants = UELookups.enchantmentsForItemWrites(true);
        if (stages == null || enchants == null) {
            return out;
        }

        for (AscensionTier tier : AscensionTier.values()) {
            for (ResourceLocation root : ReloadEvents.roots()) {
                if (!ReloadEvents.hasTier(root, tier)) {
                    continue;
                }
                StageDefinition stage = StageLookup.stageOf(stages, root, tier.asLineageTier()).orElse(null);
                ResourceLocation stageId = StageLookup.stageIdOf(stages, root, tier.asLineageTier()).orElse(null);
                Holder<Enchantment> ench = enchant(enchants, root);
                Item item = firstItem(stage);
                if (stage == null || stageId == null || ench == null || item == null) {
                    continue;
                }

                int cap = Math.max(1, stage.definition().maxLevel());
                LineageTier from = previousOf(tier);
                ResourceLocation fromStageId = from == LineageTier.NATIVE ? null
                        : StageLookup.stageIdOf(stages, root, from).orElse(null);

                // 进阶门槛：与 AscensionLogic 一字不差——min(目标阶 required_level, 源阶段上限)。
                int sourceMax = sourceMax(stages, ench, root, from, cap);
                int gate = Math.min(stage.requiredLevel(), sourceMax);

                // ── ① 附魔进阶：物品 + 进阶书 ──
                // 左槽用「刚好够门槛」的等级：基础阶比存储等级，已进阶比曲线等级（同 AscensionLogic）。
                out.add(new AnvilDisplay(AnvilDisplay.Kind.ASCEND,
                        List.of(staged(item, ench, root, fromStageId,
                                from == LineageTier.NATIVE ? gate : 1, gate)),
                        List.of(targetedAscensionBook(from, tier.asLineageTier(), root)),
                        List.of(staged(item, ench, root, stageId, 1, 1)),
                        List.of(requirement(ench, gate, sourceMax),
                                Component.translatable("jei.ultraenchantment.note.ascend"))));

                // ── ③ 附魔升级：物品 + 升级书（中间等级轮播）──
                if (cap >= 2) {
                    List<ItemStack> books = new ArrayList<>();
                    List<ItemStack> upgraded = new ArrayList<>();
                    for (int level = 2; level <= cap; level++) {
                        books.add(upgradeBook(tier, level));
                        upgraded.add(staged(item, ench, root, stageId, 1, level));
                    }
                    out.add(new AnvilDisplay(AnvilDisplay.Kind.UPGRADE,
                            List.of(staged(item, ench, root, stageId, 1, 1)),
                            books,
                            upgraded,
                            List.of(Component.translatable("jei.ultraenchantment.note.upgrade"))));
                }

                // ── ② 附魔合并：载体书 + 进阶书 / 载体书 + 升级书 ──
                AscensionTier fromAsc = AscensionTier.of(from).orElse(null);
                if (fromAsc != null) {
                    BookSpecs.Inscription carrier = new BookSpecs.Inscription(fromAsc,
                            List.of(new BookSpecs.Inscription.Entry(root, gate)));

                    InscriptionLogic.advanceTier(stages, carrier, from, tier, false).ifPresent(advanced ->
                            out.add(new AnvilDisplay(AnvilDisplay.Kind.MERGE,
                                    List.of(BookFactory.inscription(carrier)),
                                    List.of(targetedAscensionBook(from, tier.asLineageTier(), root)),
                                    List.of(BookFactory.inscription(advanced)),
                                    List.of(Component.translatable("jei.ultraenchantment.note.merge.advance")))));

                    // ⚠️ 升级书只作用于**同阶级**的书（InscriptionLogic 里 tier != book.tier() 直接返回空），
                    // 所以这里必须用「载体书自己那一阶」和「那一阶的上限」，而不是目标阶。
                    // （第一版传了目标阶，62 条配方全被静默拒掉——配方总数把它暴露了出来。）
                    InscriptionLogic.upgradeEntries(stages, carrier, fromAsc, sourceMax).ifPresent(raised ->
                            out.add(new AnvilDisplay(AnvilDisplay.Kind.MERGE,
                                    List.of(BookFactory.inscription(carrier)),
                                    List.of(upgradeBook(fromAsc, sourceMax)),
                                    List.of(BookFactory.inscription(raised)),
                                    List.of(Component.translatable("jei.ultraenchantment.note.merge.upgrade")))));
                }

                // ── ② 附魔合并：原版附魔书 + 进阶书 → 转印（只在 基础→高阶 这一步）──
                if (tier == AscensionTier.ADVANCED) {
                    int vanillaGate = Math.min(stage.requiredLevel(), ench.value().getMaxLevel());
                    ItemStack vanillaBook = enchantedBook(ench, vanillaGate);
                    InscriptionLogic.transcribe(stages, enchants, vanillaBook, tier, false).ifPresent(transcribed ->
                            out.add(new AnvilDisplay(AnvilDisplay.Kind.MERGE,
                                    List.of(vanillaBook),
                                    List.of(targetedAscensionBook(LineageTier.NATIVE, LineageTier.ADVANCED, root)),
                                    List.of(BookFactory.inscription(transcribed.payload())),
                                    List.of(Component.translatable("jei.ultraenchantment.note.merge.vanilla")))));
                }
            }

            // ── ② 附魔合并：两本载体书（每阶级一条样本）──
            // 样本必须是「能贴到同一件物品上」的一对：否则展示的就是一本永远用不全的书（P1-37 的教训）。
            List<BookSpecs.Inscription.Entry> pair = compatiblePair(stages, tier);
            if (pair.size() == 2) {
                BookSpecs.Inscription leftPayload = new BookSpecs.Inscription(tier, List.of(pair.get(0)));
                BookSpecs.Inscription rightPayload = new BookSpecs.Inscription(tier, List.of(pair.get(1)));
                InscriptionLogic.mergeBooks(stages, enchants, leftPayload, rightPayload).ifPresent(merged ->
                        out.add(new AnvilDisplay(AnvilDisplay.Kind.MERGE,
                                List.of(BookFactory.inscription(leftPayload)),
                                List.of(BookFactory.inscription(rightPayload)),
                                List.of(BookFactory.inscription(merged.payload())),
                                List.of(Component.translatable("jei.ultraenchantment.note.merge.carrier")))));
            }

            // ── ② 附魔合并：两本升级书（规则照抄 AnvilEvents.applyBookMerge）──
            int globalMax = ReloadEvents.globalMaxLevel();
            int bookTarget = Math.min(2, globalMax);
            if (bookTarget > 1) {
                out.add(new AnvilDisplay(AnvilDisplay.Kind.MERGE,
                        List.of(upgradeBook(tier, 1)),
                        List.of(upgradeBook(tier, 1)),
                        List.of(upgradeBook(tier, bookTarget)),
                        List.of(Component.translatable("jei.ultraenchantment.note.merge.upgradebook"))));
            }
        }
        return out;
    }

    // ── 进阶要求文案 ────────────────────────────────────────────────────

    /**
     * 「达到满级」还是「达到 X 级」——按机制本身决定，哪个适用就显示哪个。
     *
     * <p>门槛恰等于源阶段上限时，语义就是「满级」；否则是「指定等级」。
     */
    private static Component requirement(Holder<Enchantment> ench, int gate, int sourceMax) {
        if (gate >= sourceMax) {
            return Component.translatable("jei.ultraenchantment.require.max",
                    ench.value().description(), gate);
        }
        return Component.translatable("jei.ultraenchantment.require.level",
                ench.value().description(), gate, sourceMax);
    }

    /** 源阶段上限：基础阶取原版 max_level，已进阶取该阶级阶段条目的 max_level（同 AscensionLogic）。 */
    private static int sourceMax(HolderLookup.RegistryLookup<StageDefinition> stages,
                                 Holder<Enchantment> ench, ResourceLocation root,
                                 LineageTier from, int fallback) {
        if (from == LineageTier.NATIVE) {
            return ench.value().getMaxLevel();
        }
        return Math.max(1, StageLookup.maxLevelOf(stages, from, root, fallback));
    }

    // ── 物品构造 ────────────────────────────────────────────────────────

    private static LineageTier previousOf(AscensionTier tier) {
        return switch (tier) {
            case ADVANCED -> LineageTier.NATIVE;
            case SUPER -> LineageTier.ADVANCED;
            case ULTRA -> LineageTier.SUPER;
        };
    }

    private static Holder<Enchantment> enchant(HolderLookup.RegistryLookup<Enchantment> enchants,
                                               ResourceLocation root) {
        return enchants.get(ResourceKey.create(Registries.ENCHANTMENT, root))
                .map(h -> (Holder<Enchantment>) h).orElse(null);
    }

    private static Item firstItem(StageDefinition stage) {
        if (stage == null) {
            return null;
        }
        for (Holder<Item> holder : stage.definition().supportedItems()) {
            return holder.value();
        }
        return null;
    }

    private static ItemStack targetedAscensionBook(LineageTier from, LineageTier to, ResourceLocation root) {
        ItemStack book = BookFactory.create(BookSubject.ASCENSION, to.asViewTier().orElse(
                com.lyingice.ultraenchantment.content.UETier.ADVANCED));
        book.set(UEComponents.ASCENSION_SPEC.get(), new BookSpecs.Ascension(from, to, Optional.of(root)));
        return book;
    }

    private static ItemStack upgradeBook(AscensionTier tier, int targetLevel) {
        ItemStack book = BookFactory.create(BookSubject.UPGRADE, tier.asViewTier());
        book.set(UEComponents.UPGRADE_SPEC.get(), new BookSpecs.Upgrade(tier, targetLevel));
        return book;
    }

    private static ItemStack enchantedBook(Holder<Enchantment> ench, int level) {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable table = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        table.set(ench, Math.max(1, level));
        EnchantmentHelper.setEnchantments(stack, table.toImmutable());
        return stack;
    }

    /**
     * 造一件展示用物品。
     *
     * @param stageId     为 null 时只带原版附魔（尚未进阶的输入）
     * @param storedLevel 存储等级（原版机制那条账）
     * @param tierLevel   进阶曲线等级（进阶形态自己的等级，决定显示名后缀）
     */
    private static ItemStack staged(Item item, Holder<Enchantment> ench, ResourceLocation root,
                                    ResourceLocation stageId, int storedLevel, int tierLevel) {
        ItemStack stack = new ItemStack(item);
        ItemEnchantments.Mutable table = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        table.set(ench, Math.max(1, storedLevel));
        EnchantmentHelper.setEnchantments(stack, table.toImmutable());
        if (stageId != null) {
            stack.set(UEComponents.ASCENSION.get(), AscensionData.EMPTY.with(root, stageId, tierLevel));
        }
        return stack;
    }

    /**
     * 该阶级下「能贴到同一件物品上」的一对谱系（各取该阶级满级）——合并样本用。
     *
     * <p>取 {@code supported_items} 有交集的前两条：样本必须真的能用出去（P1-37）。
     */
    private static List<BookSpecs.Inscription.Entry> compatiblePair(
            HolderLookup.RegistryLookup<StageDefinition> stages, AscensionTier tier) {
        List<BookSpecs.Inscription.Entry> entries = new ArrayList<>();
        HolderSet<Item> firstItems = null;

        for (ResourceLocation root : ReloadEvents.roots()) {
            if (!ReloadEvents.hasTier(root, tier)) {
                continue;
            }
            StageDefinition stage = StageLookup.stageOf(stages, root, tier.asLineageTier()).orElse(null);
            if (stage == null) {
                continue;
            }
            if (firstItems == null) {
                firstItems = stage.definition().supportedItems();
                entries.add(new BookSpecs.Inscription.Entry(root,
                        ReloadEvents.maxLevelOf(root, tier, 1)));
                continue;
            }
            for (Holder<Item> item : stage.definition().supportedItems()) {
                if (firstItems.contains(item)) {
                    entries.add(new BookSpecs.Inscription.Entry(root,
                            ReloadEvents.maxLevelOf(root, tier, 1)));
                    return entries;
                }
            }
        }
        return entries;
    }
}
