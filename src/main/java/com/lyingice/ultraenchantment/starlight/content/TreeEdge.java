package com.lyingice.ultraenchantment.starlight.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

/**
 * 进化图的一条边：从 {@link #from} 节点进化到 {@link #to} 节点。
 *
 * <p>一条边就是一次「进化仪式」的完整配方：前置等级、成功概率、经验等级代价、
 * 以及可选的 branch 标签（分支选择 UI 用）。
 *
 * @param from          起始节点 id
 * @param to            目标节点 id
 * @param requiredLevel 起始节点需要达到的等级
 * @param chance        成功率 0..1
 * @param costLevels    失败/成功都要扣的经验等级数（0 = 免费）
 * @param failureDropMin 失败时附魔等级最少掉几级
 * @param failureDropMax 失败时附魔等级最多掉几级
 * @param branch        分支显示名翻译键；同一起点的多条边用不同 branch 区分
 */
public record TreeEdge(ResourceLocation from,
                       ResourceLocation to,
                       int requiredLevel,
                       float chance,
                       int costLevels,
                       int failureDropMin,
                       int failureDropMax,
                       Optional<String> branch) {

    public static final Codec<TreeEdge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("from").forGetter(TreeEdge::from),
            ResourceLocation.CODEC.fieldOf("to").forGetter(TreeEdge::to),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("required_level", 1).forGetter(TreeEdge::requiredLevel),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("chance", 1.0F).forGetter(TreeEdge::chance),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("cost_levels", 0).forGetter(TreeEdge::costLevels),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("failure_drop_min", 2).forGetter(TreeEdge::failureDropMin),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("failure_drop_max", 4).forGetter(TreeEdge::failureDropMax),
            Codec.STRING.optionalFieldOf("branch").forGetter(TreeEdge::branch)
    ).apply(instance, TreeEdge::new));

    public TreeEdge {
        if (failureDropMax < failureDropMin) {
            failureDropMax = failureDropMin;
        }
    }
}
