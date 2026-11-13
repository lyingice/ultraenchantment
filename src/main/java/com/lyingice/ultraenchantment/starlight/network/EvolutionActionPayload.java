package com.lyingice.ultraenchantment.starlight.network;

import com.lyingice.ultraenchantment.Ultraenchantment;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端的星光操作请求。
 *
 * <p>只看两个东西：目标节点 id 与动作（进化 / 升级）。
 * 其余全部由服务端从物品与数据包重新推导，客户端不参与判定。
 *
 * @param tree   树 id（防止客户端拿别的树的节点来撞）
 * @param node   目标节点 id；升级动作时是当前节点
 * @param action {@link #ACTION_EVOLVE} 或 {@link #ACTION_UPGRADE}
 */
public record EvolutionActionPayload(ResourceLocation tree,
                                     ResourceLocation node,
                                     int action) implements CustomPacketPayload {

    public static final int ACTION_EVOLVE = 0;
    public static final int ACTION_UPGRADE = 1;

    public static final Type<EvolutionActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Ultraenchantment.MODID, "evolution_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EvolutionActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, EvolutionActionPayload::tree,
                    ResourceLocation.STREAM_CODEC, EvolutionActionPayload::node,
                    ByteBufCodecs.VAR_INT, EvolutionActionPayload::action,
                    EvolutionActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
