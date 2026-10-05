package com.lyingice.ultraenchantment.api;

import com.lyingice.ultraenchantment.api.event.UltraEnchantLockChangeEvent;
import com.lyingice.ultraenchantment.api.event.UltraEnchantTierUpgradeEvent;
import com.lyingice.ultraenchantment.content.AscensionData;
import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.content.StageDefinition;
import com.lyingice.ultraenchantment.logic.ProtectionLogic;
import com.lyingice.ultraenchantment.logic.StageLookup;
import com.lyingice.ultraenchantment.logic.UEEnchantRegistry;
import com.lyingice.ultraenchantment.logic.UEEvents;
import com.lyingice.ultraenchantment.logic.UELookups;
import com.lyingice.ultraenchantment.logic.UERoots;
import com.lyingice.ultraenchantment.registry.UEComponents;
import com.mojang.logging.LogUtils;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * <b>UltraEnchantment 对外 API</b>——唯一入口，静态方法，无实例。
 *
 * <h2>⚠️ 两个「等级」，别混</h2>
 *
 * <table>
 *   <tr><th>概念</th><th>本类里的名字</th><th>取值</th><th>例子</th></tr>
 *   <tr><td>阶级（进阶到哪一档）</td><td>{@code tier} / {@link #getTierLevel}</td>
 *       <td>0=原生 1=高阶 2=超级 3=究极</td><td>「究极锋利」的 tier = 3</td></tr>
 *   <tr><td>该阶曲线上的等级</td><td>{@code curveLevel} / {@link #getCurveLevel}</td>
 *       <td>1..该阶上限</td><td>「究极锋利 V」的 curveLevel = 5</td></tr>
 * </table>
 *
 * <p>物品上原版附魔的等级（锋利 5）是第三个东西：它<b>只决定进阶门槛</b>，
 * 效果强度按 {@code curveLevel} 结算。
 *
 * <h2>侧与线程</h2>
 * <ul>
 *   <li><b>查询</b>：双端可用（工具提示、JEI、渲染都能读）；</li>
 *   <li><b>修改</b>：只能在<b>逻辑服务端主线程</b>调用，否则返回 {@code false} 并记 WARN
 *       （客户端写组件会被同步覆盖，等于幽灵状态）。</li>
 * </ul>
 *
 * <h2>返回值的约定</h2>
 *
 * <p>所有修改方法返回 {@code true} 表示<b>物品确实被改了</b>；无变化、越界、不支持、
 * 侧/线程不对、被 {@link UltraEnchantTierUpgradeEvent} 取消，一律 {@code false}。
 * <b>不夹取</b>：{@link #setTierLevel} 超上限直接拒绝（避免"静默改小"）；
 * {@link #setCurveLevel} 例外——它按该阶上限夹取并记 WARN（与升级书现有行为一致）。
 */
public final class UltraEnchantmentApi {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 阶级上限（原生 0 之外的三档）。 */
    private static final int MAX_TIER = 3;

    private UltraEnchantmentApi() {}

    // ────────────────────────────── 查询 ──────────────────────────────

    /** 这条附魔在这件物品上的 UE <b>阶级</b>；未进阶 / 不支持 → {@code 0}。 */
    public static int getTierLevel(ItemStack stack, Enchantment enchant) {
        return UERoots.rootOf(enchant).map(root -> tierOf(stack, root)).orElse(0);
    }

    /** 同上，但直接给谱系根源 id（已解析过 root 时用它，省一次反查）。 */
    public static int tierOf(ItemStack stack, ResourceLocation root) {
        return UEComponents.ascensionOf(stack).stageOf(root).map(UERoots::tierOfStage).orElse(0);
    }

    /** 该阶的<b>曲线等级</b>（玩家看到的那个数字）；未进阶 → {@code 0}。 */
    public static int getCurveLevel(ItemStack stack, Enchantment enchant) {
        return UERoots.rootOf(enchant)
                .map(root -> UEComponents.ascensionOf(stack).tierLevelOf(root))
                .orElse(0);
    }

    /** 这条附魔是否纳入 UE 进阶体系（= 数据包里存在它的任一阶级阶段条目）。 */
    public static boolean isUltraEnchant(Enchantment enchant) {
        return UERoots.rootOf(enchant).map(UltraEnchantmentApi::isUltraEnchantRoot).orElse(false);
    }

    /** 同上，直接给谱系根源 id。 */
    public static boolean isUltraEnchantRoot(ResourceLocation root) {
        return maxTierOf(root) > 0;
    }

    /**
     * 该附魔允许的最高阶级（数据包里实际存在的最高阶，并被 IMC 的 {@code max_tier} 收紧）；
     * 未纳入体系 → {@code 0}。
     */
    public static int getMaxAllowedTier(Enchantment enchant) {
        return UERoots.rootOf(enchant).map(UltraEnchantmentApi::maxTierOf).orElse(0);
    }

    /** 是否处于「锁定」状态：已进阶 → 砂轮洗不掉、铁砧合并不会抹除，只能用祛咒石解除。 */
    public static boolean isEnchantLocked(ItemStack stack, Enchantment enchant) {
        return UERoots.rootOf(enchant).map(root -> ProtectionLogic.isProtected(stack, root)).orElse(false);
    }

    /** 当前阶段条目 id（如 {@code ultraenchantment:super/sharpness}）；未进阶 → 空。 */
    public static Optional<ResourceLocation> getStageId(ItemStack stack, Enchantment enchant) {
        return UERoots.rootOf(enchant).flatMap(root -> UEComponents.ascensionOf(stack).stageOf(root));
    }

    /** 读一条 IMC 自定义键值（见 {@link UltraEnchantImc#CHANNEL_ATTRIBUTES}）。 */
    public static Optional<String> getImcAttribute(Enchantment enchant, String key) {
        return UERoots.rootOf(enchant).flatMap(root -> UEEnchantRegistry.attribute(root, key));
    }

    // ────────────────────────────── 修改 ──────────────────────────────

    /**
     * 设置阶级。
     *
     * <p>升阶时<b>曲线等级归 1</b>（新的成长曲线，与铁砧进阶一致）；
     * 阶级不变时<b>什么都不做</b>（返回 {@code false}，不动曲线等级——那用
     * {@link #setCurveLevel}）。
     *
     * @param tier 0 = 解除进阶（等价 {@link #unlockEnchant}）；1..{@link #getMaxAllowedTier}
     * @return 成功改动为 {@code true}；越界 / 未纳入体系 / 该阶没有阶段条目 / 客户端 / 被取消 → {@code false}
     */
    public static boolean setTierLevel(ItemStack stack, Enchantment enchant, int tier) {
        ResourceLocation root = UERoots.rootOf(enchant).orElse(null);
        if (root == null) {
            LOGGER.warn("[API] setTierLevel：无法确定附魔所属谱系，已忽略");
            return false;
        }
        if (tier == 0) {
            return unlock(stack, root, UltraEnchantLockChangeEvent.Cause.API);
        }
        if (tier < 1 || tier > MAX_TIER) {
            LOGGER.warn("[API] setTierLevel {}：阶级 {} 越界（合法 1..{}），已忽略", root, tier, MAX_TIER);
            return false;
        }
        int max = maxTierOf(root);
        if (max == 0) {
            LOGGER.warn("[API] setTierLevel {}：该附魔未纳入 UE 体系（数据包里没有阶段条目），已忽略", root);
            return false;
        }
        if (tier > max) {
            LOGGER.warn("[API] setTierLevel {}：{} 超过允许的最高阶级 {}，已拒绝（不夹取）", root, tier, max);
            return false;
        }
        if (!requireServer("setTierLevel")) {
            return false;
        }
        HolderLookup.RegistryLookup<StageDefinition> stages = StageLookup.lookup();
        ResourceLocation stageId = stages == null ? null
                : StageLookup.stageIdOf(stages, root, LineageTier.values()[tier]).orElse(null);
        if (stageId == null) {
            LOGGER.warn("[API] setTierLevel {}：阶级 {} 没有对应的阶段条目，已忽略", root, tier);
            return false;
        }

        AscensionData data = UEComponents.ascensionOf(stack);
        int oldTier = data.stageOf(root).map(UERoots::tierOfStage).orElse(0);
        int oldCurve = data.tierLevelOf(root);
        if (oldTier == tier) {
            return false;
        }
        int newCurve = 1;
        if (!UEEvents.fireTierUpgrade(stack, root, holderFor(root), oldTier, tier, oldCurve, newCurve,
                UltraEnchantTierUpgradeEvent.Source.API)) {
            return false;
        }
        UEComponents.setAscension(stack, data.with(root, stageId, newCurve));
        if (oldTier == 0) {
            UEEvents.fireLockChange(stack, root, holderFor(root), true,
                    UltraEnchantLockChangeEvent.Cause.API);
        }
        return true;
    }

    /**
     * 设置当前阶级的曲线等级。
     *
     * <p>物品尚未进阶时返回 {@code false}（请先 {@link #setTierLevel}）。
     * 超出该阶上限时<b>夹取</b>并记 WARN。
     */
    public static boolean setCurveLevel(ItemStack stack, Enchantment enchant, int curveLevel) {
        ResourceLocation root = UERoots.rootOf(enchant).orElse(null);
        if (root == null) {
            LOGGER.warn("[API] setCurveLevel：无法确定附魔所属谱系，已忽略");
            return false;
        }
        if (!requireServer("setCurveLevel")) {
            return false;
        }
        AscensionData data = UEComponents.ascensionOf(stack);
        ResourceLocation stageId = data.stageOf(root).orElse(null);
        if (stageId == null) {
            LOGGER.warn("[API] setCurveLevel {}：该附魔尚未进阶，请先 setTierLevel", root);
            return false;
        }
        HolderLookup.RegistryLookup<StageDefinition> stages = StageLookup.lookup();
        StageDefinition stage = stages == null ? null : StageLookup.byId(stages, stageId).orElse(null);
        if (stage == null) {
            LOGGER.warn("[API] setCurveLevel {}：阶段条目 {} 不在当前数据包里，已忽略", root, stageId);
            return false;
        }
        int cap = stage.definition().maxLevel();
        int target = Math.max(1, Math.min(curveLevel, cap));
        if (target != curveLevel) {
            LOGGER.warn("[API] setCurveLevel {}：{} 超出该阶上限 {}，已夹取为 {}", root, curveLevel, cap, target);
        }
        int oldCurve = data.tierLevelOf(root);
        if (oldCurve == target) {
            return false;
        }
        int tier = UERoots.tierOfStage(stageId);
        if (!UEEvents.fireTierUpgrade(stack, root, holderFor(root), tier, tier, oldCurve, target,
                UltraEnchantTierUpgradeEvent.Source.API)) {
            return false;
        }
        UEComponents.setAscension(stack, data.withTierLevel(root, target));
        return true;
    }

    /** 解除锁定（祛咒石做的事）：清掉进阶记录，物品回到原生阶。没有记录 → {@code false}。 */
    public static boolean unlockEnchant(ItemStack stack, Enchantment enchant) {
        return UERoots.rootOf(enchant)
                .map(root -> unlock(stack, root, UltraEnchantLockChangeEvent.Cause.API))
                .orElse(false);
    }

    private static boolean unlock(ItemStack stack, ResourceLocation root,
                                  UltraEnchantLockChangeEvent.Cause cause) {
        if (!requireServer("unlockEnchant")) {
            return false;
        }
        AscensionData data = UEComponents.ascensionOf(stack);
        if (data.stageOf(root).isEmpty()) {
            return false;
        }
        UEComponents.setAscension(stack, data.without(root));
        UEEvents.fireLockChange(stack, root, holderFor(root), false, cause);
        return true;
    }

    /**
     * 移除这条附魔的 UE 数据<b>并把附魔本身也摘掉</b>（等价于"祛咒石 + 洗掉"）。
     *
     * <p>没有进阶记录、物品上也没有这条附魔 → {@code false}。
     */
    public static boolean removeUltraEnchant(ItemStack stack, Enchantment enchant) {
        ResourceLocation root = UERoots.rootOf(enchant).orElse(null);
        if (root == null) {
            LOGGER.warn("[API] removeUltraEnchant：无法确定附魔所属谱系，已忽略");
            return false;
        }
        if (!requireServer("removeUltraEnchant")) {
            return false;
        }

        AscensionData before = UEComponents.ascensionOf(stack);
        boolean hadRecord = before.stageOf(root).isPresent();

        Holder<Enchantment> holder = holderFor(root);
        boolean removedEnchant = false;
        ItemEnchantments.Mutable table = new ItemEnchantments.Mutable(
                EnchantmentHelper.getEnchantmentsForCrafting(stack));
        if (holder != null && table.getLevel(holder) > 0) {
            table.set(holder, 0);
            removedEnchant = true;
        }
        if (!hadRecord && !removedEnchant) {
            return false;
        }
        if (removedEnchant) {
            EnchantmentHelper.setEnchantments(stack, table.toImmutable());
        }
        if (hadRecord) {
            UEComponents.setAscension(stack, before.without(root));
            UEEvents.fireLockChange(stack, root, holder, false, UltraEnchantLockChangeEvent.Cause.REMOVE);
        }
        return true;
    }

    // ────────────────────────────── 内部 ──────────────────────────────

    /** 该谱系在数据包里实际存在的最高阶级，再被 IMC 的 max_tier 收紧；没有 → 0。 */
    private static int maxTierOf(ResourceLocation root) {
        HolderLookup.RegistryLookup<StageDefinition> stages = StageLookup.lookup();
        if (stages == null) {
            return 0;
        }
        int found = 0;
        for (int tier = 1; tier <= MAX_TIER; tier++) {
            if (StageLookup.stageIdOf(stages, root, LineageTier.values()[tier]).isPresent()) {
                found = tier;
            }
        }
        int cap = UEEnchantRegistry.maxTierCap(root);
        return cap > 0 ? Math.min(found, cap) : found;
    }

    /** 写入侧/服务端的附魔 holder；拿不到就返回 null（事件里 holder 只是便利项）。 */
    @Nullable
    private static Holder<Enchantment> holderFor(ResourceLocation root) {
        try {
            return UELookups.enchantmentsForItemWrites(false)
                    .get(ResourceKey.create(Registries.ENCHANTMENT, root))
                    .orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 写接口的前置条件：逻辑服务端 + 服务端主线程。 */
    private static boolean requireServer(String what) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            LOGGER.warn("[API] {} 只能在逻辑服务端调用（当前没有服务器），已忽略", what);
            return false;
        }
        if (!server.isSameThread()) {
            LOGGER.warn("[API] {} 必须在服务端主线程调用，已忽略", what);
            return false;
        }
        return true;
    }
}
