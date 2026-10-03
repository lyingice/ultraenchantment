package com.lyingice.ultraenchantment.compat.tooltip;

import com.lyingice.ultraenchantment.Ultraenchantment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * 「提示框渲染栈」（传说提示框 Legendary Tooltips + Iceberg + Prism）的<b>唯一接入点</b>。
 *
 * <h2>为什么必须有这一层</h2>
 *
 * <p>这三个模组都不在公共 maven 上，构建时以 {@code libs/} 里的本地 jar 参与
 * （{@code compileOnly + runtimeOnly}）——<b>它们永远不会随我们的 jar 发布</b>，
 * 玩家装不装完全自愿（{@code neoforge.mods.toml} 里声明为 {@code optional}）。
 *
 * <p>于是有一条铁律：<b>引用对方类的代码，只在「确认对方装了」之后才允许被加载。</b>
 * 否则 JVM 在链接/校验那些类时会抛 {@code NoClassDefFoundError}——
 * 它在服务端、在没装的玩家那里都会炸，而且报错跟我们的功能毫无关系，极难排查（AGENT.md P0-16）。
 *
 * <h2>怎么用</h2>
 *
 * <pre>{@code
 * if (TooltipStackCompat.present()) {
 *     TooltipStackHooks.something(...);   // 真正 import 了对方类的调用写在这里
 * }
 * }</pre>
 *
 * <p>{@link #present()} 用的是 {@code ModList.isLoaded}，<b>不是</b> {@code Class.forName}：
 * 前者只查模组清单，不触发类加载，也不会因为对方改名而抛异常。
 */
public final class TooltipStackCompat {

    /** 传说提示框的 modid（Iceberg / Prism 是它的硬依赖，装了它必然也在）。 */
    public static final String LEGENDARY_TOOLTIPS = "legendarytooltips";

    /** Prism 的 modid（渐变色库）。 */
    public static final String PRISM = "prism";

    private static boolean initialised;

    private TooltipStackCompat() {
    }

    /** 传说提示框在不在？（只查模组清单，不触发任何类加载） */
    public static boolean present() {
        return ModList.get().isLoaded(LEGENDARY_TOOLTIPS);
    }

    /** Prism 在不在？（渐变色的提供者；传说提示框依赖它，但也允许只装它） */
    public static boolean prismPresent() {
        return ModList.get().isLoaded(PRISM);
    }

    /**
     * 能不能用 Prism 的渐变色？
     *
     * <p>两个条件都要满足：
     * <ul>
     *   <li><b>逻辑客户端</b>——硬要求。Prism 是客户端库（它的 mixin 配置写在 `client` 段，
     *       且 {@code DynamicColor} 的签名里带 {@code net.minecraft.client.DeltaTracker}），
     *       在专用服务端加载它会 {@code NoClassDefFoundError}。集成服务器的 Dist 是 CLIENT，不受影响。</li>
     *   <li>Prism 已安装——没装就退化成原来的静态配色。</li>
     * </ul>
     */
    public static boolean usePrismGradient() {
        return FMLEnvironment.dist == Dist.CLIENT && prismPresent();
    }

    /**
     * 要不要在本模组这侧修正传说提示框算错的攻击伤害行？
     *
     * <p>同样只认**逻辑客户端**（对方是客户端模组），并且**只在它装了**的时候做——
     * 没装时 tooltip 保持原样，一个数字都不动。
     */
    public static boolean useLegendaryTooltipsFix() {
        return FMLEnvironment.dist == Dist.CLIENT && present();
    }

    /**
     * 客户端启动时调用一次。
     *
     * <p>没装、或跑在服务端时**原样返回**，绝不触碰对方任何类型。
     */
    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;

        if (FMLEnvironment.dist != Dist.CLIENT) {
            Ultraenchantment.LOGGER.debug("[TooltipStack] 非客户端，跳过");
            return;
        }
        if (!present()) {
            Ultraenchantment.LOGGER.info("[TooltipStack] 未安装传说提示框：跳过（不影响任何功能）");
            return;
        }

        Ultraenchantment.LOGGER.info("[TooltipStack] 检测到传说提示框（Iceberg / Prism 随之可用）");
        // 下一步的接入代码写在这里，例如：
        //     TooltipStackHooks.registerBorderColors(...);
        // ⚠️ 被调用的那个类里才允许出现对方的 import——只有「装了」的分支才会类加载它。
    }
}
