package com.lyingice.ultraenchantment.compat.apotheosis;

import com.lyingice.ultraenchantment.content.AscensionData;
import com.lyingice.ultraenchantment.content.AscensionTier;
import com.lyingice.ultraenchantment.content.BookSpecs;
import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.logic.BookFactory;
import com.lyingice.ultraenchantment.logic.UERoots;
import com.lyingice.ultraenchantment.registry.UEComponents;
import dev.shadowsoffire.apothic_enchanting.objects.ExtractionTomeItem;
import dev.shadowsoffire.apothic_enchanting.objects.ImprovedScrappingTomeItem;
import dev.shadowsoffire.apothic_enchanting.objects.ScrappingTomeItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;

/**
 * {@link ApothTomes} 的实现体 —— <b>只有神化确实装着时才会被加载</b>。
 *
 * <h2>三类宝典的分工（源码实证）</h2>
 *
 * <pre>
 * 拆解宝典      产出 = 附魔书，只留【随机一半】附魔
 * 高级拆解宝典  产出 = 附魔书，留【全部】附魔
 * 提取宝典      产出 = 附魔书，留【全部】附魔；
 *               取件时把武器的附魔清空、并把武器还给玩家
 * </pre>
 *
 * <h2>为什么要改写产出</h2>
 *
 * <p>神化产出的是一本<b>原版附魔书</b>，只带走 {@code minecraft:enchantments}，
 * 我们的 {@code ultraenchantment:ascension} 不在其中。把「原版附魔书 + 我们的组件」
 * 这种四不像交给玩家是<b>错的</b>——图书馆按「原版附魔书 = 基础阶」处理它，
 * 进阶身份会在存入时蒸发。
 *
 * <h2>产出改成什么：分两种情况（这是本类的核心）</h2>
 *
 * <p>我们的载体书（{@link BookSpecs.Inscription}）的 {@code tier} 是<b>书级单值</b>，
 * 且<b>不能含基础阶</b>（见 {@code BookSpecs} 类文档）。而一次拆解可能拆出
 * 跨阶级、甚至夹杂基础阶的附魔。于是：
 *
 * <ol>
 *   <li><b>幸存条目全是进阶的</b> ⇒ 产出换成我们的载体书
 *       （{@code ultraenchantment:advanced_enchanted_book} + 铭刻载荷）。
 *       它在图书馆、铁砧、剩菜机制里都是一等公民，<b>零丢失</b>。
 *       跨多个进阶阶级时，条目最多的那一组占产出（并列取低阶级，保证确定性），
 *       其余各阶级在<b>取件时</b>各补一本——这正是本模组既有的「剩菜书」机制。</li>
 *   <li><b>夹杂基础阶</b> ⇒ 产出<b>保持他们的原版附魔书</b>，但<b>只保留基础阶条目</b>；
 *       进阶条目在取件时各补一本载体书。这样产出从源头就是「普通书 + 进阶书」两个纯形态，
 *       不会再把进阶身份写进原版书的 {@code ascension} 组件（1.0 起彻底消灭这种混合态）。</li>
 * </ol>
 *
 * <p>全是基础阶（普通原版装备）时<b>不做任何改写</b>：产出行维持原版附魔书，
 * 神化的行为一点不变。
 *
 * <h2>为什么用事件后置，而不是 mixin 进他们的方法</h2>
 *
 * <p>他们的 {@code updateAnvil}/{@code updateRepair} 都是从<b>同一批 NeoForge 事件</b>里
 * 被调用的。我们用 {@code EventPriority.LOW}（跑在他们之后）挂同一批事件即可原地后置，
 * 不碰他们的类结构，也不怕他们改内部实现。
 */
final class ApothTomesImpl {
    private ApothTomesImpl() {}

    /** 一条「幸存条目」：谱系 + 阶级（{@code tier == null} = 基础阶）+ 等级。 */
    private record Entry(ResourceLocation root, AscensionTier tier, int level) {}

    /** 右边是不是神化的三种宝典之一。 */
    private static boolean isTome(ItemStack stack) {
        return stack.getItem() instanceof ScrappingTomeItem
                || stack.getItem() instanceof ImprovedScrappingTomeItem
                || stack.getItem() instanceof ExtractionTomeItem;
    }

    /** 产出阶段：见类文档的「产出改成什么」。 */
    static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!isTome(event.getRight())) {
            return;
        }
        ItemStack weapon = event.getLeft();
        ItemStack base = event.getOutput();
        if (weapon.isEmpty() || base.isEmpty()) {
            return;
        }
        // 他们刚设好的产出是原版附魔书；幸存的谱系从它的附魔里读（这就是「幸存集合」）
        Set<ResourceLocation> survivors = rootsIn(base);
        if (survivors.isEmpty()) {
            return;
        }
        Map<AscensionTier, List<Entry>> groups = group(weapon, survivors);
        int advanced = groups.values().stream().mapToInt(List::size).sum();
        if (advanced == 0) {
            return;
        }
        if (advanced == survivors.size()) {
            event.setOutput(inscribedBook(primaryTier(groups), groups.get(primaryTier(groups))));
        } else {
            // 混合情形（基础阶 + 进阶阶）：产出保留原版附魔书，但**只留基础阶条目**。
            // 进阶条目由取件阶段各补一本载体书（下方 onAnvilRepair 的通用规则），
            // 于是「普通书 + 进阶书」从产出这一刻就是分开的，不会再形成混合态。
            stripAdvanced(base, weapon, survivors);
        }
    }

    /**
     * 取件阶段。
     *
     * <h4>1. 没进产出的进阶条目，各补一本载体书</h4>
     *
     * <p>覆盖两种情况：跨阶级时产出只装得下一组；以及<b>拆解宝典随机丢掉的那一半</b>
     * （作者定的方案 B：进阶数据不许凭空蒸发，还原成铭刻书退回玩家）。
     *
     * <h4>2. 提取宝典：武器上的进阶记录必须一起清</h4>
     *
     * <p>它的 {@code updateRepair} 会把武器的附魔清空、再把武器还给玩家；
     * 我们的组件若留在武器上 ⇒「附魔没了、进阶还在」= <b>白送一次进阶</b>。
     *
     * <p>判定全部基于<b>现场能从产出与武器上读到的信息</b>（本模组一贯做法：
     * 拿同一份输入把解析重跑一遍，见 {@code AnvilTakeEvents}），
     * 因此<b>不需要</b>跨事件保存状态，也<b>不需要</b>反推神化的随机数。
     */
    static void onAnvilRepair(AnvilRepairEvent event) {
        if (!isTome(event.getRight())) {
            return;
        }
        ItemStack weapon = event.getLeft();
        if (weapon.isEmpty()) {
            return;
        }
        AscensionData source = UEComponents.ascensionOf(weapon);
        if (source.stages().isEmpty()) {
            return;
        }
        Player player = event.getEntity();
        Set<ResourceLocation> delivered = rootsIn(event.getOutput());

        for (Map.Entry<ResourceLocation, ResourceLocation> record : source.stages().entrySet()) {
            if (delivered.contains(record.getKey())) {
                continue;
            }
            AscensionTier tier = tierOfStage(record.getValue());
            if (tier == null) {
                continue;
            }
            give(player, inscribedBook(tier, List.of(new Entry(record.getKey(), tier,
                    Math.max(1, source.tierLevelOf(record.getKey()))))));
        }

        if (event.getRight().getItem() instanceof ExtractionTomeItem) {
            weapon.remove(UEComponents.ASCENSION.get());
        }
    }

    // ────────────────────────────── 组装 ──────────────────────────────

    /** 产出（或刚拿到的产出）里承载着哪些谱系。原版附魔书与载体书都认。 */
    private static Set<ResourceLocation> rootsIn(ItemStack stack) {
        Set<ResourceLocation> roots = new HashSet<>();
        if (stack.isEmpty()) {
            return roots;
        }
        BookSpecs.Inscription spec = stack.get(UEComponents.INSCRIPTION_SPEC.get());
        if (spec != null) {
            for (BookSpecs.Inscription.Entry entry : spec.entries()) {
                roots.add(entry.enchantment());
            }
            return roots;
        }
        for (Holder<Enchantment> holder : EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet()) {
            UERoots.rootOf(holder.value()).ifPresent(roots::add);
        }
        return roots;
    }

    /**
     * 把「武器上有进阶记录、且进了幸存集合」的条目按阶级分组。
     *
     * <p>没有进阶记录 = 基础阶，不进分组（载体书容不下基础阶）。
     */
    private static Map<AscensionTier, List<Entry>> group(ItemStack weapon,
                                                          Set<ResourceLocation> survivors) {
        AscensionData data = UEComponents.ascensionOf(weapon);
        Map<AscensionTier, List<Entry>> groups = new LinkedHashMap<>();
        for (ResourceLocation root : survivors) {
            ResourceLocation stage = data.stages().get(root);
            if (stage == null) {
                continue;
            }
            AscensionTier tier = tierOfStage(stage);
            if (tier == null) {
                continue;
            }
            groups.computeIfAbsent(tier, key -> new ArrayList<>())
                    .add(new Entry(root, tier, Math.max(1, data.tierLevelOf(root))));
        }
        return groups;
    }

    /**
     * 混合产出的拆分：从原版产出书里<b>删掉进阶阶条目</b>，只留基础阶。
     *
     * <p>进阶条目并没有消失——{@link #onAnvilRepair} 会按「武器上有、产出里没有」的
     * 通用规则给它们各补一本载体书。这样普通书与进阶书从产出起就是两个物品。
     */
    private static void stripAdvanced(ItemStack base, ItemStack weapon,
                                      Set<ResourceLocation> survivors) {
        AscensionData data = UEComponents.ascensionOf(weapon);
        if (data.stages().isEmpty()) {
            return;
        }
        ItemEnchantments.Mutable table =
                new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(base));
        table.removeIf(holder -> {
            ResourceLocation root = UERoots.rootOf(holder.value()).orElse(null);
            return root != null && survivors.contains(root) && data.stages().containsKey(root);
        });
        EnchantmentHelper.setEnchantments(base, table.toImmutable());
        // 产出是原版附魔书；绝不能在这里再盖一个 ascension 组件（那正是要消灭的混合态）。
        base.remove(UEComponents.ASCENSION.get());
    }

    /**
     * 占产出的那一组 = 条目最多的一组；并列时取<b>低阶级</b>。
     *
     * <p>确定性很重要：产出阶段与取件阶段各自独立算一遍，
     * 算法不确定就会出现「取件时发重 / 漏发」。
     */
    private static AscensionTier primaryTier(Map<AscensionTier, List<Entry>> groups) {
        return groups.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<AscensionTier, List<Entry>>>comparingInt(e -> -e.getValue().size())
                        .thenComparingInt(e -> e.getKey().ordinal()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /** 阶级为书的载体书；等级按条目给。 */
    private static ItemStack inscribedBook(AscensionTier tier, List<Entry> entries) {
        if (tier == null || entries == null || entries.isEmpty()) {
            return ItemStack.EMPTY;
        }
        List<BookSpecs.Inscription.Entry> payload = new ArrayList<>();
        for (Entry entry : entries) {
            payload.add(new BookSpecs.Inscription.Entry(entry.root(), entry.level()));
        }
        return BookFactory.inscription(new BookSpecs.Inscription(tier, payload));
    }

    /** 阶段 id → 阶级（基础阶返回 null：载体书容不下它）。 */
    private static AscensionTier tierOfStage(ResourceLocation stageId) {
        Optional<LineageTier> tier = UERoots.lineageTier(UERoots.tierOfStage(stageId));
        if (tier.isEmpty() || tier.get() == LineageTier.NATIVE) {
            return null;
        }
        return AscensionTier.values()[tier.get().ordinal() - 1];
    }

    private static void give(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
