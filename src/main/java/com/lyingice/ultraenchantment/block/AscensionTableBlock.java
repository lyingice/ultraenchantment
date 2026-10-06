package com.lyingice.ultraenchantment.block;

import com.lyingice.ultraenchantment.menu.AscensionTableMenu;
import com.lyingice.ultraenchantment.registry.UEBlocks;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * <b>附魔进阶台</b>——阶级 × 等级双轴选择，消耗附近图书馆库存。
 *
 * <h2>为什么没有方块实体</h2>
 *
 * <p>物品槽由菜单自持（照原版附魔台的 {@code EnchantmentMenu} 做法）：
 * {@code AbstractContainerMenu.removed} 会把槽内物品还给玩家，因此不需要方块实体
 * 来持久化。省掉方块实体也省掉了「方块被拆时槽内物品去哪」这一整类问题。
 *
 * <h2>「附近」的判定</h2>
 *
 * <p>用原版附魔台同一套书架偏移 {@code EnchantingTableBlock.BOOKSHELF_OFFSETS}
 * （水平 ±2、垂直 ±1 的环）。把图书馆放进这个环里，进阶台就能取到它的库存。
 * 这与原版附魔台、Enchanting Infuser 的「书架环」是同一个约定，玩家不需要学新规则。
 *
 * <p>注：进阶台的菜单与界面在后续阶段接入，本类当前只负责注册与外观。
 */
public class AscensionTableBlock extends Block {

    public AscensionTableBlock() {
        super(UEBlocks.tableProperties());
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return 7;
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
