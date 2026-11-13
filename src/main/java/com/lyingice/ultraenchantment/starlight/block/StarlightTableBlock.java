package com.lyingice.ultraenchantment.starlight.block;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.registry.UEBlockEntities;
import com.lyingice.ultraenchantment.registry.UEBlocks;
import com.lyingice.ultraenchantment.starlight.menu.StarlightTableMenu;
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
 * <b>星光附魔台</b>——进化图的交互载体 + 悬浮书。
 *
 * <p>方块实体只存悬浮书的动画状态，不存物品；物品槽仍由菜单自持。
 * ticker 只在客户端返回，服务端零开销。
 */
public class StarlightTableBlock extends Block implements EntityBlock {

    /** 与附魔台相同的轮廓：16×12×16。 */
    protected static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 12.0, 16.0);

    public StarlightTableBlock() {
        super(UEBlocks.tableProperties());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return 7;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        Ultraenchantment.LOGGER.info("[starlight] creating starlight table block entity at {}", pos);
        return new StarlightTableBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (!level.isClientSide || type != UEBlockEntities.STARLIGHT_TABLE.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> StarlightTableBlockEntity.bookAnimationTick(
                lvl, pos, st, (StarlightTableBlockEntity) be);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // 旧存档里的星光台可能没有方块实体（该 BE 是后加的）。
        // 不能只 setBlockEntity：已加载的客户端收不到。这里先移除再放回，
        // 强制双端走一次「空气 → 星光台」的状态变化，从而创建 BE。
        // （不新增 blockstate 属性——那会改变方块状态 ID，破坏旧存档。）
        if (level.getBlockEntity(pos) == null) {
            level.removeBlock(pos, false);
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(state.getMenuProvider(level, pos), pos);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
                (windowId, inventory, player) -> new StarlightTableMenu(windowId, inventory, pos),
                Component.translatable("container." + Ultraenchantment.MODID + ".starlight_table"));
    }
}
