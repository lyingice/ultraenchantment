package com.lyingice.ultraenchantment.compat.legendarytooltips;

import com.lyingice.ultraenchantment.registry.UEComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

/**
 * 「传说提示框」两个 mixin 的<b>适配层</b>。
 *
 * <h2>对方在做什么</h2>
 *
 * <p>它修的是原版 **MC-271840**：附魔带来的加伤不出现在物品的攻击伤害 tooltip 行里
 * （源码见 {@code ItemStack} 的属性段：只统计玩家基础值 + 属性修正，不含附魔的
 * {@code minecraft:damage} 效果）。它的做法是在 {@code AttributeUtil} 与 {@code ItemStack} 各打一个
 * {@code getAttributeBaseValueProxy}：把「玩家基础攻击力 + 遍历附魔的 DAMAGE 效果」当作基础值。
 *
 * <h2>为什么需要适配</h2>
 *
 * <p>它遍历的是 {@code ItemStack.getOrDefault(DataComponents.ENCHANTMENTS, ...)}——
 * <b>原始组件</b>。而本模组的阶级附魔是结算层（查询期注入）才出现的：
 * 物品上<b>存储</b>的仍是原版附魔（铁律：存储等级留给原版机制，见 P0-1），
 * 于是它算出来永远是原版数字——作者实测：阶级究极锋利 V 与普通锋利 V 都显示 11。
 *
 * <h2>适配做法</h2>
 *
 * <p>不跟它抢方法体，只<b>换掉它读到的数据</b>：把原始附魔表替换成
 * {@code stack.getAllEnchantments(...)}——也就是我们结算层的表
 * （原版附魔被清零、阶级阶段按 <b>tierLevel</b> 注入）。
 * 对方的公式一行不改，算出来的却正是真实值。
 *
 * <p>本类只在「装了传说提示框 + 逻辑客户端」时才会被类加载（见 P1-40 的隔离约定）。
 */
public final class LegendaryTooltipsCompat {

    private LegendaryTooltipsCompat() {
    }

    /**
     * 把对方读到的原始附魔表换成结算层的表。
     *
     * @param stack    正在算 tooltip 的物品
     * @param original 对方原本读到的原始附魔表
     * @return 进阶物品返回结算表；其余情况<b>原样返回</b>（绝不改变普通物品的显示）
     */
    public static ItemEnchantments settledEnchantments(ItemStack stack, ItemEnchantments original) {
        if (stack == null || !stack.has(UEComponents.ASCENSION.get())) {
            return original;
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return original;   // 还没进世界：拿不到数据包注册表，保守地维持原样
        }
        // 附魔是数据包注册表：必须从**当前侧**的 registry access 取（客户端就是这条连接的表）。
        // 拿到 lookup 后走 getAllEnchantments —— 它会触发结算层事件（P0-1），
        // 返回的表里原版附魔已被清零、阶级阶段按 tierLevel 注入。
        return stack.getAllEnchantments(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT));
    }
}
