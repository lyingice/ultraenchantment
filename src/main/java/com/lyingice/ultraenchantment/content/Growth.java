package com.lyingice.ultraenchantment.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * <b>线性成长曲线</b>：{@code base + per_level × (等级 - 1)}。
 *
 * <p>这是 `ue:*` 扩展效果统一的数值形状——与原版 {@code LevelBasedValue.Linear} 同构，
 * 但这里只表达「一个随等级变化的数」，不参与原版效果执行器，因此不实现
 * {@code LevelBasedValue}（那会把它拖进原版的 codec dispatch）。
 *
 * <p>等级一律取 {@code AscensionData.tierLevel}（进阶后曲线等级），与阶段条目里
 * 原版效果组件用的是同一个等级刻度，二者不会脱节。
 */
public record Growth(float base, float perLevel) {

    /** 常量（不随等级变化）。 */
    public static final Growth CONSTANT_ZERO = new Growth(0.0f, 0.0f);

    public static final Codec<Growth> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("base").forGetter(Growth::base),
            Codec.FLOAT.optionalFieldOf("per_level", 0.0f).forGetter(Growth::perLevel)
    ).apply(instance, Growth::new));

    /** 便于 JSON 里直接写一个常数。 */
    public static Growth of(float value) {
        return new Growth(value, 0.0f);
    }

    /** 该等级下的取值；等级 &lt; 1 时按 1 计。 */
    public float at(int level) {
        return this.base + this.perLevel * Math.max(0, level - 1);
    }

    /** 该等级下的取值，取整（用于次数、数量这类整数量）。 */
    public int atInt(int level) {
        return Math.round(at(level));
    }

    /** 可取值的最大下限——所有 {@code perLevel} 非负的曲线，值域下界就是 {@code base}。 */
    public float maxAt(int maxLevel) {
        return Math.max(base, at(maxLevel));
    }
}
