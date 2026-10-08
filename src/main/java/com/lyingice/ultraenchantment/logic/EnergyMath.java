package com.lyingice.ultraenchantment.logic;

/**
 * <b>等级单位的全部换算</b>——v5 起本类只服务「提级」这一件事。
 *
 * <h2>v5 删掉了什么</h2>
 *
 * <p>升阶的代价从「阶级单位点数」换成了「消耗一本进阶书」，所以这些东西<b>整个消失</b>：
 * <ul>
 *   <li>{@code ascensionPrice} / {@code tierCoefficient}（{@code required_level × 系数}）</li>
 *   <li>{@code GENERIC_ASCENSION_BASE}（通用进阶书的定价基准）</li>
 *   <li>{@code EXCHANGE_RATE} / {@code exchangeYield}（4:1 向上兑换）</li>
 * </ul>
 *
 * <p><b>不要再把它们加回来。</b>任何「阶位也需要一个数」的念头，先看
 * {@code docs/library-energy-model.md} v5 §1（为什么点数模型是错的）。
 *
 * <h2>留下的三条</h2>
 *
 * <ol>
 *   <li><b>等级 → 等级单位</b>：{@code 2^(level-1)}（沿用神化）</li>
 *   <li><b>边际差</b>：{@code f(目标) - f(现有)}；降级时是负数 ⇒ 返还</li>
 *   <li><b>通用加成 ×4</b>：升级书（不分附魔种类）与……嗯，现在只剩升级书了</li>
 * </ol>
 */
public final class EnergyMath {
    private EnergyMath() {}

    /**
     * 位移上限，防 int 溢出。
     *
     * <p>阶段定义的 {@code max_level} 上限是 255，直接 {@code 1 << 254} 会溢出成负数。
     * 取 30 与神化的「末影图书馆 31 级」同量级。
     */
    public static final int MAX_SHIFT = 30;

    /** 单个桶的能量上限（防溢出）。 */
    public static final int MAX_ENERGY = 1 << MAX_SHIFT;

    /**
     * <b>通用加成 N = ×4</b>。
     *
     * <p>「越通用的书，存入产出越高」—— 因为它对你越有用，放弃它的<b>机会成本</b>越大，
     * 否则没人愿意把通用书存进去。
     *
     * <p>v5 起只适用于<b>升级书</b>（不分附魔种类的铭刻书）。进阶书不再产出任何能量。
     */
    public static final int GENERIC_MULTIPLIER = 4;

    /** {@code 2^(level-1)}，位移夹在 {@link #MAX_SHIFT} 以内。 */
    public static int levelToPoints(int level) {
        if (level <= 0) {
            return 0;
        }
        return 1 << Math.min(level - 1, MAX_SHIFT);
    }

    /** 把某格从 {@code currentLevel} 提到 {@code targetLevel} 所需的等级单位（只算升级，降级返回 0）。 */
    public static int costToReach(int targetLevel, int currentLevel) {
        return Math.max(0, levelToPoints(targetLevel) - levelToPoints(currentLevel));
    }

    /**
     * <b>带符号的净花费</b>：正 = 要付，负 = 应<b>退还</b>，0 = 不变。
     *
     * <p>退还是<b>按曲线价差</b>算的，与升级同源，因此「升上去再降回来」不赚不亏。
     */
    public static int netCostToReach(int targetLevel, int currentLevel) {
        return levelToPoints(targetLevel) - levelToPoints(currentLevel);
    }

    /** 把 {@code amount} 加进 {@code current}，夹在 {@link #MAX_ENERGY} 以内（long 中间量防溢出）。 */
    public static int cappedAdd(int current, int amount) {
        if (amount <= 0) {
            return current;
        }
        return (int) Math.min(MAX_ENERGY, (long) current + amount);
    }

    /** 以 {@code multiplier} 放大后再夹取——升级书的 ×4 走这里。 */
    public static int scaled(int base, int multiplier) {
        return (int) Math.min(MAX_ENERGY, (long) Math.max(0, base) * Math.max(0, multiplier));
    }
}
