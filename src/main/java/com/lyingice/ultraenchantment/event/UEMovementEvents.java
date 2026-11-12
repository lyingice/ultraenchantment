package com.lyingice.ultraenchantment.event;

import com.lyingice.ultraenchantment.UEConfig;
import com.lyingice.ultraenchantment.content.UEEffect;
import com.lyingice.ultraenchantment.logic.UEEffectExecution;
import com.lyingice.ultraenchantment.logic.UEEffectLookup;
import com.lyingice.ultraenchantment.registry.UEAttachments;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * <b>多段跳</b>（`ue:multi_jump`）——服务端权威的空中跳跃。
 *
 * <h2>分工</h2>
 *
 * <ul>
 *   <li><b>客户端</b>（{@code UEKeyInput}）：检测「跳跃键刚按下 &amp; 不在低空」，发一则
 *       {@code AirJumpPayload}。只上报意图，不做任何判定。</li>
 *   <li><b>服务端</b>（本类）：收到请求后——校验玩家正常、仍在空中、
 *       身上确实有该效果、剩余次数 &gt; 0——才扣次数并施加向上的速度。</li>
 *   <li><b>落地重置</b>：每个 tick 检查，落地即把剩余次数补满到「当前装备提供的段数」。</li>
 * </ul>
 *
 * <p>次数存在 {@link UEAttachments#AIR_JUMPS_LEFT}（服务端附件），客户端改包只能
 * 请求「我要跳」，跳不跳得成、还剩几段全在服务端算。
 */
public final class UEMovementEvents {
    private UEMovementEvents() {}

    /** 单例监听器。 */
    public static final UEMovementEvents INSTANCE = new UEMovementEvents();

    /** 空中跳跃初速度——略低于原版地面起跳（1.0 的跳跃力约 0.42），避免二段跳反而更高。 */
    private static final double AIR_JUMP_POWER = 0.42;

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (UEEffectExecution.disabled()) {
            return;
        }
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }

        // 落地：把剩余跳跃补满到装备提供的段数——这是唯一的「补充」来源，
        // 所以「空中跳几次」永远不超过当前装备允许的值。
        if (player.onGround()) {
            UEAttachments.setAirJumpsLeft(player, maxJumps(player));
        }
    }

    /**
     * 处理一则「空中跳跃」请求（由 {@code AirJumpPayload} 的处理器在服务端线程调用）。
     *
     * @return 是否真的跳了（用于调试/探针）
     */
    public static boolean handleAirJump(Player player) {
        if (UEEffectExecution.disabled() || player.level().isClientSide()) {
            return false;
        }
        // 仍在低空（或水中/飞行中）时交给原版自己的跳跃，不消耗多段跳次数。
        if (player.onGround() || player.isFallFlying() || player.isPassenger()
                || player.getAbilities().flying || player.isInWater()) {
            return false;
        }

        int left = UEAttachments.airJumpsLeft(player);
        if (left <= 0) {
            return false;
        }

        UEAttachments.setAirJumpsLeft(player, left - 1);

        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, AIR_JUMP_POWER, motion.z);
        player.hasImpulse = true;
        player.fallDistance = 0.0f;
        player.hurtMarked = true;
        return true;
    }

    /** 该玩家当前装备提供的空中跳跃段数（所有装备槽上的该效果取最大段数，再受全局上限压住）。 */
    private static int maxJumps(Player player) {
        int best = 0;
        for (UEEffectLookup.Resolved entry : UEEffectLookup.fromEquipment(player)) {
            if (entry.effect() instanceof UEEffect.MultiJump multi) {
                best = Math.max(best, multi.jumps().atInt(entry.level()));
            }
        }
        return Math.min(best, UEConfig.MAX_MULTI_JUMPS.get());
    }
}
