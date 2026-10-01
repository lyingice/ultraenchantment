package com.lyingice.ultraenchantment.event;

import com.lyingice.ultraenchantment.Ultraenchantment;
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

            // 原版渲染出的那一行（用于定位并移除）。
            //
            // 注意：这里用的是与 tooltip 完全相同的 Component 构造方式，
            // 因此 getString() 在客户端与服务端都一致——不依赖具体语言。
            Component vanillaLine = Enchantment.getFullname(root, level);

            toRemove.add(vanillaLine);
            toAdd.add(renderStagedLine(stageId, rootId, root, data.tierLevelOf(rootId)));
        });

        if (toAdd.isEmpty()) {
            return;
        }

        // 先删原行，再把改写后的行插回原位置（保持附魔区块的相对顺序）。
        int insertAt = -1;
        for (Component remove : toRemove) {
            int idx = indexOfSameText(lines, remove);
            if (idx >= 0) {
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

        MutableComponent line = Component.translatable(stageNameKey(stageId)).withStyle(colorOf(tier));

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
            line.append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable("enchantment.level." + shown)
                            .withStyle(ChatFormatting.GRAY));
        }
        return line;
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

    /** 按文本内容定位行（tooltip 行是 Component，按纯文本比较最稳）。 */
    private static int indexOfSameText(List<Component> lines, Component target) {
        String want = target.getString();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).getString().equals(want)) {
                return i;
            }
        }
        return -1;
    }
}
