package com.lyingice.ultraenchantment.logic;

import com.lyingice.ultraenchantment.registry.UEComponents;
import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.content.StageDefinition;
import com.lyingice.ultraenchantment.registry.UERegistries;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * 阶段条目的查找。
 *
 * <p><b>不缓存</b>：数据包注册表在 {@code /reload} 时会重建，任何静态缓存的
 * {@code Holder} / {@code StageDefinition} 都会变成悬空引用（见 AGENT.md P1-6）。
 * lookup 是一次 hash 查表，开销可忽略。
 *
 * <p>取法与 {@code EnchantmentHelper} 一致：{@link CommonHooks#resolveLookup}
 * （服务端优先、客户端兜底），保证两侧行为相同。
 */
public final class StageLookup {
    private StageLookup() {}

    /** 取阶段注册表的 lookup；不可用时返回 {@code null}。 */
    public static HolderLookup.RegistryLookup<StageDefinition> lookup() {
        return CommonHooks.resolveLookup(UERegistries.STAGE);
    }

    /** 按条目 id 取阶段定义。 */
    public static Optional<StageDefinition> byId(HolderLookup.RegistryLookup<StageDefinition> lookup,
                                                 ResourceLocation stageId) {
        return lookup.get(ResourceKey.create(UERegistries.STAGE, stageId)).map(Holder.Reference::value);
    }

    /** 取物品上某条谱系当前所处的阶段定义。 */
    public static Optional<StageDefinition> currentStage(ItemStack stack, ResourceLocation root) {
        ResourceLocation stageId = UEComponents.ascensionOf(stack).stages().get(root);
        if (stageId == null) {
            return Optional.empty();
        }
        HolderLookup.RegistryLookup<StageDefinition> lookup = lookup();
        return lookup == null ? Optional.empty() : byId(lookup, stageId);
    }

    /**
     * 取物品上全部已进阶谱系的阶段定义，返回 {@code 谱系根源 → 阶段定义}。
     *
     * <p>返回的 map 保留 {@code AscensionData} 的迭代顺序（LinkedHashMap），
     * 便于调试与稳定重现。
     */
    public static Map<ResourceLocation, StageDefinition> allStages(
            HolderLookup.RegistryLookup<StageDefinition> lookup, ItemStack stack) {

        Map<ResourceLocation, ResourceLocation> recorded = UEComponents.ascensionOf(stack).stages();
        if (recorded.isEmpty()) {
            return Map.of();
        }

        Map<ResourceLocation, StageDefinition> result = new LinkedHashMap<>();
        recorded.forEach((root, stageId) -> byId(lookup, stageId).ifPresent(stage -> result.put(root, stage)));
        return result;
    }

    /**
     * 取某条谱系在某个阶级的阶段条目 id。
     *
     * <p><b>不做「表头」过滤</b>——这与 {@code AscensionLogic} 里「原生阶只能走链头」的规矩
     * 是两件事：那条规矩约束的是<b>进阶书</b>（数据包若把 next 接到跳阶位置，书就应当不生效），
     * 这里服务的是<b>创造旁路直接授予</b>（规格 §5.2：白装备可以直接上究极，本就允许跳阶）。
     */
    public static Optional<ResourceLocation> stageIdOf(
            HolderLookup.RegistryLookup<StageDefinition> lookup,
            ResourceLocation root, LineageTier tier) {
        if (lookup == null) {
            return Optional.empty();
        }
        for (Holder.Reference<StageDefinition> holder : lookup.listElements().toList()) {
            StageDefinition stage = holder.value();
            if (stage.tier() == tier && stage.root().equals(root)) {
                return Optional.of(holder.key().location());
            }
        }
        return Optional.empty();
    }

    /** 取某条谱系在某个阶级的阶段定义。 */
    public static Optional<StageDefinition> stageOf(
            HolderLookup.RegistryLookup<StageDefinition> lookup,
            ResourceLocation root, LineageTier tier) {
        return stageIdOf(lookup, root, tier).flatMap(id -> byId(lookup, id));
    }

    /**
     * 取<b>某一条谱系</b>在某个阶级的阶段条目的等级上限。
     *
     * <p>铭刻书要写入「该阶级的最高级」——这个最高级由<b>该阶级的阶段条目</b>定义
     * （数据包项「每个阶级的等级上限」），而不是原版附魔自身的 {@code max_level}。
     *
     * <h2>⚠️ 必须按谱系查，不能取全阶级的最大值</h2>
     *
     * <p>等级上限是<b>逐谱系</b>的：耐久 3、保护 4、锋利 5 各自沿用其根源附魔的上限。
     * 若像早期版本那样「扫全阶级取最大」，铭刻书会给一件保护（上限 4）的物品写入 5 级——
     * 越过了它自己的上限，于是同一条附魔在不同来源下能到的等级不一致。
     * 一个附魔的上限只有它自己那份阶段条目说了算。
     *
     * @param root 谱系根源（原版附魔 id），与 {@link StageDefinition#root()} 对应
     * @return 该谱系该阶级的等级上限；找不到条目时返回 {@code fallback}
     */
    public static int maxLevelOf(HolderLookup.RegistryLookup<StageDefinition> lookup,
                                 LineageTier tier, ResourceLocation root, int fallback) {
        if (lookup == null) {
            return fallback;
        }

        for (Holder.Reference<StageDefinition> holder : lookup.listElements().toList()) {
            StageDefinition stage = holder.value();
            if (stage.tier() == tier && stage.root().equals(root)) {
                return stage.definition().maxLevel();
            }
        }
        return fallback;
    }

    /**
     * <b>显示层</b>的等级上限——tooltip 决定「要不要省略等级数字」时用它。
     *
     * <p>规则与原版 {@code Enchantment.getFullname} 同源：
     * {@code level != 1 || getMaxLevel() != 1} 才显示数字。上限一律取<b>该阶级阶段条目</b>的
     * {@code max_level}（逐谱系，P1-28），取不到时退化为原版附魔自身上限。
     *
     * <p><b>物品 tooltip 与书 tooltip 必须共用这一份判定</b>：曾经两处各写各的，
     * 于是书那边补了省略规则、物品那边漏了——「经验修补 1」这种原版根本不存在的行
     * 就是这么冒出来的。一条规则只准有一份实现。
     */
    public static int displayLevelCap(LineageTier tier, ResourceLocation root, int vanillaMaxLevel) {
        return maxLevelOf(lookup(), tier, root, Math.max(1, vanillaMaxLevel));
    }
}
