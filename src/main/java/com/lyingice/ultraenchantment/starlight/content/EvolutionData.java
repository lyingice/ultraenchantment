package com.lyingice.ultraenchantment.starlight.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 物品上的「进化履历」组件——{@code ultraenchantment:evolution}。
 *
 * <p><b>等级不在这里</b>：进化后的等级就是物品 {@code minecraft:enchantments} 里
 * 那个阶段附魔的等级。本组件只记「哪棵树、当前哪颗星、走过哪些星」。
 *
 * <p>物品上真正携带的是阶段附魔本身（真实注册表条目），所以：
 * <ul>
 *   <li>原版 tooltip / 第三方模组 / JEI 都能直接看到进化后的附魔；</li>
 *   <li>不需要 {@code GetEnchantmentLevelEvent} 注入，也不需要 {@code Holder.Direct}。</li>
 * </ul>
 *
 * @param tree    所属树 id
 * @param node    当前节点 id（树内唯一）
 * @param history 已经走过的节点 id，按时间顺序
 */
public record EvolutionData(ResourceLocation tree,
                            ResourceLocation node,
                            List<ResourceLocation> history) {

    public static final Codec<EvolutionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("tree").forGetter(EvolutionData::tree),
            ResourceLocation.CODEC.fieldOf("node").forGetter(EvolutionData::node),
            ResourceLocation.CODEC.listOf().optionalFieldOf("history", List.of()).forGetter(EvolutionData::history)
    ).apply(instance, EvolutionData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, EvolutionData> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public EvolutionData {
        history = List.copyOf(history);
    }

    /** 从当前节点走到下一颗星：把当前节点压进历史。 */
    public EvolutionData advance(ResourceLocation fromNode, ResourceLocation toNode) {
        List<ResourceLocation> next = new ArrayList<>(this.history);
        next.add(fromNode);
        return new EvolutionData(this.tree, toNode, next);
    }

    /** 这条履历是否经过某个节点（含当前节点）。 */
    public boolean hasVisited(ResourceLocation nodeId) {
        return this.node.equals(nodeId) || this.history.contains(nodeId);
    }
}
