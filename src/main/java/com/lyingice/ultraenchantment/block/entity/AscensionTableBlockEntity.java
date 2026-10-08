package com.lyingice.ultraenchantment.block.entity;

import com.lyingice.ultraenchantment.registry.UEBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 附魔进阶台的方块实体 —— <b>只存「悬浮书」的动画状态，不存任何物品</b>。
 *
 * <h2>为什么现在又有了方块实体</h2>
 *
 * <p>本类之前<b>故意不存在</b>：进阶台的物品槽由菜单自持（照原版附魔台的
 * {@code EnchantmentMenu} 做法），关界面时 {@code AbstractContainerMenu.removed}
 * 会把槽内物品还给玩家 —— 省掉方块实体也省掉了「方块被拆时槽内物品去哪」这一整类问题。
 *
 * <p>但「悬浮书」需要一个<b>跨帧累积</b>的状态：书本的开合、翻页、朝玩家转动
 * 都是逐帧插值的（见下），没法只靠当前时间算出来。原版附魔台正是为此挂了
 * {@code EnchantingTableBlockEntity}。所以这里补一个 ——
 * <b>它依然不持有任何物品</b>，上面那条设计理由一个字都没被推翻。
 *
 * <h2>只跑在客户端</h2>
 *
 * <p>原版 {@code EnchantingTableBlock.getTicker} 只在 {@code level.isClientSide} 时返回 ticker：
 * 悬浮书是<b>纯客户端装饰</b>，服务端不参与、也<b>不需要同步</b> ——
 * 每个客户端各自把自己的书动画算出来。我们照抄这个决定（服务端零开销）。
 *
 * <h2>字段与 {@link #bookAnimationTick} 逐字照抄原版</h2>
 *
 * <p>字段名（{@code time / flip / oFlip / flipT / flipA / open / oOpen / rot / oRot / tRot}）
 * 与整体算法都取自 {@code EnchantingTableBlockEntity}，一个字没改 ——
 * 这样悬浮书的手感与原版<b>完全一致</b>，也方便日后对着原版源码核。
 */
public class AscensionTableBlockEntity extends BlockEntity {

    /** 累计帧数，渲染时用来算上下轻微浮动。 */
    public int time;
    /** 当前翻页进度。 */
    public float flip;
    /** 上一帧的 {@link #flip}（渲染时插值用）。 */
    public float oFlip;
    /** 翻页目标值。 */
    public float flipT;
    /** 翻页速度（带缓动）。 */
    public float flipA;
    /** 开合度 0..1。 */
    public float open;
    /** 上一帧的 {@link #open}。 */
    public float oOpen;
    /** 当前朝向（弧度）。 */
    public float rot;
    /** 上一帧的 {@link #rot}。 */
    public float oRot;
    /** 目标朝向：玩家在附近时指向玩家，否则缓慢自转。 */
    public float tRot;

    private static final RandomSource RANDOM = RandomSource.create();

    public AscensionTableBlockEntity(BlockPos pos, BlockState state) {
        super(UEBlockEntities.ASCENSION_TABLE.get(), pos, state);
    }

    /**
     * 悬浮书的逐帧动画 —— <b>逐字照抄</b>原版
     * {@code EnchantingTableBlockEntity.bookAnimationTick}。
     *
     * <p>三件事：① 玩家在 3 格内 ⇒ 书朝玩家转、开合度升到 1；否则缓慢自转、合上；
     * ② 翻页目标 {@code flipT} 随机跳，{@code flip} 带缓动追上去（所以书页会「哗啦」翻）；
     * ③ 所有角度都夹回 {@code [-π, π)}，免得累加到浮点精度丢失。
     */
    public static void bookAnimationTick(Level level, BlockPos pos, BlockState state,
                                         AscensionTableBlockEntity be) {
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
