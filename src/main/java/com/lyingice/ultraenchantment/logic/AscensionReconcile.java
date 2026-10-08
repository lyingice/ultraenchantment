package com.lyingice.ultraenchantment.logic;

import com.lyingice.ultraenchantment.content.AscensionData;
import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.content.StageDefinition;
import com.lyingice.ultraenchantment.registry.UEComponents;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * <b>外部编辑器改过附魔之后，让我们的进阶记录与物品现实对齐。</b>
 *
 * <h2>背景：编辑台只认原版那一个组件</h2>
 *
 * <p>第三方「附魔编辑台」（Enchantment Custom Table）改的是
 * {@code minecraft:enchantments}。我们的进阶身份在 {@code ultraenchantment:ascension} 里，
 * 两边一旦不同步，就会出现两种坏现象：
 *
 * <ul>
 *   <li><b>删不掉</b>：编辑台把某条附魔删了，我们的记录还在。虽然结算层要求原版等级 {@code > 0}
 *       才注入（所以「看起来」是删掉了），但只要玩家之后**再加回**同名附魔（哪怕 1 级），
 *       我们的记录就会把进阶形态<b>复活</b>；</li>
 *   <li><b>改不动</b>：编辑台把等级改成别的，我们的结算层仍按曲线等级报数 ⇒ 玩家的编辑像没生效。</li>
 * </ul>
 *
 * <h2>对账规则（以编辑台的结果为准）</h2>
 *
 * <table>
 *   <tr><th>编辑台做了什么</th><th>我们怎么做</th></tr>
 *   <tr><td>删掉了这条附魔</td><td>删掉对应记录</td></tr>
 *   <tr><td>改了这条附魔的等级</td><td><b>删掉对应记录</b>——这条退回普通附魔，等级就是玩家设的那个</td></tr>
 *   <tr><td>没动过（等级仍等于我们写下的「阶上限」）</td><td>原样保留，进阶身份不受影响</td></tr>
 *   <tr><td>加了新附魔</td><td>不管（本来就没有记录）</td></tr>
 * </table>
 *
 * <h2>为什么「改等级 ⇒ 退回普通附魔」而不是「当成新的曲线等级」</h2>
 *
 * <p>编辑台的界面显示的是<b>原版存储等级</b>（我们把它钉在「该阶上限」，见
 * {@link AscensionLogic#writeAscension}），它<b>无法</b>表达我们的曲线等级。若把玩家的编辑
 * 解释成曲线等级，我们随后会把存储等级写回阶上限 ⇒ 玩家屏幕上的数字会<b>跳回去</b>，
 * 看起来就是「改了没用」；而且玩家想设超限等级（编辑台允许）时还会被我们夹掉。
 * <b>以编辑台为准</b>最可预测：你设什么就是什么，代价是这条附魔不再是进阶形态
 * （要保留进阶形态改等级，走升级书 / 图书馆那条路）。
 */
public final class AscensionReconcile {
    private AscensionReconcile() {}

    /**
     * 对账一个物品。返回是否改动过我们的组件。
     *
     * <p>快路径：没有 {@code ascension} 组件的物品（绝大多数）直接返回，不碰注册表、不建集合。
     */
    public static boolean reconcile(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        AscensionData data = UEComponents.ascensionOf(stack);
        if (data.lineages().isEmpty()) {
            return false;
        }
        HolderLookup.RegistryLookup<StageDefinition> lookup = StageLookup.lookup();
        if (lookup == null) {
            return false;
        }
        ItemEnchantments enchants = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        Map<ResourceLocation, AscensionData.Record> keep = new LinkedHashMap<>();
        boolean changed = false;

        for (Map.Entry<ResourceLocation, AscensionData.Record> entry : data.lineages().entrySet()) {
            ResourceLocation root = entry.getKey();
            Holder<Enchantment> holder = holderOf(enchants, root);
            if (holder == null) {
                changed = true;                        // 编辑台把它删了
                continue;
            }
            Optional<LineageTier> tier =
                    UERoots.lineageTier(UERoots.tierOfStage(entry.getValue().stage()));
            if (tier.isEmpty()) {
                keep.put(root, entry.getValue());      // 阶段条目本身没了（数据包变动）⇒ 保守保留
                continue;
            }
            int stageMax = StageLookup.maxLevelOf(lookup, tier.get(), root, 0);
            if (stageMax <= 0) {
                keep.put(root, entry.getValue());
                continue;
            }
            if (enchants.getLevel(holder) != AscensionLogic.storedLevel(holder, stageMax)) {
                changed = true;                        // 等级被改过 ⇒ 退回普通附魔，玩家设的值原样保留
                continue;
            }
            keep.put(root, entry.getValue());          // 没被动过
        }

        if (!changed) {
            return false;
        }
        UEComponents.setAscension(stack, new AscensionData(keep));
        return true;
    }

    /** 物品上属于该谱系的附魔（按 root 匹配，不比等级）。 */
    private static Holder<Enchantment> holderOf(ItemEnchantments enchantments, ResourceLocation root) {
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (UERoots.rootOf(holder.value()).filter(root::equals).isPresent()) {
                return holder;
            }
        }
        return null;
    }
}
