package com.lyingice.ultraenchantment.network;

import com.lyingice.ultraenchantment.Ultraenchantment;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * <b>「空中按下了跳跃键」上报</b>——多段跳（`ue:multi_jump`）的客户端输入通道。
 *
 * <h2>为什么必须自建通道</h2>
 *
 * <p>原版的跳跃键只在<b>地面附近</b>被消费：{@code LivingEntity.travel} 里
 * {@code this.jumping && this.isAffectedByFluids()} 且 {@code this.onGround()} 才调
 * {@code jumpFromGround()}。腾空之后按住跳跃键完全没有任何服务端副作用，
 * 而且 1.21.1 <b>只在骑乘时</b>才同步 {@code ServerboundPlayerInputPacket.jumping}
 * （见 {@code LocalPlayer.tick}），空中跳跃键状态根本不往服务端发。
 *
 * <p>所以「空中第二段跳」只能由客户端在检测到「跳跃键刚被按下 + 自己不在低空」时
 * 主动上报一个空载荷，服务端据此扣减 {@code AIR_JUMPS_LEFT} 并施加位移。
 * 客户端只上报<b>意图</b>，真正的次数判定与位移全在服务端，改包无法凭空加跳。
 */
public record AirJumpPayload() implements CustomPacketPayload {

    public static final AirJumpPayload INSTANCE = new AirJumpPayload();

    public static final CustomPacketPayload.Type<AirJumpPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(Ultraenchantment.MODID, "air_jump"));

    /** 空载荷：不携带任何字段，服务端只关心「收到一则请求」。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, AirJumpPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
