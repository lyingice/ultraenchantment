package com.lyingice.ultraenchantment.mixin;

import com.lyingice.ultraenchantment.event.UEShieldEvents;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>盾牌破防钩子</b>——{@code on_shield_disabled} 的唯一落点。
 *
 * <h2>为什么这次必须用 Mixin</h2>
 *
 * <p>NeoForge 有 {@code LivingShieldBlockEvent}（成功格挡）却<b>没有</b>
 * 「盾牌被斧类打中、进入冷却」的事件。而 {@code Player#disableShield()} 正是那一刻：
 * 原版在这里给盾牌上 100 tick 冷却、停止使用、广播事件 30（盾牌打飞的手部动画）。
 *
 * <p>{@code blockUsingShield} 里 {@code canDisableShield()} 为真时才会调到它，
 * 所以注入 {@code disableShield} 的 {@code HEAD} 就精确对准了「被破防」这一个时刻，
 * 不会与成功格挡混淆。
 *
 * <h2>注入面</h2>
 *
 * <p>单一无参方法、{@code HEAD}、{@code CallbackInfo}（不取消、只旁路通知）。
 * 即使原版未来改了冷却时长，本注入点也不会失效——它只关心「这个方法被调到了」。
 */
@Mixin(Player.class)
public abstract class PlayerDisableShieldMixin {

    /** 破防瞬间通知扩展效果层。 */
    @Inject(method = "disableShield", at = @At("HEAD"))
    private void ultraenchantment$onShieldDisabled(CallbackInfo ci) {
        UEShieldEvents.onShieldDisabled((Player) (Object) this);
    }
}
