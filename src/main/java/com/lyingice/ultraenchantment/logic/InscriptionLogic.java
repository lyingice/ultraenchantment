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
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * <b>载体书（铭刻型）的解析</b>——纯函数，铁砧两侧共用。
 *
 * <p>三件事：贴装备（{@link #resolve}）、转印（{@link #transcribe}）、两本合并（{@link #mergeBooks}）。
 * 规则全部见 docs/book-system-spec.md §5。
 *
 * <h2>为什么是纯函数</h2>
 *
 * <p>{@code AnvilMenu.createResult} 在客户端也执行（AGENT.md P0-4），因此这里不碰世界、背包、随机数；
 * 取不到注册表就返回空，绝不抛。
 *
 * <h2>剩菜为什么在解析里</h2>
 *
 * <p>「哪些条目吃下去了、哪些没吃」是解析的自然产物。铁砧只有一个输出槽，
 * 剩菜必须在<b>取件那一刻</b>用同一份输入重算一次（{@code AnvilTakeEvents}），
 * 所以解析必须保证：同样输入永远得到同样结果。
 */
public final class InscriptionLogic {
    private InscriptionLogic() {}

    /** 贴装备的解析结果。{@code output} 一定非空（调用方只在 {@code applied} 非空时使用）。 */
    public record Resolution(List<BookSpecs.Inscription.Entry> applied,
                             List<BookSpecs.Inscription.Entry> leftovers,
                             ItemStack output,
                             int cost) {}

    /** 转印结果：产物载荷 + 铁砧成本。 */
    public record Transcription(BookSpecs.Inscription payload, int cost) {}

    /** 两本载体书合并的结果。 */
    public record Merged(BookSpecs.Inscription payload, int cost) {}

    // ── ① 贴装备 ────────────────────────────────────────────────────────

    /**
     * 载体书 → 装备。
     *
     * <p>逐条判定，<b>改变的才算吃下去</b>，其余进剩菜；一条都没改变则整次操作无产出
     * （书不消耗，等价于原版「无效操作」）。
     *
     * @param bypass 创造旁路：放开「目标谱系必须已有记录」（规格 §5.2，允许白装备直接上究极）
     */
    public static Optional<Resolution> resolve(HolderLookup.RegistryLookup<StageDefinition> stages,
                                               HolderLookup.RegistryLookup<Enchantment> enchants,
                                               ItemStack left,
                                               BookSpecs.Inscription spec,
                                               boolean bypass) {
        if (stages == null || enchants == null || left.isEmpty() || spec.isEmpty()) {
            return Optional.empty();
        }
        // 原版附魔书不走这条路——它只能被「进阶书」转印（见 transcribe）。
        // 少了这道守卫，就会被写成一个「有进阶记录的原版书」这种死状态。
        if (left.is(Items.ENCHANTED_BOOK)) {
            return Optional.empty();
        }

        List<BookSpecs.Inscription.Entry> applied = new ArrayList<>();
        List<BookSpecs.Inscription.Entry> leftovers = new ArrayList<>();
        ItemStack out = left.copy();
        AscensionData data = UEComponents.ascensionOf(out);
        ItemEnchantments.Mutable table = null;
        int cost = 0;

        for (BookSpecs.Inscription.Entry entry : spec.entries()) {
            ResourceLocation root = entry.enchantment();
            Holder<Enchantment> ench = enchants.get(ResourceKey.create(Registries.ENCHANTMENT, root))
                    .map(h -> (Holder<Enchantment>) h).orElse(null);
            if (ench == null || !out.supportsEnchantment(ench)) {
                leftovers.add(entry);
                continue;
            }

            LineageTier current = AscensionLogic.currentTierOf(stages, out, root);

            // ① 原生阶：生存拒绝（必须先用进阶书把阶级升上来），创造旁路直接授予。
            if (current == LineageTier.NATIVE) {
                if (!bypass) {
                    leftovers.add(entry);
                    continue;
                }
                LineageTier target = spec.tier().asLineageTier();
                ResourceLocation stageId = StageLookup.stageIdOf(stages, root, target).orElse(null);
                StageDefinition stage = stageId == null ? null
                        : StageLookup.byId(stages, stageId).orElse(null);
                if (stage == null) {
                    leftovers.add(entry);
                    continue;
                }
                int cap = Math.max(1, stage.definition().maxLevel());
                int level = clamp(entry.level(), cap);
                data = data.with(root, stageId, level);
                if (table == null) {
                    table = mutableOf(out);
                }
                // 存储等级设为该阶上限：结算层要求存储等级 > 0 才会注入阶段（EnchantmentLevelEvents），
                // 而原版机制看到的也应该是「满级附魔」。
                table.set(ench, cap);
                cost += costOf(ench, level);
                applied.add(entry);
                continue;
            }

            // ② 阶级不符：拒绝该条目（不倒退、不靠贴书跳阶）。
            if (AscensionTier.of(current).orElse(null) != spec.tier()) {
                leftovers.add(entry);
                continue;
            }

            // ③ 同阶级：把曲线等级提上去——这就是「铭刻型升级书」。
            int cap = StageLookup.maxLevelOf(stages, current, root, entry.level());
            int targetLevel = clamp(entry.level(), cap);
            if (targetLevel <= data.tierLevelOf(root)) {
                leftovers.add(entry);
                continue;
            }
            data = data.withTierLevel(root, targetLevel);
            cost += costOf(ench, targetLevel);
            applied.add(entry);
        }

        if (applied.isEmpty()) {
            return Optional.empty();
        }
        UEComponents.setAscension(out, data);
        if (table != null) {
            EnchantmentHelper.setEnchantments(out, table.toImmutable());
        }
        return Optional.of(new Resolution(List.copyOf(applied), List.copyOf(leftovers), out, Math.max(1, cost)));
    }

    // ── ② 转印 ──────────────────────────────────────────────────────────

    /**
     * 转印：原版附魔书 + 「基础→X」进阶书 → 载体书（规格 §5.5）。
     *
     * <p>门槛逐条判：原版书上的存储等级必须 ≥ {@code min(该阶 required_level, 原版 max_level)}。
     * <b>任一条不够就整本不转</b>（§9-1 已确认）——比「部分搬走、剩菜留原版书」简单且可预测。
     *
     * <p>条目一律从 {@code level = 1} 起——原版书上的 V 的价值是「够得上门槛」，
     * 与装备侧「升阶后曲线归 1」同构。
     */
    public static Optional<Transcription> transcribe(HolderLookup.RegistryLookup<StageDefinition> stages,
                                                     HolderLookup.RegistryLookup<Enchantment> enchants,
                                                     ItemStack vanillaBook,
                                                     AscensionTier toTier,
                                                     boolean bypass) {
        if (stages == null || enchants == null || toTier == null) {
            return Optional.empty();
        }
        if (!vanillaBook.is(Items.ENCHANTED_BOOK)) {
            return Optional.empty();
        }
        ItemEnchantments stored = EnchantmentHelper.getEnchantmentsForCrafting(vanillaBook);
        if (stored.isEmpty()) {
            return Optional.empty();
        }

        List<BookSpecs.Inscription.Entry> entries = new ArrayList<>();
        int cost = 1;
        for (var entry : stored.entrySet()) {
            Holder<Enchantment> ench = entry.getKey();
            int level = entry.getIntValue();
            ResourceLocation root = ench.unwrapKey().map(ResourceKey::location).orElse(null);
            if (root == null || level <= 0) {
                return Optional.empty();
            }
            ResourceLocation stageId = StageLookup.stageIdOf(stages, root, toTier.asLineageTier()).orElse(null);
            StageDefinition stage = stageId == null ? null : StageLookup.byId(stages, stageId).orElse(null);
            if (stage == null) {
                return Optional.empty();   // 该谱系没铺这一阶 → 不产出拿不到的书
            }
            if (!bypass) {
                int gate = Math.min(stage.requiredLevel(), ench.value().getMaxLevel());
                if (level < gate) {
                    return Optional.empty();
                }
            }
            entries.add(new BookSpecs.Inscription.Entry(root, 1));
            cost = Math.max(cost, stage.definition().anvilCost());
        }
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Transcription(
                new BookSpecs.Inscription(toTier, List.copyOf(entries)), Math.max(1, cost)));
    }

    // ── ③ 书 + 书 ───────────────────────────────────────────────────────

    /**
     * 两本载体书合并（规格 §5.4）：同阶级 → 条目并集；同一附魔同级 → +1（逐谱系夹取）、不同级 → 取 max。
     *
     * <p>右槽没带来任何变化时返回空——不消耗、不产出（原版无效操作同义）。
     * 成本只为<b>右槽带入的条目</b>计费，与原版「书+书」的循环一致。
     */
    public static Optional<Merged> mergeBooks(HolderLookup.RegistryLookup<StageDefinition> stages,
                                              HolderLookup.RegistryLookup<Enchantment> enchants,
                                              BookSpecs.Inscription left,
                                              BookSpecs.Inscription right) {
        if (left.tier() != right.tier() || right.isEmpty()) {
            return Optional.empty();
        }

        LinkedHashMap<ResourceLocation, Integer> base = new LinkedHashMap<>();
        for (BookSpecs.Inscription.Entry entry : left.entries()) {
            base.merge(entry.enchantment(), entry.level(), Math::max);
        }
        LinkedHashMap<ResourceLocation, Integer> result = new LinkedHashMap<>(base);
        for (BookSpecs.Inscription.Entry entry : right.entries()) {
            Integer have = result.get(entry.enchantment());
            if (have == null) {
                result.put(entry.enchantment(), entry.level());
                continue;
            }
            if (have.intValue() == entry.level()) {
                int cap = StageLookup.maxLevelOf(stages, left.tier().asLineageTier(),
                        entry.enchantment(), have);
                result.put(entry.enchantment(), Math.min(have + 1, Math.max(1, cap)));
            } else {
                result.put(entry.enchantment(), Math.max(have, entry.level()));
            }
        }
        if (result.equals(base)) {
            return Optional.empty();
        }

        int cost = 0;
        for (BookSpecs.Inscription.Entry entry : right.entries()) {
            Integer level = result.get(entry.enchantment());
            if (level == null || enchants == null) {
                continue;
            }
            Holder<Enchantment> ench = enchants
                    .get(ResourceKey.create(Registries.ENCHANTMENT, entry.enchantment()))
                    .map(h -> (Holder<Enchantment>) h).orElse(null);
            if (ench != null) {
                cost += costOf(ench, level);
            }
        }

        List<BookSpecs.Inscription.Entry> entries = new ArrayList<>();
        result.forEach((id, level) -> entries.add(new BookSpecs.Inscription.Entry(id, level)));
        return Optional.of(new Merged(new BookSpecs.Inscription(left.tier(), entries), Math.max(1, cost)));
    }

    // ── 辅助 ────────────────────────────────────────────────────────────

    /**
     * 单条附魔的铁砧成本——照原版「附魔书贴到物品」的公式：{@code max(1, anvil_cost / 2) × 等级}。
     *
     * <p>多条目书因此自然变贵，不会白嫖。
     */
    private static int costOf(Holder<Enchantment> ench, int level) {
        return Math.max(1, ench.value().getAnvilCost() / 2) * Math.max(1, level);
    }

    private static int clamp(int level, int cap) {
        return Math.max(1, Math.min(level, Math.max(1, cap)));
    }

    private static ItemEnchantments.Mutable mutableOf(ItemStack stack) {
        return new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(stack));
    }
}
