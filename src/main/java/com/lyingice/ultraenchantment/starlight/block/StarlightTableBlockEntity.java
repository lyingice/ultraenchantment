package com.lyingice.ultraenchantment.starlight.block;

import com.lyingice.ultraenchantment.registry.UEBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 星光台方块实体——<b>只存悬浮书的动画状态，不存物品</b>。
 *
 * <p>字段与算法逐字照抄原版 {@code EnchantingTableBlockEntity}：
 * 开合、翻页、朝向玩家/缓慢自转。ticker 只在客户端跑。
 */
public class StarlightTableBlockEntity extends BlockEntity {

    public int time;
    public float flip;
    public float oFlip;
    public float flipT;
    public float flipA;
    public float open;
    public float oOpen;
    public float rot;
    public float oRot;
    public float tRot;

    private static final RandomSource RANDOM = RandomSource.create();

    public StarlightTableBlockEntity(BlockPos pos, BlockState state) {
        super(UEBlockEntities.STARLIGHT_TABLE.get(), pos, state);
    }

    public static void bookAnimationTick(Level level, BlockPos pos, BlockState state,
                                         StarlightTableBlockEntity be) {
        be.oOpen = be.open;
        be.oRot = be.rot;
        Player player = level.getNearestPlayer((double) pos.getX() + 0.5,
                (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5, 3.0, false);
        if (player != null) {
            double dx = player.getX() - ((double) pos.getX() + 0.5);
            double dz = player.getZ() - ((double) pos.getZ() + 0.5);
            be.tRot = (float) Mth.atan2(dz, dx);
            be.open += 0.1F;
            if (be.open < 0.5F || RANDOM.nextInt(40) == 0) {
                float before = be.flipT;
                do {
                    be.flipT = be.flipT + (float) (RANDOM.nextInt(4) - RANDOM.nextInt(4));
                } while (before == be.flipT);
            }
        } else {
            be.tRot += 0.02F;
            be.open -= 0.1F;
        }

        while (be.rot >= (float) Math.PI) {
            be.rot -= (float) (Math.PI * 2);
        }
        while (be.rot < (float) -Math.PI) {
            be.rot += (float) (Math.PI * 2);
        }
        while (be.tRot >= (float) Math.PI) {
            be.tRot -= (float) (Math.PI * 2);
        }
        while (be.tRot < (float) -Math.PI) {
            be.tRot += (float) (Math.PI * 2);
        }

        float delta = be.tRot - be.rot;
        while (delta >= (float) Math.PI) {
            delta -= (float) (Math.PI * 2);
        }
        while (delta < (float) -Math.PI) {
            delta += (float) (Math.PI * 2);
        }

        be.rot += delta * 0.4F;
        be.open = Mth.clamp(be.open, 0.0F, 1.0F);
        be.time++;
        be.oFlip = be.flip;
        float speed = Mth.clamp((be.flipT - be.flip) * 0.4F, -0.2F, 0.2F);
        be.flipA = be.flipA + (speed - be.flipA) * 0.9F;
        be.flip = be.flip + be.flipA;
    }
}
