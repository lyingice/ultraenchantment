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
        String text = source.getString();
        List<DynamicColor> colors = palette();
        MutableComponent out = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            TextColor color = colors.get(i % colors.size());
            out.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(source.getStyle().withColor(color)));
        }
        return out;
    }

    /** 第 index 档颜色（取模循环），供自检与将来的其它接入点使用。 */
    public static TextColor colorAt(int index) {
        List<DynamicColor> colors = palette();
        return colors.get(Math.floorMod(index, colors.size()));
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
