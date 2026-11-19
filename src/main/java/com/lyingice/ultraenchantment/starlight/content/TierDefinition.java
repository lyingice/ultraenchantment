package com.lyingice.ultraenchantment.starlight.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

/**
 * 阶级定义——数据包注册表 {@code ultraenchantment:starlight_tier} 的一条。
 *
 * <p>这是「星图」的纵轴：{@code ordinal} 只负责排序与高低比较，
 * 具体叫什么、什么颜色、这一阶的等级上限是多少，全部由数据包决定。
 * 代码里不写死「高阶 / 超级 / 究极」三档。
 *
 * @param ordinal       阶级序号（0 = 基础阶，越大越高）
 * @param display       显示名翻译键
 * @param color         星点/阶级色（0xRRGGBB）
 * @param maxLevel      该阶级的默认等级上限（节点自己的附魔 JSON 也会带 max_level，以附魔为准）
 * @param evolutionCost 进化到该阶级需要消耗的原材料；空 = 不消耗
 * @param evolutionXp   进化到该阶级的基础经验等级；空 = 回落到边的 {@code cost_levels}。
 *                      实际扣除再按台面周围书架打折（见 {@code Bookshelves}）
 */
public record TierDefinition(int ordinal, String display, int color, int maxLevel,
                             Optional<MaterialCost> evolutionCost,
                             Optional<Integer> evolutionXp) {

    /** 进化到该阶级的原材料消耗。 */
    public record MaterialCost(ResourceLocation item, int count) {
        public static final Codec<MaterialCost> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(MaterialCost::item),
                ExtraCodecs.POSITIVE_INT.fieldOf("count").forGetter(MaterialCost::count)
        ).apply(instance, MaterialCost::new));
    }

    public static final Codec<TierDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("ordinal").forGetter(TierDefinition::ordinal),
            Codec.STRING.fieldOf("display").forGetter(TierDefinition::display),
            Codec.INT.optionalFieldOf("color", 0xFFFFFF).forGetter(TierDefinition::color),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("max_level", 255).forGetter(TierDefinition::maxLevel),
            MaterialCost.CODEC.optionalFieldOf("evolution_cost").forGetter(TierDefinition::evolutionCost),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("evolution_xp").forGetter(TierDefinition::evolutionXp)
    ).apply(instance, TierDefinition::new));

    /** 兼容无原材料/经验消耗的旧构造点（例如 TreeIndex 的兜底阶级）。 */
    public TierDefinition(int ordinal, String display, int color, int maxLevel) {
        this(ordinal, display, color, maxLevel, Optional.empty(), Optional.empty());
    }
}
