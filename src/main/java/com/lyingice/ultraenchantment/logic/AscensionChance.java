package com.lyingice.ultraenchantment.logic;

import com.lyingice.ultraenchantment.UEConfig;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * 附魔台「进阶」的<b>概率模型</b> —— 纯函数，两条路径（原版附魔台 / 神化的附魔台）共用。
 *
 * <h2>规则（作者原话 → 代码）</h2>
 *
 * <pre>
 * 无神化：每点附魔能力 × {0.1%, 0.2%, 0.4%}      // 按玩家点的第 1/2/3 行
 * 有神化：每点附魔能力 × {0.1%, 0.25%, 0.5%}
 *         + 阿卡那 × 每点加成（{@link UEConfig#ARCANA_BONUS}）
 *         × 量子稳定倍率（{@link UEConfig#STABLE_MULTIPLIER}，只有 stable 才乘）
 * 进阶数量：无神化恒为 1；
 *          有神化按位阶（Eterna）随机：≤30→1；30<x<45→1..2；45≤x<90→1..3；≥90→2..3
 * 进阶等级：无神化按配置（保持原级 / 归一为 1）；有神化按量子化随机
 * </pre>
 *
 * <h2>为什么单独一个类</h2>
 *
 * <p>这套规则要在<b>两个地方</b>生效（原版 {@code EnchantmentMenu} 的 mixin、
 * 神化 {@code ApothEnchantmentMenu} 的 compat mixin）。判定只写一份，
 * 另一份只负责「把参数送进来」——否则两边迟早会漂移（本项目已经栽过一次「判定与显示不一致」）。
 */
public final class AscensionChance {
    private AscensionChance() {}

    /** 无神化：每点附魔能力、每一行的触发概率。索引 = 行号 0/1/2。 */
    public static final double[] VANILLA_PER_POINT = {0.001D, 0.002D, 0.004D};
    /** 有神化：同上（作者给的更高一档）。 */
    public static final double[] APOTH_PER_POINT = {0.001D, 0.0025D, 0.005D};

    /**
     * 某一行本次附魔「产生进阶」的概率。
     *
     * @param row     玩家点的第几行（0/1/2）
     * @param power   附魔能力（无神化 = 书架点数；有神化 = 位阶 Eterna）
     * @param arcana  阿卡那（只有神化路径给非零值）
     * @param stable  量子稳定
     * @param apothic 这一笔是不是神化的附魔台
     */
    public static double chance(int row, double power, double arcana, boolean stable, boolean apothic) {
        double[] table = apothic ? APOTH_PER_POINT : VANILLA_PER_POINT;
        double perPoint = table[Mth.clamp(row, 0, table.length - 1)];
        double p = Math.max(0.0D, power) * perPoint;
        if (apothic) {
            p += Math.max(0.0D, arcana) * UEConfig.ARCANA_BONUS.get();
            if (stable) {
                p *= UEConfig.STABLE_MULTIPLIER.get();
            }
        }
        return Mth.clamp(p, 0.0D, 1.0D);
    }

    /**
     * 本次进阶<b>几条</b>附魔（作者按位阶给的分档）。
     *
     * <p>边界取值：{@code x ≤ 30} 一条；{@code x < 45} 一到二；{@code x < 90} 一到三；
     * 其余二到三。作者写的是「大于 30 但小于 45 / 大于 45 但小于 90」，
     * 45 这个点两边都不算——这里明确归到「一到三」那一档。
     *
     * <p>无神化路径请直接传 {@code 0}（恒为 1 条）。
     */
    public static int count(double eterna, RandomSource random) {
        if (eterna <= 30.0D) {
            return 1;
        }
        if (eterna < 45.0D) {
            return 1 + random.nextInt(2);
        }
        if (eterna < 90.0D) {
            return 1 + random.nextInt(3);
        }
        return 2 + random.nextInt(2);
    }

    /**
     * 神化路径下，进阶后的等级由<b>量子化</b>决定。
     *
     * <p>⚠️ 作者只说了「量子化也会影响最终等级」，**没给范围**。这里取一个可解释的默认：
     * 先在上限 {@code max(1, 位阶 / {@link UEConfig#LEVEL_DIVISOR})} 内均匀取一个，
     * 再按量子化百分比有机会往上抬一次。量子化 0 ⇒ 纯均匀；越高越偏高等级。
     * 手感不对就改配置里的 {@code levelDivisor}。
     */
    public static int level(double eterna, double quanta, RandomSource random) {
        int max = Math.max(1, (int) Math.floor(Math.max(0.0D, eterna) / UEConfig.LEVEL_DIVISOR.get()));
        int value = 1 + random.nextInt(max);
        double q = Mth.clamp(quanta / 100.0D, 0.0D, 1.0D);
        if (random.nextDouble() < q) {
            value = Math.min(max, value + 1 + random.nextInt(Math.max(1, max / 2)));
        }
        return value;
    }
}
