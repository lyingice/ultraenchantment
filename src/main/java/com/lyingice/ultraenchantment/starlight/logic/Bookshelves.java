package com.lyingice.ultraenchantment.starlight.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * 星光附魔台的书架联动。
 *
 * <p>摆法与原版附魔台完全一致：台面一格之外必须是空气（上下两格都算），
 * 两格处的书架才计入，最多 15 个。书架越多经验越便宜，15 个书架时
 * 经验代价减半——低阶本来便宜，高阶贵但值得为它搭书架墙。
 *
 * <p>折扣是线性的：{@code 折扣 = 50% × min(书架, 15) / 15}，
 * 结算后的经验至少 1 级（基础为 0 时保持免费）。
 */
public final class Bookshelves {
    private Bookshelves() {}

    /** 计入折扣的书架上限，与原版附魔台一致。 */
    public static final int MAX_SHELVES = 15;
    /** 满书架时的最大经验折扣。 */
    public static final float MAX_DISCOUNT = 0.5F;

    /** 按原版附魔台的摆法扫描台面周围的书架数（上限 15）。 */
    public static int count(Level level, BlockPos pos) {
        int count = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx == 0 && dz == 0)
                        || !level.isEmptyBlock(pos.offset(dx, 0, dz))
                        || !level.isEmptyBlock(pos.offset(dx, 1, dz))) {
                    continue;
                }
                count += shelf(level, pos.offset(dx * 2, 0, dz * 2));
                count += shelf(level, pos.offset(dx * 2, 1, dz * 2));
                if (dx != 0 && dz != 0) {
                    count += shelf(level, pos.offset(dx * 2, 0, dz));
                    count += shelf(level, pos.offset(dx * 2, 1, dz));
                    count += shelf(level, pos.offset(dx, 0, dz * 2));
                    count += shelf(level, pos.offset(dx, 1, dz * 2));
                }
            }
        }
        return Math.min(count, MAX_SHELVES);
    }

    /** 当前书架数对应的经验折扣比例（0..0.5）。 */
    public static float discount(int shelves) {
        return MAX_DISCOUNT * Mth.clamp(shelves, 0, MAX_SHELVES) / (float) MAX_SHELVES;
    }

    /** 把折扣套到基础经验上；免费保持免费，其余至少 1 级。 */
    public static int apply(int baseLevels, int shelves) {
        if (baseLevels <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(baseLevels * (1.0F - discount(shelves))));
    }

    private static int shelf(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.BOOKSHELF) ? 1 : 0;
    }
}
