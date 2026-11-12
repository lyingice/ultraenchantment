package com.lyingice.ultraenchantment.network;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.event.UEMovementEvents;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * <b>网络载荷注册</b>。
 *
 * <p>挂在 mod bus（{@link RegisterPayloadHandlersEvent} 是 {@code IModBusEvent}）。
 *
 * <p>用 {@code optional()}：本模组的自定义载荷只在装了本模组的客户端上存在；
 * 设为 optional 后，客户端与「未装本模组的服务端」之间也不会因载荷协商失败而拒连——
 * 对纯客户端模组、以及「服务端版本比客户端旧」这类组合都更安全。
 */
@EventBusSubscriber(modid = Ultraenchantment.MODID)
public final class UENetwork {
    private UENetwork() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(
                AirJumpPayload.TYPE,
                AirJumpPayload.STREAM_CODEC,
                UENetwork::onAirJump);
    }

    /** 服务端处理「空中跳跃」请求——真正的判定与位移都在 {@link UEMovementEvents}。 */
    private static void onAirJump(AirJumpPayload payload, IPayloadContext context) {
        Player player = context.player();
        context.enqueueWork(() -> UEMovementEvents.handleAirJump(player));
    }
}
