package com.lyingice.ultraenchantment.starlight.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

/**
 * 阶级定义——数据包注册表 {@code ultraenchantment:starlight_tier} 的一条。
 *
 * <p>这是「星图」的纵轴：{@code ordinal} 只负责排序与高低比较，
 * 具体叫什么、什么颜色、这一阶的等级上限是多少，全部由数据包决定。
 * 代码里不写死「高阶 / 超级 / 究极」三档。
 *
 * @param ordinal  阶级序号（0 = 基础阶，越大越高）
 * @param display  显示名翻译键
 * @param color    星点/阶级色（0xRRGGBB）
 * @param maxLevel 该阶级的默认等级上限（节点自己的附魔 JSON 也会带 max_level，以附魔为准）
 */
public record TierDefinition(int ordinal, String display, int color, int maxLevel) {

    public static final Codec<TierDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("ordinal").forGetter(TierDefinition::ordinal),
            Codec.STRING.fieldOf("display").forGetter(TierDefinition::display),
            Codec.INT.optionalFieldOf("color", 0xFFFFFF).forGetter(TierDefinition::color),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("max_level", 255).forGetter(TierDefinition::maxLevel)
    ).apply(instance, TierDefinition::new));
}
