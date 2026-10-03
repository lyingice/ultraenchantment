package com.lyingice.ultraenchantment.compat.legendarytooltips;

import com.lyingice.ultraenchantment.registry.UEComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;

/**
 * 修正「传说提示框」算错的那条**绿色攻击伤害行**。
 *
 * <h2>它错在哪</h2>
 *
 * <p>它修的是原版 MC-271840（附魔加伤不出现在攻击伤害行），做法是把
 * {@code 玩家基础攻击力 + 遍历附魔的 DAMAGE 效果} 当作基础值。但它遍历的是
 * {@code DataComponents.ENCHANTMENTS}——<b>原始组件</b>。
 * 本模组的阶级附魔只存在于结算层，物品上存储的仍是原版附魔，于是它永远算出原版数字：
 * 实测「阶级究极锋利 V」与「普通锋利 V」都显示 <b>11</b>（1 基础 + 7 剑 + 3 原版锋利 V）。
 *
 * <h2>为什么不直接改它的计算</h2>
 *
 * <p>试过在它注入的方法上再注入（{@code @ModifyExpressionValue} 换掉它读的附魔表），
 * <b>跨模组的 mixin 应用顺序无法保证</b>：两种 priority 都实测「Scanned 0 target(s)」——
 * 我们应用时它注入的代码还没进去。所以改成<b>在成品 tooltip 上补正差值</b>，
 * 不依赖对方任何内部结构。
 *
 * <h2>补正量</h2>
 *
 * <p>{@code Δ = 结算表的附魔加伤 − 原始表的附魔加伤}。
 * 结算表由 {@link LegendaryTooltipsCompat#settledEnchantments} 取得（走我们的结算层），
 * 两边用<b>同一套求和方式</b>（遍历 DAMAGE 效果、按各自表里的等级求值），
 * 因此对原版物品 Δ 恒为 0——普通物品的显示一个数字都不会变。
 *
 * <p>只在本模组入场（装了传说提示框 + 逻辑客户端）时被调用，见
 * {@code TooltipStackCompat.useLegendaryTooltipsFix()}。
 */
public final class LegendaryTooltipsFixup {

    /** 行里的数值（可能带负号与小数）。 */
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private LegendaryTooltipsFixup() {
    }

    /**
     * 把攻击伤害行上的数值补正 {@code Δ}（只改第一处命中的那条行）。
     *
     * @param stack   正在渲染 tooltip 的物品
     * @param tooltip 已成型的 tooltip 行（可变）
     */
    public static void correctAttackDamageLine(ItemStack stack, List<Component> tooltip) {
        if (!stack.has(UEComponents.ASCENSION.get())) {
            return;
        }
        float delta = damageDelta(stack);
        if (Math.abs(delta) < 0.001f) {
            return;
        }

        String attributeName = Component
                .translatable(Attributes.ATTACK_DAMAGE.value().getDescriptionId()).getString();
        for (int i = 0; i < tooltip.size(); i++) {
            Component line = tooltip.get(i);
            if (!line.getString().contains(attributeName)) {
                continue;
            }
            Component corrected = rewriteNumber(line, delta);
            if (corrected != null) {
                tooltip.set(i, corrected);
                return;
            }
        }
    }

    /**
     * 把行里第一处数值换成补正后的值，其余文本与样式逐段保留。
     *
     * <p><b>为什么按「带样式的文本片段」重建，而不是改 siblings</b>：
     * 那条行是 {@code Component.translatable("attribute.modifier.equals.0", 数值, 属性名)}，
     * 数值是<b>参数</b>而不是 sibling——{@code getSiblings()} 是空的（实测踩过）。
     * {@code visit} 会把参数展开成带样式的片段，于是可以逐段重建：
     * 命中第一处数字就换掉，其它片段原样（含各自颜色）拼回去。
     *
     * @return 重建后的行；没命中数字时返回 {@code null}（调用方保持原样）
     */
    private static Component rewriteNumber(Component line, float delta) {
        List<Component> parts = new ArrayList<>();
        boolean[] replaced = {false};
        line.visit((style, text) -> {
            String out = text;
            if (!replaced[0]) {
                Matcher matcher = NUMBER.matcher(text);
                if (matcher.find()) {
                    out = text.substring(0, matcher.start())
                            + format(Double.parseDouble(matcher.group()) + delta)
                            + text.substring(matcher.end());
                    replaced[0] = true;
                }
            }
            parts.add(Component.literal(out).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);

        if (!replaced[0]) {
            return null;
        }
        MutableComponent rebuilt = Component.empty();
        parts.forEach(rebuilt::append);
        return rebuilt;
    }

    /** 与对方一致：结算表与原始表的附魔加伤之差。 */
    private static float damageDelta(ItemStack stack) {
        ItemEnchantments raw = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments settled = LegendaryTooltipsCompat.settledEnchantments(stack, raw);
        return sumDamage(settled) - sumDamage(raw);
    }

    /**
     * 遍历一张附魔表里**无条件**的 DAMAGE 效果并按各条目的等级求值求和。
     *
     * <p><b>有条件的一律不计入</b>——像亡灵杀手（只对亡灵）、穿刺（只对水生）这类，
     * 加伤取决于打谁，tooltip 上写一个数字就是骗人。
     * 这不是我们的口味问题：实测对方的字节码里同样调用了
     * {@code ConditionalEffect.requirements()} → {@code Optional.isEmpty()} 来跳过条件效果
     * （反编译 {@code AttributeUtilMixin} 第 173/176 行），所以「两边同口径」也必须跳过，
     * 否则差值会凭空多出/少掉一块。**两边都只算无条件部分。**
     */
    private static float sumDamage(ItemEnchantments enchantments) {
        float sum = 0.0f;
        for (var entry : enchantments.entrySet()) {
            for (ConditionalEffect<EnchantmentValueEffect> effect : entry.getKey().value()
                    .getEffects(EnchantmentEffectComponents.DAMAGE)) {
                if (effect.requirements().isPresent()) {
                    continue;   // 有条件（如只对特定生物）→ 不计入
                }
                sum += effect.effect().process(entry.getIntValue(), RandomSource.create(), 0.0f);
            }
        }
        return sum;
    }

    /** 与原版 tooltip 一致的数字格式：整数不带小数点。 */
    private static String format(double value) {
        return value == Math.floor(value)
                ? String.valueOf((long) value)
                : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
