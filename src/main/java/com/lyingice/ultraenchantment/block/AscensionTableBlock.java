package com.lyingice.ultraenchantment.block;

import com.lyingice.ultraenchantment.block.entity.AscensionTableBlockEntity;
import com.lyingice.ultraenchantment.menu.AscensionTableMenu;
import com.lyingice.ultraenchantment.registry.UEBlockEntities;
import com.lyingice.ultraenchantment.registry.UEBlocks;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * <b>附魔进阶台</b>——阶级 × 等级双轴选择，消耗附近图书馆库存。
 *
 * <h2>方块实体只放「悬浮书」的动画状态</h2>
 *
 * <p><b>物品槽仍然由菜单自持</b>（照原版附魔台的 {@code EnchantmentMenu} 做法）：
 * {@code AbstractContainerMenu.removed} 会把槽内物品还给玩家，
 * 所以方块实体里<b>一个物品都没有</b>，也就没有「方块被拆时槽内物品去哪」这个问题。
 *
 * <p>它存在的唯一理由是悬浮书：开合 / 翻页 / 转向都是跨帧累积的（原版
 * {@code EnchantingTableBlockEntity} 同理）。而且 ticker <b>只在客户端</b>返回 ——
 * 原版就是这么做的：悬浮书是纯客户端装饰，服务端零开销、不需要同步。
 *
 * <h2>「附近」的判定</h2>
 *
 * <p>用原版附魔台同一套书架偏移 {@code EnchantingTableBlock.BOOKSHELF_OFFSETS}
 * （水平 ±2、垂直 ±1 的环）。把图书馆放进这个环里，进阶台就能取到它的库存。
 * 这与原版附魔台、Enchanting Infuser 的「书架环」是同一个约定，玩家不需要学新规则。
 *
 * <p>注：进阶台的菜单与界面在后续阶段接入，本类当前只负责注册与外观。
 */
public class AscensionTableBlock extends Block implements EntityBlock {

    /**
     * 碰撞箱 —— <b>与附魔台完全相同</b>（16×12×16，高 12 像素）。
     *
     * <p>取原版 {@code EnchantingTableBlock.SHAPE} 的同一个值。理由：
     * 进阶台在玩法上就是附魔台的升级版（同样吃书架环、同样的交互姿势），
     * 外形轮廓保持一致，玩家不需要重新建立空间感。
     *
     * <p>⚠️ 原版只覆写 {@code getShape}（轮廓），碰撞仍是整方块——
     * 所以这里也只覆写 {@code getShape}，与附魔台<b>逐字一致</b>。
     */
    protected static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 12.0, 16.0);

    public AscensionTableBlock() {
        super(UEBlocks.tableProperties());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return 7;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AscensionTableBlockEntity(pos, state);
    }

    /**
     * 只给<b>客户端</b>挂 ticker —— 逐字照抄原版 {@code EnchantingTableBlock.getTicker}
     * 的那个三元判断（{@code level.isClientSide ? ... : null}）。
     *
     * <p>服务端不跑动画也没有任何损失：书上那几个字段只在渲染时被读，
     * 而渲染只发生在客户端。
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (!level.isClientSide || type != UEBlockEntities.ASCENSION_TABLE.get()) {
            return null;
        }
        return (lvl, pos, st, be) ->
                AscensionTableBlockEntity.bookAnimationTick(lvl, pos, st,
                        (AscensionTableBlockEntity) be);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(state.getMenuProvider(level, pos), pos);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
                (windowId, inventory, player) -> new AscensionTableMenu(windowId, inventory, pos),
                Component.translatable("container." + com.lyingice.ultraenchantment.Ultraenchantment.MODID
                        + ".ascension_table"));
    }
}
