package com.lyingice.ultraenchantment.compat.jei;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * JEI 里一条铁砧操作的显示数据：<b>左槽 + 右槽 → 结果</b>。
 *
 * <p>三个槽都是<b>列表</b>：JEI 会把列表里的多个物品在同一格里<b>轮播</b>。
 * 「中间等级」正是靠这个展示的——一本配方覆盖一组等级，而不是每个等级铺一条，
 * 既不膨胀条目数，又能让玩家看到「从几级到几级」。
 *
 * @param kind   操作种类（决定配方上那行字：附魔进阶 / 附魔合并 / 附魔升级）
 * @param input  左槽（可轮播）
 * @param book   右槽（可轮播）
 * @param output 结果（可轮播，与右槽对应展示「这些等级能变成什么」）
 * @param notes  悬停时补充的说明（进阶要求、合并规则等）
 */
public record AnvilDisplay(Kind kind, List<ItemStack> input, List<ItemStack> book,
                           List<ItemStack> output, List<Component> notes) {

    public enum Kind {
        /** 物品 + 进阶书：推进一阶。 */
        ASCEND,
        /** 书 + 书：合并 / 推阶 / 提升 / 转印。 */
        MERGE,
        /** 物品 + 升级书：同阶提升曲线等级。 */
        UPGRADE
    }
}
