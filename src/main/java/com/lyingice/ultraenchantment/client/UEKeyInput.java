package com.lyingice.ultraenchantment.client;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.network.AirJumpPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * <b>多段跳的客户端输入检测</b>——在「跳跃键刚按下且不在低空」时上报一次请求。
 *
 * <h2>为什么在客户端边缘检测</h2>
 *
 * <p>服务端拿不到「空中时跳跃键是否被按下」（见 {@code AirJumpPayload} 的说明），
 * 因此必须在客户端把「按下」这一<b>事件</b>（而非持续状态）捕捉下来：
 * 用 {@code keyJump.consumeClick()} 而不是 {@code isDown()}——
 * 后者每 tick 都为真，会一口气把剩余跳跃全花光。
 *
 * <p>本类只做「检测 + 上报」，不含任何判定。玩家能否真的跳由服务端决定。
 */
@EventBusSubscriber(modid = Ultraenchantment.MODID, value = Dist.CLIENT)
public final class UEKeyInput {
    private UEKeyInput() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null) {
            return;
        }
        // consumeClick：一次按键只消费一次——按住不放不会连续上报。
        while (minecraft.options.keyJump.consumeClick()) {
            // 地面/飞行/水中交给原版跳跃，不上报，避免白白消耗空中跳跃次数。
            if (player.onGround() || player.getAbilities().flying || player.isFallFlying()
                    || player.isPassenger() || player.isInWater()) {
                continue;
            }
            PacketDistributor.sendToServer(AirJumpPayload.INSTANCE);
        }
    }
}
