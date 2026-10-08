package com.lyingice.ultraenchantment.logic;

import com.lyingice.ultraenchantment.content.LineageTier;

/**
 * <b>「钥匙书」的唯一判定</b>（v5）。
 *
 * <h2>为什么单开一个类</h2>
 *
 * <p>v5 把升阶的代价从「阶级单位点数」换成了「消耗一本对应阶级的进阶书」。
 * 于是「这一行现在能不能升」这个判定出现在<b>两个必须一致的地方</b>：
 * GUI 的「阶耗」列（说「1 本」还是「缺书」）与 {@code AscensionTableMenu.apply} 的实际消耗。
 * 两边各写一份就会出现「显示有书、点了却失败」——所以判定只准有这一份实现。
 *
 * <h2>消耗顺序：先定向、后通用</h2>
 *
 * <p>定向书只能用于它钉住的那条谱系，通用书能用于任意谱系。所以玩家留通用书去应付别的谱系
 * 永远更划算 —— 这是玩家的最优策略，也是我们的默认行为，不需要玩家手选。
 * {@link #hasUsable} 与实际消耗循环都按这个顺序。
 */
public final class KeyBooks {
    private KeyBooks() {}

    /**
     * 从 {@code fromOrdinal} 换到 {@code toOrdinal} 需要几把钥匙。<b>0 = 不需要</b>。
     *
     * <h2>方案 B（作者 2026-10 拍板）：跨几档收几本</h2>
     *
     * <pre>
     *   基础 → 高阶   = 1 - 0 = 1 本
     *   高阶 → 超级   = 2 - 1 = 1 本
     *   基础 → 究极   = 3 - 0 = 3 本     ← 与「一步步走」总消耗相同
     *   究极 → 高阶   = 0 本（往低阶不收费）
     *   同阶级提级     = 0 本（那不是升阶）
     * </pre>
     *
     * <p><b>为什么不是「一次只收一本」</b>：那样「基础 → 究极」只花 1 本究极书，
     * 而逐级走要 3 本 —— 这不是「便宜一点」，是<b>结构性的定价倒挂</b>。
     * v4 靠一条手写的「完整路径定价」公式堵这个洞；v5 删掉那套公式之后，
     * 唯一还能正确表达同一件事的东西就是<b>数量</b>：
     * 「完整路径」＝「把那几档的书都付了」。
     *
     * <p><b>也不是「禁止跳阶」</b>：那会把「基础 → 究极」这条合法路径整个砍掉，
     * 是改玩法而不是修定价，而且与 {@code next} 链允许跳过的语义冲突。
     *
     * <h2>⚠️ 这里是「收几本」的唯一开关</h2>
     *
     * <p>GUI 的「N 本 / 缺 N 本」与实际扣减都按<b>数量</b>处理
     * （{@link #hasEnough} 与 {@code AscensionTableMenu.consumeKeyBooks}），
     * 所以改策略只动这一行。
     *
     * <p><b>边界</b>：只有 1 本究极书时从基础跳究极会<b>失败</b>（需要 3 本）——
     * 这是对的：玩家要么攒够 3 本一次跳，要么逐级走（各 1 本，也是 3 本）。<b>两条路等价。</b>
     */
    public static int required(int fromOrdinal, int toOrdinal) {
        return Math.max(0, toOrdinal - fromOrdinal);
    }

    /**
     * 手上/库里共有几把可用的钥匙。
     *
     * <p>定向与通用<b>可以互相顶替</b>（只是优先用定向），所以可用总数就是两者之和。
     *
     * @param targetedBooks 环内图书馆里<b>该谱系</b>的定向书总数
     * @param genericBooks  环内图书馆里的通用书总数
     */
    public static int available(int targetedBooks, int genericBooks) {
        return Math.max(0, targetedBooks) + Math.max(0, genericBooks);
    }

    /**
     * 够不够换这一档。
     *
     * <p><b>显示与消耗的唯一判据</b>：GUI 的「1 本 / 缺书」、灌注前校验、
     * 实际扣减三处都必须走它，否则会出现「显示有书、点了失败」。
     */
    public static boolean hasEnough(int targetedBooks, int genericBooks, int need) {
        return available(targetedBooks, genericBooks) >= need;
    }
}
