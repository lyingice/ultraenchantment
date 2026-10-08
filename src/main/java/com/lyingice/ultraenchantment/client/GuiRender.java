package com.lyingice.ultraenchantment.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * <b>界面绘制原语</b>——v4 起全部改为<b>贴图</b>，不再用 {@code fill} 程序画。
 *
 * <h2>为什么改成贴图</h2>
 *
 * <p>v2/v3 用 {@code fill} 画凸起/凹陷边框，能看但没有美术空间。
 * v4 把面板与控件拆成「背景 PNG + 精灵表」，美术可以整张重绘，代码一行不用改
 * （坐标表在 {@link GuiSprites}）。
 *
 * <h2>两类资源</h2>
 *
 * <ul>
 *   <li>{@link #background} —— 每屏一张，含面板边框、槽位凹陷、列表底、分隔线</li>
 *   <li>按钮 / 阶级方块 / 箭头 —— 三屏共用的精灵表 {@link GuiSprites#WIDGETS}</li>
 * </ul>
 *
 * <h2>仍然保留的颜色常量</h2>
 *
 * <p>文字颜色不属于贴图（文字是动态的），所以下面这些常量继续用。
 */
public final class GuiRender {
    private GuiRender() {}

    public static final int TEXT = 0x404040;
    public static final int TEXT_DIM = 0x707070;
    public static final int TEXT_OFF = 0xA0A0A0;
    public static final int TEXT_BAD = 0xA00000;
    public static final int TEXT_OK = 0x106010;
    public static final int TEXT_WHITE = 0xFFFFFF;

    /** 行悬停叠色。 */
    public static final int HOVER_TINT = 0x30FFFFFF;
    /** 行「已选中」叠色。 */
    public static final int SELECTED_TINT = 0x4080FF80;

    // ── 背景 ────────────────────────────────────────────────────────────

    /**
     * 贴一整张背景。尺寸必须与 PNG 完全一致（不缩放）。
     *
     * <p>⚠️ <b>必须用带「贴图尺寸」的重载。</b>简版 {@code blit(tex, x, y, u, v, w, h)}
     * 内部<b>假定贴图是 256×256</b>；我们的背景宽 260 / 340，超过 256 的部分
     * UV 会<b>回绕</b>，画面表现为「面板被重复平铺、中间一条竖缝」。
     * 这个 bug 在源码里完全看不出来，只有截图才暴露。
     *
     * <p>因为背景是 1:1 绘制的，贴图尺寸就等于绘制尺寸。
     */
    public static void background(GuiGraphics graphics, ResourceLocation texture,
                                  int x, int y, int w, int h) {
        graphics.blit(texture, x, y, 0, 0, w, h, w, h);
    }

    // ── 按钮 ────────────────────────────────────────────────────────────

    private static void sprite(GuiGraphics graphics, GuiSprites.Sprite sprite, int x, int y) {
        graphics.blit(sprite.texture(), x, y, sprite.u(), sprite.v(), sprite.w(), sprite.h());
    }

    /** 宽按钮（灌注）。 */
    public static void wideButton(GuiGraphics g, int x, int y, boolean hovered, boolean enabled) {
        sprite(g, !enabled ? GuiSprites.WIDE_OFF
                : (hovered ? GuiSprites.WIDE_HOVER : GuiSprites.WIDE_NORMAL), x, y);
    }

    /** 中按钮（图鉴）。 */
    public static void midButton(GuiGraphics g, int x, int y, boolean hovered, boolean enabled) {
        sprite(g, !enabled ? GuiSprites.MID_OFF
                : (hovered ? GuiSprites.MID_HOVER : GuiSprites.MID_NORMAL), x, y);
    }

    /** 小方按钮（等级 −/+）。 */
    public static void squareButton(GuiGraphics g, int x, int y, boolean hovered, boolean enabled) {
        sprite(g, !enabled ? GuiSprites.SQUARE_OFF
                : (hovered ? GuiSprites.SQUARE_HOVER : GuiSprites.SQUARE_NORMAL), x, y);
    }

    /** 微按钮（滚动）。 */
    public static void miniButton(GuiGraphics g, int x, int y, boolean hovered, boolean enabled) {
        sprite(g, !enabled ? GuiSprites.MINI_OFF
                : (hovered ? GuiSprites.MINI_HOVER : GuiSprites.MINI_NORMAL), x, y);
    }

    /**
     * 阶级方块。
     *
     * @param unlocked 数据包是否铺了这一档<b>且</b>图鉴已解锁
     * @param selected 是否是当前选中的那一档
     */
    public static void chip(GuiGraphics g, int x, int y, boolean unlocked, boolean selected, boolean hovered) {
        GuiSprites.Sprite s;
        if (!unlocked) {
            s = GuiSprites.CHIP_LOCKED;
        } else if (selected) {
            s = GuiSprites.CHIP_SELECTED;
        } else if (hovered) {
            s = GuiSprites.CHIP_HOVER;
        } else {
            s = GuiSprites.CHIP_AVAILABLE;
        }
        sprite(g, s, x, y);
    }

    /** 箭头。贴图是 9×5，按 12×12 的按钮居中放置。 */
    public static void arrow(GuiGraphics g, int buttonX, int buttonY, boolean up, boolean enabled) {
        GuiSprites.Sprite s = up
                ? (enabled ? GuiSprites.ARROW_UP : GuiSprites.ARROW_UP_OFF)
                : (enabled ? GuiSprites.ARROW_DOWN : GuiSprites.ARROW_DOWN_OFF);
        sprite(g, s, buttonX + 2, buttonY + 4);
    }

    // ── 文字 ────────────────────────────────────────────────────────────

    /** 把文本水平居中在 {@code [x, x+w)} 区间内。 */
    public static void centered(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                Component text, int x, int y, int w, int color, boolean shadow) {
        graphics.drawString(font, text, x + (w - font.width(text)) / 2, y, color, shadow);
    }
}
