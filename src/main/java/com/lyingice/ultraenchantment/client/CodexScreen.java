package com.lyingice.ultraenchantment.client;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.content.AscensionTier;
import com.lyingice.ultraenchantment.content.CodexData;
import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.registry.UEAttachments;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * <b>图鉴界面</b>——玩家查询「哪些 (谱系, 阶级) 已解锁」的只读总表。
 *
 * <h2>为什么要能打开</h2>
 *
 * <p>图鉴原本只是一个<b>看不见的</b>附件：它决定图书馆能取出什么、进阶台能选哪一档，
 * 但玩家没有任何地方能看到它。作者要求「图鉴最好能够打开，由进阶台和图书馆提供按钮」。
 *
 * <h2>为什么不是容器菜单</h2>
 *
 * <p>没有槽位、没有服务端判定，纯读——所以是 {@link Screen} 而不是
 * {@code AbstractContainerScreen}。数据来自<b>已同步的玩家附件</b>
 * （`UEAttachments.CODEX` 已 `.sync()`，见步骤 1），不需要任何网络往返。
 *
 * <h2>一行的四个方块</h2>
 *
 * <p>基础 / 高阶 / 超级 / 究极并排，四档<b>都</b>看图鉴位掩码（v4 起基础档也要「见过」才解锁，
 * 见 {@link CodexData}）。数据包没铺该阶级时它永远解不开，自然画成暗色。
 *
 * <h2>点列标题切换阶级</h2>
 *
 * <p>玩家看的是<b>该阶级的本地化名</b>（`enchantment.ultraenchantment.super.sharpness` = 「超级锋利」），
 * 而键是<b>逐阶级</b>的，所以一次只能显示一档。点上方四个阶级名即可切换；
 * 这条谱系没有该档（数据包没铺）或语言文件里没有这个键时，退回原版附魔名并压暗 ——
 * <b>绝不显示原始键</b>。
 */
public class CodexScreen extends Screen {

    private static final int PANEL_W = 260;
    private static final int PANEL_H = 204;

    private static final int SCROLL_UP_X = 228;
    private static final int SCROLL_DOWN_X = 242;
    private static final int SCROLL_Y = 7;
    private static final int SCROLL_SIZE = 12;

    private static final int HEADER_Y = 24;
    private static final int LIST_X = 10;
    private static final int LIST_Y = 36;
    private static final int LIST_W = 240;
    private static final int ROW_H = 20;
    private static final int ROWS = 8;

    private static final int NAME_X = 4;
    private static final int NAME_MAX_W = 118;
    private static final int CHIP_X = 128;
    private static final int CHIP_W = 26;
    private static final int CHIP_H = 14;
    private static final int CHIP_GAP = 2;

    private int leftPos;
    private int topPos;
    private int scroll;

    /**
     * 名称列显示哪一档的本地化名。
     *
     * <p>默认 {@link LineageTier#NATIVE} = 原版附魔名（基础档没有专属键）。
     */
    private LineageTier shownTier = LineageTier.NATIVE;

    private List<Holder.Reference<Enchantment>> entries = List.of();

    public CodexScreen() {
        super(Component.translatable("container.ultraenchantment.codex.button"));
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - PANEL_W) / 2;
        this.topPos = (this.height - PANEL_H) / 2;
        this.rebuild();
    }

    /** 列出全部附魔，按注册名排序（稳定，行位置不会跳）。 */
    private void rebuild() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        List<Holder.Reference<Enchantment>> out = new ArrayList<>(
                mc.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders().toList());
        out.sort(Comparator.comparing(h -> h.key().location().toString()));
        this.entries = out;
        this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, out.size() - ROWS)));
    }

    private CodexData codex() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? CodexData.EMPTY : mc.player.getData(UEAttachments.CODEX);
    }

    // ── 渲染 ────────────────────────────────────────────────────────────

    /** 只压暗背景，不 blit 原版那张泥土贴图——我们不是容器界面。 */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = this.leftPos;
        int y = this.topPos;

        GuiRender.background(graphics, GuiSprites.CODEX, x, y, PANEL_W, PANEL_H);
        graphics.drawString(this.font, this.title, x + 10, y + 8, GuiRender.TEXT, false);

        GuiRender.miniButton(graphics, x + SCROLL_UP_X, y + SCROLL_Y,
                inside(mouseX, mouseY, SCROLL_UP_X, SCROLL_Y, SCROLL_SIZE, SCROLL_SIZE), canScrollUp());
        GuiRender.miniButton(graphics, x + SCROLL_DOWN_X, y + SCROLL_Y,
                inside(mouseX, mouseY, SCROLL_DOWN_X, SCROLL_Y, SCROLL_SIZE, SCROLL_SIZE), canScrollDown());
        GuiRender.arrow(graphics, x + SCROLL_UP_X, y + SCROLL_Y, true, canScrollUp());
        GuiRender.arrow(graphics, x + SCROLL_DOWN_X, y + SCROLL_Y, false, canScrollDown());

        this.renderHeader(graphics, mouseX, mouseY);
        this.renderRows(graphics, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.drawString(this.font,
                Component.translatable("container.ultraenchantment.codex.col.enchantment"),
                x + LIST_X + NAME_X, y + HEADER_Y, GuiRender.TEXT_DIM, false);
        // 不给提示的话，「列标题能点」没有任何线索
        graphics.drawString(this.font,
                Component.translatable("container.ultraenchantment.codex.hint"),
                x + 42, y + 9, GuiRender.TEXT_OFF, false);

        int hovered = this.tierHeaderAt(mouseX, mouseY);
        for (int j = 0; j < LineageTier.values().length; j++) {
            LineageTier tier = LineageTier.values()[j];
            int color = tier == this.shownTier ? GuiRender.TEXT_OK
                    : (hovered == j ? GuiRender.TEXT : GuiRender.TEXT_DIM);
            GuiRender.centered(graphics, this.font, Component.translatable(tierKey(tier)),
                    x + LIST_X + CHIP_X + j * (CHIP_W + CHIP_GAP), y + HEADER_Y,
                    CHIP_W, color, false);
        }
    }

    /**
     * 鼠标落在<b>某一行的第几档方块</b>上；不在方块上返回 -1。
     *
     * <p>方块与列标题<b>都要能点</b>：作者反馈「图鉴切换不了阶级按钮、无法互动」——
     * 一屏里最像按钮的就是每行那四个方块，只让列标题可点，玩家自然会去点方块。
     */
    private int tierChipTierAt(double mouseX, double mouseY) {
        int left = this.leftPos + LIST_X + CHIP_X;
        int top = this.topPos + LIST_Y;
        for (int i = 0; i < ROWS; i++) {
            int rowY = top + i * ROW_H + 3;
            if (mouseY < rowY || mouseY >= rowY + CHIP_H) {
                continue;
            }
            for (int j = 0; j < LineageTier.values().length; j++) {
                int x0 = left + j * (CHIP_W + CHIP_GAP);
                if (mouseX >= x0 && mouseX < x0 + CHIP_W) {
                    return j;
                }
            }
        }
        return -1;
    }

    /** 鼠标落在第几个阶级列标题上；不在标题带里返回 -1。 */
    private int tierHeaderAt(double mouseX, double mouseY) {
        int y0 = this.topPos + HEADER_Y - 2;
        if (mouseY < y0 || mouseY >= y0 + 12) {
            return -1;
        }
        int left = this.leftPos + LIST_X + CHIP_X;
        for (int j = 0; j < LineageTier.values().length; j++) {
            int x0 = left + j * (CHIP_W + CHIP_GAP);
            if (mouseX >= x0 && mouseX < x0 + CHIP_W) {
                return j;
            }
        }
        return -1;
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = this.leftPos + LIST_X;
        int top = this.topPos + LIST_Y;
        CodexData codex = this.codex();

        if (this.entries.isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("container.ultraenchantment.codex.empty"),
                    left + 5, top + 6, GuiRender.TEXT_DIM, false);
            return;
        }

        for (int i = 0; i < ROWS; i++) {
            int index = this.scroll + i;
            if (index >= this.entries.size()) {
                break;
            }
            Holder.Reference<Enchantment> holder = this.entries.get(index);
            ResourceLocation rootId = holder.key().location();
            int rowY = top + i * ROW_H;
            if (mouseX >= left && mouseX < left + LIST_W && mouseY >= rowY && mouseY < rowY + ROW_H) {
                graphics.fill(left, rowY, left + LIST_W, rowY + ROW_H, GuiRender.HOVER_TINT);
            }

            // ⚠️ 用附魔的【显示名】而不是注册名——否则玩家看到的是 berserkers_fury 这种内部标识
            Component display = this.displayName(holder, rootId);
            String name = this.font.plainSubstrByWidth(display.getString(), NAME_MAX_W);
            int nameColor = this.tierExists(holder, rootId) ? GuiRender.TEXT : GuiRender.TEXT_OFF;
            graphics.drawString(this.font, name, left + NAME_X, rowY + 6, nameColor, false);

            for (int j = 0; j < LineageTier.values().length; j++) {
                LineageTier tier = LineageTier.values()[j];
                boolean unlocked = codex.isUnlocked(rootId, tier);
                // 方块也能点：点哪一档就把名称列切到那一档。
                // 「当前显示的那一档」用选中态画出来，点完立刻有反馈。
                GuiRender.chip(graphics, left + CHIP_X + j * (CHIP_W + CHIP_GAP), rowY + 3,
                        unlocked,
                        unlocked && tier == this.shownTier,
                        this.tierChipTierAt(mouseX, mouseY) == j);
                GuiRender.centered(graphics, this.font,
                        Component.translatable(tierKey(tier)),
                        left + CHIP_X + j * (CHIP_W + CHIP_GAP), rowY + 7, CHIP_W,
                        unlocked ? GuiRender.TEXT : GuiRender.TEXT_OFF, false);
            }
        }
    }

    private static String tierKey(LineageTier tier) {
        return "tier." + Ultraenchantment.MODID + "." + tier.id();
    }

    /** 该阶级的本地化名；基础档与「这条谱系没有这一档」都退回原版附魔名。 */
    private Component displayName(Holder.Reference<Enchantment> holder, ResourceLocation rootId) {
        if (this.shownTier == LineageTier.NATIVE) {
            return holder.value().description();
        }
        String key = "enchantment." + Ultraenchantment.MODID + "."
                + this.shownTier.id() + "." + rootId.getPath();
        // ⚠️ 必须查「键在不在」再翻译。直接 translatable(...).getString() 在键缺失时
        //    会把键本身当显示名返回 —— 玩家看到一整串 enchantment.ultraenchantment.…
        return Language.getInstance().has(key)
                ? Component.translatable(key)
                : holder.value().description();
    }

    /** 当前显示的这一档，语言文件里有没有这条谱系的键（等价于数据包铺没铺这一阶）。 */
    private boolean tierExists(Holder.Reference<Enchantment> holder, ResourceLocation rootId) {
        if (this.shownTier == LineageTier.NATIVE) {
            return true;
        }
        return Language.getInstance().has("enchantment." + Ultraenchantment.MODID + "."
                + this.shownTier.id() + "." + rootId.getPath());
    }

    // ── 输入 ────────────────────────────────────────────────────────────

    private boolean inside(double mouseX, double mouseY, int relX, int relY, int w, int h) {
        return mouseX >= this.leftPos + relX && mouseX < this.leftPos + relX + w
                && mouseY >= this.topPos + relY && mouseY < this.topPos + relY + h;
    }

    private boolean canScrollUp() {
        return this.scroll > 0;
    }

    private boolean canScrollDown() {
        return this.scroll + ROWS < this.entries.size();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int header = this.tierHeaderAt(mouseX, mouseY);
            if (header >= 0) {
                this.shownTier = LineageTier.values()[header];
                return true;
            }
            int chip = this.tierChipTierAt(mouseX, mouseY);
            if (chip >= 0) {
                this.shownTier = LineageTier.values()[chip];
                return true;
            }
            if (inside(mouseX, mouseY, SCROLL_UP_X, SCROLL_Y, SCROLL_SIZE, SCROLL_SIZE)) {
                if (canScrollUp()) {
                    this.scroll--;
                }
                return true;
            }
            if (inside(mouseX, mouseY, SCROLL_DOWN_X, SCROLL_Y, SCROLL_SIZE, SCROLL_SIZE)) {
                if (canScrollDown()) {
                    this.scroll++;
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0 && canScrollUp()) {
            this.scroll--;
            return true;
        }
        if (scrollY < 0 && canScrollDown()) {
            this.scroll++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
