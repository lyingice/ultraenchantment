package com.lyingice.ultraenchantment.starlight.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * 进化图上的一个节点 = 一颗星。
 *
 * <p>节点<b>指向一个真实的 {@code minecraft:enchantment} 条目</b>（{@link #enchantment}），
 * 物品上实际携带的就是这个附魔；节点自身只保留「星图身份」与布局信息。
 *
 * <p>{@link #id} 只在所属树内唯一，用于 {@code EvolutionData}、边和星图选中。
 *
 * @param id             节点 id（树内唯一）
 * @param enchantment    真实附魔 id
 * @param tier           阶级 id（{@code ultraenchantment:starlight_tier} 的条目）
 * @param pos            星图坐标；缺省时客户端自动布局
 * @param conflictGroup  冲突组：同一组的节点不能共存（同树兄弟分支默认同组）
 */
public record TreeNode(ResourceLocation id,
                       ResourceLocation enchantment,
                       ResourceLocation tier,
                       Optional<Pos> pos,
                       Optional<ResourceLocation> conflictGroup) {

    /** 星图坐标，单位由内容包自定；客户端会按内容包围盒缩放。 */
    public record Pos(float x, float y) {
        public static final Codec<Pos> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.fieldOf("x").forGetter(Pos::x),
                Codec.FLOAT.fieldOf("y").forGetter(Pos::y)
        ).apply(instance, Pos::new));
    }

    public static final Codec<TreeNode> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(TreeNode::id),
            ResourceLocation.CODEC.fieldOf("enchantment").forGetter(TreeNode::enchantment),
            ResourceLocation.CODEC.fieldOf("tier").forGetter(TreeNode::tier),
            Pos.CODEC.optionalFieldOf("pos").forGetter(TreeNode::pos),
            ResourceLocation.CODEC.optionalFieldOf("conflict_group").forGetter(TreeNode::conflictGroup)
    ).apply(instance, TreeNode::new));
}
