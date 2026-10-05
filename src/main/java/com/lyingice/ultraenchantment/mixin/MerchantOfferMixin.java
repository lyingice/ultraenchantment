package com.lyingice.ultraenchantment.mixin;

import com.lyingice.ultraenchantment.logic.trade.TradeRequirement;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让附魔进阶师的「收走满级原生附魔」这条成本规则真正生效。
 *
 * <h2>为什么必须在这里注入</h2>
 *
 * <p>成交判定链是：
 * <pre>
 *   MerchantContainer.updateSellItem()
 *     → MerchantOffers.getRecipeFor(pay1, pay2, hint)
 *       → MerchantOffer.satisfiedBy(ItemStack, ItemStack)
 *         → ItemCost.test(stack)          ← 只能做「物品 + 组件精确相等」
 * </pre>
 *
 * <p>我们的规则是「<b>带有</b>某条附魔、等级 ≥ N、<b>且该谱系未进阶</b>」——
 * 前两条谓词表达不了（{@code DataComponentPredicate} 是整条组件的
 * {@code Objects.equals} 精确比对），第三条需要读模组自己的 {@code ascension} 组件。
 *
 * <p>而 {@code ItemCost} 是 {@code final} 不可继承（{@code javap} 实测），
 * NeoForge 在这条链上也没有任何事件。因此唯一干净的落点是
 * {@code MerchantOffer.satisfiedBy}——它是<b>非 final 方法</b>，且是成交的唯一闸门。
 *
 * <h2>注入语义</h2>
 *
 * <p>只在原版<b>已判定通过</b>（{@code cir.getReturnValue() == true}）时追加校验：
 * 我们不放宽原版规则，只收紧。<b>不 {@code cancellable}</b>——不改流程，只改返回值。
 *
 * <h2>只影响我们的 offer</h2>
 *
 * <p>{@link TradeRequirement#accepts} 在成本上没有规则组件时直接返回 {@code true}，
 * 因此原版交易与其它模组的交易完全不受影响。
 */
@Mixin(MerchantOffer.class)
public abstract class MerchantOfferMixin {

    @Inject(method = "satisfiedBy", at = @At("RETURN"), cancellable = false)
    private void ultraenchantment$enforceTradeRequirement(ItemStack pay1, ItemStack pay2,
                                                          CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }
        MerchantOffer self = (MerchantOffer) (Object) this;
        if (!TradeRequirement.accepts(self.getItemCostA(), pay1)) {
            cir.setReturnValue(false);
            return;
        }
        self.getItemCostB().ifPresent(costB -> {
            if (!TradeRequirement.accepts(costB, pay2)) {
                cir.setReturnValue(false);
            }
        });
    }
}
