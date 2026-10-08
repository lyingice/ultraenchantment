package com.lyingice.ultraenchantment.client;

import com.lyingice.ultraenchantment.Ultraenchantment;
import net.minecraft.resources.ResourceLocation;

/**
 * <b>GUI 贴图与精灵表的坐标表</b>——美术只需替换 PNG，不必改代码。
 *
 * <h2>两类资源</h2>
 *
 * <ul>
 *   <li><b>背景</b>（每屏一张，尺寸 = 面板尺寸）：
 *       {@link #ASCENSION_TABLE} 340×236、{@link #ENCHANTMENT_LIBRARY} 260×236、
 *       {@link #CODEX} 260×204。槽位凹陷、列表底、分隔线都<b>烘焙在背景里</b>——
 *       这是原版 GUI 的惯例，也让美术能整体掌控观感。</li>
 *   <li><b>精灵图</b>（三屏共用一张）：{@link #WIDGETS} 256×256，
 *       按钮 / 阶级方块 / 箭头。每个精灵的 UV 见下方常量。</li>
 * </ul>
 *
 * <h2>⚠️ 改布局就要重画背景</h2>
 *
 * <p>槽位位置烘焙在背景里 ⇒ 若移动槽位，必须同步改背景 PNG 与
 * {@code *Menu} 里的槽位坐标。两处必须一致，否则物品会画在错误的位置。
 *
 * <p>详细布局见 {@code docs/gui-textures.md}；占位图由
 * {@code tools/gen-gui-textures.py} 生成。
 */
public final class GuiSprites {
    private GuiSprites() {}

    private static final String DIR = "textures/gui/";

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Ultraenchantment.MODID, DIR + name);
    }

    // ── 背景 ────────────────────────────────────────────────────────────
    public static final ResourceLocation ASCENSION_TABLE = tex("ascension_table.png");
    public static final ResourceLocation ENCHANTMENT_LIBRARY = tex("advanced_enchantment_library.png");
    public static final ResourceLocation CODEX = tex("codex.png");

    // ── 精灵图 ──────────────────────────────────────────────────────────
    public static final ResourceLocation WIDGETS = tex("widgets.png");
    /** 精灵图边长（{@code blit} 的贴图尺寸参数）。 */
    public static final int SHEET = 256;

    /** 精灵矩形。 */
    public record Sprite(ResourceLocation texture, int u, int v, int w, int h) {}

    private static Sprite widget(int u, int v, int w, int h) {
        return new Sprite(WIDGETS, u, v, w, h);
    }

    // A. 宽按钮（灌注）100×16，三态垂直堆叠
    public static final Sprite WIDE_NORMAL = widget(0, 0, 100, 16);
    public static final Sprite WIDE_HOVER = widget(0, 18, 100, 16);
    public static final Sprite WIDE_OFF = widget(0, 36, 100, 16);

    // B. 中按钮（图鉴）30×14
    public static final Sprite MID_NORMAL = widget(0, 56, 30, 14);
    public static final Sprite MID_HOVER = widget(0, 72, 30, 14);
    public static final Sprite MID_OFF = widget(0, 88, 30, 14);

    // C. 小方按钮（等级 −/+）14×14，三态横向排列
    public static final Sprite SQUARE_NORMAL = widget(0, 104, 14, 14);
    public static final Sprite SQUARE_HOVER = widget(16, 104, 14, 14);
    public static final Sprite SQUARE_OFF = widget(32, 104, 14, 14);

    // D. 微按钮（滚动）12×12
    public static final Sprite MINI_NORMAL = widget(0, 120, 12, 12);
    public static final Sprite MINI_HOVER = widget(14, 120, 12, 12);
    public static final Sprite MINI_OFF = widget(28, 120, 12, 12);

    // E. 阶级方块 26×16，四态横向：锁 / 可选 / 选中 / 悬停
    public static final Sprite CHIP_LOCKED = widget(0, 136, 26, 16);
    public static final Sprite CHIP_AVAILABLE = widget(28, 136, 26, 16);
    public static final Sprite CHIP_SELECTED = widget(56, 136, 26, 16);
    public static final Sprite CHIP_HOVER = widget(84, 136, 26, 16);

    // F. 箭头 9×5，四态纵向：上常态 / 上禁用 / 下常态 / 下禁用
    public static final Sprite ARROW_UP = widget(0, 154, 9, 5);
    public static final Sprite ARROW_UP_OFF = widget(0, 161, 9, 5);
    public static final Sprite ARROW_DOWN = widget(0, 168, 9, 5);
    public static final Sprite ARROW_DOWN_OFF = widget(0, 175, 9, 5);
}
