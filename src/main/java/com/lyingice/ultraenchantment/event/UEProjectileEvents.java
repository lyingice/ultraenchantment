package com.lyingice.ultraenchantment.event;

import com.lyingice.ultraenchantment.content.UEEffect;
import com.lyingice.ultraenchantment.logic.UEEffectExecution;
import com.lyingice.ultraenchantment.logic.UEEffectLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * <b>弹射物加速</b>（`ue:projectile_speed`）——射出的箭/投掷物飞得更快。
 *
 * <h2>为什么挂在「实体加入世界」</h2>
 *
 * <p>NeoForge 没有「弹射物出膛」事件；原版的 {@code PROJECTILE_SPAWNED} 效果组件只
 * 表达「点燃」这类实体效果，改不了速度。而所有原版弹射物在 {@code shoot(...)} 设好初速度后
 * 都会 {@code addFreshEntity}，那一刻速度已经是最终值——{@link EntityJoinLevelEvent} 正是
 * 「速度快照」最干净的落点。
 *
 * <h2>为什么用持久化标记</h2>
 *
 * <p>实体跨维度或区块卸载重载时会<b>再次</b>触发 join 事件。若每次都乘一遍倍率，
 * 一支箭穿过下界门后速度会指数暴涨。因此在实体自己的 {@code PersistentData} 上打一个
 * 一次性标记，保证「一支箭只加速一次」。
 */
public final class UEProjectileEvents {
    private UEProjectileEvents() {}

    /** 单例监听器。 */
    public static final UEProjectileEvents INSTANCE = new UEProjectileEvents();

    /** 一次性标记键（写在实体 PersistentData 上，随实体存盘，重载也不会重复加速）。 */
    private static final String ACCELERATED_TAG = "ultraenchantment.projectile_speed";

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (UEEffectExecution.disabled() || event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Projectile projectile)) {
            return;
        }
        // 跨维度/重载：已经加速过的不再加速。
        CompoundTag data = projectile.getPersistentData();
        if (data.getBoolean(ACCELERATED_TAG)) {
            return;
        }

        Entity owner = projectile.getOwner();
        if (!(owner instanceof LivingEntity shooter)) {
            return;
        }

        float multiplier = speedMultiplier(shooter);
        if (multiplier == 1.0f) {
            return;
        }

        Vec3 motion = projectile.getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-6) {
            return; // 尚未设定速度（极少数自定义弹射物），不干预。
        }
        projectile.setDeltaMovement(motion.scale(multiplier));
        data.putBoolean(ACCELERATED_TAG, true);
    }

    /** 射击者武器上的弹射物速度倍率（取最高一条，至少 1.0）。 */
    private static float speedMultiplier(LivingEntity shooter) {
        float best = 1.0f;
        for (UEEffectLookup.Resolved entry : UEEffectLookup.fromWeapon(shooter)) {
            if (entry.effect() instanceof UEEffect.ProjectileSpeed speed) {
                best = Math.max(best, Math.max(1.0f, speed.multiplier().at(entry.level())));
            }
        }
        return best;
    }
}
