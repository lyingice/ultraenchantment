package com.lyingice.ultraenchantment.api;

/**
 * 通过 NeoForge IMC <b>软注册</b>的三条通道。
 *
 * <p>载荷一律是 {@code String}——<b>这样对方不需要编译依赖本模组</b>，只要装了就能发：
 *
 * <pre>{@code
 * InterModComms.sendTo("ultraenchantment", UltraEnchantImc.CHANNEL_REGISTER,
 *         () -> "mymod:my_enchant;max_tier=2");
 * }</pre>
 *
 * <p>格式：分号分段，第一段是附魔 id，其余是 {@code key=value}。
 *
 * <h2>三条通道</h2>
 * <ul>
 *   <li>{@link #CHANNEL_REGISTER} —— 声明「这条附魔纳入 UE 体系」，可带
 *       {@link #KEY_MAX_TIER}（<b>收紧</b>上限：不能超过数据包里实际存在的阶级数）；</li>
 *   <li>{@link #CHANNEL_ATTRIBUTES} —— 任意自定义键值，供整合包/附属读取
 *       （例如 {@code "mymod:my_enchant;damage_tier_bonus=4"}）；</li>
 *   <li>{@link #CHANNEL_UPGRADE_COST} —— 覆盖升级书的花费，键为
 *       {@link #KEY_BASE} 与 {@link #KEY_PER_TIER}
 *       （实际花费 = base + per_tier × (目标等级 - 1)）。</li>
 * </ul>
 *
 * <h2>⚠️ 进阶效果仍然来自数据包</h2>
 *
 * <p>IMC 只做注册与调参：<b>每一阶的实际效果必须由数据包提供</b>
 * （{@code data/<你的命名空间>/ultraenchantment/enchantment/<阶级>/<附魔>.json}）。
 * 只注册而没带数据包，这条附魔不会获得任何进阶效果，加载时会留下一条 WARN。
 *
 * <p>坏格式、未知键、非法数值一律 <b>WARN 并忽略</b>，不影响其它注册。
 */
public final class UltraEnchantImc {
    private UltraEnchantImc() {}

    /** 声明附魔纳入 UE 体系。 */
    public static final String CHANNEL_REGISTER = "register_enchant";
    /** 自定义键值（供整合包读取）。 */
    public static final String CHANNEL_ATTRIBUTES = "tier_attributes";
    /** 覆盖升级书花费。 */
    public static final String CHANNEL_UPGRADE_COST = "upgrade_cost";

    /** 上限（收紧用，1..3）。 */
    public static final String KEY_MAX_TIER = "max_tier";
    /** 升级花费的固定部分。 */
    public static final String KEY_BASE = "base";
    /** 升级花费的每级增量。 */
    public static final String KEY_PER_TIER = "per_tier";
}
