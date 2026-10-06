package com.lyingice.ultraenchantment.client;

import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.menu.AscensionTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * <b>附魔进阶台界面（v2 重做）</b>。
 *
 * <h2>v1 为什么不能看</h2>
 *
 * <ul>
 *   <li>16px 行高、160px 宽里塞了<b>四个 9px 的小三角</b>——点不准，也看不清</li>
 *   <li>阶级只有两个三角，玩家<b>不知道总共几档、当前在第几档</b></li>
 *   <li>整块面板是纯色 {@code fill}，<b>没有明暗边</b>，可点与不可点长得一样</li>
 *   <li>没有列标题，四个数字/文字挤在一行靠位置区分</li>
 * </ul>
 *
 * <h2>v2 的做法</h2>
 *
 * <ul>
 *   <li>面板 320×258、行高 22px，全部坐标常量<b>渲染与命中判定共用</b></li>
 *   <li>阶级改成<b>四个并排方块</b>：亮=可选 / 暗=未解锁 / 绿=当前选中——
 *       「有哪几档」一眼可见，这也是本方块区别于灌注台的核心</li>
 *   <li>等级用<b>实体 −/+ 按钮</b>，不再是三角</li>
 *   <li>加了<b>列标题行</b>（附魔 / 阶级 / 等级 / 花费）</li>
 *   <li>所有可点元素都有悬停反馈；凸起=可点、凹陷=槽位（见 {@link GuiRender}）</li>
 * </ul>
 */
public class AscensionTableScreen extends AbstractContainerScreen<AscensionTableMenu> {

    // ── 面板 ──
    //
    // ⚠️ 高度必须 ≤ 逻辑视口高度。实测：GUI scale 2 + 480p 窗口 ⇒ 逻辑高度仅 240，
    // 而 v2 初版用了 258，于是 topPos 算成负数、标题与列标题【被顶部裁掉】。
    // 这个错在源码里完全看不出来，只有截图才暴露。236 留 4px 余量。
    private static final int PANEL_W = 320;
    private static final int PANEL_H = 236;

    // ── 输入槽 ──
    private static final int SLOT_X = 10;
    private static final int SLOT_Y = 18;
    private static final int HINT_X = 32;
    private static final int HINT_Y = 23;

    // ── 列标题 ──
    private static final int HEADER_Y = 38;

    // ── 列表 ──
    private static final int LIST_X = 10;
    private static final int LIST_Y = 48;
    private static final int LIST_W = 300;
    private static final int ROW_H = 22;

    // 行内相对 x（与命中判定同源）
    private static final int NAME_X = 4;
    private static final int NAME_MAX_W = 96;
    private static final int CHIP_X = 102;
    private static final int CHIP_W = 26;
    private static final int CHIP_H = 16;
    private static final int CHIP_GAP = 2;
    private static final int LEVEL_MINUS_X = 218;
    private static final int LEVEL_TEXT_X = 236;
    private static final int LEVEL_PLUS_X = 252;
    private static final int LEVEL_BTN = 14;
    private static final int COST_RIGHT = 288;

    // ── 灌注按钮 ──
    private static final int APPLY_X = 10;
    private static final int APPLY_Y = 140;
    private static final int APPLY_W = 110;
    private static final int APPLY_H = 18;
    private static final int STATUS_X = 126;

    public AscensionTableScreen(AscensionTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
        this.titleLabelX = 10;
        this.titleLabelY = 7;
    }

    /**
     * 只画标题——<b>不画「物品栏」标签</b>。
     *
     * <p>面板已经排满：灌注按钮在 y=140..158，玩家背包从 164 开始。
     * 原版那个标签会落在按钮上方并与状态文字挤在一起，所以这里直接不画。
     * 背包位置本身就是约定俗成的，不需要标签。
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, GuiRender.TEXT, false);
    }

    // ── 背景 ────────────────────────────────────────────────────────────

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        GuiRender.panel(graphics, x, y, PANEL_W, PANEL_H);

        // 槽位底：自己画面板就必须自己画槽位，否则玩家看不到往哪放（见 GuiRender 类文档）
        GuiRender.slotBackgrounds(graphics, this.menu.slots, x, y);

        // 列表底（凹陷）
        GuiRender.inset(graphics, x + LIST_X, y + LIST_Y,
                LIST_W, AscensionTableMenu.ROWS * ROW_H);

        // 灌注按钮
        boolean applyHovered = this.inside(mouseX, mouseY, APPLY_X, APPLY_Y, APPLY_W, APPLY_H);
        GuiRender.button(graphics, x + APPLY_X, y + APPLY_Y, APPLY_W, APPLY_H, applyHovered, true);

        this.renderRowsBg(graphics, mouseX, mouseY);
    }

    private void renderRowsBg(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = this.leftPos + LIST_X;
        int top = this.topPos + LIST_Y;
        for (int i = 0; i < AscensionTableMenu.ROWS; i++) {
            int rowY = top + i * ROW_H;
            AscensionTableMenu.Row row = this.menu.rowAt(i);
            if (row == null) {
                continue;
            }
            if (row.tierOrdinal() >= 0) {
                graphics.fill(left, rowY, left + LIST_W, rowY + ROW_H, GuiRender.SELECTED_TINT);
            } else if (this.hovered(mouseX, mouseY, left, rowY)) {
                graphics.fill(left, rowY, left + LIST_W, rowY + ROW_H, GuiRender.HOVER_TINT);
            }

            // 阶级方块
            for (int j = 0; j < LineageTier.values().length; j++) {
                int cx = left + CHIP_X + j * (CHIP_W + CHIP_GAP);
                int cy = rowY + 3;
                boolean unlocked = row.maxLevels()[j] > 0 && (j == 0 || (row.unlockMask() & (1 << (j - 1))) != 0);
                boolean selected = row.tierOrdinal() == j;
                boolean hover = this.inside(mouseX, mouseY,
                        CHIP_X + j * (CHIP_W + CHIP_GAP), rowY - top + 3, CHIP_W, CHIP_H);
                GuiRender.chip(graphics, cx, cy, CHIP_W, CHIP_H, unlocked, selected, hover);
            }

            // 等级 −/+
            if (row.tierOrdinal() >= 0) {
                boolean minusHover = this.inside(mouseX, mouseY, LEVEL_MINUS_X, rowY - top + 4, LEVEL_BTN, LEVEL_BTN);
                boolean plusHover = this.inside(mouseX, mouseY, LEVEL_PLUS_X, rowY - top + 4, LEVEL_BTN, LEVEL_BTN);
                GuiRender.button(graphics, left + LEVEL_MINUS_X, rowY + 4, LEVEL_BTN, LEVEL_BTN, minusHover, true);
                GuiRender.button(graphics, left + LEVEL_PLUS_X, rowY + 4, LEVEL_BTN, LEVEL_BTN, plusHover, true);
            }
        }
    }

    // ── 前景 ────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderHeader(graphics);
        this.renderHint(graphics);
        this.renderRows(graphics);
        this.renderApplyLabel(graphics);
        this.renderStatus(graphics);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    /** 输入槽旁的说明文字——没有它，左上角那个空格子不知道是干什么的。 */
    private void renderHint(GuiGraphics graphics) {
        graphics.drawString(this.font,
                Component.translatable("container.ultraenchantment.ascension_table.hint"),
                this.leftPos + HINT_X, this.topPos + HINT_Y, GuiRender.TEXT_DIM, false);
    }

    private void renderHeader(GuiGraphics graphics) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.drawString(this.font,
                Component.translatable("container.ultraenchantment.ascension_table.col.enchantment"),
                x + LIST_X + NAME_X, y + HEADER_Y, GuiRender.TEXT_DIM, false);
        GuiRender.centered(graphics, this.font,
                Component.translatable("container.ultraenchantment.ascension_table.col.tier"),
                x + LIST_X + CHIP_X, y + HEADER_Y,
                LineageTier.values().length * (CHIP_W + CHIP_GAP) - CHIP_GAP, GuiRender.TEXT_DIM, false);
        GuiRender.centered(graphics, this.font,
                Component.translatable("container.ultraenchantment.ascension_table.col.level"),
                x + LIST_X + LEVEL_MINUS_X, y + HEADER_Y, 50, GuiRender.TEXT_DIM, false);
        graphics.drawString(this.font,
                Component.translatable("container.ultraenchantment.ascension_table.col.cost"),
                x + LIST_X + COST_RIGHT - this.font.width(
                        Component.translatable("container.ultraenchantment.ascension_table.col.cost")),
                y + HEADER_Y, GuiRender.TEXT_DIM, false);
    }

    private void renderRows(GuiGraphics graphics) {
        int left = this.leftPos + LIST_X;
        int top = this.topPos + LIST_Y;

        if (this.menu.totalEntries() == 0) {
            graphics.drawString(this.font,
                    Component.translatable("container.ultraenchantment.ascension_table.empty"),
                    left + 4, top + 5, GuiRender.TEXT_DIM, false);
            return;
        }

        for (int i = 0; i < AscensionTableMenu.ROWS; i++) {
            int rowY = top + i * ROW_H;
            AscensionTableMenu.Row row = this.menu.rowAt(i);
            if (row == null) {
                continue;
            }

            // 附魔名（超宽截断，绝不盖到阶级方块）
            String name = this.font.plainSubstrByWidth(row.root().value().description().getString(), NAME_MAX_W);
            graphics.drawString(this.font, name, left + NAME_X, rowY + 7, GuiRender.TEXT, false);

            // 阶级方块上的文字
            for (int j = 0; j < LineageTier.values().length; j++) {
                int cx = left + CHIP_X + j * (CHIP_W + CHIP_GAP);
                boolean unlocked = row.maxLevels()[j] > 0 && (j == 0 || (row.unlockMask() & (1 << (j - 1))) != 0);
                int color = !unlocked ? GuiRender.TEXT_OFF
                        : (row.tierOrdinal() == j ? 0xFFFFFFFF : GuiRender.TEXT);
                GuiRender.centered(graphics, this.font,
                        Component.translatable(tierKey(LineageTier.values()[j])),
                        cx, rowY + 7, CHIP_W, color, false);
            }

            // 等级
            if (row.tierOrdinal() >= 0) {
                int color = row.affordable() ? GuiRender.TEXT : GuiRender.TEXT_BAD;
                GuiRender.centered(graphics, this.font, Component.literal("−"),
                        left + LEVEL_MINUS_X, rowY + 8, LEVEL_BTN, color, false);
                GuiRender.centered(graphics, this.font, Component.literal(String.valueOf(row.level())),
                        left + LEVEL_TEXT_X, rowY + 7, 14, color, false);
                GuiRender.centered(graphics, this.font, Component.literal("+"),
                        left + LEVEL_PLUS_X, rowY + 8, LEVEL_BTN, color, false);

                // 花费
                Component cost = row.tierOrdinal() == LineageTier.NATIVE.ordinal()
                        ? Component.translatable("container.ultraenchantment.ascension_table.free")
                        : Component.literal(String.valueOf(row.cost()));
                graphics.drawString(this.font, cost,
                        left + COST_RIGHT - this.font.width(cost), rowY + 7,
                        row.affordable() ? GuiRender.TEXT_DIM : GuiRender.TEXT_BAD, false);
            }
        }
    }

    private void renderApplyLabel(GuiGraphics graphics) {
        GuiRender.centered(graphics, this.font,
                Component.translatable("container.ultraenchantment.ascension_table.apply"),
                this.leftPos + APPLY_X, this.topPos + APPLY_Y + 6, APPLY_W, GuiRender.TEXT, false);
    }

    private void renderStatus(GuiGraphics graphics) {
        int status = this.menu.status();
        String key = switch (status) {
            case AscensionTableMenu.STATUS_OK -> "ok";
            case AscensionTableMenu.STATUS_NO_ITEM -> "no_item";
            case AscensionTableMenu.STATUS_NO_SELECTION -> "no_selection";
            case AscensionTableMenu.STATUS_LOCKED -> "locked";
            case AscensionTableMenu.STATUS_NOT_ENOUGH -> "not_enough";
            case AscensionTableMenu.STATUS_CANCELLED -> "cancelled";
            case AscensionTableMenu.STATUS_NO_LIBRARY -> "no_library";
            default -> null;
        };
        if (key == null) {
            return;
        }
        graphics.drawString(this.font,
                Component.translatable("container.ultraenchantment.ascension_table.status." + key),
                this.leftPos + STATUS_X, this.topPos + APPLY_Y + 7,
                status == AscensionTableMenu.STATUS_OK ? GuiRender.TEXT_OK : GuiRender.TEXT_BAD, false);
    }

    private static String tierKey(LineageTier tier) {
        return "tier." + com.lyingice.ultraenchantment.Ultraenchantment.MODID + "." + tier.id();
    }

    // ── 输入 ────────────────────────────────────────────────────────────

    private boolean inside(double mouseX, double mouseY, int relX, int relY, int w, int h) {
        int x = this.leftPos + relX;
        int y = this.topPos + relY;
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private boolean hovered(double mouseX, double mouseY, int left, int rowY) {
        return mouseX >= left && mouseX < left + LIST_W && mouseY >= rowY && mouseY < rowY + ROW_H;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (this.inside(mouseX, mouseY, APPLY_X, APPLY_Y, APPLY_W, APPLY_H)) {
            this.sendButton(AscensionTableMenu.BTN_APPLY);
            return true;
        }
        int row = this.rowIndexAt(mouseX, mouseY);
        if (row < 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        AscensionTableMenu.Row data = this.menu.rowAt(row);
        if (data == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int rowTop = this.topPos + LIST_Y + row * ROW_H;
        int left = this.leftPos + LIST_X;

        // 阶级方块：点一下就是「选这一档」，不再是循环切换
        for (int j = 0; j < LineageTier.values().length; j++) {
            int relX = CHIP_X + j * (CHIP_W + CHIP_GAP);
            if (mouseX >= left + relX && mouseX < left + relX + CHIP_W
                    && mouseY >= rowTop + 3 && mouseY < rowTop + 3 + CHIP_H) {
                this.sendButton(AscensionTableMenu.BTN_TIER_SET + j * 100 + row);
                return true;
            }
        }
        if (mouseY >= rowTop + 4 && mouseY < rowTop + 4 + LEVEL_BTN) {
            int relX = (int) mouseX - left;
            if (relX >= LEVEL_MINUS_X && relX < LEVEL_MINUS_X + LEVEL_BTN) {
                this.sendButton(AscensionTableMenu.BTN_LEVEL_DOWN + row);
                return true;
            }
            if (relX >= LEVEL_PLUS_X && relX < LEVEL_PLUS_X + LEVEL_BTN) {
                this.sendButton(AscensionTableMenu.BTN_LEVEL_UP + row);
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
        if (index < 0 || index >= AscensionTableMenu.ROWS) {
            return -1;
        }
        return this.menu.rowAt(index) == null ? -1 : index;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0 && this.menu.canScrollUp()) {
            this.sendButton(AscensionTableMenu.BTN_SCROLL_UP);
            return true;
        }
        if (scrollY < 0 && this.menu.canScrollDown()) {
            this.sendButton(AscensionTableMenu.BTN_SCROLL_DOWN);
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
