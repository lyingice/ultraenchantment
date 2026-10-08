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

    /** 神化路径：**每点阿卡那**额外增加的绝对触发概率（默认 0.02%）。 */
    public static final ModConfigSpec.DoubleValue ARCANA_BONUS;
    /** 神化路径：有「量子稳定」时，触发概率的倍率（默认 ×1.5）。 */
    public static final ModConfigSpec.DoubleValue STABLE_MULTIPLIER;
    /** 神化路径：进阶后等级的随机上限系数 —— 上限 = max(1, 台面等级 / 这个数)（默认 3）。 */
    public static final ModConfigSpec.IntValue LEVEL_DIVISOR;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("附魔台「进阶」—— 只在装了 Apothic Enchanting 时才有数值意义的三项").push("enchanting");
        ARCANA_BONUS = builder
                .comment("每点阿卡那额外增加的触发概率（绝对值，0.0002 = 0.02%）")
                .defineInRange("arcanaBonusPerPoint", 0.0002D, 0.0D, 1.0D);
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
