package com.lyingice.ultraenchantment.event;

import com.lyingice.ultraenchantment.content.UEEffect;
import com.lyingice.ultraenchantment.logic.UEEffectExecution;
import com.lyingice.ultraenchantment.logic.UEEffectGuard;
import com.lyingice.ultraenchantment.logic.UEEffectLookup;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

/**
 * <b>盾牌效果</b>——成功格挡后的反击 / 反伤 / 施加效果。
 *
 * <h2>格挡与破防是两件事</h2>
 *
 * <p>{@link LivingShieldBlockEvent} 只在「成功格挡」时给出，对应 {@code on_block}。
 * 盾牌<b>被斧类打中进入冷却</b>（{@code Player.disableShield}）没有事件，
 * 由 {@code PlayerDisableShieldMixin} 在那一刻回调 {@link #onShieldDisabled}——
 * 两条路径<b>不会</b>同时成立：能被破防的一击本身就绕过了格挡。
 *
 * <h2>为什么用副手</h2>
 *
 * <p>格挡用的是 {@code getUseItem()}，对玩家就是正在使用的那只手（盾牌必在副手）。
 * 取副手栈即可对上「是哪面盾提供了这个效果」。
 */
public final class UEShieldEvents {
    private UEShieldEvents() {}

    /** 单例监听器。 */
    public static final UEShieldEvents INSTANCE = new UEShieldEvents();

    @SubscribeEvent
    public void onShieldBlock(LivingShieldBlockEvent event) {
        if (UEEffectExecution.disabled() || !event.getBlocked()) {
            return;
        }
        LivingEntity blocker = event.getEntity();
        if (!UEEffectExecution.serverSide(blocker)) {
            return;
        }
        LivingEntity attacker = UECombatEvents.resolveAttacker(event.getDamageSource());
        float blocked = event.getBlockedDamage();

        for (UEEffectLookup.Resolved entry : UEEffectLookup.fromOffhand(blocker)) {
            if (entry.effect() instanceof UEEffect.OnBlock onBlock) {
                trigger(onBlock.back(), onBlock.effects(), entry.level(), blocker, attacker, blocked);
            }
        }
    }

    /**
     * 盾牌被破防（进入冷却）时触发——由 Mixin 在 {@code Player#disableShield} 里回调。
     *
     * @param blocker 盾牌被破防的一方
     */
    public static void onShieldDisabled(LivingEntity blocker) {
        if (UEEffectExecution.disabled() || blocker == null || !UEEffectExecution.serverSide(blocker)) {
            return;
        }
        // 破防一击的攻击者：原版在受害者的 lastHurtByMob 上记着；取不到就不施加攻击者向效果。
        LivingEntity attacker = blocker.getLastHurtByMob();

        for (UEEffectLookup.Resolved entry : UEEffectLookup.fromOffhand(blocker)) {
            if (entry.effect() instanceof UEEffect.OnShieldDisabled disabled) {
                trigger(disabled.back(), disabled.effects(), entry.level(), blocker, attacker, 0.0f);
            }
        }
    }

    /**
     * 两处触发的共用结算：反击伤害、按比例反伤、施加状态效果。
     *
     * @param blocked 被格挡掉的伤害量（破防路径为 0，此时只走固定反击伤害）
     */
    private static void trigger(UEEffect.DamageBack back, List<UEEffect.AppliedEffect> effects,
                                int level, LivingEntity blocker, LivingEntity attacker, float blocked) {
        // 施加状态效果（对自身 / 攻击者）。
        UEEffectExecution.applyEffects(effects, level, blocker, attacker);

        if (attacker == null || attacker == blocker || !attacker.isAlive()) {
            return;
        }
        // 反击 / 反伤都要重入保护：打出去的伤害可能再次触发对方的同类效果。
        UEEffectGuard.run(blocker, () -> {
            // 固定反击伤害。
            float counter = back.counterDamage().at(level);
            if (counter > 0.0f) {
                attacker.hurt(blocker.damageSources().thorns(blocker), counter);
            }
            // 按概率把被格挡的伤害返还给攻击者。
            float chance = UEEffectExecution.clamp01(back.reflectChance().at(level));
            if (blocked > 0.0f && chance > 0.0f && blocker.getRandom().nextFloat() < chance) {
                float ratio = UEEffectExecution.clamp01(back.reflectRatio().at(level));
                float reflected = blocked * (ratio > 0.0f ? ratio : 1.0f);
                if (reflected > 0.0f) {
                    attacker.hurt(blocker.damageSources().thorns(blocker), reflected);
                }
            }
        });
    }
}
