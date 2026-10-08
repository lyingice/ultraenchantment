package com.lyingice.ultraenchantment.logic;

import com.lyingice.ultraenchantment.UEConfig;
import com.lyingice.ultraenchantment.content.AscensionTier;
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
 * <p><b>所有数值都来自 {@link UEConfig}（toml）</b>，默认值与上表一致；想快速验证机制就把它们调成 1.0。
 *
 * <p>这套规则要在<b>两个地方</b>生效（原版 {@code EnchantmentMenu} 的 mixin、
 * 神化 {@code ApothEnchantmentMenu} 的 compat mixin）。判定只写一份，
 * 另一份只负责「把参数送进来」——否则两边迟早会漂移（本项目已经栽过一次「判定与显示不一致」）。
 */
public final class AscensionChance {
    private AscensionChance() {}

    /** 无神化：每点附魔能力、每一行的触发概率。索引 = 行号 0/1/2。 */
    private static double vanillaRate(int row) {
        return switch (row) {
            case 0 -> UEConfig.VANILLA_RATE_ROW_1.get();
            case 1 -> UEConfig.VANILLA_RATE_ROW_2.get();
            default -> UEConfig.VANILLA_RATE_ROW_3.get();
        };
    }

    /** 有神化：同上（作者给的更高一档）。 */
    private static double apothicRate(int row) {
        return switch (row) {
            case 0 -> UEConfig.APOTHIC_RATE_ROW_1.get();
            case 1 -> UEConfig.APOTHIC_RATE_ROW_2.get();
            default -> UEConfig.APOTHIC_RATE_ROW_3.get();
        };
    }

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
        int safeRow = Mth.clamp(row, 0, 2);
        double perPoint = apothic ? apothicRate(safeRow) : vanillaRate(safeRow);
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
        double band1 = UEConfig.APOTHIC_ETERNA_BAND_1.get();
        double band2 = UEConfig.APOTHIC_ETERNA_BAND_2.get();
        double band3 = UEConfig.APOTHIC_ETERNA_BAND_3.get();
        int min;
        int max;
        if (eterna <= band1) {
            min = UEConfig.APOTHIC_COUNT_BAND_1_MIN.get();
            max = UEConfig.APOTHIC_COUNT_BAND_1_MAX.get();
        } else if (eterna < band2) {
            min = UEConfig.APOTHIC_COUNT_BAND_2_MIN.get();
            max = UEConfig.APOTHIC_COUNT_BAND_2_MAX.get();
        } else if (eterna < band3) {
            min = UEConfig.APOTHIC_COUNT_BAND_3_MIN.get();
            max = UEConfig.APOTHIC_COUNT_BAND_3_MAX.get();
        } else {
            min = UEConfig.APOTHIC_COUNT_BAND_4_MIN.get();
            max = UEConfig.APOTHIC_COUNT_BAND_4_MAX.get();
        }
        min = Math.max(1, min);
        max = Math.max(min, max);
        return min + random.nextInt(max - min + 1);
    }

    /**
     * 有神化：掷**目标阶级**（默认 1 / 0 / 0 ⇒ 只进一阶，保持既有行为）。
     *
     * <p>与 {@link #vanillaTier} 同一套归一化规则；全填 0 时退化为「只出高阶」。
     */
    public static AscensionTier apothicTier(RandomSource random) {
        return weightedTier(random,
                UEConfig.APOTHIC_TIER_ADVANCED.get(),
                UEConfig.APOTHIC_TIER_SUPER.get(),
                UEConfig.APOTHIC_TIER_ULTRA.get());
    }

    /** 有神化：某阶级的等级系数（默认全 1.0）。 */
    public static double apothicLevelFactor(AscensionTier tier) {
        return switch (tier) {
            case ADVANCED -> UEConfig.APOTHIC_LEVEL_FACTOR_ADVANCED.get();
            case SUPER -> UEConfig.APOTHIC_LEVEL_FACTOR_SUPER.get();
            case ULTRA -> UEConfig.APOTHIC_LEVEL_FACTOR_ULTRA.get();
        };
    }

    /** 按权重掷阶级（三档权重按总和归一化）。 */
    private static AscensionTier weightedTier(RandomSource random, double advanced, double sup, double ultra) {
        double a = Math.max(0.0D, advanced);
        double s = Math.max(0.0D, sup);
        double u = Math.max(0.0D, ultra);
        double total = a + s + u;
        if (total <= 0.0D) {
            return AscensionTier.ADVANCED;
        }
        double roll = random.nextDouble() * total;
        if (roll < a) {
            return AscensionTier.ADVANCED;
        }
        return roll < a + s ? AscensionTier.SUPER : AscensionTier.ULTRA;
    }

    /** 无神化：本次进阶**几条**附魔（可配 min..max，默认 1..2）。 */
    public static int vanillaCount(RandomSource random) {
        int min = Math.max(1, UEConfig.VANILLA_COUNT_MIN.get());
        int max = Math.max(min, UEConfig.VANILLA_COUNT_MAX.get());
        return min + random.nextInt(max - min + 1);
    }

    /**
     * 无神化：按权重掷**目标阶级**（默认 高阶 70% / 超级 25% / 究极 5%）。
     *
     * <p>三个权重会按总和归一化，所以填 {@code 0.7 / 0.25 / 0.05} 与 {@code 70 / 25 / 5} 等价；
     * 全填 0 时退化为「只出高阶」。
     */
    public static AscensionTier vanillaTier(RandomSource random) {
        return weightedTier(random,
                UEConfig.VANILLA_TIER_ADVANCED.get(),
                UEConfig.VANILLA_TIER_SUPER.get(),
                UEConfig.VANILLA_TIER_ULTRA.get());
    }

    /**
     * 无神化：某阶级吃「附魔能力带来的等级」的**比例**（默认 60% / 40% / 20%）。
     *
     * <p>阶级越高越稀有、越不受附魔能力影响 ⇒ 系数递减。
     */
    public static double vanillaLevelFactor(AscensionTier tier) {
        return switch (tier) {
            case ADVANCED -> UEConfig.VANILLA_LEVEL_FACTOR_ADVANCED.get();
            case SUPER -> UEConfig.VANILLA_LEVEL_FACTOR_SUPER.get();
            case ULTRA -> UEConfig.VANILLA_LEVEL_FACTOR_ULTRA.get();
        };
    }

    /** 无神化：进阶后的曲线等级 = 基础等级 × 该阶级比例（四舍五入，至少 1）。 */
    public static int vanillaLevel(int baseLevel, AscensionTier tier) {
        return Math.max(1, (int) Math.round(Math.max(1, baseLevel) * vanillaLevelFactor(tier)));
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
        return level(levelCapFromEterna(eterna), quanta, random);
    }

    /**
     * 神化：**位阶**推出的等级上限（还需与该阶曲线上限取小 —— 见 {@link #level(int, double, RandomSource)}）。
     *
     * <p>默认上限 = {@code 位阶 / levelDivisor}（默认 3）。
     */
    public static int levelCapFromEterna(double eterna) {
        return Math.max(1, (int) Math.floor(Math.max(0.0D, eterna) / UEConfig.LEVEL_DIVISOR.get()));
    }

    /**
     * 神化：在 <b>[1, cap]</b> 内按量子化加权取一个等级。
     *
     * <h2>⚠️ 为什么必须把上限传进来「先夹再掷」</h2>
     *
     * <p>早先的写法是「先在 [1, 位阶/3] 里掷，最后再用该阶曲线上限夹结果」。位阶 90 时
     * 掷出来是 1..30、而锋利高阶的曲线上限只有 5 ⇒ <b>六分之五的结果都被夹成 5</b>，
     * 量子化与 levelDivisor 几乎完全失效（实测平均 4.65/5，几乎恒顶格）。
     * 正确做法是<b>先把上限夹到 [1, 曲线上限]</b>，再在这个区间里掷 —— 于是
     * 位阶决定「能掷多高」、量子化决定「掷得多高」。
     */
    public static int level(int cap, double quanta, RandomSource random) {
        int max = Math.max(1, cap);
        int value = 1 + random.nextInt(max);
        double q = Mth.clamp(quanta / 100.0D, 0.0D, 1.0D);
        if (random.nextDouble() < q) {
            value = Math.min(max, value + 1 + random.nextInt(Math.max(1, max / 2)));
        }
        return value;
    }
}
