package com.lyingice.ultraenchantment.starlight.logic;

import com.lyingice.ultraenchantment.starlight.content.EvolutionData;
import com.lyingice.ultraenchantment.starlight.content.TreeNode;
import com.lyingice.ultraenchantment.starlight.content.TreeDefinition;
import com.lyingice.ultraenchantment.starlight.content.TreeEdge;
import com.lyingice.ultraenchantment.starlight.menu.StarlightTableMenu;
import com.lyingice.ultraenchantment.starlight.registry.StarlightComponents;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * 进化的服务端判定与落盘。
 *
 * <p><b>只做三件事</b>：
 * <ol>
 *   <li>回答「这件物品现在是哪颗星、几级」；</li>
 *   <li>校验一次进化/升级是否合法（前置、等级、经验、附魔适用范围）；</li>
 *   <li>成功后把物品上的附魔<b>真正替换</b>成目标阶段附魔，并写进化履历。</li>
 * </ol>
 *
 * <p>没有合成 Holder，没有注入事件。物品上携带的就是真实注册表里的阶段附魔。
 */
public final class EvolutionService {
    private EvolutionService() {}

    /** 成功/失败/升级的客户端消息键。 */
    public static final String MSG_SUCCESS = "gui.ultraenchantment.starlight.success";
    public static final String MSG_FAILURE_DROP = "gui.ultraenchantment.starlight.failure.drop";
    public static final String MSG_FAILURE_NO_DROP = "gui.ultraenchantment.starlight.failure.no_drop";
    public static final String MSG_UPGRADED = "gui.ultraenchantment.starlight.upgraded";
    public static final String MSG_NOT_ENOUGH_LEVELS = "gui.ultraenchantment.starlight.not_enough_levels";
    public static final String MSG_MAX_LEVEL = "gui.ultraenchantment.starlight.max_level";
    public static final String MSG_INVALID = "gui.ultraenchantment.starlight.invalid";

    /** 当前节点：优先读履历，其次看物品身上带的是不是树里的节点附魔。 */
    public static Optional<ResourceLocation> currentNode(ResourceLocation treeId,
                                                        TreeDefinition tree,
                                                        ItemStack stack) {
        EvolutionData data = StarlightComponents.evolutionOf(stack);
        if (data != null && data.tree().equals(treeId) && tree.node(data.node()).isPresent()) {
            return Optional.of(data.node());
        }

        Set<ResourceLocation> present = new HashSet<>();
        for (Holder<Enchantment> holder : EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet()) {
            holder.unwrapKey().map(ResourceKey::location).ifPresent(present::add);
        }

        ResourceLocation best = null;
        int bestTier = Integer.MIN_VALUE;
        for (TreeNode node : tree.nodes()) {
            if (!present.contains(node.enchantment())) {
                continue;
            }
            int tier = TreeIndex.tierOrdinal(node.tier());
            if (best == null || tier > bestTier) {
                best = node.id();
                bestTier = tier;
            }
        }
        return Optional.ofNullable(best);
    }

    /** 节点附魔在物品上的等级；物品上没这条附魔时返回 0。 */
    public static int levelOf(ItemStack stack, TreeNode node,
                              HolderLookup.RegistryLookup<Enchantment> enchants) {
        Holder<Enchantment> holder = holderOf(node, enchants);
        return holder == null ? 0 : EnchantmentHelper.getEnchantmentsForCrafting(stack).getLevel(holder);
    }

    /** 节点附魔自己的等级上限。 */
    public static int maxLevelOf(TreeNode node, HolderLookup.RegistryLookup<Enchantment> enchants) {
        Holder<Enchantment> holder = holderOf(node, enchants);
        return holder == null ? 0 : holder.value().getMaxLevel();
    }

    /** 阶级内升级的经验代价：越高越贵，但始终可预期。 */
    public static int upgradeCost(int currentLevel) {
        return 3 + Math.max(1, currentLevel) * 2;
    }

    // ── 进化 ────────────────────────────────────────────────────────────

    /**
     * 尝试从当前节点走到 {@code targetNodeId}。
     *
     * <p><b>顺序</b>：全量校验 → 扣经验/催化剂 → 掷骰 → 成功才改物品。
     * 失败也扣代价（尝试本身有成本），但物品绝不损坏。
     */
    public static boolean attempt(ServerPlayer player, StarlightTableMenu menu,
                                  ResourceLocation treeId, ResourceLocation targetNodeId) {
        ItemStack item = menu.itemStack();
        if (item.isEmpty()) {
            message(player, ChatFormatting.RED, MSG_INVALID);
            return false;
        }
        TreeDefinition tree = TreeIndex.tree(treeId).orElse(null);
        TreeNode target = tree == null ? null : tree.node(targetNodeId).orElse(null);
        ResourceLocation currentId = tree == null ? null : currentNode(treeId, tree, item).orElse(null);
        TreeEdge edge = tree == null || currentId == null
                ? null : tree.edge(currentId, targetNodeId).orElse(null);
        if (tree == null || target == null || edge == null) {
            message(player, ChatFormatting.RED, MSG_INVALID);
            return false;
        }

        HolderLookup.RegistryLookup<Enchantment> enchants =
                player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        TreeNode current = tree.node(currentId).orElse(null);
        int currentLevel = current == null ? 0 : levelOf(item, current, enchants);
        if (currentLevel < edge.requiredLevel()) {
            message(player, ChatFormatting.RED, MSG_INVALID);
            return false;
        }
        if (player.experienceLevel < edge.costLevels()) {
            message(player, ChatFormatting.RED, MSG_NOT_ENOUGH_LEVELS);
            return false;
        }
        Holder<Enchantment> targetHolder = holderOf(target, enchants);
        if (targetHolder == null || !targetHolder.value().canEnchant(item)) {
            message(player, ChatFormatting.RED, MSG_INVALID);
            return false;
        }

        // 扣代价（成功失败都扣）。催化剂是「这次仪式」的材料，也一并消耗。
        if (edge.costLevels() > 0) {
            player.giveExperienceLevels(-edge.costLevels());
        }
        menu.consumeCatalyst();

        boolean success = player.getRandom().nextFloat() < edge.chance();
        if (!success) {
            applyFailurePenalty(player, menu, item, enchants, current, currentLevel, edge);
            return false;
        }

        applyEvolution(menu, item, enchants, treeId, current, target, targetHolder);
        message(player, ChatFormatting.GREEN, MSG_SUCCESS,
                targetHolder.value().description().getString());
        return true;
    }

    /**
     * 进阶失败惩罚：当前节点附魔等级随机下降 {@code failureDropMin..failureDropMax} 级，
     * 最低保留 1 级。失败也会消耗经验/催化剂（在调用前已扣）。
     */
    private static void applyFailurePenalty(ServerPlayer player, StarlightTableMenu menu,
                                            ItemStack item,
                                            HolderLookup.RegistryLookup<Enchantment> enchants,
                                            TreeNode current, int currentLevel, TreeEdge edge) {
        int min = edge.failureDropMin();
        int max = edge.failureDropMax();
        int rolled = min + (max > min ? player.getRandom().nextInt(max - min + 1) : 0);
        int newLevel = Math.max(1, currentLevel - rolled);
        int actualDrop = currentLevel - newLevel;

        Holder<Enchantment> holder = current == null ? null : holderOf(current, enchants);
        if (actualDrop > 0 && holder != null) {
            ItemEnchantments.Mutable table = new ItemEnchantments.Mutable(
                    EnchantmentHelper.getEnchantmentsForCrafting(item));
            table.set(holder, newLevel);
            EnchantmentHelper.setEnchantments(item, table.toImmutable());
            menu.markItemChanged();
            message(player, ChatFormatting.RED, MSG_FAILURE_DROP,
                    holder.value().description().getString(),
                    String.valueOf(actualDrop), String.valueOf(newLevel));
        } else {
            message(player, ChatFormatting.RED, MSG_FAILURE_NO_DROP);
        }
    }

    // ── 阶级内升级 ──────────────────────────────────────────────────────

    /**
     * 把当前节点附魔的等级 +1。这是多段进阶的「横向」成长：
     * 进化改变物种，升级只提高当前物种的等级。
     */
    public static boolean upgrade(ServerPlayer player, StarlightTableMenu menu,
                                  ResourceLocation treeId, ResourceLocation nodeId) {
        ItemStack item = menu.itemStack();
        TreeDefinition tree = TreeIndex.tree(treeId).orElse(null);
        if (item.isEmpty() || tree == null || tree.node(nodeId).isEmpty()) {
            message(player, ChatFormatting.RED, MSG_INVALID);
            return false;
        }
        ResourceLocation currentId = currentNode(treeId, tree, item).orElse(null);
        if (!nodeId.equals(currentId)) {
            message(player, ChatFormatting.RED, MSG_INVALID);
            return false;
        }

        HolderLookup.RegistryLookup<Enchantment> enchants =
                player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        TreeNode node = tree.node(nodeId).orElseThrow();
        int currentLevel = levelOf(item, node, enchants);
        int maxLevel = maxLevelOf(node, enchants);
        if (currentLevel <= 0) {
            message(player, ChatFormatting.RED, MSG_INVALID);
            return false;
        }
        if (currentLevel >= maxLevel) {
            message(player, ChatFormatting.YELLOW, MSG_MAX_LEVEL);
            return false;
        }

        int cost = upgradeCost(currentLevel);
        if (player.experienceLevel < cost) {
            message(player, ChatFormatting.RED, MSG_NOT_ENOUGH_LEVELS);
            return false;
        }
        player.giveExperienceLevels(-cost);

        Holder<Enchantment> holder = holderOf(node, enchants);
        if (holder == null) {
            message(player, ChatFormatting.RED, MSG_INVALID);
            return false;
        }
        ItemEnchantments.Mutable table =
                new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(item));
        table.set(holder, currentLevel + 1);
        EnchantmentHelper.setEnchantments(item, table.toImmutable());
        menu.markItemChanged();
        message(player, ChatFormatting.GREEN, MSG_UPGRADED,
                holder.value().description().getString(), String.valueOf(currentLevel + 1));
        return true;
    }

    // ── 物品落盘 ────────────────────────────────────────────────────────

    private static void applyEvolution(StarlightTableMenu menu, ItemStack item,
                                       HolderLookup.RegistryLookup<Enchantment> enchants,
                                       ResourceLocation treeId, TreeNode from, TreeNode to,
                                       Holder<Enchantment> toHolder) {
        applyEvolutionDirect(item, enchants, treeId, from, to, toHolder);
        menu.markItemChanged();
    }

    /**
     * 纯物品变换：移除起始附魔、写入目标阶段附魔、更新履历。
     *
     * <p>与菜单解耦，便于自检与将来的「非台面」进化入口复用。
     */
    public static void applyEvolutionDirect(ItemStack item,
                                            HolderLookup.RegistryLookup<Enchantment> enchants,
                                            ResourceLocation treeId, TreeNode from, TreeNode to,
                                            Holder<Enchantment> toHolder) {
        ItemEnchantments.Mutable table =
                new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(item));
        Holder<Enchantment> fromHolder = from == null ? null : holderOf(from, enchants);
        if (fromHolder != null) {
            table.set(fromHolder, 0);
        }
        table.set(toHolder, 1);
        EnchantmentHelper.setEnchantments(item, table.toImmutable());

        EvolutionData data = StarlightComponents.evolutionOf(item);
        EvolutionData next = data != null && data.tree().equals(treeId)
                ? data.advance(from == null ? to.id() : from.id(), to.id())
                : new EvolutionData(treeId, to.id(),
                        from == null ? List.of() : List.of(from.id()));
        StarlightComponents.setEvolution(item, next);
    }

    private static Holder<Enchantment> holderOf(TreeNode node,
                                                HolderLookup.RegistryLookup<Enchantment> enchants) {
        return enchants.get(ResourceKey.create(Registries.ENCHANTMENT, node.enchantment()))
                .orElse(null);
    }

    private static void message(ServerPlayer player, ChatFormatting color, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(color), true);
    }
}
