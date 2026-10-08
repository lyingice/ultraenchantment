package com.lyingice.ultraenchantment.client;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.menu.AscensionTableMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
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
    private static final int PANEL_W = 340;
    private static final int PANEL_H = 236;

    // ── 输入槽 ──
    private static final int SLOT_X = 10;
    private static final int SLOT_Y = 18;
    private static final int HINT_X = 32;
    private static final int HINT_Y = 23;

    // ── 列标题 ──
    private static final int HEADER_Y = 60;

    // ── 列表 ──
    private static final int LIST_X = 10;
    private static final int LIST_Y = 70;
    private static final int LIST_W = 320;
    private static final int ROW_H = 22;

    // 行内相对 x（与命中判定同源）
    private static final int NAME_X = 4;
    private static final int NAME_MAX_W = 84;
    private static final int CHIP_X = 92;
    private static final int CHIP_W = 26;
    private static final int CHIP_H = 16;
    private static final int CHIP_GAP = 2;
    private static final int LEVEL_MINUS_X = 208;
    private static final int LEVEL_TEXT_X = 224;
    private static final int LEVEL_PLUS_X = 240;
    private static final int LEVEL_BTN = 14;

    /**
     * v5：花费拆成两列——左「书耗」（进阶书，本数）、右「级耗」（等级单位）。
     *
     * <h2>⚠️ 这里的右边界是【面板物理边界】卡死的，不是随便留的余量</h2>
     *
     * <p>背景是<b>1:1 整图</b>（槽位、列表底都烘焙在 PNG 里，见 {@code docs/gui-textures.md}），
     * 面板宽 340 是贴图尺寸，<b>不能改宽</b>——那些贴图是作者手绘的，
     * 生成器有防覆盖保险（重跑会把美术冲掉）。
     *
     * <p>面板内沿 ≈ {@code left + 325}（{@code left = leftPos + LIST_X}）。所以两列必须一起塞进
     * 「加号按钮右沿(250)」到「面板内沿(325)」这 <b>75px</b> 里：
     * 书耗最长 19px（{@code 3 本}）＋ 级耗最长 30px（{@code +124} 这类）＋ 两个 10px 间隙 = 69。
     *
     * <p>上一轮把两列各右移 8，只量了「离加号按钮的间隙」、<b>没量右边界</b>，
     * 结果级耗整列顶到边框外——作者截图里一眼就看见了。
     * <b>「放得下」必须量到边，不能只量一边。</b>
     */
    private static final int COST_TIER_RIGHT = 279;
    private static final int COST_LEVEL_RIGHT = 319;
    /** 悬浮提示的判定区（右对齐的格子往左取这么宽；不覆盖加号按钮）。 */
    private static final int COST_HOVER_W = 28;
    private static final int COST_TIER_LEFT = COST_TIER_RIGHT - COST_HOVER_W;
    private static final int COST_LEVEL_LEFT = COST_LEVEL_RIGHT - COST_HOVER_W;

    // ── 灌注按钮（v4 上移到输入槽下方，给列表和物品栏腾地方）──
    private static final int APPLY_X = 10;
    private static final int APPLY_Y = 38;
    private static final int APPLY_W = 100;
    private static final int APPLY_H = 16;
    private static final int STATUS_X = 116;

    /**
     * 中按钮（图鉴 / 重置）——尺寸必须<b>等于精灵表的 30×14</b>。
     *
     * <p>曾经命中框写成 36 宽而贴图只有 30：右边那 6px 点得到却看不见按钮。
     */
    private static final int MID_W = 30;
    private static final int MID_H = 14;

    /** v4：右上角的「图鉴」入口。 */
    private static final int CODEX_X = PANEL_W - MID_W - 10;
    private static final int CODEX_Y = 6;

    /** 灌注行右侧的「重置」——清掉手动改动，回到物品当前状态（对应 {@code BTN_CLEAR}）。 */
    private static final int RESET_X = PANEL_W - MID_W - 10;
    private static final int RESET_Y = APPLY_Y + 1;

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
        // 面板 / 槽位凹陷 / 列表底全部烘焙在背景贴图里（美术可整张重绘）
        GuiRender.background(graphics, GuiSprites.ASCENSION_TABLE, x, y, PANEL_W, PANEL_H);

        // 灌注按钮
        boolean applyHovered = this.inside(mouseX, mouseY, APPLY_X, APPLY_Y, APPLY_W, APPLY_H);
        GuiRender.wideButton(graphics, x + APPLY_X, y + APPLY_Y, applyHovered, true);

        // 图鉴入口
        GuiRender.midButton(graphics, x + CODEX_X, y + CODEX_Y,
                this.inside(mouseX, mouseY, CODEX_X, CODEX_Y, MID_W, MID_H), true);

        // 重置（清掉手动改动）：BTN_CLEAR 早就实现了，但界面上一直没有入口
        GuiRender.midButton(graphics, x + RESET_X, y + RESET_Y,
                this.inside(mouseX, mouseY, RESET_X, RESET_Y, MID_W, MID_H), true);

        this.renderRowsBg(graphics, mouseX, mouseY);
    }

    /**
     * 第 {@code j} 档方块是否可选（数据包铺了 <b>且</b> 图鉴解锁了）。
     *
     * <p>⚠️ 判据必须与服务端 {@code AscensionTableMenu.selectTier} 完全一致，
     * 且位的取法必须走 {@link AscensionTableMenu#unlockBit}。
     * 曾经这里对基础阶写死「亮」，而服务端照样查图鉴 ⇒ 「所有基础都亮着却点不动」。
     */
    private boolean chipUnlocked(AscensionTableMenu.Row row, int j) {
        LineageTier tier = LineageTier.values()[j];
        return row.maxLevels()[j] > 0
                && (row.unlockMask() & AscensionTableMenu.unlockBit(tier)) != 0;
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
                boolean unlocked = chipUnlocked(row, j);
                boolean selected = row.tierOrdinal() == j;
                boolean hover = this.inside(mouseX, mouseY,
                        CHIP_X + j * (CHIP_W + CHIP_GAP), rowY - top + 3, CHIP_W, CHIP_H);
                GuiRender.chip(graphics, cx, cy, unlocked, selected, hover);
            }

            // 等级 −/+
            if (row.tierOrdinal() >= 0) {
                boolean minusHover = this.inside(mouseX, mouseY, LEVEL_MINUS_X, rowY - top + 4, LEVEL_BTN, LEVEL_BTN);
                boolean plusHover = this.inside(mouseX, mouseY, LEVEL_PLUS_X, rowY - top + 4, LEVEL_BTN, LEVEL_BTN);
                GuiRender.squareButton(graphics, left + LEVEL_MINUS_X, rowY + 4, minusHover, true);
                GuiRender.squareButton(graphics, left + LEVEL_PLUS_X, rowY + 4, plusHover, true);
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
        // 悬停明细要盖在列表之上（和图书馆的书库存 tooltip 同一套做法）
        this.renderCostTooltip(graphics, mouseX, mouseY);
        this.renderCodexLabel(graphics);
        this.renderResetLabel(graphics);
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
        // v5：花费拆成两列——左「钥匙」（进阶书，本数）、右「级耗」（等级单位）
        Component tierCost = Component.translatable(
                "container.ultraenchantment.ascension_table.col.key_book");
        graphics.drawString(this.font, tierCost,
                x + LIST_X + COST_TIER_RIGHT - this.font.width(tierCost),
                y + HEADER_Y, GuiRender.TEXT_DIM, false);
        Component levelCost = Component.translatable(
                "container.ultraenchantment.ascension_table.col.level_cost");
        graphics.drawString(this.font, levelCost,
                x + LIST_X + COST_LEVEL_RIGHT - this.font.width(levelCost),
                y + HEADER_Y, GuiRender.TEXT_DIM, false);
    }

    /**
     * 鼠标停在「书耗」或「级耗」格子上时，给出这一笔账的完整来龙去脉。
     *
     * <p>三件事（作者要的）：<b>具体消耗什么</b>、<b>环内图书馆的总储备</b>、
     * <b>消耗之后还剩多少</b>。
     *
     * <p>数据全部来自 {@code ContainerData}（服务端算好下发）——
     * 客户端<b>从不</b>碰方块实体，这是 v5 定下的规矩（§12.4）。
     */
    private void renderCostTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = this.leftPos + LIST_X;
        int top = this.topPos + LIST_Y;
        for (int i = 0; i < AscensionTableMenu.ROWS; i++) {
            AscensionTableMenu.Row row = this.menu.rowAt(i);
            if (row == null || row.tierOrdinal() < 0) {
                continue;
            }
            int rowY = top + i * ROW_H;
            if (mouseY < rowY || mouseY >= rowY + ROW_H) {
                continue;
            }
            LineageTier tier = LineageTier.values()[row.tierOrdinal()];
            if (mouseX >= left + COST_TIER_LEFT && mouseX < left + COST_TIER_RIGHT) {
                graphics.renderComponentTooltip(this.font, bookTooltip(row, tier), mouseX, mouseY);
            } else if (mouseX >= left + COST_LEVEL_LEFT && mouseX < left + COST_LEVEL_RIGHT) {
                graphics.renderComponentTooltip(this.font, levelTooltip(row, tier), mouseX, mouseY);
            }
            return;
        }
    }

    /** 「书耗」明细：要几本什么书、定向 / 通用各多少、环内共多少、扣完剩多少。 */
    private static List<Component> bookTooltip(AscensionTableMenu.Row row, LineageTier tier) {
        List<Component> out = new ArrayList<>();
        if (row.keyBook() == 0) {
            out.add(Component.translatable(
                    "container.ultraenchantment.ascension_table.tip.book.none"));
            return out;
        }
        int need = Math.abs(row.keyBook());
        Component tierName = Component.translatable(tierKey(tier));
        out.add(Component.translatable(
                "container.ultraenchantment.ascension_table.tip.book.need", need, tierName));
        out.add(Component.translatable(
                "container.ultraenchantment.ascension_table.tip.book.stock",
                row.bookStock(), row.keyTargeted(), row.keyGeneric()));
        if (row.keyBook() < 0) {
            out.add(Component.translatable(
                            "container.ultraenchantment.ascension_table.tip.book.short",
                            need - row.bookStock())
                    .withStyle(ChatFormatting.RED));
        } else {
            out.add(Component.translatable(
                    "container.ultraenchantment.ascension_table.tip.book.left",
                    row.bookStock() - need));
        }
        return out;
    }

    /** 「级耗」明细：要几点、环内共多少、扣完（或退完）剩多少。 */
    private static List<Component> levelTooltip(AscensionTableMenu.Row row, LineageTier tier) {
        List<Component> out = new ArrayList<>();
        Component tierName = Component.translatable(tierKey(tier));
        int cost = row.levelCost();
        if (cost == 0) {
            out.add(Component.translatable(
                    "container.ultraenchantment.ascension_table.tip.level.none"));
        } else if (cost < 0) {
            // 负 = 降级退还。写清楚这是【退给你】的，不是要你付的。
            out.add(Component.translatable(
                            "container.ultraenchantment.ascension_table.tip.level.refund",
                            -cost, tierName)
                    .withStyle(ChatFormatting.GREEN));
        } else {
            out.add(Component.translatable(
                    "container.ultraenchantment.ascension_table.tip.level.need", cost, tierName));
        }
        out.add(Component.translatable(
                "container.ultraenchantment.ascension_table.tip.level.stock", row.levelStock()));
        if (cost > 0) {
            if (row.levelStock() < cost) {
                out.add(Component.translatable(
                                "container.ultraenchantment.ascension_table.tip.level.short",
                                cost - row.levelStock())
                        .withStyle(ChatFormatting.RED));
            } else {
                out.add(Component.translatable(
                        "container.ultraenchantment.ascension_table.tip.level.left",
                        row.levelStock() - cost));
            }
        } else if (cost < 0) {
            out.add(Component.translatable(
                    "container.ultraenchantment.ascension_table.tip.level.left",
                    row.levelStock() - cost));
        }
        return out;
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
            // 选了【进阶】阶级就显示该阶级的专属名（玩家可以给每条谱系的每一阶单独做本地化）；
            // ⚠️ 基础阶没有专属名 —— 必须退回原版附魔名，否则会去查
            //    `enchantment.ultraenchantment.native.<x>` 这个【不存在的键】，
            //    getString() 会把键本身当显示名返回，界面上就冒出一整串原始键。
            //    预选之后每一行原版附魔都会是选中态，这个坑会立刻炸出来。
            LineageTier shown = row.tierOrdinal() >= 0 ? LineageTier.values()[row.tierOrdinal()] : null;
            Component display = shown != null && shown != LineageTier.NATIVE
                    ? Component.translatable("enchantment." + Ultraenchantment.MODID + "."
                            + shown.id() + "." + row.rootId().getPath())
                    : row.root().value().description();
            String name = this.font.plainSubstrByWidth(display.getString(), NAME_MAX_W);
            graphics.drawString(this.font, name, left + NAME_X, rowY + 7, GuiRender.TEXT, false);

            // 阶级方块上的文字
            for (int j = 0; j < LineageTier.values().length; j++) {
                int cx = left + CHIP_X + j * (CHIP_W + CHIP_GAP);
                boolean unlocked = chipUnlocked(row, j);
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

                // 花费（两列分开显示；付不起时整行标红）
                int costColor = row.affordable() ? GuiRender.TEXT_DIM : GuiRender.TEXT_BAD;
                if (row.keyBook() != 0) {
                    // v5：书耗不是点数，是【N 本进阶书】。正 N = 够，负 N = 缺 N 本。
                    // ⚠️ 缺的时候也报【数量】——跨阶跳跃要 3 本，只写「缺」玩家分不清差几本。
                    // ⚠️ 但不能再写成「缺 N 本」（32px 宽，面板里塞不下，会顶出边框）：
                    //    统一成「N 本」，缺就用红字，和右边「级耗」付不起时标红是同一套视觉语言。
                    //    缺多少、还剩多少，鼠标一悬停就有明细（见 renderCostTooltip）。
                    Component book = Component.translatable(
                            "container.ultraenchantment.ascension_table.book_count",
                            Math.abs(row.keyBook()));
                    graphics.drawString(this.font, book,
                            left + COST_TIER_RIGHT - this.font.width(book), rowY + 7,
                            row.keyBook() < 0 ? GuiRender.TEXT_BAD : GuiRender.TEXT_DIM, false);
                }
                if (row.levelCost() != 0) {
                    // 负 = 降级返还。写成「+N」并用绿色 —— 这一笔是【退给你】的，
                    // 不是要你付的。原来负值落进「免费」那一支，看起来像免费降级，
                    // 实际是把投进去的点数悄悄吞掉。
                    boolean refund = row.levelCost() < 0;
                    Component levelCost = Component.literal(refund
                            ? "+" + (-row.levelCost())
                            : String.valueOf(row.levelCost()));
                    graphics.drawString(this.font, levelCost,
                            left + COST_LEVEL_RIGHT - this.font.width(levelCost), rowY + 7,
                            refund ? GuiRender.TEXT_OK : costColor, false);
                }
                if (row.keyBook() == 0 && row.levelCost() == 0) {
                    Component free = Component.translatable(
                            "container.ultraenchantment.ascension_table.free");
                    graphics.drawString(this.font, free,
                            left + COST_LEVEL_RIGHT - this.font.width(free), rowY + 7,
                            GuiRender.TEXT_DIM, false);
                }
            }
        }
    }

    private void renderCodexLabel(GuiGraphics graphics) {
        GuiRender.centered(graphics, this.font,
                Component.translatable("container.ultraenchantment.codex.button"),
                this.leftPos + CODEX_X, this.topPos + CODEX_Y + 3, MID_W, GuiRender.TEXT, false);
    }

    private void renderResetLabel(GuiGraphics graphics) {
        GuiRender.centered(graphics, this.font,
                Component.translatable("container.ultraenchantment.ascension_table.reset"),
                this.leftPos + RESET_X, this.topPos + RESET_Y + 4, MID_W, GuiRender.TEXT, false);
    }

    private void renderApplyLabel(GuiGraphics graphics) {
        GuiRender.centered(graphics, this.font,
                Component.translatable("container.ultraenchantment.ascension_table.apply"),
                this.leftPos + APPLY_X, this.topPos + APPLY_Y + 4, APPLY_W, GuiRender.TEXT, false);
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
            case AscensionTableMenu.STATUS_TIER_LOCKED -> "tier_locked";
            case AscensionTableMenu.STATUS_TIER_MISSING -> "tier_missing";
            case AscensionTableMenu.STATUS_NO_KEY_BOOK -> "no_key_book";
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
        if (this.inside(mouseX, mouseY, CODEX_X, CODEX_Y, MID_W, MID_H)) {
            // 图鉴数据已同步到客户端，直接开界面，不需要服务端往返
            if (this.minecraft != null) {
                this.minecraft.setScreen(new CodexScreen());
            }
            return true;
        }
        if (this.inside(mouseX, mouseY, RESET_X, RESET_Y, MID_W, MID_H)) {
            // 恢复到物品当前状态（物品上本来就有的谱系是锁定项，清不掉）
            this.sendButton(AscensionTableMenu.BTN_CLEAR);
            return true;
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
