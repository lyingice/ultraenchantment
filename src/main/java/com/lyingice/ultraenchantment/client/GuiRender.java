package com.lyingice.ultraenchantment.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 本模组界面的<b>绘制原语</b>——统一的原版风格凸起/凹陷边框。
 *
 * <h2>为什么抽出来</h2>
 *
 * <p>原版 GUI 的「立体感」全靠 1px 的明暗边：亮边在<b>上/左</b>、暗边在<b>下/右</b>，
 * 于是同一块灰底读起来是「凸起的按钮」还是「凹进去的槽位」完全由边的方向决定。
 *
 * <p>v1 的界面只用了纯色 {@code fill}，没有边——所有元素看起来都是同一张灰纸，
 * 玩家分不清哪里能点、哪里是槽位。这不是美术问题，是<b>缺少这层约定</b>。
 *
 * <h2>两种立体方向</h2>
 * <ul>
 *   <li>{@link #panel}/{@link #button}——<b>凸起</b>：亮上左、暗下右（可点的东西）</li>
 *   <li>{@link #inset}——<b>凹陷</b>：暗上左、亮下右（槽位、列表底、输入框）</li>
 * </ul>
 *
 * <p>全部坐标由调用方给绝对像素（已含 {@code leftPos/topPos}），本类不做居中。
 */
public final class GuiRender {
    private GuiRender() {}

    /** 面板底色。原版 GUI 的标准灰。 */
    public static final int PANEL = 0xFFC6C6C6;
    /** 凸起亮边。 */
    public static final int BEVEL_LIGHT = 0xFFFFFFFF;
    /** 凸起暗边。 */
    public static final int BEVEL_DARK = 0xFF555555;
    /** 凹陷区底色。 */
    public static final int INSET_BG = 0xFF8B8B8B;
    /** 凹陷边（比凸起暗边更深，才压得下去）。 */
    public static final int INSET_EDGE = 0xFF373737;
    /** 按钮按下/悬停时的底色。 */
    public static final int BUTTON_HOVER = 0xFFD8D8D8;
    /** 按钮禁用底色。 */
    public static final int BUTTON_OFF = 0xFF9E9E9E;

    public static final int TEXT = 0x404040;
    public static final int TEXT_DIM = 0x707070;
    public static final int TEXT_OFF = 0xA0A0A0;
    public static final int TEXT_BAD = 0xA00000;
    public static final int TEXT_OK = 0x106010;

    /** 行悬停叠色。 */
    public static final int HOVER_TINT = 0x30FFFFFF;
    /** 行「已选中」叠色。 */
    public static final int SELECTED_TINT = 0x4080FF80;

    /** 凸起面板：亮上左、暗下右。 */
    public static void panel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, PANEL);
        graphics.fill(x, y, x + w, y + 1, BEVEL_LIGHT);
        graphics.fill(x, y, x + 1, y + h, BEVEL_LIGHT);
        graphics.fill(x + w - 1, y, x + w, y + h, BEVEL_DARK);
        graphics.fill(x, y + h - 1, x + w, y + h, BEVEL_DARK);
    }

    /** 凹陷区：暗上左、亮下右。槽位与列表底用它。 */
    public static void inset(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, INSET_BG);
        graphics.fill(x, y, x + w, y + 1, INSET_EDGE);
        graphics.fill(x, y, x + 1, y + h, INSET_EDGE);
        graphics.fill(x + w - 1, y, x + w, y + h, BEVEL_LIGHT);
        graphics.fill(x, y + h - 1, x + w, y + h, BEVEL_LIGHT);
    }

    /** 按钮：凸起；悬停更亮、禁用变灰。 */
    public static void button(GuiGraphics graphics, int x, int y, int w, int h,
                              boolean hovered, boolean enabled) {
        int body = !enabled ? BUTTON_OFF : (hovered ? BUTTON_HOVER : PANEL);
        graphics.fill(x, y, x + w, y + h, body);
        int light = enabled ? BEVEL_LIGHT : 0xFFB8B8B8;
        int dark = enabled ? BEVEL_DARK : 0xFF6E6E6E;
        graphics.fill(x, y, x + w, y + 1, light);
        graphics.fill(x, y, x + 1, y + h, light);
        graphics.fill(x + w - 1, y, x + w, y + h, dark);
        graphics.fill(x, y + h - 1, x + w, y + h, dark);
    }

    /**
     * 阶级方块（chip）——「有哪几档可选」的可见表达。
     *
     * <p>v1 用两个小三角表示阶级，玩家看不出总共几档、当前在哪档。
     * 改成并排的方块后：<b>亮 = 可选，暗 = 未解锁，高亮 = 当前选中</b>。
     */
    public static void chip(GuiGraphics graphics, int x, int y, int w, int h,
                            boolean unlocked, boolean selected, boolean hovered) {
        int body;
        if (!unlocked) {
            body = 0xFF6E6E6E;
        } else if (selected) {
            body = 0xFF6FA86F;
        } else {
            body = hovered ? BUTTON_HOVER : PANEL;
        }
        graphics.fill(x, y, x + w, y + h, body);
        int light = !unlocked ? 0xFF8A8A8A : BEVEL_LIGHT;
        int dark = !unlocked ? 0xFF4A4A4A : BEVEL_DARK;
        graphics.fill(x, y, x + w, y + 1, light);
        graphics.fill(x, y, x + 1, y + h, light);
        graphics.fill(x + w - 1, y, x + w, y + h, dark);
        graphics.fill(x, y + h - 1, x + w, y + h, dark);
    }

    /**
     * 给<b>每一个</b>菜单槽位画凹陷底。
     *
     * <h2>⚠️ 自己画面板就必须自己画槽位</h2>
     *
     * <p>原版 {@code AbstractContainerScreen.renderBg} 会 blit 一张 GUI 贴图，
     * 而<b>槽位的凹陷底是画在那张贴图里的</b>。我们既然用代码画面板、
     * 不 blit 贴图，槽位就会变成「看不见的空格子」——物品放进去才显形，
     * 玩家根本不知道往哪放。这个 bug 源码里完全看不出来，只有截图才暴露。
     *
     * @param slots 菜单的全部槽位（含玩家背包），{@code Slot.x/y} 是相对面板的坐标
     */
    public static void slotBackgrounds(GuiGraphics graphics, java.util.List<net.minecraft.world.inventory.Slot> slots,
                                       int leftPos, int topPos) {
        for (net.minecraft.world.inventory.Slot slot : slots) {
            inset(graphics, leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18);
        }
    }

    /** 一条细分隔线（凹陷感），用于分区。 */
    public static void divider(GuiGraphics graphics, int x, int y, int w) {
        graphics.fill(x, y, x + w, y + 1, BEVEL_DARK);
        graphics.fill(x, y + 1, x + w, y + 2, BEVEL_LIGHT);
    }

    /** 把文本水平居中在 {@code [x, x+w)} 区间内。 */
    public static void centered(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                net.minecraft.network.chat.Component text,
                                int x, int y, int w, int color, boolean shadow) {
        graphics.drawString(font, text, x + (w - font.width(text)) / 2, y, color, shadow);
    }
}
