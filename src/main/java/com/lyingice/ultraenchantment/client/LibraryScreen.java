package com.lyingice.ultraenchantment.client;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.block.entity.EnchantmentLibraryBlockEntity;
import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.menu.LibraryMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * <b>附魔图书馆界面（v5）</b>。
 *
 * <h2>它要同时说三件事</h2>
 *
 * <ol>
 *   <li><b>提级用的等级单位</b> —— 4 个桶（基础 / 高阶 / 超级 / 究极）</li>
 *   <li><b>升阶用的进阶书库存</b> —— 3 个桶（基础阶没有进阶书，画占位符）；
 *       格子里是「×总数」，<b>悬停</b>看通用 / 定向的明细</li>
 *   <li><b>能取出什么</b> —— 图鉴解锁的 {@code (谱系, 阶级)} 列表</li>
 * </ol>
 *
 * <pre>
 * ┌────────────────────────────────┐
 * │ 附魔图书馆            [图鉴]   │
 * │ [入][出] 放入铭刻书             │
 * │         基础  高阶  超级  究极  │  ← 列标题
 * │ 等级单位  4    12    4     0   │  ← 提级燃料（点数）
 * │ 进阶书    —    ×3    ×1    ×0  │  ← 升阶钥匙（本数，悬停看明细）
 * │ ─────────────────────────────  │
 * │ 附魔                    阶级    │
 * │ 锋利                    高阶    │  ← 可取出列表（图鉴解锁的）
 * │ ...                            │
 * │ [玩家背包]                      │
 * └────────────────────────────────┘
 * </pre>
 *
 * <h2>🔑 取出是「一级一级加」</h2>
 *
 * <p>左键 = 输出槽那本书 <b>+1 级</b>（只付边际差）；Shift+左键 = 取到 1 级。
 * 这是神化 {@code EnchLibraryContainer:137} 的机制，v3 抄漏了。
 *
 * <h2>⚠️ v5 起「升阶书库存」只能看，不能取</h2>
 *
 * <p>书库存只被<b>进阶台</b>消耗（而且优先消耗定向书）。图书馆不再是「兑换所」。
 */
public class LibraryScreen extends AbstractContainerScreen<LibraryMenu> {

    private static final int PANEL_W = 260;
    private static final int PANEL_H = 236;

    private static final int SLOT_IN_X = 10;
    private static final int SLOT_OUT_X = 34;
    private static final int SLOT_Y = 14;
    private static final int HINT_X = 56;
    private static final int HINT_Y = 19;

    private static final int CODEX_X = 220;
    private static final int CODEX_Y = 5;
    private static final int CODEX_W = 30;
    private static final int CODEX_H = 14;

    // ── 能量条 / 书库存 ──
    private static final int STRIP_LABEL_X = 10;
    private static final int STRIP_CELL_X = 62;
    private static final int STRIP_CELL_W = 46;
    private static final int STRIP_CELL_H = 13;
    private static final int STRIP_COL_HEADER_Y = 30;
    private static final int STRIP_LEVEL_Y = 40;    // 等级单位
    private static final int STRIP_BOOK_Y = 55;     // 进阶书库存

    // ── 列表 ──
    private static final int LIST_X = 10;
    private static final int LIST_Y = 83;
    private static final int LIST_W = 240;
    private static final int ROW_H = 20;

    // ── 行内控件（v6：一行 = 一条**谱系**，阶级与等级都在行里自己选）──
    //
    // 一行的横向排布（相对 left = leftPos + LIST_X，总宽 240）：
    //   [名字 60] [− 阶级 +] [− 等级 +] [花费] [取出 14×14]
    // 中间原来全空着 —— 作者说「有效空间没有利用起来」，指的就是这一段。
    private static final int CTRL = 12;            // 迷你按钮 12×12（现成精灵）
    private static final int CTRL_DY = 4;          // (ROW_H - CTRL) / 2
    private static final int NAME_W = 60;
    private static final int TIER_MINUS_X = 68;
    private static final int TIER_LABEL_X = 84;
    private static final int TIER_LABEL_W = 24;
    private static final int TIER_PLUS_X = 112;
    private static final int LEVEL_MINUS_X = 128;
    private static final int LEVEL_NUM_X = 144;
    private static final int LEVEL_NUM_W = 14;
    private static final int LEVEL_PLUS_X = 162;
    /** 花费（「N 点」）右对齐到这里。 */
    private static final int COST_RIGHT = 206;
    /** 取出按钮：14×14 方块 + 一个向下箭头（都是 widgets.png 里现成的）。 */
    private static final int EXTRACT_X = 214;
    private static final int EXTRACT_SIZE = 14;
    private static final int EXTRACT_DY = 3;
    /** 列表下方的状态行。 */
    private static final int STATUS_DY = LibraryMenu.ROWS * ROW_H + 4;

    // 滚动按钮挪到标题行、图鉴按钮左侧——放 y=69 会压住列表的「阶级」表头
    private static final int SCROLL_UP_X = 190;
    private static final int SCROLL_DOWN_X = 204;
    private static final int SCROLL_Y = 4;
    private static final int SCROLL_SIZE = 12;

    public LibraryScreen(LibraryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
        this.titleLabelX = 10;
        this.titleLabelY = 3;
    }

    /** 只画标题——不画「物品栏」标签（列表底延伸到 164，标签会压在上面）。 */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, GuiRender.TEXT, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        // 面板 / 槽位凹陷 / 能量条底 / 分隔线 / 列表底全部烘焙在背景贴图里
        GuiRender.background(graphics, GuiSprites.ENCHANTMENT_LIBRARY, x, y, PANEL_W, PANEL_H);

        GuiRender.midButton(graphics, x + CODEX_X, y + CODEX_Y,
                this.inside(mouseX, mouseY, CODEX_X, CODEX_Y, CODEX_W, CODEX_H), true);
        GuiRender.miniButton(graphics, x + SCROLL_UP_X, y + SCROLL_Y,
                this.inside(mouseX, mouseY, SCROLL_UP_X, SCROLL_Y, SCROLL_SIZE, SCROLL_SIZE),
                this.menu.canScrollUp());
        GuiRender.miniButton(graphics, x + SCROLL_DOWN_X, y + SCROLL_Y,
                this.inside(mouseX, mouseY, SCROLL_DOWN_X, SCROLL_Y, SCROLL_SIZE, SCROLL_SIZE),
                this.menu.canScrollDown());

        int left = x + LIST_X;
        int top = y + LIST_Y;
        for (int i = 0; i < LibraryMenu.ROWS; i++) {
            int rowY = top + i * ROW_H;
            if (this.menu.rowAt(i) != null && this.hovered(mouseX, mouseY, left, rowY)) {
                graphics.fill(left, rowY, left + LIST_W, rowY + ROW_H, GuiRender.HOVER_TINT);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderHint(graphics);
        this.renderCodexLabel(graphics);
        this.renderStrip(graphics);
        this.renderArrows(graphics);
        this.renderList(graphics, mouseX, mouseY);
        this.renderExtractTooltip(graphics, mouseX, mouseY);
        this.renderTooltip(graphics, mouseX, mouseY);
        // 自绘的 tooltip 必须在原版那个之后，否则会被盖住
        this.renderStockTooltip(graphics, mouseX, mouseY);
    }

    private void renderHint(GuiGraphics graphics) {
        graphics.drawString(this.font,
                Component.translatable("container.ultraenchantment.advanced_enchantment_library.hint"),
                this.leftPos + HINT_X, this.topPos + HINT_Y, GuiRender.TEXT_DIM, false);
    }

    private void renderCodexLabel(GuiGraphics graphics) {
        GuiRender.centered(graphics, this.font,
                Component.translatable("container.ultraenchantment.codex.button"),
                this.leftPos + CODEX_X, this.topPos + CODEX_Y + 3, CODEX_W, GuiRender.TEXT, false);
    }

    /** 两行：等级单位（4 档）+ 进阶书库存（3 档，基础档画占位）。 */
    private void renderStrip(GuiGraphics graphics) {
        int x = this.leftPos;
        int y = this.topPos;
        for (int j = 0; j < LineageTier.values().length; j++) {
            GuiRender.centered(graphics, this.font,
                    Component.translatable(tierKey(LineageTier.values()[j])),
                    x + STRIP_CELL_X + j * (STRIP_CELL_W + 2), y + STRIP_COL_HEADER_Y,
                    STRIP_CELL_W, GuiRender.TEXT_DIM, false);
        }
        this.renderLevelRow(graphics, y + STRIP_LEVEL_Y);
        this.renderBookRow(graphics, y + STRIP_BOOK_Y);
    }

    /** 第一行：等级单位（提级燃料，仍然是点数）。 */
    private void renderLevelRow(GuiGraphics graphics, int rowY) {
        int x = this.leftPos;
        GuiRender.centered(graphics, this.font,
                Component.translatable("energy." + Ultraenchantment.MODID + ".level"),
                x + STRIP_LABEL_X, rowY + 3, STRIP_CELL_X - STRIP_LABEL_X - 4, GuiRender.TEXT, false);
        for (int j = 0; j < LineageTier.values().length; j++) {
            int value = this.menu.levelEnergy(LineageTier.values()[j]);
            GuiRender.centered(graphics, this.font, Component.literal(String.valueOf(value)),
                    x + STRIP_CELL_X + j * (STRIP_CELL_W + 2), rowY + 3, STRIP_CELL_W,
                    value > 0 ? GuiRender.TEXT : GuiRender.TEXT_OFF, false);
        }
    }

    /** 第二行：进阶书库存（升阶钥匙，本数）。基础阶没有进阶书，画「—」。 */
    private void renderBookRow(GuiGraphics graphics, int rowY) {
        int x = this.leftPos;
        GuiRender.centered(graphics, this.font,
                Component.translatable("container." + Ultraenchantment.MODID
                        + ".advanced_enchantment_library.strip.books"),
                x + STRIP_LABEL_X, rowY + 3, STRIP_CELL_X - STRIP_LABEL_X - 4, GuiRender.TEXT, false);
        for (int j = 0; j < LineageTier.values().length; j++) {
            LineageTier tier = LineageTier.values()[j];
            int cellX = x + STRIP_CELL_X + j * (STRIP_CELL_W + 2);
            if (!EnchantmentLibraryBlockEntity.isBookTier(tier)) {
                GuiRender.centered(graphics, this.font, Component.literal("—"), cellX, rowY + 3,
                        STRIP_CELL_W, GuiRender.TEXT_OFF, false);
                continue;
            }
            int total = this.menu.genericBooks(tier) + this.menu.targetedBookTotal(tier);
            GuiRender.centered(graphics, this.font, Component.literal("×" + total), cellX, rowY + 3,
                    STRIP_CELL_W, total > 0 ? GuiRender.TEXT : GuiRender.TEXT_OFF, false);
        }
    }

    /**
     * 悬停「进阶书」格子时给出明细：通用几本、每条谱系各几本。
     *
     * <p>格子里只有总数——因为明细本身可能是被截断的（{@link LibraryMenu#STOCK_DETAIL_MAX}），
     * 而格子宽度只有 46px，塞不下。
     */
    private void renderStockTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int j = 0; j < LineageTier.values().length; j++) {
            LineageTier tier = LineageTier.values()[j];
            if (!EnchantmentLibraryBlockEntity.isBookTier(tier)) {
                continue;
            }
            int cellX = this.leftPos + STRIP_CELL_X + j * (STRIP_CELL_W + 2);
            int cellY = this.topPos + STRIP_BOOK_Y;
            if (mouseX < cellX || mouseX >= cellX + STRIP_CELL_W
                    || mouseY < cellY || mouseY >= cellY + STRIP_CELL_H) {
                continue;
            }
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("container." + Ultraenchantment.MODID
                            + ".advanced_enchantment_library.strip.books")
                    .append(Component.literal(" · "))
                    .append(Component.translatable(tierKey(tier)))
                    .withStyle(ChatFormatting.GOLD));
            int generic = this.menu.genericBooks(tier);
            lines.add(Component.translatable("container." + Ultraenchantment.MODID
                    + ".advanced_enchantment_library.stock.generic", generic)
                    .withStyle(generic > 0 ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
            for (LibraryMenu.StockEntry entry : this.menu.targetedBooks(tier)) {
                lines.add(Component.literal("  ")
                        .append(entry.root().value().description())
                        .append(Component.literal(" ×" + entry.count()))
                        .withStyle(ChatFormatting.GRAY));
            }
            if (this.menu.targetedBookTotal(tier) > this.menu.targetedBooks(tier).size()) {
                lines.add(Component.literal("  …").withStyle(ChatFormatting.DARK_GRAY));
            }
            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
            return;
        }
    }

    private void renderArrows(GuiGraphics graphics) {
        GuiRender.arrow(graphics, this.leftPos + SCROLL_UP_X, this.topPos + SCROLL_Y,
                true, this.menu.canScrollUp());
        GuiRender.arrow(graphics, this.leftPos + SCROLL_DOWN_X, this.topPos + SCROLL_Y,
                false, this.menu.canScrollDown());
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = this.leftPos + LIST_X;
        int top = this.topPos + LIST_Y;

        graphics.drawString(this.font,
                Component.translatable("container.ultraenchantment.advanced_enchantment_library.col.enchantment"),
                left + 5, top - 10, GuiRender.TEXT_DIM, false);
        GuiRender.centered(graphics, this.font,
                Component.translatable("container.ultraenchantment.advanced_enchantment_library.col.tier"),
                left + TIER_LABEL_X, top - 10, TIER_LABEL_W, GuiRender.TEXT_DIM, false);
        GuiRender.centered(graphics, this.font,
                Component.translatable("container.ultraenchantment.advanced_enchantment_library.col.level"),
                left + LEVEL_NUM_X, top - 10, LEVEL_NUM_W, GuiRender.TEXT_DIM, false);

        if (this.menu.totalEntries() == 0) {
            graphics.drawString(this.font,
                    Component.translatable("container.ultraenchantment.advanced_enchantment_library.empty"),
                    left + 5, top + 6, GuiRender.TEXT_DIM, false);
            return;
        }

        for (int i = 0; i < LibraryMenu.ROWS; i++) {
            int rowY = top + i * ROW_H;
            LibraryMenu.Row row = this.menu.rowAt(i);
            if (row == null) {
                continue;
            }
            int cy = rowY + CTRL_DY;

            String label = this.font.plainSubstrByWidth(rowLabel(row), NAME_W);
            graphics.drawString(this.font, label, left + 5, rowY + 6, GuiRender.TEXT, false);

            // 阶级 − / +：只有 ≥2 个可选档才点得动（只有一个档时按了也没意义）
            boolean multiTier = Integer.bitCount(row.tierMask()) > 1;
            this.miniCtrl(graphics, mouseX, mouseY, left + TIER_MINUS_X, cy, i, 0, "−",
                    multiTier);
            GuiRender.centered(graphics, this.font,
                    Component.translatable(tierKey(row.tier().asLineageTier())),
                    left + TIER_LABEL_X, rowY + 6, TIER_LABEL_W, GuiRender.TEXT, false);
            this.miniCtrl(graphics, mouseX, mouseY, left + TIER_PLUS_X, cy, i, 1, "+",
                    multiTier);

            // 等级 − / +
            boolean canDown = row.maxLevel() > 0 && row.level() > 1;
            boolean canUp = row.maxLevel() > 0 && row.level() < row.maxLevel();
            this.miniCtrl(graphics, mouseX, mouseY, left + LEVEL_MINUS_X, cy, i, 2, "−",
                    canDown);
            GuiRender.centered(graphics, this.font,
                    Component.literal(String.valueOf(row.level())),
                    left + LEVEL_NUM_X, rowY + 6, LEVEL_NUM_W,
                    row.maxLevel() > 0 ? GuiRender.TEXT : GuiRender.TEXT_OFF, false);
            this.miniCtrl(graphics, mouseX, mouseY, left + LEVEL_PLUS_X, cy, i, 3, "+",
                    canUp);

            // 花费（「N 点」）：余量不够就标红 —— 和进阶台的花费列同一套语言
            Component cost = Component.translatable(
                    "container.ultraenchantment.advanced_enchantment_library.cost", row.cost());
            graphics.drawString(this.font, cost, left + COST_RIGHT - this.font.width(cost),
                    rowY + 6, row.affordable() ? GuiRender.TEXT_DIM : GuiRender.TEXT_BAD, false);

            // 取出：14×14 方块 + 向下箭头（防止点一行就误取）
            boolean extractHovered = this.inside(mouseX, mouseY,
                    LIST_X + EXTRACT_X, LIST_Y + i * ROW_H + EXTRACT_DY,
                    EXTRACT_SIZE, EXTRACT_SIZE);
            GuiRender.squareButton(graphics, left + EXTRACT_X, rowY + EXTRACT_DY,
                    extractHovered, row.affordable());
            // ⚠️ arrow() 内部会再 +4，所以这里给 0 才是垂直居中（原来 +3 → 图标偏下 3px）
            GuiRender.arrow(graphics, left + EXTRACT_X + 1, rowY + EXTRACT_DY, false,
                    row.affordable());
        }
        this.renderStatusLine(graphics);
    }

    /** 行名字：选了某一档就显示该档的专属名，没有专属名就退回原版名。 */
    private String rowLabel(LibraryMenu.Row row) {
        LineageTier tier = row.tier().asLineageTier();
        String key = "enchantment." + Ultraenchantment.MODID + "." + tier.id() + "."
                + row.rootId().getPath();
        return Language.getInstance().has(key)
                ? Component.translatable(key).getString()
                : row.root().value().description().getString();
    }

    /** 12×12 迷你按钮 + 中间一个符号；由 {@link #ctrlIndexAt} 同源判定命中。 */
    private void miniCtrl(GuiGraphics graphics, int mouseX, int mouseY,
                          int x, int y, int row, int which, String glyph, boolean enabled) {
        boolean hovered = this.ctrlIndexAt(mouseX, mouseY, row) == which;
        GuiRender.miniButton(graphics, x, y, hovered, enabled);
        GuiRender.centered(graphics, this.font, Component.literal(glyph), x, y + 3, CTRL,
                enabled ? GuiRender.TEXT : GuiRender.TEXT_OFF, false);
    }

    /** 鼠标压在这一行的第几个控件上：0 阶级− / 1 阶级+ / 2 等级− / 3 等级+ / 4 取出 / −1 无。 */
    private int ctrlIndexAt(double mouseX, double mouseY, int row) {
        if (row < 0 || row >= LibraryMenu.ROWS) {
            return -1;
        }
        int[] xs = {TIER_MINUS_X, TIER_PLUS_X, LEVEL_MINUS_X, LEVEL_PLUS_X};
        for (int i = 0; i < xs.length; i++) {
            if (this.inside(mouseX, mouseY, LIST_X + xs[i], LIST_Y + row * ROW_H + CTRL_DY,
                    CTRL, CTRL)) {
                return i;
            }
        }
        if (this.inside(mouseX, mouseY, LIST_X + EXTRACT_X, LIST_Y + row * ROW_H + EXTRACT_DY,
                EXTRACT_SIZE, EXTRACT_SIZE)) {
            return 4;
        }
        return -1;
    }

    /**
     * 「取出」按钮的悬停明细：取出什么、花多少、**图书馆有多少、取完还剩多少**。
     *
     * <p>作者要的「检查余量够不够」在这里看得见 —— 不够时最后一行红字写「还差 N 点」，
     * 同时按钮画成不可用态。
     */
    private void renderExtractTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int row = this.rowIndexAt(mouseX, mouseY);
        if (row < 0 || this.ctrlIndexAt(mouseX, mouseY, row) != 4) {
            return;
        }
        LibraryMenu.Row entry = this.menu.rowAt(row);
        if (entry == null) {
            return;
        }
        int left = this.leftPos + LIST_X;
        int top = this.topPos + LIST_Y;
        int bx = left + EXTRACT_X;
        int by = top + row * ROW_H + EXTRACT_DY;
        if (!this.inside(mouseX, mouseY, LIST_X + EXTRACT_X,
                LIST_Y + row * ROW_H + EXTRACT_DY, EXTRACT_SIZE, EXTRACT_SIZE)) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(
                "container.ultraenchantment.advanced_enchantment_library.tip.extract.title",
                this.rowLabel(entry), entry.level()));
        lines.add(Component.translatable(
                "container.ultraenchantment.advanced_enchantment_library.tip.extract.cost",
                entry.cost(), Component.translatable(tierKey(entry.tier().asLineageTier()))));
        lines.add(Component.translatable(
                "container.ultraenchantment.advanced_enchantment_library.tip.extract.stock",
                entry.energy(), entry.energy() - entry.cost()));
        if (!entry.affordable()) {
            lines.add(Component.translatable(
                            "container.ultraenchantment.advanced_enchantment_library.tip.extract.short",
                            entry.cost() - entry.energy())
                    .withStyle(ChatFormatting.RED));
        }
        graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    /** 列表下方的状态行：取出被拒时说清楚为什么。 */
    private void renderStatusLine(GuiGraphics graphics) {
        int status = this.menu.status();
        // ⚠️ 成功不提示：取出来书就在输出槽里，玩家看得见。只有被拒才需要解释。
        String key = switch (status) {
            case LibraryMenu.STATUS_OUTPUT_BUSY -> "output_busy";
            case LibraryMenu.STATUS_NOT_ENOUGH -> "not_enough";
            case LibraryMenu.STATUS_NO_TIER -> "no_tier";
            default -> null;
        };
        if (key == null) {
            return;
        }
        graphics.drawString(this.font,
                Component.translatable("container.ultraenchantment.advanced_enchantment_library.status."
                        + key),
                this.leftPos + LIST_X + 5, this.topPos + LIST_Y + STATUS_DY,
                status == LibraryMenu.STATUS_OK ? GuiRender.TEXT_OK : GuiRender.TEXT_BAD, false);
    }

    private static String tierKey(LineageTier tier) {
        return "tier." + Ultraenchantment.MODID + "." + tier.id();
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
        if (button == 0) {
            if (this.inside(mouseX, mouseY, CODEX_X, CODEX_Y, CODEX_W, CODEX_H)) {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new CodexScreen());
                }
                return true;
            }
            if (this.inside(mouseX, mouseY, SCROLL_UP_X, SCROLL_Y, SCROLL_SIZE, SCROLL_SIZE)) {
                if (this.menu.canScrollUp()) {
                    this.sendButton(LibraryMenu.BTN_SCROLL_UP);
                }
                return true;
            }
            if (this.inside(mouseX, mouseY, SCROLL_DOWN_X, SCROLL_Y, SCROLL_SIZE, SCROLL_SIZE)) {
                if (this.menu.canScrollDown()) {
                    this.sendButton(LibraryMenu.BTN_SCROLL_DOWN);
                }
                return true;
            }
            // v6：点一行**不再直接取出**（那正是误触与吞书的来源）。
            // 行内四个调节钮 + 一个明确的取出钮，各自单独判定。
            int ctrl = this.ctrlIndexAt(mouseX, mouseY, this.rowIndexAt(mouseX, mouseY));
            switch (ctrl) {
                case 0 -> {
                    this.sendButton(LibraryMenu.BTN_TIER_DOWN + this.rowIndexAt(mouseX, mouseY));
                    return true;
                }
                case 1 -> {
                    this.sendButton(LibraryMenu.BTN_TIER_UP + this.rowIndexAt(mouseX, mouseY));
                    return true;
                }
                case 2 -> {
                    this.sendButton(LibraryMenu.BTN_LEVEL_DOWN + this.rowIndexAt(mouseX, mouseY));
                    return true;
                }
                case 3 -> {
                    this.sendButton(LibraryMenu.BTN_LEVEL_UP + this.rowIndexAt(mouseX, mouseY));
                    return true;
                }
                case 4 -> {
                    this.sendButton(LibraryMenu.BTN_EXTRACT + this.rowIndexAt(mouseX, mouseY));
                    return true;
                }
                default -> {
                }
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
