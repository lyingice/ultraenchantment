package com.lyingice.ultraenchantment.event;

import com.lyingice.ultraenchantment.content.AscensionTier;
import com.lyingice.ultraenchantment.content.BookSpecs;
import com.lyingice.ultraenchantment.content.BookSubject;
import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.logic.StageLookup;
import com.lyingice.ultraenchantment.registry.UEComponents;
import com.lyingice.ultraenchantment.registry.UEItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * <b>附魔书提示框</b>——进阶附魔书自己的 tooltip。
 *
 * <h2>四套格式</h2>
 *
 * <p>科目不同，提示框结构完全不同（规格已定义）：
 *
 * <pre>
 * ① 进化型 · 通用进阶          ② 进化型 · 定向进阶
 *    附魔进阶                     定向进阶
 *    可应用于附魔：                可应用于附魔：
 *    普通/高阶/超级附魔            锋利
 *    进阶为：                     进阶为：
 *    高阶/超级/究极附魔            高阶锋利
 *
 * ③ 铭刻型                     ④ 升级型
 *    高阶附魔                     高阶升级 III
 *    高级锋利 IV                  可应用于附魔：
 *                                 高阶附魔
 * </pre>
 *
 * <h2>为什么全部走翻译键而不是拼接字面量</h2>
 *
 * <p>tooltip 是纯客户端行为，但 <b>专用服务器没有语言数据</b>（P1-17）。
 * 因此这里必须用 {@link Component#translatable}，让键值在客户端本地解析——
 * 服务端只负责把「结构」送给客户端，不负责把「文本」定死。
 */
public final class BookTooltipEvents {
    private BookTooltipEvents() {}

    /** 单例监听器，供 game bus 注册。 */
    public static final BookTooltipEvents INSTANCE = new BookTooltipEvents();

    private static final String KEY_PREFIX = "tooltip.ultraenchantment.";

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();

        // 祛咒石：独立分支（它不是书，没有科目）。
        if (UEItems.isCurativeStone(stack)) {
            curativeTooltip(event, stack);
            return;
        }

        if (!UEItems.isAdvancedBook(stack)) {
            return;
        }

        Optional<BookSubject> subject = UEItems.subjectOf(stack);
        if (subject.isEmpty()) {
            return;
        }

        HolderLookup.RegistryLookup<Enchantment> lookup = CommonHooks.resolveLookup(Registries.ENCHANTMENT);
        if (lookup == null) {
            return;
        }

        List<Component> lines = new ArrayList<>();
        switch (subject.get()) {
            case ASCENSION -> {
                BookSpecs.Ascension spec = stack.get(UEComponents.ASCENSION_SPEC.get());
                if (spec != null) {
                    ascensionTooltip(lines, spec, lookup);
                }
            }
            case INSCRIPTION -> {
                BookSpecs.Inscription spec = stack.get(UEComponents.INSCRIPTION_SPEC.get());
                if (spec != null) {
                    inscriptionTooltip(lines, spec, lookup);
                }
            }
            case UPGRADE -> {
                BookSpecs.Upgrade spec = stack.get(UEComponents.UPGRADE_SPEC.get());
                if (spec != null) {
                    upgradeTooltip(lines, spec);
                }
            }
        }

        appendLines(event, lines);
    }

    /**
     * 祛咒石的提示框——表明它作用于哪一档。
     *
     * <pre>
     *   可应用于：
     *   高阶附魔
     * </pre>
     *
     * <p>结构复用进阶书的「可应用于附魔：」标签与 {@code tier_scope} 文案，
     * 因此不需要新键——祛咒石与升级书描述的是同一件事：
     * 「这本书/这块石头只对 X 阶的附魔有效」。
     *
     * <p>不同之处在配色：祛咒石用该档<b>自己的颜色</b>（高阶=青、超级=淡紫、究极=金），
     * 与附魔行上的阶级色一致，玩家扫一眼就能对上。
     */
    private void curativeTooltip(ItemTooltipEvent event, ItemStack stone) {
        AscensionTier tier = UEComponents.curativeTierOf(stone);

        List<Component> lines = new ArrayList<>();
        lines.add(label("applies_to"));
        lines.add(Component.translatable(KEY_PREFIX + "tier_scope",
                        Component.translatable(tierKey(tier)))
                .withStyle(colorOf(tier)));

        appendLines(event, lines);
    }

    /** 空行分隔 + 追加。物品名与说明之间留白，贴近原版附魔书的排版习惯。 */
    private static void appendLines(ItemTooltipEvent event, List<Component> lines) {
        if (lines.isEmpty()) {
            return;
        }
        event.getToolTip().add(Component.empty());
        event.getToolTip().addAll(lines);
    }

    // ── ① ② 进化型 ──────────────────────────────────────────────────────

    /**
     * 进化型：通用与定向共用入口，靠 {@code applicable} 是否存在分流。
     *
     * <p>规格里 h3 / h5 随品质动态替换，这正是 {@code fromTier} / {@code toTier} 两个字段的作用。
     */
    private void ascensionTooltip(List<Component> out, BookSpecs.Ascension spec,
                                  HolderLookup.RegistryLookup<Enchantment> lookup) {
        if (spec.applicable().isPresent()) {
            targetedTooltip(out, spec, lookup);
        } else {
            genericTooltip(out, spec);
        }
    }

    /** ① 通用进阶：只写阶级范围，不写具体附魔名。 */
    private void genericTooltip(List<Component> out, BookSpecs.Ascension spec) {
        out.add(header("ascension.generic"));

        out.add(label("applies_to"));
        out.add(Component.translatable(tierRangeKey(spec.fromTier()))
                .withStyle(ChatFormatting.GRAY));

        out.add(label("advances_to"));
        out.add(Component.translatable(tierRangeKey(spec.toTier()))
                .withStyle(ChatFormatting.BLUE));
    }

    /**
     * ② 定向进阶：写具体附魔名。
     *
     * <p>示例：
     * <pre>
     *   定向进阶
     *   可应用于附魔：
     *   锋利
     *   进阶为：
     *   高阶锋利
     * </pre>
     */
    private void targetedTooltip(List<Component> out, BookSpecs.Ascension spec,
                                 HolderLookup.RegistryLookup<Enchantment> lookup) {
        ResourceLocation id = spec.applicable().get();
        Component name = enchantName(lookup, id);
        if (name == null) {
            return;
        }

        out.add(header("ascension.targeted"));

        out.add(label("applies_to"));
        // 「上一阶」——**不是原版附魔名**。
        //
        // 定向书是「限定谱系的通用进阶书」，它和通用进阶书描述的是同一件事：
        // 谱系链上的一条边 from_tier → to_tier。所以这一行必须写边的**起点**：
        //   · 高阶→超级 那本书要贴在「已经是高阶」的物品上 → 写「高阶锋利」
        //   · 只有 from_tier 是原生阶时，起点才恰好是原版附魔本身 → 写「锋利」
        //
        // ⚠️ 早期实现一律写原版附魔名，于是「高阶→超级」显示成
        // 「可应用于附魔：锋利 / 进阶为：超级锋利」——中间整整少了一阶。
        out.add(displayOf(spec.fromTier(), id, name).withStyle(ChatFormatting.GRAY));

        out.add(label("advances_to"));
        // 「高阶锋利」——**直接取阶段条目自己的本地化键**，不要拿「阶级词缀 + 原版附魔名」拼。
        //
        // 拼接有两个毛病，而且都在中文下才露馅（英文恰好同序同形，所以看不出问题）：
        //   ① 中文拼出来是「高阶 锋利」，中间那个空格是为了英文语序硬加的，中文里就是一道裂缝；
        //      正确键值就是「高阶锋利」。
        //   ② 更要紧的是**命名权**：键可以被整合包覆盖（想让超级锋利叫「裂空」只有走键才行），
        //      拼接把语序和名字一起焊死在了代码里。
        //
        // 这也让「同一串字在哪里都长一样」——物品上进阶附魔行、铭刻型 tooltip 的 h2
        // （见 {@link #inscriptionTooltip}）与这里的「进阶为」用的是同一个键。
        //
        // 颜色沿用原版附魔字段规则（普通附魔 = GRAY，P1-24），与物品上那行附魔一致。
        out.add(Component.translatable(stageNameKey(spec.toTier(), id))
                .withStyle(ChatFormatting.GRAY));
    }

    // ── ③ 铭刻型 ────────────────────────────────────────────────────────

    /**
     * 铭刻型：
     * <pre>
     *   高阶附魔
     *   高阶锋利 IV
     * </pre>
     *
     * <h2>h1：阶级名 + 「附魔」</h2>
     *
     * <p>相比原版附魔书多一行阶级标识，用于区分高阶 / 超级 / 究极。
     *
     * <h2>h2：对应阶段附魔的本地化 + 附魔字段同款颜色</h2>
     *
     * <p>第二行显示的是<b>阶段附魔自己的名字</b>——即 {@code enchantment.ultraenchantment.<阶级>.<名>}
     * 这个独立键（「高阶锋利」），而不是「阶级词缀 + 原版附魔名」拼出来。
     * 与物品上进阶附魔行的渲染同源，整合包覆盖一个键即可重命名。
     *
     * <p>颜色沿用原版附魔字段的规则（{@code Enchantment.getFullname} 源码实证）：
     * <pre>
     *   holder.is(EnchantmentTags.CURSE) ? RED : GRAY
     * </pre>
     * 本模组的阶段附魔都是 {@code Holder.Direct} 且无注册表标签，
     * {@code is(TagKey)} 恒为 false（P1-5），因此<b>必然是 GRAY</b>——与原版普通附魔一致。
     */
    private void inscriptionTooltip(List<Component> out, BookSpecs.Inscription spec,
                                    HolderLookup.RegistryLookup<Enchantment> lookup) {
        // 阶级直接取自载荷——这是本书自身的属性，与它铭刻哪个附魔无关。
        //
        // ⚠️ 不要试图从附魔 id 反推阶级：铭刻书铭刻的是原版 id（minecraft:sharpness），
        // 路径里没有阶级信息，反推只会得到原生阶，导致三档书全显示同一个阶级。
        AscensionTier tier = spec.tier();

        // h1：阶级名 + 「附魔」
        out.add(Component.translatable(KEY_PREFIX + "inscription.header",
                        Component.translatable(tierKey(tier)))
                .withStyle(colorOf(tier)));

        // h2..hN：<b>每条目一行</b>——多条目书就是多行，这是载体书与 v1 单条铭刻书最大的显示差异。
        //
        // 阶段附魔的独立本地化名 + 等级，颜色同原版附魔字段（GRAY）。
        //
        // 等级按原版规则省略——源码实证 Enchantment.getFullname：
        //     if (level != 1 || enchantment.getMaxLevel() != 1) { 显示数字 }
        // 即「1 级 且 上限为 1」才省略，两个条件必须同时满足。
        //
        // ⚠️ 这里的「上限」取**该阶级阶段条目的 max_level**（逐谱系，见 P1-28），
        // 不是原版附魔自身的 getMaxLevel()——铭刻书写入的是阶段上限，
        // 用它来判断才自洽。取不到阶段定义时退化为附魔自身上限。
        for (BookSpecs.Inscription.Entry entry : spec.entries()) {
            // 附魔不存在（数据包被移除）→ 这一行不显示，也不显示半个 tooltip。
            if (enchantName(lookup, entry.enchantment()) == null) {
                continue;
            }
            MutableComponent full = Component.translatable(stageNameKey(tier, entry.enchantment()))
                    .withStyle(ChatFormatting.GRAY);

            int cap = inscriptionCap(tier, entry.enchantment(), lookup);
            if (entry.level() != 1 || cap != 1) {
                full.append(Component.literal(" "))
                        .append(Component.translatable("enchantment.level." + entry.level()));
            }
            out.add(full);
        }
    }

    /**
     * 铭刻书等级的显示判定依据——该阶级阶段条目的 {@code max_level}。
     *
     * <p>与 {@code AnvilEvents.applyInscription} 写入时用的上限同源
     * （都走 {@code StageLookup}），保证 tooltip 显示与实际写入一致。
     *
     * @return 该谱系该阶级的上限；查不到时退化为原版附魔自身的 {@code getMaxLevel()}
     */
    private static int inscriptionCap(AscensionTier tier, ResourceLocation enchantment,
                                      HolderLookup.RegistryLookup<Enchantment> lookup) {
        // 判定与物品 tooltip 共用（StageLookup.displayLevelCap）——这条规则曾经两边各写一份，
        // 结果物品那边漏掉了省略，见 P1-32 补记。
        int vanillaMax = lookup.get(ResourceKey.create(Registries.ENCHANTMENT, enchantment))
                .map(h -> h.value().getMaxLevel())
                .orElse(1);
        return StageLookup.displayLevelCap(tier.asLineageTier(), enchantment, vanillaMax);
    }

    // ── ④ 升级型 ────────────────────────────────────────────────────────

    /**
     * 升级型：
     * <pre>
     *   高阶升级 III
     *   可应用于附魔：
     *   高阶附魔
     * </pre>
     *
     * <p>h1 中阶级与等级连写——「高阶升级 III」表示本书可把附魔提升至 3 级，
     * 贴近原版附魔书的显示习惯。
     */
    private void upgradeTooltip(List<Component> out, BookSpecs.Upgrade spec) {
        out.add(Component.translatable(KEY_PREFIX + "upgrade.header",
                        Component.translatable(tierKey(spec.tier())),
                        Component.translatable("enchantment.level." + spec.targetLevel()))
                .withStyle(colorOf(spec.tier())));

        out.add(label("applies_to"));
        out.add(Component.translatable(KEY_PREFIX + "tier_scope",
                        Component.translatable(tierKey(spec.tier())))
                .withStyle(ChatFormatting.GRAY));
    }

    // ── 辅助 ────────────────────────────────────────────────────────────

    /**
     * 某一阶的显示名。
     *
     * <p>原生阶没有阶段条目（它只是「还没进阶」这个状态的代称），所以退回原版附魔自己的名字；
     * 其余各阶一律走阶段条目的独立键——与物品上进阶附魔行、铭刻书 h2 同源，
     * 整合包覆盖一个键即可重命名。
     */
    private static MutableComponent displayOf(LineageTier tier, ResourceLocation enchantment, Component vanillaName) {
        if (tier == LineageTier.NATIVE) {
            return vanillaName.copy();
        }
        return Component.translatable(stageNameKey(tier, enchantment));
    }

    private static Component header(String path) {
        return Component.translatable(KEY_PREFIX + path).withStyle(ChatFormatting.GOLD);
    }

    private static Component label(String path) {
        return Component.translatable(KEY_PREFIX + path).withStyle(ChatFormatting.DARK_GRAY);
    }

    /** 附魔的本地化名；查不到时返回 null（不显示半个 tooltip）。 */
    private static Component enchantName(HolderLookup.RegistryLookup<Enchantment> lookup, ResourceLocation id) {
        return lookup.get(ResourceKey.create(Registries.ENCHANTMENT, id))
                .map(h -> h.value().description())
                .orElse(null);
    }

    private static String tierKey(AscensionTier tier) {
        return "tier.ultraenchantment." + tier.id();
    }

    /**
     * 阶段附魔的独立本地化键：{@code enchantment.ultraenchantment.<阶级>.<附魔名>}。
     *
     * <p>构造规则与 {@code EnchantmentFactory.descriptionOf}、
     * {@code LineageTable.Lineage#langKey} 一致——把阶段条目 id 的斜杠换成点。
     * 例：{@code ultraenchantment:super/sharpness} → {@code enchantment.ultraenchantment.super.sharpness}。
     *
     * <p>键只由「阶级的 {@code id()}」与「附魔的 {@code path}」决定，
     * 所以两个阶级类型（{@link AscensionTier} / {@link LineageTier}）共用同一套构造。
     * 定向进阶书拿的是 {@link LineageTier}（它的 {@code to_tier} 允许从原生阶起步），
     * 铭刻书拿的是 {@link AscensionTier}，两者最终落在同一个键上。
     */
    private static String stageNameKey(LineageTier tier, ResourceLocation enchantment) {
        return "enchantment.ultraenchantment." + tier.id() + "." + enchantment.getPath();
    }

    private static String stageNameKey(AscensionTier tier, ResourceLocation enchantment) {
        return stageNameKey(tier.asLineageTier(), enchantment);
    }

    /**
     * 阶级范围的翻译键。
     *
     * <p>规格的 h3 / h5 是「普通附魔 / 高阶附魔 / 超级附魔」这种<b>范围</b>表述，
     * 而不是单个阶级名——所以另开一套键，不能复用 {@code tier.*}。
     */
    private static String tierRangeKey(LineageTier tier) {
        return KEY_PREFIX + "scope." + tier.id();
    }

    private static ChatFormatting colorOf(AscensionTier tier) {
        return switch (tier) {
            case ADVANCED -> ChatFormatting.BLUE;
            case SUPER -> ChatFormatting.LIGHT_PURPLE;
            case ULTRA -> ChatFormatting.GOLD;
        };
    }
}
