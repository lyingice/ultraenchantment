package com.lyingice.ultraenchantment.compat.apotheosis;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import org.slf4j.Logger;

/**
 * <b>神化那三类宝典的兼容入口</b>（拆解 / 高级拆解 / 提取，外加 9 种装备宝典的搬运）。
 *
 * <h2>要解决的到底是什么</h2>
 *
 * <p>神化的宝典只搬原版 {@code minecraft:enchantments} 组件，<b>不认我们的
 * {@code ultraenchantment:ascension}</b>。后果有两类，都会<b>静默吞掉玩家资产</b>：
 *
 * <ul>
 *   <li><b>产出端</b>：拆解/提取产出的是一本原版附魔书，进阶记录<b>不跟着走</b>
 *       —— 玩家以为「拆出来的书还是进阶的」，实际上进阶没了；</li>
 *   <li><b>输入端</b>：提取宝典的 {@code updateRepair} 会把武器的附魔清空再把武器还给你，
 *       但我们的进阶组件<b>留在武器上</b> ⇒ 「附魔没了、进阶还在」= <b>白送一次进阶</b>。</li>
 * </ul>
 *
 * <h2>为什么用事件后置，而不是 mixin 进他们的方法</h2>
 *
 * <p>他们的 {@code updateAnvil}/{@code updateRepair} 都是从 <b>同一批 NeoForge 事件</b>里
 * 被调用的。我们只要用 {@link EventPriority#LOW}（跑在他们之后）挂同一批事件，
 * 就能在原地做后置处理 —— <b>不需要碰他们的类结构，也不怕他们改内部实现</b>。
 *
 * <h2>类加载铁律</h2>
 *
 * <p>神化是 {@code compileOnly}。本类<b>只查模组清单</b>；真正 import 神化类的是
 * {@link ApothTomesImpl}，它只在 {@link ApothCaps#present()} 为真时才会被链接
 * （所以 {@code catch} 里必须带 {@link LinkageError}，见 AGENT.md P1-40）。
 */
public final class ApothTomes {
    private ApothTomes() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 单例监听器，供 game bus 注册。 */
    public static final ApothTomes INSTANCE = new ApothTomes();

    /** 铁砧「生成产出」阶段：把进阶记录跟着搬进产出书。 */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!ApothCaps.present()) {
            return;
        }
        try {
            ApothTomesImpl.onAnvilUpdate(event);
        } catch (RuntimeException | LinkageError t) {
            LOGGER.error("[UE] 神化宝典兼容（AnvilUpdate）失败", t);
        }
    }

    /** 铁砧「取件」阶段：清掉该清掉的、按方案 B 返还该返还的。 */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onAnvilRepair(AnvilRepairEvent event) {
        if (!ApothCaps.present()) {
            return;
        }
        try {
            ApothTomesImpl.onAnvilRepair(event);
        } catch (RuntimeException | LinkageError t) {
            LOGGER.error("[UE] 神化宝典兼容（AnvilRepair）失败", t);
        }
    }
}
