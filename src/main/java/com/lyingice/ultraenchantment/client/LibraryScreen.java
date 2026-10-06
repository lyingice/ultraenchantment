package com.lyingice.ultraenchantment.client;

import com.lyingice.ultraenchantment.menu.LibraryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * <b>附魔图书馆界面（v2 重做）</b>。
 *
 * <p>与进阶台同一套绘制原语（{@link GuiRender}）：凸起=可点、凹陷=槽位/列表底，
 * 所有可点元素都有悬停反馈。行高从 12px 提到 20px，滚动改成两个实体按钮。
 */
public class LibraryScreen extends AbstractContainerScreen<LibraryMenu> {

    private static final int PANEL_W = 220;
    private static final int PANEL_H = 230;

    private static final int SLOT_IN_X = 10;
    private static final int SLOT_OUT_X = 34;
    private static final int SLOT_Y = 18;
    private static final int HINT_X = 56;
    private static final int HINT_Y = 23;

    private static final int SCROLL_UP_X = 182;
    private static final int SCROLL_DOWN_X = 196;
    private static final int SCROLL_Y = 8;
    private static final int SCROLL_W = 14;
    private static final int SCROLL_H = 14;

    private static final int LIST_X = 10;
    private static final int LIST_Y = 40;
    private static final int LIST_W = 200;
    private static final int ROW_H = 20;

    public LibraryScreen(LibraryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
        this.titleLabelX = 10;
        this.titleLabelY = 7;
    }

    /**
     * 只画标题，<b>不画「物品栏」标签</b>。
     *
     * <p>列表底延伸到 y=140，而玩家背包从 150 开始——原版那个标签正好落在
     * 列表最后一行上（截图里「物品栏」直接压在「高阶 效率」上面）。
     * 背包位置是约定俗成的，不需要标签。
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, GuiRender.TEXT, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        GuiRender.panel(graphics, x, y, PANEL_W, PANEL_H);

        // 槽位底：自己画面板就必须自己画槽位，否则玩家看不到往哪放（见 GuiRender 类文档）
        GuiRender.slotBackgrounds(graphics, this.menu.slots, x, y);

        GuiRender.inset(graphics, x + LIST_X, y + LIST_Y, LIST_W, LibraryMenu.ROWS * ROW_H);

        GuiRender.button(graphics, x + SCROLL_UP_X, y + SCROLL_Y, SCROLL_W, SCROLL_H,
                this.inside(mouseX, mouseY, SCROLL_UP_X, SCROLL_Y, SCROLL_W, SCROLL_H), this.menu.canScrollUp());
        GuiRender.button(graphics, x + SCROLL_DOWN_X, y + SCROLL_Y, SCROLL_W, SCROLL_H,
                this.inside(mouseX, mouseY, SCROLL_DOWN_X, SCROLL_Y, SCROLL_W, SCROLL_H), this.menu.canScrollDown());

        int left = x + LIST_X;
        int top = y + LIST_Y;
        for (int i = 0; i < LibraryMenu.ROWS; i++) {
            int rowY = top + i * ROW_H;
            if (this.menu.rowAt(i) == null) {
                continue;
            }
            if (this.hovered(mouseX, mouseY, left, rowY)) {
                graphics.fill(left, rowY, left + LIST_W, rowY + ROW_H, GuiRender.HOVER_TINT);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderHint(graphics);
        this.renderArrows(graphics);
        this.renderRows(graphics);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderHint(GuiGraphics graphics) {
        graphics.drawString(this.font,
                Component.translatable("container.ultraenchantment.enchantment_library.hint"),
                this.leftPos + HINT_X, this.topPos + HINT_Y, GuiRender.TEXT_DIM, false);
    }

    private void renderArrows(GuiGraphics graphics) {
        int color = this.menu.canScrollUp() ? GuiRender.TEXT : GuiRender.TEXT_OFF;
        this.drawArrow(graphics, this.leftPos + SCROLL_UP_X, this.topPos + SCROLL_Y + 4, true, color);
        color = this.menu.canScrollDown() ? GuiRender.TEXT : GuiRender.TEXT_OFF;
        this.drawArrow(graphics, this.leftPos + SCROLL_DOWN_X, this.topPos + SCROLL_Y + 4, false, color);
    }

    private void renderRows(GuiGraphics graphics) {
        int left = this.leftPos + LIST_X;
        int top = this.topPos + LIST_Y;

        if (this.menu.totalEntries() == 0) {
            graphics.drawString(this.font,
                    Component.translatable("container.ultraenchantment.enchantment_library.empty"),
                    left + 5, top + 6, GuiRender.TEXT_DIM, false);
            return;
        }

        for (int i = 0; i < LibraryMenu.ROWS; i++) {
            int rowY = top + i * ROW_H;
            LibraryMenu.Row row = this.menu.rowAt(i);
            if (row == null) {
                continue;
            }
            Component name = Component.translatable(row.tier().translationKey())
                    .append(Component.literal(" "))
                    .append(row.root().value().description());
            String label = this.font.plainSubstrByWidth(name.getString(), LIST_W - 70);
            graphics.drawString(this.font, label, left + 5, rowY + 6, GuiRender.TEXT, false);

            Component amounts = Component.literal(row.points() + " / " + row.maxLevel());
            graphics.drawString(this.font, amounts,
                    left + LIST_W - 5 - this.font.width(amounts), rowY + 6, GuiRender.TEXT_DIM, false);
        }
    }

    private void drawArrow(GuiGraphics graphics, int x, int y, boolean up, int color) {
        for (int i = 0; i < 5; i++) {
            int half = up ? i : 4 - i;
            graphics.fill(x + 5 - half, y + i, x + 6 + half, y + i + 1, color);
        }
    }

    // ── 输入 ────────────────────────────────────────────────────────────

    private boolean inside(double mouseX, double mouseY, int relX, int relY, int w, int h) {
        return mouseX >= this.leftPos + relX && mouseX < this.leftPos + relX + w
                && mouseY >= this.topPos + relY && mouseY < this.topPos + relY + h;
    }

    private boolean hovered(double mouseX, double mouseY, int left, int rowY) {
        return mouseX >= left && mouseX < left + LIST_W && mouseY >= rowY && mouseY < rowY + ROW_H;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (this.inside(mouseX, mouseY, SCROLL_UP_X, SCROLL_Y, SCROLL_W, SCROLL_H)) {
                if (this.menu.canScrollUp()) {
                    this.sendButton(LibraryMenu.BTN_SCROLL_UP);
                }
                return true;
            }
            if (this.inside(mouseX, mouseY, SCROLL_DOWN_X, SCROLL_Y, SCROLL_W, SCROLL_H)) {
                if (this.menu.canScrollDown()) {
                    this.sendButton(LibraryMenu.BTN_SCROLL_DOWN);
                }
                return true;
            }
            int row = this.rowIndexAt(mouseX, mouseY);
            if (row >= 0) {
                // 左键取「点数够到的最高级」，Shift+左键取「1 级」
                this.sendButton(hasShiftDown()
                        ? LibraryMenu.BTN_EXTRACT_ONE + row
                        : LibraryMenu.BTN_EXTRACT_MAX + row);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int rowIndexAt(double mouseX, double mouseY) {
        int left = this.leftPos + LIST_X;
        int top = this.topPos + LIST_Y;
        if (mouseX < left || mouseX >= left + LIST_W || mouseY < top) {
            return -1;
        }
        int index = (int) ((mouseY - top) / ROW_H);
        if (index < 0 || index >= LibraryMenu.ROWS) {
            return -1;
        }
        return this.menu.rowAt(index) == null ? -1 : index;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0 && this.menu.canScrollUp()) {
            this.sendButton(LibraryMenu.BTN_SCROLL_UP);
            return true;
        }
        if (scrollY < 0 && this.menu.canScrollDown()) {
            this.sendButton(LibraryMenu.BTN_SCROLL_DOWN);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }
}
