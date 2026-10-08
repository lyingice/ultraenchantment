package com.lyingice.ultraenchantment.event;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.compat.apotheosis.ApothCaps;
import com.lyingice.ultraenchantment.compat.tooltip.PrismRainbow;
import com.lyingice.ultraenchantment.compat.tooltip.TooltipStackCompat;
import com.lyingice.ultraenchantment.content.AscensionData;
import com.lyingice.ultraenchantment.content.AscensionTier;
import com.lyingice.ultraenchantment.logic.ProtectionLogic;
import com.lyingice.ultraenchantment.logic.StageLookup;
import com.lyingice.ultraenchantment.registry.UEComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * <b>显示层</b>——把附魔行改写成带阶级的样子。
 *
 * <h2>为什么必须改写</h2>
 *
 * <p>tooltip 里的附魔行由 {@code ItemEnchantments.addToTooltip} 走
 * {@code Enchantment.getFullname(holder, level)} 生成，读的是附魔自己的 description。
 * 而本模组的阶段附魔从不真正写进物品（结算层只在查询期注入），
 * 所以原版只会显示「锋利 5」，看不出它已经是超级阶。
 *
 * <h2>两条渲染规则（都是规格明确要求的）</h2>
 *
 * <ol>
 *   <li><b>用阶梯自己的名字</b>：进阶后的附魔有独立本地化键
 *       {@code enchantment.ultraenchantment.<阶级>.<根源名>}，
 *       而不是「阶级词缀 + 原版附魔名」拼出来。整合包可以覆盖这个键来重命名。</li>
 *   <li><b>显示等级归 1</b>：进阶是一条<b>新的成长曲线</b>，等级从 1 重新起算。
 *       锋利 5 升阶后显示为「超级锋利 1」，而不是「超级锋利 5」。</li>
 * </ol>
 *
 * <h2>规则 2 的实现边界（重要）</h2>
 *
 * <p>归 1 只发生在<b>显示层</b>。物品上存储的等级仍是原值（锋利 5 的 5），
 * 理由：
 * <ul>
 *   <li>存储层等级是原版 {@code minecraft:enchantments} 组件的一部分，
 *       改写它会影响附魔台、铁砧、村民交易等一切原版机制</li>
 *   <li>结算层注入阶段定义时用的也是原等级——效果的绝对强度不回退</li>
 * </ul>
 *
 * <p>这是刻意的「数据与显示分离」：内部按原等级运算，对外按新曲线呈现。
 *
 * <h2>装了神化时会多出「过时原版行」，两种都要删（机制实证）</h2>
 *
 * <p>神化的 {@code ItemStackMixin} 接管附魔行渲染时遍历<b>三张表</b>：
 *
 * <ol>
 *   <li>{@code iterationOrder}（原版附魔标签顺序）——里面的 holder 是<b>原版</b>的</li>
 *   <li>{@code enchants}（物品 nbt 表）——同样是<b>原版</b> holder</li>
 *   <li>{@code realLevels}（{@code getAllEnchantments()}，即<b>我们的结算表</b>）
 *       ——里面是我们注入的<b>合成 holder</b></li>
 * </ol>
 *
 * <p>于是同一个谱系被渲染<b>两次</b>，实测（截图，附魔区共 6 行）：
 *
 * <pre>
 *   锋利 0 (V - V)              ← ❌ 第 1/2 张表：原版 holder，等级已被我们清零
 *   亡灵杀手 0 (V - V)          ←    （nbt=5, real=0 → 神化的「等级差」格式）
 *   横扫之刃 0 (III - III)      ← ❌
 *   🌟 究极锋利 IX (0 + IX)     ← ✅ 第 3 张表：名字来自**我们的合成 holder**
 *   究极亡灵杀手 V (0 + V)       ← ✅   （后缀 (0 + IX) 是神化的调试格式）
 *   🌟 究极横扫之刃 VIII (0 + VIII) ← ✅
 * </pre>
 *
 * <p><b>关键认识</b>：下面那 3 行<b>本来就是我们的数据</b>——合成 holder 的
 * {@code description} 就是我们生成的阶级名键
 * （{@code enchantment.ultraenchantment.<阶级>.<根源>}），神化只是借它的管线渲染出来。
 * 不是「别人的行」。
 *
 * <p>因此本类的做法是：<b>两种行都删，再插入我们自己渲染的那一行</b>——
 * 于是装了神化与没装神化，最终呈现完全一致（都由我们决定外观：阶级配色 + 究极阶彩虹），
 * 同时消掉 {@code (0 + IX)} 这种对玩家无意义的调试后缀。
 *
 * <p>判据一律按<b>名字本体</b>匹配（原版名 / 阶级名），不硬编码神化的后缀格式——
 * 那种内部格式改版就失效（P1-18 的老教训）。
 */
public final class TooltipEvents {
    private TooltipEvents() {}

    /** 单例监听器，供 game bus 注册。 */
    public static final TooltipEvents INSTANCE = new TooltipEvents();

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }

        // 热路径早退：绝大多数物品没有阶段记录。
        AscensionData data = UEComponents.ascensionOf(stack);
        if (data.isEmpty()) {
            return;
        }

        var lookup = CommonHooks.resolveLookup(Registries.ENCHANTMENT);
        if (lookup == null) {
            return;
        }

        List<Component> lines = event.getToolTip();

        // 「传说提示框」那条绿色攻击伤害行算的是「玩家基础值 + 原始附魔加伤」，
        // 而进阶物品的原版附魔已被结算层清零、加伤换成阶级效果——它算出来永远是原版数字。
        // 这里补正差值；**只在装了它时**才做，没装则 tooltip 一个数字都不动。
        if (TooltipStackCompat.useLegendaryTooltipsFix()) {
            com.lyingice.ultraenchantment.compat.legendarytooltips.LegendaryTooltipsFixup
                    .correctAttackDamageLine(stack, lines);
        }

        ItemEnchantments present = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        if (present.isEmpty()) {
            return;
        }

        // 收集需要改写/追加的行。
        List<Component> toRemove = new ArrayList<>();
        List<Component> toAdd = new ArrayList<>();

        data.stages().forEach((rootId, stageId) -> {
            Holder<Enchantment> root = lookup.get(ResourceKey.create(Registries.ENCHANTMENT, rootId))
                    .map(h -> (Holder<Enchantment>) h).orElse(null);
            if (root == null) {
                return;
            }

            int level = present.getLevel(root);
            if (level <= 0) {
                return;
            }

            // 要删的行有**两种**（装了神化时都会出现，见类文档「三张表」）：
            //   ① 以【原版附魔名】开头 → 神化用原版 holder 渲染的过时行 '锋利 0 (V - V)'
            //   ② 以【阶级名】开头     → 神化用我们的合成 holder 渲染的 '究极锋利 IX (0 + IX)'
            // 两种都要删；②本来就是我们的数据，只是借它的管线渲染出来。
            toRemove.add(markerFor(root));                        // ① 原版名
            toRemove.add(Component.translatable(stageNameKey(stageId)));  // ② 阶级名

            toAdd.add(renderStagedLine(stageId, rootId, root, data.tierLevelOf(rootId)));
        });

        if (toAdd.isEmpty()) {
            return;
        }

        // 先删原行，再把改写后的行插回原位置（保持附魔区块的相对顺序）。
        //
        // ⚠️ 必须删掉**所有**匹配行，不能只删第一条。实测（客户端日志）：
        //     装了神化时同一个附魔会出现**两行**——原版/神化渲染的那行，
        //     以及神化「等级差」形态的那行，二者名字本体相同：
        //       '🌟 高阶锋利 IX'           ← 我们插的
        //       '🌟 高阶锋利 IX (0 + IX)'  ← 神化插的
        //     只删第一条就会把神化那行留在原地，玩家看到双行。
        //
        // 先收集行号再倒序删：正序删会让后续下标失效（经典 off-by-one）。
        int insertAt = -1;
        for (Component remove : toRemove) {
            List<Integer> hits = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                if (matchesEnchantmentLine(lines.get(i), remove)) {
                    hits.add(i);
                }
            }
            for (int k = hits.size() - 1; k >= 0; k--) {
                int idx = hits.get(k);
                if (insertAt < 0 || idx < insertAt) {
                    insertAt = idx;
                }
                lines.remove(idx);
            }
        }

        if (insertAt < 0) {
            insertAt = lines.size();
        }
        lines.addAll(Math.min(insertAt, lines.size()), toAdd);
    }

    /**
     * 渲染进阶附魔行：「超级锋利 1」。
     *
     * <h2>名字来自阶梯自己的键</h2>
     *
     * <p>{@code stageId} 形如 {@code ultraenchantment:super/sharpness}，斜杠换成点即得
     * {@code enchantment.ultraenchantment.super.sharpness}。该键由 {@code UEStageNames}
     * 生成，整合包可覆盖以重命名。
     *
     * <p>之所以能把阶段 id 当键用，是因为<b>显示名与阶段条目一一对应</b>——
     * 一个阶段就是一条谱系的一个阶级，不存在两个阶段共用一个显示名的情况。
     *
     * <h2>等级来自进阶曲线</h2>
     *
     * <p>显示的是 {@code AscensionData.tierLevel}，不是物品上存储的附魔等级：
     * <ul>
     *   <li>刚升阶 → {@code tierLevel = 1}，显示「超级锋利 1」</li>
     *   <li>用 3 级升级书后 → {@code tierLevel = 3}，显示「超级锋利 3」</li>
     *   <li>再升一阶 → {@code tierLevel} 重置为 1，显示「究极锋利 1」</li>
     * </ul>
     *
     * <p>存储等级（锋利 5 的 5）不参与显示，也不参与算效果强度——效果按 **tierLevel** 结算，
     * 存储等级只留给原版机制读（铁砧合并、附魔台、村民交易）。
     */
    private static Component renderStagedLine(ResourceLocation stageId, ResourceLocation rootId,
                                              Holder<Enchantment> root, int tierLevel) {
        AscensionTier tier = ProtectionLogic.tierOfStageId(stageId);

        // tierLevel 为 0 表示无进阶曲线记录（异常情况），归 1 兜底。
        int shown = Math.max(1, tierLevel);

        // 类型是 Component 而不是 MutableComponent：下面可能被渐变整体替换。
        Component line = Component.translatable(stageNameKey(stageId)).withStyle(colorOf(tier));

        // 等级数字的省略规则和原版 Enchantment.getFullname 同源：
        //     if (level != 1 || enchantment.getMaxLevel() != 1) { 才显示数字 }
        // 上限取该阶级阶段条目的 max_level（逐谱系，P1-28），查不到退化为原版附魔自身上限；
        // 判定与铭刻书 tooltip 共用 StageLookup.displayLevelCap，避免两处规则再次漂移。
        //
        // 受影响的是那 5 条「原版上限就是 1」的谱系——经验修补 / 引雷 / 火矢 / 无限 / 多重射击：
        // 它们的 tierLevel 恒为 1，此前被无条件渲染成「高阶经验修补 1」，
        // 而原版与书 tooltip 都只写「经验修补」。
        int cap = StageLookup.displayLevelCap(tier.asLineageTier(), rootId, root.value().getMaxLevel());
        if (shown != 1 || cap != 1) {
            MutableComponent withLevel = Component.translatable(stageNameKey(stageId)).withStyle(colorOf(tier));
            withLevel.append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable("enchantment.level." + shown)
                            .withStyle(ChatFormatting.GRAY));
            line = withLevel;
        }

        // 「超过数据包定义等级」——超限配色的判据（神化 B1 把上限抬高后才会出现）。
        boolean aboveCap = StageLookup.isAboveDataPackCap(tier.asLineageTier(), rootId, shown);

        // ── 颜色规则（作者规格 v2.23）────────────────────────────────────
        //
        // ① 超过数据包定义等级时 → 按阶级走「浅 ⇄ 深」双色**流体渐变**：
        //      高阶  蓝     ⇄ 深蓝
        //      超级  淡紫   ⇄ 深紫
        //      究极  橙     ⇄ 红
        // ② 未超限时：
        //      装了神化 → **不用彩虹**，究极保留原色（GOLD，由 colorOf 给）
        //      没装神化 → 究极阶仍走原本的整行彩虹渐变
        //
        // 两条硬约束（写在 TooltipStackCompat.usePrismGradient 里）：
        //   1) 必须是逻辑客户端（Prism 是客户端库，专用服务端加载它会 NoClassDefFoundError）；
        //   2) Prism 必须已安装——PrismRainbow 这个类里才有对方的 import，
        //      它只在本分支被类加载，没装的玩家永远碰不到（AGENT.md P1-40）。
        if (!TooltipStackCompat.usePrismGradient()) {
            return line;
        }

        if (aboveCap) {
            int[] pair = overCapColors(tier);
            return PrismRainbow.applyFlow(line, pair[0], pair[1]);
        }
        if (tier == AscensionTier.ULTRA && !ApothCaps.present()) {
            return PrismRainbow.apply(line);
        }
        return line;
    }

    /**
     * 超限阶级的「浅色 / 深色」配色对。
     *
     * <pre>
     *   高阶  0x5555FF (BLUE)          ⇄ 0x0000AA (DARK_BLUE)
     *   超级  0xFF55FF (LIGHT_PURPLE)  ⇄ 0xAA00AA (DARK_PURPLE)
     *   究极  0xFFAA00 (GOLD/橙)       ⇄ 0xFF5555 (RED/红)
     * </pre>
     *
     * <p>取值与原版 {@code ChatFormatting} 的对应色一致，保证「未超限用 ChatFormatting、
     * 超限用同一族的渐变」时色调连贯。
     *
     * @return {@code [浅色, 深色]}
     */
    static int[] overCapColors(AscensionTier tier) {
        return switch (tier) {
            case ADVANCED -> new int[] { 0x5555FF, 0x0000AA };   // BLUE ⇄ DARK_BLUE
            case SUPER    -> new int[] { 0xFF55FF, 0xAA00AA };   // LIGHT_PURPLE ⇄ DARK_PURPLE
            case ULTRA    -> new int[] { 0xFFAA00, 0xFF5555 };   // 橙 ⇄ 红（试验）
        };
    }


    /**
     * 阶段条目的本地化键。
     *
     * <p>必须与 {@code EnchantmentFactory.descriptionOf} 以及
     * {@code LineageTable.Lineage#langKey} 保持同一构造规则。
     */
    private static String stageNameKey(ResourceLocation stageId) {
        return "enchantment." + Ultraenchantment.MODID + "."
                + stageId.getPath().replace('/', '.');
    }

    /** 档位配色：越高阶越亮。 */
    private static ChatFormatting colorOf(AscensionTier tier) {
        return switch (tier) {
            case ADVANCED -> ChatFormatting.BLUE;
            case SUPER -> ChatFormatting.LIGHT_PURPLE;
            case ULTRA -> ChatFormatting.GOLD;
        };
    }

    /**
     * 造一个「行标记」——只承载<b>附魔名字本体</b>，用于在 tooltip 里定位它的行。
     *
     * <p>{@code Enchantment} 是 record，它的 {@code description} 组件就是<b>名字本体</b>
     * （源码 {@code Enchantment.java} 第 61 行）——原版 {@code getFullname} 正是拿它
     * {@code copy()} 之后拼等级的（第 190-191 行），所以它是所有形态的共同前缀。
     */
    private static Component markerFor(Holder<Enchantment> root) {
        return root.value().description();
    }

    /**
     * 定位某条附魔在 tooltip 里的行。
     *
     * <h2>为什么不能用「固定文本精确比对」（原实现，已废弃）</h2>
     *
     * <p>原实现找的是 {@code Enchantment.getFullname(root, level).getString()}，
     * 即 {@code "Sharpness V"} 这种精确文本。装了 <b>Apothic Enchanting</b> 后它会失效，
     * 因为对方<b>两处</b>改写了附魔行的形态（都是源码实证）：
     *
     * <ol>
     *   <li><b>{@code ItemStackMixin}（priority 500）</b>在
     *       {@code ItemStack.addToTooltip(ENCHANTMENTS)} 的 HEAD 处 {@code cancel}，
     *       自己渲染。当「物品上的等级」与「结算层给出的等级」不一致时，
     *       它渲染成<b>等级差</b>形态：{@code "Sharpness 0 (- 5)"}。</li>
     *   <li><b>{@code EnchantmentMixin}（priority 1500）</b>改写 {@code getFullname} 本身：
     *       等级超过神化配置上限时，返回 {@code "🌟 " + 原名}。</li>
     * </ol>
     *
     * <p>而我们的结算层<b>必然</b>让两者不一致——进阶后基础附魔被清零、注入的是合成 holder，
     * 于是实测得到 {@code nbtLevel=5 / realLevel=0} → 走等级差分支。
     *
     * <h2>现在的判据</h2>
     *
     * <p>不比对完整文本，而是比对<b>名字本体是否出现在行首附近</b>：
     * <ul>
     *   <li>原版形态 {@code "Sharpness V"} → 直接以名字开头</li>
     *   <li>神化超限形态 {@code "🌟 Sharpness V"} → 前面多了个星标</li>
     *   <li>神化等级差形态 {@code "Sharpness 0 (- 5)"} → 仍以名字开头</li>
     * </ul>
     *
     * <p>为兼顾星标前缀，比较前先剥掉行首的<b>非字母数字</b>字符（星标、空格、符号）。
     * 这样对「不同语言的名字本体」也成立——判据全部来自附魔自身的
     * {@code descriptionId}，不硬编码任何语言字符串（与 P1-18 一致）。
     *
     * <p>⚠️ 边界：若两条附魔的名字互为前缀（如 {@code Sharpness} 与 {@code Sharpness II}），
     * 可能误匹配。原版附魔名不存在这种关系；真出现时也是「显示成同一条」而非崩溃，
     * 且我们只在<b>已进阶的谱系</b>上做替换，影响面受控。
     *
     * @param marker {@link #markerFor} 造出的名字本体
     */
    private static int indexOfEnchantmentLine(List<Component> lines, Component marker) {
        for (int i = 0; i < lines.size(); i++) {
            if (matchesEnchantmentLine(lines.get(i), marker)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 这一行是不是「某条附魔的附魔行」？
     *
     * <p>与 {@link #indexOfEnchantmentLine} 同一判据，只是返回布尔——
     * 调用方需要「删掉**全部**匹配行」时用这个（见 {@code onItemTooltip} 里的注释）。
     */
    private static boolean matchesEnchantmentLine(Component line, Component marker) {
        String name = marker.getString();
        if (name.isEmpty()) {
            return false;
        }
        return stripLeadingSymbols(line.getString()).startsWith(name);
    }

    /** 剥掉行首的非字母数字字符（神化的 🌟 星标、空格等）。 */
    private static String stripLeadingSymbols(String text) {
        int i = 0;
        while (i < text.length() && !Character.isLetterOrDigit(text.charAt(i))) {
            i++;
        }
        return text.substring(i);
    }
}
