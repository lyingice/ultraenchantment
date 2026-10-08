package com.lyingice.ultraenchantment.block;

import com.lyingice.ultraenchantment.block.entity.EnchantmentLibraryBlockEntity;
import com.lyingice.ultraenchantment.menu.LibraryMenu;
import com.lyingice.ultraenchantment.registry.UEBlocks;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * <b>附魔图书馆</b>——按「谱系 × 阶级」存取合并进阶附魔书，并解锁图鉴。
 *
 * <h2>库存随方块搬运</h2>
 *
 * <p>拆掉图书馆再放下，库存必须<b>完整保留</b>。做法照神化图书馆：
 * <ul>
 *   <li>破坏时：{@link #getDrops} 把方块实体的数据写进掉落物
 *       （{@code BlockEntity.saveToItem} → {@code minecraft:block_entity_data} 组件）</li>
 *   <li>放置时：{@link #setPlacedBy} 从物品读回</li>
 * </ul>
 *
 * <p><b>为什么放置时自己再读一遍</b>：原版 {@code BlockItem} 自己也会还原
 * {@code block_entity_data}，但调用顺序不写在我们手里。这里显式再读一次，
 * 两条路径读的是同一份数据，{@code loadAdditional} 内部先清空再读，因此<b>幂等</b>。
 * 宁可重复读，也不要赌原版行为。
 *
 * <h2>无朝向状态</h2>
 *
 * <p>刻意不加 {@code FACING}：状态越少，方块状态 JSON 越简单，也不会因为
 * 朝向变化让存档里的旧方块失配。
 */
public class EnchantmentLibraryBlock extends Block implements EntityBlock {

    public EnchantmentLibraryBlock() {
        super(UEBlocks.tableProperties());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnchantmentLibraryBlockEntity(pos, state);
    }

    /** 破坏时把库存写进掉落物。 */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof EnchantmentLibraryBlockEntity library) {
            for (ItemStack drop : drops) {
                if (drop.is(this.asItem())) {
                    library.saveToItem(drop, params.getLevel().registryAccess());
                }
            }
        }
        return drops;
    }

    /** 放置时从物品读回库存（与原版 {@code BlockItem} 的还原幂等叠加）。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data != null && level.getBlockEntity(pos) instanceof EnchantmentLibraryBlockEntity library) {
            library.loadWithComponents(data.copyTag(), level.registryAccess());
        }
    }

    /** 创造模式取方块（中键）也带上库存。 */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level,
                                       BlockPos pos, net.minecraft.world.entity.player.Player player) {
        ItemStack stack = new ItemStack(this);
        if (level.getBlockEntity(pos) instanceof EnchantmentLibraryBlockEntity library) {
            library.saveToItem(stack, level.registryAccess());
        }
        return stack;
    }

    /**
     * 右键打开图书馆界面。
     *
     * <p>{@code openMenu(provider, pos)} 的 <b>BlockPos 重载</b>是关键：
     * 它把坐标写进「打开界面」的包，客户端据此构造菜单
     * （见 {@code UEMenus} 用 {@code IMenuTypeExtension} 的原因）。
     * 少了它，客户端拿到的坐标是原点，库存永远读不到。
     */
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
                (windowId, inventory, player) -> new LibraryMenu(windowId, inventory, pos),
                Component.translatable("container." + com.lyingice.ultraenchantment.Ultraenchantment.MODID
                        + ".advanced_enchantment_library"));
    }

    /** 方块实体不参与比较器输出。 */
    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return false;
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return 7;
    }
}
