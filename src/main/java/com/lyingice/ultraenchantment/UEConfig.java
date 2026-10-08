package com.lyingice.ultraenchantment;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组配置（COMMON）。
 *
 * <p>存在的理由很具体：<b>附魔台「进阶」那几个数值是拍脑袋定的</b> ——
 * 作者只给了方向（「阿卡那会进一步提升概率」「量子稳定有益」「量子化影响最终等级」），
 * 没给数。所以它们必须能在不改代码的前提下调。
 *
 * <p>读的时候直接 {@code UEConfig.XXX.get()}，不做缓存：这几条都在玩家点附魔台按钮时
 * 才读一次，不在热路径上。
 */
public final class UEConfig {
    private UEConfig() {}

    public static final ModConfigSpec SPEC;
    /** 无神化：第 1/2/3 行的**每点附魔能力**触发率（锚点：15 点 + 第 3 行 ⇒ 30%）。 */
    public static final ModConfigSpec.DoubleValue VANILLA_RATE_ROW_1;
    public static final ModConfigSpec.DoubleValue VANILLA_RATE_ROW_2;
    public static final ModConfigSpec.DoubleValue VANILLA_RATE_ROW_3;
    /** 无神化：一次进阶**几条**附魔（默认 1..2）。 */
    public static final ModConfigSpec.IntValue VANILLA_COUNT_MIN;
    public static final ModConfigSpec.IntValue VANILLA_COUNT_MAX;
    /** 无神化：掷到的**目标阶级**权重（默认 70% / 25% / 5%）。会按总和归一化，填 0 即禁用该档。 */
    public static final ModConfigSpec.DoubleValue VANILLA_TIER_ADVANCED;
    public static final ModConfigSpec.DoubleValue VANILLA_TIER_SUPER;
    public static final ModConfigSpec.DoubleValue VANILLA_TIER_ULTRA;
    /**
     * 无神化：各阶级吃「附魔能力带来的等级」的**比例**（默认 高阶 60% / 超级 40% / 究极 20%）。
     *
     * <p>阶级越高越稀有，越不受附魔能力影响 —— 所以系数越来越小。
     * 进阶后的曲线等级 = 基础等级 × 本系数（至少 1）。
     */
    public static final ModConfigSpec.DoubleValue VANILLA_LEVEL_FACTOR_ADVANCED;
    public static final ModConfigSpec.DoubleValue VANILLA_LEVEL_FACTOR_SUPER;
    public static final ModConfigSpec.DoubleValue VANILLA_LEVEL_FACTOR_ULTRA;

    /** 有神化：同上（默认 0.1% / 0.25% / 0.5%）。 */
    public static final ModConfigSpec.DoubleValue APOTHIC_RATE_ROW_1;
    public static final ModConfigSpec.DoubleValue APOTHIC_RATE_ROW_2;
    public static final ModConfigSpec.DoubleValue APOTHIC_RATE_ROW_3;

    /**
     * 有神化：**位阶（Eterna）分档**决定一次进阶几条 —— 三处边界 + 每档的最小/最大条数。
     *
     * <p>默认（作者 2026-10 给的口径）：{@code ≤30 → 1}；{@code 30~45 → 1..2}；
     * {@code 45~90 → 1..3}；{@code ≥90 → 2..3}。不足则作废。
     */
    public static final ModConfigSpec.DoubleValue APOTHIC_ETERNA_BAND_1;
    public static final ModConfigSpec.DoubleValue APOTHIC_ETERNA_BAND_2;
    public static final ModConfigSpec.DoubleValue APOTHIC_ETERNA_BAND_3;
    public static final ModConfigSpec.IntValue APOTHIC_COUNT_BAND_1_MIN;
    public static final ModConfigSpec.IntValue APOTHIC_COUNT_BAND_1_MAX;
    public static final ModConfigSpec.IntValue APOTHIC_COUNT_BAND_2_MIN;
    public static final ModConfigSpec.IntValue APOTHIC_COUNT_BAND_2_MAX;
    public static final ModConfigSpec.IntValue APOTHIC_COUNT_BAND_3_MIN;
    public static final ModConfigSpec.IntValue APOTHIC_COUNT_BAND_3_MAX;
    public static final ModConfigSpec.IntValue APOTHIC_COUNT_BAND_4_MIN;
    public static final ModConfigSpec.IntValue APOTHIC_COUNT_BAND_4_MAX;

    /**
     * 有神化：掷到的**目标阶级**权重。默认 {@code 1 / 0 / 0} ⇒ <b>只进一阶</b>（保持既有行为）；
     * 想让它也能跳阶，按无神化那样填即可（例如 0.7 / 0.25 / 0.05）。
     */
    public static final ModConfigSpec.DoubleValue APOTHIC_TIER_ADVANCED;
    public static final ModConfigSpec.DoubleValue APOTHIC_TIER_SUPER;
    public static final ModConfigSpec.DoubleValue APOTHIC_TIER_ULTRA;

    /**
     * 有神化：各阶级的**等级系数**（乘在「量子化给出的等级」上）。
     *
     * <p>默认全 1.0 ⇒ 不改变既有行为；想和无神化一样让「阶级越高越不吃加成」，
     * 填成 0.6 / 0.4 / 0.2 即可。
     */
    public static final ModConfigSpec.DoubleValue APOTHIC_LEVEL_FACTOR_ADVANCED;
    public static final ModConfigSpec.DoubleValue APOTHIC_LEVEL_FACTOR_SUPER;
    public static final ModConfigSpec.DoubleValue APOTHIC_LEVEL_FACTOR_ULTRA;

    /** 神化路径：**每点阿卡那**额外增加的绝对触发概率（默认 0.02%）。 */
    public static final ModConfigSpec.DoubleValue ARCANA_BONUS;
    /** 神化路径：有「量子稳定」时，触发概率的倍率（默认 ×1.5）。 */
    public static final ModConfigSpec.DoubleValue STABLE_MULTIPLIER;
    /** 神化路径：进阶后等级的随机上限系数 —— 上限 = max(1, 台面等级 / 这个数)（默认 3）。 */
    public static final ModConfigSpec.IntValue LEVEL_DIVISOR;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("附魔台「进阶」的全部数值。想快速验证机制，把这些数临时调成 1.0 即可（1.0 = 100%）。")
                .push("enchanting");
        // ── 锚点（作者 2026-10 指定，以此为准）──
        //   无神化：满书架 15 点附魔能力 + 第 3 行 ⇒ 30%   ⇒ 每点 0.30/15 = 0.02
        //   有神化：位阶 100 + 第 3 行 ⇒ 50%            ⇒ 每点 0.50/100 = 0.005
        //   有神化：阿卡那 100 ⇒ +50%                  ⇒ 每点 0.50/100 = 0.005（与上一条相加 ⇒ 满配 100%）
        // 第 1/2 行按同一比率衰减（无神化 1 : 2 : 4；有神化 1 : 2.5 : 5）——
        // 于是「1 行最低、2 行性价比最高」（2 行只要 2 倍价就拿到 2 倍概率）。
        VANILLA_RATE_ROW_1 = builder.comment("无神化 · 第 1 行 · 每点附魔能力的触发率（15 点 ⇒ 7.5%）")
                .defineInRange("vanillaRateRow1", 0.005D, 0.0D, 1.0D);
        VANILLA_RATE_ROW_2 = builder.comment("无神化 · 第 2 行（15 点 ⇒ 15%）")
                .defineInRange("vanillaRateRow2", 0.010D, 0.0D, 1.0D);
        VANILLA_RATE_ROW_3 = builder.comment("无神化 · 第 3 行（15 点 ⇒ 30%，锚点）")
                .defineInRange("vanillaRateRow3", 0.020D, 0.0D, 1.0D);
        APOTHIC_RATE_ROW_1 = builder.comment("有神化 · 第 1 行 · 每点位阶的触发率（位阶 100 ⇒ 10%）")
                .defineInRange("apothicRateRow1", 0.001D, 0.0D, 1.0D);
        APOTHIC_RATE_ROW_2 = builder.comment("有神化 · 第 2 行（位阶 100 ⇒ 25%）")
                .defineInRange("apothicRateRow2", 0.0025D, 0.0D, 1.0D);
        APOTHIC_RATE_ROW_3 = builder.comment("有神化 · 第 3 行（位阶 100 ⇒ 50%，锚点）")
                .defineInRange("apothicRateRow3", 0.005D, 0.0D, 1.0D);

        VANILLA_COUNT_MIN = builder.comment("无神化 · 一次进阶最少几条附魔")
                .defineInRange("vanillaCountMin", 1, 1, 16);
        VANILLA_COUNT_MAX = builder.comment("无神化 · 一次进阶最多几条附魔（不足则作废）")
                .defineInRange("vanillaCountMax", 2, 1, 16);
        VANILLA_TIER_ADVANCED = builder.comment("无神化 · 目标阶级权重：高阶")
                .defineInRange("vanillaTierAdvanced", 0.70D, 0.0D, 1.0D);
        VANILLA_TIER_SUPER = builder.comment("无神化 · 目标阶级权重：超级")
                .defineInRange("vanillaTierSuper", 0.25D, 0.0D, 1.0D);
        VANILLA_TIER_ULTRA = builder.comment("无神化 · 目标阶级权重：究极")
                .defineInRange("vanillaTierUltra", 0.05D, 0.0D, 1.0D);
        VANILLA_LEVEL_FACTOR_ADVANCED = builder.comment("无神化 · 高阶的等级系数（基础等级 × 本值）")
                .defineInRange("vanillaLevelFactorAdvanced", 0.60D, 0.0D, 10.0D);
        VANILLA_LEVEL_FACTOR_SUPER = builder.comment("无神化 · 超级的等级系数")
                .defineInRange("vanillaLevelFactorSuper", 0.40D, 0.0D, 10.0D);
        VANILLA_LEVEL_FACTOR_ULTRA = builder.comment("无神化 · 究极的等级系数")
                .defineInRange("vanillaLevelFactorUltra", 0.20D, 0.0D, 10.0D);

        APOTHIC_ETERNA_BAND_1 = builder.comment("有神化 · 位阶分档边界 1（≤ 本值 → 第 1 档）")
                .defineInRange("apothicEternaBand1", 30.0D, 0.0D, 10000.0D);
        APOTHIC_ETERNA_BAND_2 = builder.comment("有神化 · 位阶分档边界 2（< 本值 → 第 2 档）")
                .defineInRange("apothicEternaBand2", 45.0D, 0.0D, 10000.0D);
        APOTHIC_ETERNA_BAND_3 = builder.comment("有神化 · 位阶分档边界 3（< 本值 → 第 3 档，否则第 4 档）")
                .defineInRange("apothicEternaBand3", 90.0D, 0.0D, 10000.0D);
        APOTHIC_COUNT_BAND_1_MIN = builder.comment("有神化 · 第 1 档最少几条").defineInRange("apothicCountBand1Min", 1, 1, 16);
        APOTHIC_COUNT_BAND_1_MAX = builder.comment("有神化 · 第 1 档最多几条").defineInRange("apothicCountBand1Max", 1, 1, 16);
        APOTHIC_COUNT_BAND_2_MIN = builder.comment("有神化 · 第 2 档最少几条").defineInRange("apothicCountBand2Min", 1, 1, 16);
        APOTHIC_COUNT_BAND_2_MAX = builder.comment("有神化 · 第 2 档最多几条").defineInRange("apothicCountBand2Max", 2, 1, 16);
        APOTHIC_COUNT_BAND_3_MIN = builder.comment("有神化 · 第 3 档最少几条").defineInRange("apothicCountBand3Min", 1, 1, 16);
        APOTHIC_COUNT_BAND_3_MAX = builder.comment("有神化 · 第 3 档最多几条").defineInRange("apothicCountBand3Max", 3, 1, 16);
        APOTHIC_COUNT_BAND_4_MIN = builder.comment("有神化 · 第 4 档最少几条").defineInRange("apothicCountBand4Min", 2, 1, 16);
        APOTHIC_COUNT_BAND_4_MAX = builder.comment("有神化 · 第 4 档最多几条").defineInRange("apothicCountBand4Max", 3, 1, 16);
        APOTHIC_TIER_ADVANCED = builder.comment("有神化 · 目标阶级权重：高阶（默认 1.0 = 只进一阶）")
                .defineInRange("apothicTierAdvanced", 0.7D, 0.0D, 1.0D);
        APOTHIC_TIER_SUPER = builder.comment("有神化 · 目标阶级权重：超级").defineInRange("apothicTierSuper", 0.25D, 0.0D, 1.0D);
        APOTHIC_TIER_ULTRA = builder.comment("有神化 · 目标阶级权重：究极").defineInRange("apothicTierUltra", 0.05D, 0.0D, 1.0D);
        APOTHIC_LEVEL_FACTOR_ADVANCED = builder.comment("有神化 · 高阶的等级系数（乘在量子化等级上）")
                .defineInRange("apothicLevelFactorAdvanced", 1.0D, 0.0D, 10.0D);
        APOTHIC_LEVEL_FACTOR_SUPER = builder.comment("有神化 · 超级的等级系数")
                .defineInRange("apothicLevelFactorSuper", 1.0D, 0.0D, 10.0D);
        APOTHIC_LEVEL_FACTOR_ULTRA = builder.comment("有神化 · 究极的等级系数")
                .defineInRange("apothicLevelFactorUltra", 1.0D, 0.0D, 10.0D);
        // 锚点：阿卡那 100 ⇒ +50% ⇒ 每点 0.50/100 = 0.005。
        // 与位阶那条相加：位阶 100（50%）+ 阿卡那 100（+50%）= 100% ⇒ 满配必出。
        ARCANA_BONUS = builder
                .comment("每点阿卡那额外增加的触发概率（绝对值，0.005 = 0.5%；阿卡那 100 ⇒ +50%）")
                .defineInRange("arcanaBonusPerPoint", 0.005D, 0.0D, 1.0D);
        STABLE_MULTIPLIER = builder
                .comment("有量子稳定时触发概率的倍率")
                .defineInRange("stableMultiplier", 1.5D, 1.0D, 10.0D);
        LEVEL_DIVISOR = builder
                .comment("进阶后等级上限 = max(1, 台面等级 / 本值)")
                .defineInRange("levelDivisor", 3, 1, 64);
        builder.pop();
        SPEC = builder.build();
    }
}
