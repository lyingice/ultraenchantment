package com.lyingice.ultraenchantment.starlight.logic;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.starlight.content.EvolutionData;
import com.lyingice.ultraenchantment.starlight.content.TierDefinition;
import com.lyingice.ultraenchantment.starlight.content.TreeNode;
import com.lyingice.ultraenchantment.starlight.content.TreeDefinition;
import com.lyingice.ultraenchantment.starlight.registry.StarlightComponents;
import com.lyingice.ultraenchantment.starlight.registry.StarlightRegistries;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * 星光数据包的运行期索引。
 *
 * <p><b>为什么需要索引</b>：星图界面每次打开都要回答三类问题——
 * 「这件物品属于哪棵树」「它现在是哪颗星」「这颗星是什么阶级」。
 * 每次都扫注册表会让界面随内容包膨胀而变慢；这里在数据包重载后建一次表。
 *
 * <p><b>生命周期</b>：只在 {@code TagsUpdatedEvent} 后重建（见 {@code StarlightEvents}），
 * 不在静态字段里长期缓存 Holder——数据包 {@code /reload} 后旧 Holder 会悬空（AGENT.md P1-6）。
 * 这里存的是不可变的 {@link TreeDefinition} 值本身，重载时整体替换。
 */
public final class TreeIndex {
    private TreeIndex() {}

    private static volatile Map<ResourceLocation, TreeDefinition> trees = Map.of();
    private static volatile Map<ResourceLocation, ResourceLocation> treeByEnchantment = Map.of();
    private static volatile Map<ResourceLocation, TierDefinition> tiers = Map.of();

    /**
     * 数据包重载后重建。
     *
     * <p>必须优先用 {@code TagsUpdatedEvent} 自带的 {@link RegistryAccess}：
     * 事件触发时 {@code ServerLifecycleHooks.currentServer} 可能还没赋值，
     * {@code CommonHooks.resolveLookup} 会返回 null（AGENT.md P0-13）。
     */
    public static synchronized void rebuild(RegistryAccess access) {
        rebuild(access.lookup(StarlightRegistries.TREE).orElse(null),
                access.lookup(StarlightRegistries.TIER).orElse(null));

        // 数据校验：树里引用的阶段附魔必须真的存在于 minecraft:enchantment。
        // 这条日志是「内容包写错/漏文件」的第一道警报，别删。
        var enchants = access.lookup(Registries.ENCHANTMENT).orElse(null);
        if (enchants != null) {
            for (ResourceLocation enchantmentId : treeByEnchantment.keySet()) {
                if (enchants.get(ResourceKey.create(Registries.ENCHANTMENT, enchantmentId)).isEmpty()) {
                    Ultraenchantment.LOGGER.warn("[starlight] 树引用了不存在的附魔：{}", enchantmentId);
                }
            }
        }
    }

    /** 客户端兜底：没有事件参数时走 {@code CommonHooks.resolveLookup}。 */
    public static synchronized void rebuild() {
        rebuild(CommonHooks.resolveLookup(StarlightRegistries.TREE),
                CommonHooks.resolveLookup(StarlightRegistries.TIER));
    }

    private static synchronized void rebuild(HolderLookup.RegistryLookup<TreeDefinition> treeLookup,
                                             HolderLookup.RegistryLookup<TierDefinition> tierLookup) {
        Map<ResourceLocation, TreeDefinition> newTrees = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> newByEnchantment = new LinkedHashMap<>();
        if (treeLookup != null) {
            for (Holder.Reference<TreeDefinition> holder : treeLookup.listElements().toList()) {
                ResourceLocation treeId = holder.key().location();
                TreeDefinition tree = holder.value();
                newTrees.put(treeId, tree);
                for (TreeNode node : tree.nodes()) {
                    ResourceLocation previous = newByEnchantment.putIfAbsent(node.enchantment(), treeId);
                    if (previous != null && !previous.equals(treeId)) {
                        Ultraenchantment.LOGGER.warn(
                                "[starlight] 附魔 {} 同时出现在树 {} 与 {} 中，星图反查取前者",
                                node.enchantment(), previous, treeId);
                    }
                }
            }
        }

        Map<ResourceLocation, TierDefinition> newTiers = new LinkedHashMap<>();
        if (tierLookup != null) {
            for (Holder.Reference<TierDefinition> holder : tierLookup.listElements().toList()) {
                newTiers.put(holder.key().location(), holder.value());
            }
        }

        trees = Map.copyOf(newTrees);
        treeByEnchantment = Map.copyOf(newByEnchantment);
        tiers = Map.copyOf(newTiers);

        Ultraenchantment.LOGGER.info("[starlight] indexed {} trees / {} tiers / {} node enchantments",
                trees.size(), tiers.size(), treeByEnchantment.size());
    }

    /** 数据包重载前/服务端关闭时清空。 */
    public static synchronized void invalidate() {
        trees = Map.of();
        treeByEnchantment = Map.of();
        tiers = Map.of();
    }

    /** 按 id 取树。索引为空时尝试重建一次（客户端首次打开界面可能早于重载事件）。 */
    public static Optional<TreeDefinition> tree(ResourceLocation treeId) {
        ensureReady();
        return Optional.ofNullable(trees.get(treeId));
    }

    /** 全部树的 id，按显示名排序。供图鉴这类只读界面稳定遍历。 */
    public static List<ResourceLocation> treeIds() {
        ensureReady();
        return trees.keySet().stream()
                .sorted(Comparator.comparing(id -> trees.get(id).display()))
                .toList();
    }

    /** 某件物品属于哪棵树：优先看进化履历，其次看它身上带的是不是某棵树的节点附魔。 */
    public static Optional<ResourceLocation> treeIdOf(ItemStack stack) {
        return treeIdsOf(stack).stream().findFirst();
    }

    /**
     * 物品身上<b>所有</b>可进化的树。
     *
     * <p>一把剑同时带锋利与抢夺时，两个谱系都能进阶；界面需要在它们之间切换。
     * 顺序：进化履历所在的树优先，其余按物品上的附魔顺序。
     */
    public static List<ResourceLocation> treeIdsOf(ItemStack stack) {
        ensureReady();
        Set<ResourceLocation> out = new LinkedHashSet<>();
        EvolutionData data = StarlightComponents.evolutionOf(stack);
        if (data != null && trees.containsKey(data.tree())) {
            out.add(data.tree());
        }
        for (Holder<Enchantment> holder : EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet()) {
            ResourceLocation enchantmentId = holder.unwrapKey().map(ResourceKey::location).orElse(null);
            if (enchantmentId == null) {
                continue;
            }
            ResourceLocation treeId = treeByEnchantment.get(enchantmentId);
            if (treeId != null) {
                out.add(treeId);
            }
        }
        return List.copyOf(out);
    }

    /** 阶级定义；索引里没有时返回一个白色兜底，保证界面不抛。 */
    public static TierDefinition tier(ResourceLocation tierId) {
        ensureReady();
        TierDefinition tier = tiers.get(tierId);
        return tier == null ? new TierDefinition(0, tierId.toString(), 0xFFFFFF, 255) : tier;
    }

    public static int tierOrdinal(ResourceLocation tierId) {
        return tier(tierId).ordinal();
    }

    /** 附魔 id → 它在树里所属节点的阶级 id；不是阶段附魔时返回空。 */
    public static Optional<ResourceLocation> tierIdOfEnchantment(ResourceLocation enchantmentId) {
        ensureReady();
        ResourceLocation treeId = treeByEnchantment.get(enchantmentId);
        if (treeId == null) {
            return Optional.empty();
        }
        TreeDefinition tree = trees.get(treeId);
        if (tree == null) {
            return Optional.empty();
        }
        TreeNode node = tree.nodeOfEnchantment(enchantmentId).orElse(null);
        return node == null ? Optional.empty() : Optional.of(node.tier());
    }

    /** 附魔 id → 它在树里所属节点的阶级定义；不是阶段附魔时返回空。 */
    public static Optional<TierDefinition> tierOfEnchantment(ResourceLocation enchantmentId) {
        return tierIdOfEnchantment(enchantmentId).map(TreeIndex::tier);
    }

    private static void ensureReady() {
        if (trees.isEmpty() && tiers.isEmpty()) {
            rebuild();
        }
    }
}
