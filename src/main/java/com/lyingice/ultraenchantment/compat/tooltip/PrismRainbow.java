package com.lyingice.ultraenchantment.compat.tooltip;

import com.anthonyhilyard.prism.text.DynamicColor;
import com.anthonyhilyard.prism.util.IColor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;

/**
 * 究极阶级的「渐变彩虹」——用 <b>Prism</b> 的 {@link DynamicColor} 实现。
 *
 * <p><b>软联动</b>：本类只在「逻辑客户端 + Prism 已安装」时才会被类加载
 * （调用点见 {@link TooltipStackCompat#usePrismGradient()}）；没装 Prism 时它一个字节都不会被解析，
 * 玩家也不会因此崩（AGENT.md P1-40 的类加载隔离铁律）。
 *
 * <h2>为什么是「每档一个 DynamicColor」而不是每帧现算</h2>
 *
 * <p>{@code DynamicColor} 的动画状态（当前相位）<b>在实例里</b>，由 Prism 的客户端 mixin 推进。
 * tooltip 每帧都会重新构建组件，所以颜色实例必须<b>长期复用</b>——
 * 每帧 new 一个会把动画时钟重置，看起来就是死的。
 * 因此这里在首次使用时建好整条色带并缓存。
 *
 * <h2>为什么每档的色环要「错开相位」</h2>
 *
 * <p>如果每档都是同一份色环，整行只会一起变色（呼吸感）；把第 i 档的色环整体旋转 i 格，
 * 整行就呈现**渐变**，且随时间向同一方向流动——就是常见的彩虹文字。
 */
public final class PrismRainbow {

    /** 一条 tooltip 行同时需要的最大色阶数（也是同时存在的 DynamicColor 实例数）。 */
    private static final int STEPS = 24;

    /** 走完一整圈彩虹所需的秒数。 */
    private static final float DURATION_SECONDS = 4.0f;

    /**
     * 走完一整圈<b>超限流体渐变</b>所需的秒数。
     *
     * <p>比彩虹快——作者反馈超限那几行的流动「太慢」，看着像静止的。
     * 单独一个常量而不是改 {@link #DURATION_SECONDS}：那个还管着
     * 「没装神化时究极阶的彩虹」，不该一起变。
     */
    private static final float FLOW_DURATION_SECONDS = 1.0f;

    /** 饱和度 / 明度：亮而不刺眼，白色 tooltip 背景上也读得清。 */
    private static final float SATURATION = 0.85f;
    private static final float VALUE = 1.0f;

    /** 懒建、长期复用：动画状态在这些实例里。 */
    private static List<DynamicColor> palette;

    private PrismRainbow() {
    }

    /**
     * 把整行渲染成逐字渐变彩虹：保留原样式，只把颜色换成色带上的一档。
     *
     * @param source 已经构建好的附魔行（文本为本地化后的最终文字）
     */
    public static Component apply(Component source) {
        return colorize(source, palette());
    }

    /** 第 index 档颜色（取模循环），供自检与将来的其它接入点使用。 */
    public static TextColor colorAt(int index) {
        List<DynamicColor> colors = palette();
        return colors.get(Math.floorMod(index, colors.size()));
    }

    // ── 深浅双色「流体渐变」（超限阶级配色） ────────────────────────────

    /** 每条色带的档数。 */
    private static final int DUAL_STEPS = 24;

    /** 双色色带缓存：{@code (浅色, 深色) → 色带}。必须缓存——见 {@link #colorize} 上方说明。 */
    private static final java.util.Map<Long, List<DynamicColor>> DUAL_CACHE = new java.util.HashMap<>();

    /**
     * 把整行渲染成「浅色 ⇄ 深色」的<b>流体渐变</b>。
     *
     * <p>用于<b>超过数据包定义等级</b>的阶级配色（作者规格）：
     * <pre>
     *   高阶  蓝       ⇄ 深蓝
     *   超级  淡紫     ⇄ 深紫
     *   究极  橙       ⇄ 红
     * </pre>
     *
     * <p>「流体」= 每一档的色带整体平移一格的经典做法（本类主色环同款），
     * 整行呈逐字渐变并随时间流动。
     *
     * <p>色带做成<b>去-回闭合环</b>（浅→深→浅），循环播放时颜色不会跳变。
     */
    public static Component applyFlow(Component source, int lightRgb, int darkRgb) {
        return colorize(source, dualPalette(lightRgb, darkRgb));
    }

    private static List<DynamicColor> dualPalette(int lightRgb, int darkRgb) {
        long key = ((long) lightRgb << 32) | (darkRgb & 0xFFFFFFFFL);
        return DUAL_CACHE.computeIfAbsent(key, k -> {
            // 去-回闭合环：浅 → 深 → 浅
            int half = DUAL_STEPS / 2;
            final List<IColor> ring = new ArrayList<>(DUAL_STEPS);
            for (int j = 0; j < half; j++) {
                ring.add(lerp(lightRgb, darkRgb, (float) j / (half - 1)));
            }
            for (int j = half - 2; j >= 1; j--) {
                ring.add(lerp(lightRgb, darkRgb, (float) j / (half - 1)));
            }

            // 第 step 档整体平移 step 格 → 整行逐字渐变且随时间流动（「流体」）。
            //
            // ⚠️ 取模必须用 ring.size()，不能用 DUAL_STEPS：
            //    去-回闭合环的元素数是 half + (half - 2) = DUAL_STEPS - 2（24 档时为 22），
            //    按 DUAL_STEPS 取模会 IndexOutOfBounds——实测崩在
            //    「Index 22 out of bounds for length 22」（客户端渲染 tooltip 时整屏崩掉）。
            int ringSize = ring.size();
            List<DynamicColor> built = new ArrayList<>(DUAL_STEPS);
            for (int step = 0; step < DUAL_STEPS; step++) {
                List<IColor> shifted = new ArrayList<>(ringSize);
                for (int j = 0; j < ringSize; j++) {
                    shifted.add(ring.get((step + j) % ringSize));
                }
                built.add(new DynamicColor(shifted, FLOW_DURATION_SECONDS));
            }
            return List.copyOf(built);
        });
    }

    /** 两个 RGB 之间线性插值。 */
    private static IColor lerp(int from, int to, float t) {
        int r = (int) (((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t));
        int g = (int) (((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t));
        int b = (int) ((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * t));
        return DynamicColor.fromRGB(Mth.clamp(r, 0, 255), Mth.clamp(g, 0, 255), Mth.clamp(b, 0, 255));
    }

    /**
     * 按<b>码点</b>逐「字」染色——<b>不能用 {@code charAt} 遍历</b>。
     *
     * <h2>⚠️ 代理对必须整体处理（实测踩到）</h2>
     *
     * <p>神化的超限星标是 {@code 🌟 U+1F31F}，在 UTF-16 里是<b>代理对</b>
     * （两个 {@code char}：{@code U+D83C} + {@code U+DF1F}）。
     * 早先这里按 {@code charAt(i)} 逐字拆分，于是星标被<b>劈成两半</b>，
     * 两个孤立代理各自渲染成无效字形——实机看起来就是
     * 「<b>两个不知名的字符</b>」，而神化自己渲染的那行（没走渐变）星标正常。
     *
     * <p>改用 {@link String#codePoints()}：一个码点 = 一个「字」，
     * 代理对天然整体保留，emoji 与 CJK 都不会被拆。
     */
    private static Component colorize(Component source, List<DynamicColor> colors) {
        String text = source.getString();
        MutableComponent out = Component.empty();
        int index = 0;
        for (int offset = 0; offset < text.length(); ) {
            int cp = text.codePointAt(offset);
            offset += Character.charCount(cp);
            TextColor color = colors.get(index % colors.size());
            index++;
            out.append(Component.literal(new String(Character.toChars(cp)))
                    .withStyle(source.getStyle().withColor(color)));
        }
        return out;
    }



    private static List<DynamicColor> palette() {
        if (palette == null) {
            List<DynamicColor> built = new ArrayList<>(STEPS);
            for (int step = 0; step < STEPS; step++) {
                List<IColor> ring = new ArrayList<>(STEPS);
                for (int k = 0; k < STEPS; k++) {
                    ring.add(hue((float) (step + k) * 360.0f / STEPS));
                }
                built.add(new DynamicColor(ring, DURATION_SECONDS));
            }
            palette = List.copyOf(built);
        }
        return palette;
    }

    /** 一个固定色相的颜色（Prism 的 HSV 入参单位不明，这里用原版 Mth 算 RGB，稳）。 */
    private static IColor hue(float degrees) {
        int rgb = Mth.hsvToRgb(degrees / 360.0f, SATURATION, VALUE);
        return DynamicColor.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }
}
