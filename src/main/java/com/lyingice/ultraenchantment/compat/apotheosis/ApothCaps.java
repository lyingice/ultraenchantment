package com.lyingice.ultraenchantment.compat.apotheosis;

import com.lyingice.ultraenchantment.Ultraenchantment;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.fml.ModList;

/**
 * <b>神化（Apotheosis / Apothic Enchanting）联动的唯一接入点。</b>
 *
 * <h2>为什么必须有这一层</h2>
 *
 * <p>神化在构建里是 {@code compileOnly}——<b>永远不会随我们的 jar 发布</b>，
 * 玩家装不装完全自愿。于是有铁律：<b>引用对方类的代码，只在「确认对方装了」之后才允许被加载</b>，
 * 否则 JVM 链接那些类时会抛 {@code NoClassDefFoundError}（AGENT.md P0-16 / P1-40）。
 *
 * <p>本类<b>只查模组清单</b>（{@code ModList.isLoaded}，不触发类加载），
 * 真正 import 神化类的代码全在 {@link ApothCapsImpl} 里——只有 {@link #present()} 为真时才会被链接。
 *
 * <h2>神化扩展了什么（源码实证）</h2>
 *
 * <p>它用 coremod（{@code coremods/ench/ench_info_redirector.js}）把一批类里的
 * {@code Enchantment.getMaxLevel()} 调用重定向到 {@code EnchHooks.getMaxLevel()}，
 * 于是原版附魔的上限被抬到它的配置值（默认上限 127）。
 *
 * <p>实测日志（runServer）可见：
 * <pre>
 * [COREMODLOG]: Replaced 2 calls to Enchantment#getMaxLevel() in ...
 * </pre>
 *
 * <p><b>关键：它是按类白名单重定向的，我们自己的类不在名单里</b>——
 * 我们直接调 {@code root.value().getMaxLevel()} 拿到的仍是原版值。
 * 所以想跟随它的上限，必须主动去读（见 {@link #vanillaCapOf}）。
 */
public final class ApothCaps {

    /** Apothic Enchanting 的 modid（上限与附魔行渲染都在它里面；Apotheosis 本体是它的前置方向相反）。 */
    public static final String APOTHIC_ENCHANTING = "apothic_enchanting";

    /** 神化本体的 modid（用于门控「装了神化全家桶」）。 */
    public static final String APOTHEOSIS = "apotheosis";

    private ApothCaps() {
    }

    /**
     * 神化在不在？（只查模组清单，不触发任何类加载）
     *
     * <p>只认 {@code apothic_enchanting}：附魔上限与附魔行渲染都由它提供，
     * 而 Apotheosis 本体依赖它，所以装了本体必然也在。
     */
    public static boolean present() {
        return ModList.get().isLoaded(APOTHIC_ENCHANTING);
    }

    /**
     * 神化给这条附魔的上限；<b>神化缺席、或读取失败时返回 {@code fallback}</b>。
     *
     * <p>这是 B1 语义的入口：神化<b>只抬高「可到达的等级」</b>，
     * 不改变数据包里阶段条目的 {@code max_level}，也不改数值曲线——
     * 超出的部分按数据包的 {@code Linear} 自然外推。
     *
     * <p>⚠️ 读取全程必须包在 try/catch 里：{@code ApothicEnchanting.getEnchInfo} 在
     * 「附魔配置尚未加载」时会**抛 {@code UnsupportedOperationException}**（源码第 198 行），
     * 那会在数据包加载竞态里炸出来。宁可返回 fallback。
     *
     * @param root     谱系根源附魔
     * @param fallback 神化不可用时的原值（通常是数据包/原版的上限）
     * @return 神化上限与 fallback 的较大值
     */
    public static int vanillaCapOf(Holder<Enchantment> root, int fallback) {
        if (!present()) {
            return fallback;
        }
        try {
            return Math.max(fallback, ApothCapsImpl.capOf(root));
        } catch (Throwable t) {
            // 配置未加载 / API 变动 / 任何意外：退回原值，绝不因为联动失败影响主功能。
            Ultraenchantment.LOGGER.debug("[Apoth] 读取附魔上限失败，退回 {}：{}", fallback, t.toString());
            return fallback;
        }
    }
}
