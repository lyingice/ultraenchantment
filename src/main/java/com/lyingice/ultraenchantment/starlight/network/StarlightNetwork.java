package com.lyingice.ultraenchantment.starlight.network;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.starlight.menu.StarlightTableMenu;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 星光系统的网络载荷注册。
 *
 * <p>与旧网络的 {@code UENetwork} 分开：这个分支的星光系统不依赖旧 18 ue 的代码，
 * 载荷注册自己一份，互不影响。
 */
@EventBusSubscriber(modid = Ultraenchantment.MODID)
public final class StarlightNetwork {
    private StarlightNetwork() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToServer(
                EvolutionActionPayload.TYPE,
                EvolutionActionPayload.STREAM_CODEC,
                StarlightNetwork::onAction);
    }

    private static void onAction(EvolutionActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && player.containerMenu instanceof StarlightTableMenu menu) {
            context.enqueueWork(() -> menu.handleAction(player, payload));
        }
    }
}
