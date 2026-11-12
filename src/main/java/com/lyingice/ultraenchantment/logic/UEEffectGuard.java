package com.lyingice.ultraenchantment.logic;

import com.lyingice.ultraenchantment.UEConfig;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * <b>扩展效果的重入保护</b>——防止真实伤害 / 反伤 / 换位互相触发成环。
 *
 * <h2>为什么必须有</h2>
 *
 * <p>这些效果之间天然会互相触发：
 * <ul>
 *   <li>真实伤害走 {@code setHealth}，但为了触发死亡流程仍会走一次 {@code hurt}
 *       → 又进入伤害监听器 → 又可能再算一次真实伤害；</li>
 *   <li>反伤会调用 {@code attacker.hurt(...)} → 攻击者身上的反伤又会打回来；</li>
 *   <li>荆棘（原版 {@code post_attack}）也会走 {@code damage_entity}，同样可能成环。</li>
 * </ul>
 *
 * <p>不加保护就是<b>栈溢出 / 服务器假死</b>，而且在正常游玩中就能触发（两个都穿反伤甲的玩家对砍）。
 *
 * <h2>机制</h2>
 *
 * <p>用<b>不可重入的重入锁</b>：进入某个效果前把「被打上标记的实体」记进当前线程的深度栈，
 * 若同一实体在本次链上已处于触发状态，就直接跳过二级触发。深度上限来自
 * {@link UEConfig#MAX_EFFECT_RECURSION}，即使标记逻辑有漏网，深度也不会无限增长。
 *
 * <p>用 {@link ThreadLocal} + {@link WeakHashMap}：一个实体一次只在一个线程上结算，
 * 且不会因为持有强引用而阻止实体回收。
 */
public final class UEEffectGuard {
    private UEEffectGuard() {}

    /** 当前线程正在被「扩展效果」处理的实体集合（含深度）。 */
    private static final ThreadLocal<WeakHashMap<Object, Integer>> ACTIVE =
            ThreadLocal.withInitial(WeakHashMap::new);

    /**
     * 尝试进入某个实体的扩展效果结算。
     *
     * @return {@code true} = 可以结算（调用方必须随后 {@link #exit}）；{@code false} = 已在链上，跳过
     */
    public static boolean enter(Object entity) {
        WeakHashMap<Object, Integer> active = ACTIVE.get();
        int depth = active.getOrDefault(entity, 0);
        if (depth >= UEConfig.MAX_EFFECT_RECURSION.get()) {
            return false;
        }
        active.put(entity, depth + 1);
        return true;
    }

    /** 退出某个实体的扩展效果结算；与 {@link #enter} 成对调用。 */
    public static void exit(Object entity) {
        WeakHashMap<Object, Integer> active = ACTIVE.get();
        Integer depth = active.get(entity);
        if (depth == null) {
            return;
        }
        if (depth <= 1) {
            active.remove(entity);
        } else {
            active.put(entity, depth - 1);
        }
    }

    /** 某个实体当前是否正处于扩展效果结算中。 */
    public static boolean isActive(Object entity) {
        return ACTIVE.get().containsKey(entity);
    }

    /**
     * 在重入保护下执行一段结算。
     *
     * <p>已在链上 / 超过深度上限时<b>不执行</b>并返回 {@code false}，
     * 调用方据此跳过后续动作（例如「这次不再反伤」）。
     */
    public static boolean run(Object entity, Runnable action) {
        if (!enter(entity)) {
            return false;
        }
        try {
            action.run();
            return true;
        } finally {
            exit(entity);
        }
    }

    /** 清空当前线程的记录（测试 / 异常兜底用）。 */
    public static void clear() {
        ACTIVE.get().clear();
    }

    /** 当前线程已记录的实体数（自检用）。 */
    public static int activeCount() {
        Set<Object> keys = ACTIVE.get().keySet();
        return keys.size();
    }
}
