package com.lyingice.ultraenchantment.starlight.menu;

import com.lyingice.ultraenchantment.registry.UEBlocks;
import com.lyingice.ultraenchantment.starlight.logic.EvolutionService;
import com.lyingice.ultraenchantment.starlight.network.EvolutionActionPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 星光附魔台的容器菜单。
 *
 * <p>只有两个槽：<b>物品</b>与<b>催化剂</b>。星图本身不占槽位——它是界面，
 * 状态从物品的 {@code evolution} 组件与数据包树注册表实时推导。
 *
 * <p>操作走 {@link EvolutionActionPayload}（节点 id 是 ResourceLocation，
 * 用按钮 id 传不下）。
 */
public class StarlightTableMenu extends AbstractContainerMenu {

    public static final int ITEM_SLOT = 0;
    public static final int CATALYST_SLOT = 1;
    public static final int PLAYER_INV_START = 2;

    private final Container container = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            StarlightTableMenu.this.slotsChanged(this);
        }
    };
    private final BlockPos pos;
    private final ContainerLevelAccess access;

    public StarlightTableMenu(int windowId, Inventory inventory, BlockPos pos) {
        this(windowId, inventory, pos, inventory.player.level().isClientSide()
                ? ContainerLevelAccess.NULL
                : ContainerLevelAccess.create(inventory.player.level(), pos));
    }

    public StarlightTableMenu(int windowId, Inventory inventory, BlockPos pos,
                              ContainerLevelAccess access) {
        super(com.lyingice.ultraenchantment.registry.UEMenus.STARLIGHT_TABLE.get(), windowId);
        this.pos = pos;
        this.access = access;

        // 祭品 / 催化槽放在物品栏左侧；上半区完整留给星图与详情卡。
        this.addSlot(new Slot(this.container, ITEM_SLOT, 12, 159));
        this.addSlot(new Slot(this.container, CATALYST_SLOT, 12, 181));

        // 玩家背包：与旧进阶台同一套坐标，面板 340×236。
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 89 + col * 18, 159 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 89 + col * 18, 217));
        }
    }

    /** 服务端/客户端通用的当前物品。 */
    public ItemStack itemStack() {
        return this.container.getItem(ITEM_SLOT);
    }

    /** 催化剂槽当前物品（界面显示用）。 */
    public ItemStack catalystStack() {
        return this.container.getItem(CATALYST_SLOT);
    }

    /** 一次仪式消耗一个催化剂；空槽不报错。 */
    public void consumeCatalyst() {
        ItemStack catalyst = this.container.getItem(CATALYST_SLOT);
        if (!catalyst.isEmpty()) {
            catalyst.shrink(1);
            this.container.setChanged();
        }
    }

    /** 物品被服务端改写后刷新槽位，让客户端收到同步。 */
    public void markItemChanged() {
        this.container.setChanged();
    }

    /** 网络包入口：所有判定都在 {@link EvolutionService}。 */
    public void handleAction(ServerPlayer player, EvolutionActionPayload payload) {
        if (!this.stillValid(player)) {
            return;
        }
        if (payload.action() == EvolutionActionPayload.ACTION_UPGRADE) {
            EvolutionService.upgrade(player, this, payload.tree(), payload.node());
        } else {
            EvolutionService.attempt(player, this, payload.tree(), payload.node());
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, UEBlocks.STARLIGHT_TABLE.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < PLAYER_INV_START) {
            if (!this.moveItemStackTo(stack, PLAYER_INV_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, ITEM_SLOT, CATALYST_SLOT + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, blockPos) -> clearContainer(player, this.container));
    }

    public BlockPos pos() {
        return this.pos;
    }
}
