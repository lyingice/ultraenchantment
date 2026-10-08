package com.lyingice.ultraenchantment.logic;

import com.lyingice.ultraenchantment.content.AscensionData;
import com.lyingice.ultraenchantment.content.AscensionTier;
import com.lyingice.ultraenchantment.content.BookSpecs;
import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.content.StageDefinition;
import com.lyingice.ultraenchantment.registry.UEComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * <b>载体书（铭刻书）与「原版附魔」之间的翻译层</b>——给第三方附魔编辑台用。
 *
 * <h2>要解决的问题</h2>
 *
 * <p>「附魔编辑台」（Enchantment Custom Table）的两个方向都只认
 * {@code minecraft:enchantments}：
 *
 * <ul>
 *   <li><b>拿下</b>（{@code exportAllEnchantments}）：把物品的附魔导出成
 *       <b>{@code Items.ENCHANTED_BOOK}</b> 塞进背包 ⇒ 进阶身份<b>丢了</b>；</li>
 *   <li><b>放进去</b>（{@code checkCanPlaceEnchantedBook} /
 *       {@code getEnchantmentInstanceFromEnchantedBook}）：只从原版附魔组件读，
 *       我们的载体书在它眼里<b>就是一本空书</b> ⇒ 用不了。</li>
 * </ul>
 *
 * <h2>翻译的对应关系</h2>
 *
 * <pre>
 * 载体书（阶级 T，条目 [根附魔 → 等级]）  ⇄  物品上的「根附魔 @ 等级」+ 我们的进阶记录（阶级 T）
 * </pre>
 *
 * <p>注意等级：载体书上的 {@code level} 是<b>曲线等级</b>（书本来就这么存的），
 * 写回物品时交给 {@link AscensionLogic#writeAscension} —— 它会把原版存储等级设成
 * 「该阶上限」，维持既有不变量（见 {@code writeAscension} 类文档）。
 */
public final class AscensionBookIO {
    private AscensionBookIO() {}

    /** 是不是我们的载体书（铭刻书）。 */
    public static boolean isInscriptionBook(ItemStack stack) {
        BookSpecs.Inscription spec = stack.get(UEComponents.INSCRIPTION_SPEC.get());
        return spec != null && !spec.isEmpty();
    }

    /**
     * 载体书 → 原版附魔实例（根附魔 + 书上的等级）。第三方编辑台的「放入」方向要的就是这个。
     *
     * @param clientSide 当前是不是逻辑客户端（只影响取哪一侧的注册表；本方法<b>不写</b>组件，安全）
     */
    public static List<EnchantmentInstance> instancesOf(ItemStack book, boolean clientSide) {
        List<EnchantmentInstance> out = new ArrayList<>();
        BookSpecs.Inscription spec = book.get(UEComponents.INSCRIPTION_SPEC.get());
        if (spec == null || spec.isEmpty()) {
            return out;
        }
        HolderLookup.RegistryLookup<Enchantment> lookup = UELookups.enchantmentsForItemWrites(clientSide);
        if (lookup == null) {
            return out;
        }
        for (BookSpecs.Inscription.Entry entry : spec.entries()) {
            lookup.get(ResourceKey.create(Registries.ENCHANTMENT, entry.enchantment()))
                    .ifPresent(holder -> out.add(new EnchantmentInstance(holder, Math.max(1, entry.level()))));
        }
        return out;
    }

    /**
     * 把载体书的条目落成物品上的进阶记录 —— 编辑台的「放入」方向在它写完原版附魔后调用。
     *
     * <p><b>只对「物品上确实已经有这条附魔」的条目生效</b>：编辑台的流程是先写
     * {@code minecraft:enchantments}、再走到这里；若某条根本没写上去（玩家没选它 / 被拒绝），
     * 我们凭空写一条进阶记录就等于<b>无中生有</b>。这个门槛是必要的。
     *
     * @return 实际写入的条目数
     */
    public static int applyTo(ItemStack item, ItemStack book) {
        if (item.isEmpty()) {
            return 0;
        }
        BookSpecs.Inscription spec = book.get(UEComponents.INSCRIPTION_SPEC.get());
        if (spec == null || spec.isEmpty()) {
            return 0;
        }
        HolderLookup.RegistryLookup<StageDefinition> stages = StageLookup.lookup();
        if (stages == null) {
            return 0;
        }
        ItemEnchantments present = EnchantmentHelper.getEnchantmentsForCrafting(item);
        LineageTier tier = spec.tier().asLineageTier();
        int written = 0;
        for (BookSpecs.Inscription.Entry entry : spec.entries()) {
            // ⚠️ Holder 一律**从物品自己的附魔组件里取**，绝不去别的注册表按 id 查：
            //    这样写回去的 Holder 天然属于**这一侧**，客户端路径也不会踩 P0-17
            //    （跨侧注册表 ⇒ 发包找不到 id ⇒ 掉线）。
            Holder<Enchantment> holder = holderForRoot(present, entry.enchantment());
            if (holder == null) {
                continue;                                  // 物品上还没有这条 ⇒ 不凭空写
            }
            int stageMax = StageLookup.maxLevelOf(stages, tier, entry.enchantment(), 0);
            if (stageMax <= 0) {
                continue;
            }
            ResourceLocation stage = StageLookup.stageIdOf(stages, entry.enchantment(), tier).orElse(null);
            if (stage == null) {
                continue;
            }
            AscensionLogic.writeAscension(item, holder, entry.enchantment(), stage,
                    stageMax, Math.max(1, entry.level()));
            written++;
        }
        return written;
    }

    /**
     * 把物品上的进阶条目导出成一本载体书（编辑台的「拿下」方向）。
     *
     * <p><b>只在「进阶条目同属一个阶级」时才成立</b>：载体书是<b>书级单值</b>阶级
     * （{@code BookSpecs} 类文档），跨阶级装不下。表达不了时返回空 ⇒ 调用方保留它原本的
     * 原版附魔书，并把进阶身份写进 {@code ascension} 组件带走（至少不丢）。
     *
     * <p>物品上已经没有的条目（例如刚被编辑台删掉）不算数。
     */
    public static ItemStack exportFrom(ItemStack item) {
        if (item.isEmpty()) {
            return ItemStack.EMPTY;
        }
        AscensionData data = UEComponents.ascensionOf(item);
        if (data.lineages().isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemEnchantments present = EnchantmentHelper.getEnchantmentsForCrafting(item);
        AscensionTier shared = null;
        List<BookSpecs.Inscription.Entry> entries = new ArrayList<>();
        for (Map.Entry<ResourceLocation, AscensionData.Record> record : data.lineages().entrySet()) {
            if (!hasRoot(present, record.getKey())) {
                continue;
            }
            Optional<LineageTier> tier = UERoots.lineageTier(UERoots.tierOfStage(record.getValue().stage()));
            if (tier.isEmpty() || tier.get() == LineageTier.NATIVE) {
                continue;
            }
            AscensionTier ascensionTier = AscensionTier.values()[tier.get().ordinal() - 1];
            if (shared == null) {
                shared = ascensionTier;
            } else if (shared != ascensionTier) {
                return ItemStack.EMPTY;                 // 跨阶级 ⇒ 载体书装不下
            }
            entries.add(new BookSpecs.Inscription.Entry(record.getKey(),
                    Math.max(1, record.getValue().tierLevel())));
        }
        if (shared == null || entries.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return BookFactory.inscription(new BookSpecs.Inscription(shared, entries));
    }

    /**
     * 把编辑台缓存里的<b>一条附魔的（原版）书</b>换成对应阶级的**载体书**。
     *
     * <p>编辑台的网格（{@code enchantmentsOnCurrentTool}）是<b>玩家实际点选的东西</b>：
     * 网格里放的是原版附魔书，点它带进物品的就是「根附魔 @ 原版等级」——
     * 而 {@link #applyTo} 只认载体书载荷（原版书没有载荷）⇒ 进阶记录写不进去 ⇒
     * 玩家看到的就是「进阶书放进去、出来只有普通附魔」。把网格自己也换成载体书，
     * 这条链路才自洽：<b>看到的是载体书，点的是载体书，写进去的也就是进阶形态</b>。
     *
     * <p>不是进阶条目（物品上没记录 / 阶级无效）时返回空 ⇒ 调用方保留它原本那本书。
     *
     * @param item        台子里正在编辑的物品（进阶记录从它身上读）
     * @param vanillaBook 该条目原本的原版附魔书（只用来认出是哪个根附魔）
     */
    public static ItemStack inscriptionFor(ItemStack item, ItemStack vanillaBook) {
        if (item.isEmpty() || vanillaBook.isEmpty() || isInscriptionBook(vanillaBook)) {
            return ItemStack.EMPTY;
        }
        AscensionData data = UEComponents.ascensionOf(item);
        if (data.lineages().isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(vanillaBook);
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            ResourceLocation rootId = UERoots.rootOf(holder.value()).orElse(null);
            if (rootId == null) {
                continue;
            }
            AscensionData.Record record = data.lineages().get(rootId);
            if (record == null) {
                continue;
            }
            Optional<LineageTier> tier = UERoots.lineageTier(UERoots.tierOfStage(record.stage()));
            if (tier.isEmpty() || tier.get() == LineageTier.NATIVE) {
                continue;
            }
            AscensionTier ascensionTier = AscensionTier.values()[tier.get().ordinal() - 1];
            return BookFactory.inscription(new BookSpecs.Inscription(ascensionTier,
                    List.of(new BookSpecs.Inscription.Entry(rootId, Math.max(1, record.tierLevel())))));
        }
        return ItemStack.EMPTY;
    }

    /** 物品自己的附魔组件里，属于该谱系的那个 Holder（找不到返回 null）。 */
    private static Holder<Enchantment> holderForRoot(ItemEnchantments enchantments, ResourceLocation root) {
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (UERoots.rootOf(holder.value()).filter(root::equals).isPresent()) {
                return holder;
            }
        }
        return null;
    }

    /** 物品上还有没有这条谱系的附魔。 */
    private static boolean hasRoot(ItemEnchantments enchantments, ResourceLocation root) {
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (UERoots.rootOf(holder.value()).filter(root::equals).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 「拿下」的兜底形态：<b>原版附魔书 + 我们的进阶组件</b>。
     *
     * <p>用在「载体书表达不了」的场合（跨阶级 / 混着基础阶附魔）。虽然不如载体书，
     * 但至少进阶身份<b>跟着走</b>，不会凭空蒸发。物品上没有进阶条目时返回空 ⇒ 让编辑台原样处理。
     */
    public static ItemStack plainExport(ItemStack item) {
        if (item.isEmpty() || UEComponents.ascensionOf(item).lineages().isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack book = new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK);
        EnchantmentHelper.setEnchantments(book,
                new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(item)).toImmutable());
        UEComponents.setAscension(book, UEComponents.ascensionOf(item));
        return book;
    }
}

