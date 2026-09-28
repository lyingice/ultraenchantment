package com.lyingice.ultraenchantment.event;

import com.lyingice.ultraenchantment.content.AscensionTier;
import com.lyingice.ultraenchantment.content.StageDefinition;
import com.lyingice.ultraenchantment.logic.EnchantmentFactory;
import com.lyingice.ultraenchantment.logic.StageLookup;
import com.lyingice.ultraenchantment.registry.UERegistries;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/**
 * 数据包重载后的缓存维护。
 *
 * <h2>为什么要缓存数据包内容</h2>
 *
 * <p>创造栏（{@link CreativeTabEvents}）是 <b>mod bus</b> 事件，在物品注册期就会触发；
 * 而阶段条目住在<b>数据包注册表</b>里，其内容随数据包而定、且可能在创造栏构建时尚未就绪。
 *
 * <p>因此凡「数据包派生、创造栏要用」的值，都在数据包加载后<b>算一次、存下来</b>，
 * 创造栏只读缓存。（见 AGENT.md P1-21）
 *
 * <h2>两个缓存</h2>
 *
 * <ol>
 *   <li>{@link #globalMaxLevel()} —— 所有阶段条目 {@code max_level} 的最大值，
 *       决定升级书/铭刻书展开到第几级</li>
 *   <li>{@link #lineages()} —— <b>数据包里实际存在哪些谱系</b>（{@code root} 去重后的集合），
 *       决定要生成哪些铭刻书、哪些定向进阶书</li>
 * </ol>
 *
 * <p>第 2 项是关键：**数据包是事实源，创造栏只是它的视图**。
 * 把「有哪些谱系」硬编码成常量会导致新增谱系后创造栏不跟随——
 * 必须动态枚举。
 *
 * <p>选 {@link TagsUpdatedEvent} 而非 {@code AddReloadListenerEvent}：后者要求注册一个
 * 真正的 {@code PreparableReloadListener}（还要处理 prepare/apply 两阶段），
 * 对「扫一遍表存两个值」来说过重。{@code TagsUpdatedEvent} 在每次数据包重载后触发，正合适。
 */
public final class ReloadEvents {
    private ReloadEvents() {}

    /** 单例监听器，供 game bus 注册。 */
    public static final ReloadEvents INSTANCE = new ReloadEvents();

    /** 兜底等级上限：数据包不可用时的展开高度。 */
    public static final int DEFAULT_MAX_LEVEL = 5;

    /**
     * 全局等级上限——数据包里所有阶段条目 {@code max_level} 的最大值。
     *
     * <p>数据包从未加载过（如纯客户端首次进入）时保持兜底值，
     * 保证创造栏至少能显示 1..默认值 的条目，不会因为空表而一片空白。
     */
    private static volatile int globalMaxLevel = DEFAULT_MAX_LEVEL;

    /**
     * 数据包里实际存在的阶段条目矩阵：
     * {@code root（原版附魔）→ { 阶级 → 该条目的等级上限 }}。
     *
     * <p><b>为什么记「每一格的等级上限」而不是「有哪些阶级」</b>：
     * 等级上限是<b>逐谱系</b>的（AGENT.md P1-28）——耐久 3、保护 4、锋利 5
     * 各自沿用其根源附魔的上限。创造栏展开铭刻书时需要知道
     * 「这条谱系这个阶级能到几级」，用全局最大值会产出永远拿不到的条目。
     *
     * <p>用 {@code EnumMap} 保证迭代顺序稳定（枚举序）。
     */
    private static volatile Map<ResourceLocation, Map<AscensionTier, Integer>> matrix = Map.of();

    /**
     * 数据包重载后重扫。
     *
     * <h2>⚠️ 必须用事件自带的 {@code RegistryAccess}，不能用 {@link StageLookup#lookup()}</h2>
     *
     * <p>本事件在 {@code ReloadableServerResources.updateRegistryTags} 里触发，
     * 而那一刻 <b>{@code MinecraftServer} 实例还没被赋给
     * {@code ServerLifecycleHooks.currentServer}</b>。{@code CommonHooks.resolveLookup}
     * 的第一句就是 {@code ServerLifecycleHooks.getCurrentServer()}，于是恒为 {@code null}，
     * {@link StageLookup#lookup()} 跟着返回 {@code null}，刷新被静默跳过——
     * <b>创造栏的谱系矩阵永远是空的</b>（专用服务器上实测：{@code matrix.size = 0}）。
     *
     * <p>而事件本身就把「刚绑定完标签的那份注册表」端上来了：
     * {@link TagsUpdatedEvent#getRegistryAccess()}。用它才对得上时间点，
     * 也同时覆盖服务端（{@code SERVER_DATA_LOAD}）与客户端（{@code CLIENT_PACKET_RECEIVED}）两条路径。
     */
    @SubscribeEvent
    public void onTagsUpdated(TagsUpdatedEvent event) {
        EnchantmentFactory.invalidate();
        refresh(event.getRegistryAccess().lookup(UERegistries.STAGE).orElse(null));
    }

    /**
     * 重扫数据包，刷新全部缓存。
     *
     * <p>没有事件在手时的兜底入口——走 {@link StageLookup#lookup()} 现场解析注册表。
     * <b>只在服务器/客户端都已就绪之后才可用</b>，数据包重载那一刻请用
     * {@link #onTagsUpdated} 里那条路径。
     */
    public static void refresh() {
        refresh(StageLookup.lookup());
    }

    /** 重扫数据包，刷新全部缓存。{@code lookup} 为 {@code null} 时保持旧值不动。 */
    public static void refresh(HolderLookup.RegistryLookup<StageDefinition> lookup) {
        if (lookup == null) {
            // 注册表尚未建立：保持旧值不动，宁可显示上一次的已知值。
            return;
        }

        int maxLevel = 0;
        Map<ResourceLocation, Map<AscensionTier, Integer>> found = new LinkedHashMap<>();

        for (Holder.Reference<StageDefinition> holder : lookup.listElements().toList()) {
            StageDefinition stage = holder.value();

            maxLevel = Math.max(maxLevel, stage.definition().maxLevel());

            // 原生阶不是阶段条目，因此 AscensionTier.of 必然有值；
            // 兜底跳过而非抛异常——异常数据不会通过注册表加载，这里只为健壮性。
            AscensionTier tier = AscensionTier.of(stage.tier()).orElse(null);
            if (tier == null) {
                continue;
            }
            found.computeIfAbsent(stage.root(), k -> new EnumMap<>(AscensionTier.class))
                    .merge(tier, stage.definition().maxLevel(), Math::max);
        }

        globalMaxLevel = maxLevel > 0 ? maxLevel : DEFAULT_MAX_LEVEL;

        // 冻结成不可变嵌套结构。
        Map<ResourceLocation, Map<AscensionTier, Integer>> frozen = new LinkedHashMap<>();
        found.forEach((root, tiers) -> frozen.put(root, Map.copyOf(tiers)));
        matrix = Map.copyOf(frozen);
    }

    /** 当前全局等级上限（≥1）。这是所有谱系中的最大值，仅供兜底用。 */
    public static int globalMaxLevel() {
        return Math.max(1, globalMaxLevel);
    }

    /**
     * 数据包里实际存在的阶段条目矩阵：{@code root → { 阶级 → 等级上限 }}。
     *
     * <p>创造栏据此生成铭刻书与定向进阶书；数据包新增谱系后自动跟随。
     */
    public static Map<ResourceLocation, Map<AscensionTier, Integer>> matrix() {
        return matrix;
    }

    /** 谱系清单（按 id 字典序），供创造栏按稳定顺序迭代。 */
    public static List<ResourceLocation> roots() {
        return matrix.keySet().stream().sorted().toList();
    }

    /**
     * 查某条谱系是否定义了某个阶级的阶段条目。
     *
     * <p>创造栏据此决定要不要为「该阶级 × 该谱系」生成条目——
     * 数据包没铺的阶级不该产出对应的书。
     */
    public static boolean hasTier(ResourceLocation root, AscensionTier tier) {
        Map<AscensionTier, Integer> tiers = matrix.get(root);
        return tiers != null && tiers.containsKey(tier);
    }

    /**
     * 取某条谱系某个阶级的等级上限——<b>逐谱系</b>，不是全局最大值。
     *
     * <p>创造栏展开铭刻书时用这个值，保证只产出真能写入的等级。
     *
     * @return 该格的等级上限；该格不存在时返回 {@code fallback}
     */
    public static int maxLevelOf(ResourceLocation root, AscensionTier tier, int fallback) {
        Map<AscensionTier, Integer> tiers = matrix.get(root);
        if (tiers == null) {
            return fallback;
        }
        Integer max = tiers.get(tier);
        return max == null ? fallback : max;
    }
}
