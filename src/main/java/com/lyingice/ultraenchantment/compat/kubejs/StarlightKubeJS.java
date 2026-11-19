package com.lyingice.ultraenchantment.compat.kubejs;

import com.lyingice.ultraenchantment.starlight.content.EvolutionData;
import com.lyingice.ultraenchantment.starlight.content.TreeNode;
import com.lyingice.ultraenchantment.starlight.content.TreeDefinition;
import com.lyingice.ultraenchantment.starlight.content.TreeEdge;
import com.lyingice.ultraenchantment.starlight.logic.EvolutionService;
import com.lyingice.ultraenchantment.starlight.logic.TreeIndex;
import com.lyingice.ultraenchantment.starlight.registry.StarlightComponents;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * KubeJS 脚本绑定 {@code UltraEnchantment}。
 *
 * <pre>{@code
 * UltraEnchantment.tree(stack)        // 'ultraenchantment:sharpness'
 * UltraEnchantment.node(stack)        // 'ultraenchantment:blood'
 * UltraEnchantment.tier(stack)        // 'ultraenchantment:super'
 * UltraEnchantment.level(stack)       // 3
 * UltraEnchantment.next(stack)        // ['ultraenchantment:blood_ultra']
 * UltraEnchantment.history(stack)     // ['ultraenchantment:root', ...]
 * UltraEnchantment.tierOfEnchantment('ultraenchantment:integration/fancyenchantments/abyssal_maelstrom/super')
 * UltraEnchantment.tierColor('ultraenchantment:super')
 * UltraEnchantment.canEvolve(stack, 'ultraenchantment:blood')
 * UltraEnchantment.evolve(stack, 'ultraenchantment:blood')
 * }</pre>
 *
 * <p>{@code evolve} 是<b>脚本级直接进化</b>：不掷概率、不扣经验/材料，
 * 只校验当前节点→目标节点与前置等级。适合剧情、KubeJS 自定义仪式。
 */
public final class StarlightKubeJS {
    private StarlightKubeJS() {}

    public static String tree(ItemStack stack) {
        return TreeIndex.treeIdOf(stack).map(ResourceLocation::toString).orElse(null);
    }

    public static String node(ItemStack stack) {
        TreeContext ctx = context(stack);
        return ctx == null || ctx.current() == null ? null : ctx.current().id().toString();
    }

    public static String tier(ItemStack stack) {
        TreeContext ctx = context(stack);
        return ctx == null || ctx.current() == null ? null : ctx.current().tier().toString();
    }

    public static int level(ItemStack stack) {
        TreeContext ctx = context(stack);
        HolderLookup.RegistryLookup<Enchantment> enchants = enchants();
        if (ctx == null || ctx.current() == null || enchants == null) {
            return 0;
        }
        return EvolutionService.levelOf(stack, ctx.current(), enchants);
    }

    public static List<String> next(ItemStack stack) {
        TreeContext ctx = context(stack);
        if (ctx == null || ctx.current() == null) {
            return List.of();
        }
        return ctx.tree().edgesFrom(ctx.current().id()).stream()
                .map(edge -> edge.to().toString()).toList();
    }

    public static List<String> history(ItemStack stack) {
        var data = StarlightComponents.evolutionOf(stack);
        return data == null ? List.of()
                : data.history().stream().map(ResourceLocation::toString).toList();
    }

    public static String tierOfEnchantment(String enchantmentId) {
        ResourceLocation id = ResourceLocation.tryParse(enchantmentId);
        return id == null ? null
                : TreeIndex.tierIdOfEnchantment(id).map(ResourceLocation::toString).orElse(null);
    }

    /** 阶级色（0xRRGGBB）；未知 id 返回 -1。 */
    public static int tierColor(String tierId) {
        ResourceLocation id = ResourceLocation.tryParse(tierId);
        return id == null ? -1 : TreeIndex.tier(id).color();
    }

    public static boolean canEvolve(ItemStack stack, String targetNode) {
        TreeContext ctx = context(stack);
        HolderLookup.RegistryLookup<Enchantment> enchants = enchants();
        if (ctx == null || ctx.current() == null || enchants == null) {
            return false;
        }
        ResourceLocation targetId = ResourceLocation.tryParse(targetNode);
        TreeEdge edge = targetId == null ? null
                : ctx.tree().edge(ctx.current().id(), targetId).orElse(null);
        if (edge == null) {
            return false;
        }
        return EvolutionService.levelOf(stack, ctx.current(), enchants) >= edge.requiredLevel();
    }

    /**
     * 造一本阶段附魔书——封面由客户端 Mixin 按阶段阶级自动切换。
     *
     * <p>参数可以是数据包生成的<b>阶段附魔 id</b>
     * （{@code ultraenchantment:integration/fancyenchantments/abyssal_maelstrom/super}），
     * 也可以是<b>代码驱动阶段的节点 id</b>
     * （{@code ultraenchantment:integration/jlme/lifesteal/super}）：
     * 后者书里存原附魔，并用 {@code ultraenchantment:evolution} 组件记录节点。
     *
     * <pre>{@code
     * event.player.give(UltraEnchantment.stageBook('ultraenchantment:integration/jlme/lifesteal/super', 1))
     * }</pre>
     */
    public static ItemStack stageBook(String enchantmentOrNode, int level) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        HolderLookup.RegistryLookup<Enchantment> enchants = enchants();
        ResourceLocation id = ResourceLocation.tryParse(enchantmentOrNode);
        if (enchants == null || id == null) {
            return book;
        }

        // ① 数据驱动的真实阶段附魔：直接写 stored_enchantments。
        Holder<Enchantment> direct = enchants.get(
                ResourceKey.create(Registries.ENCHANTMENT, id)).orElse(null);
        if (direct != null) {
            setStored(book, direct, level);
            return book;
        }

        // ② 代码驱动阶段：id 是树节点，不是附魔；书里保留原附魔 + evolution 组件。
        ResourceLocation treeId = treeIdForNode(id);
        TreeDefinition tree = treeId == null ? null : TreeIndex.tree(treeId).orElse(null);
        TreeNode node = tree == null ? null : tree.node(id).orElse(null);
        if (node == null) {
            return book;
        }
        Holder<Enchantment> root = enchants.get(
                ResourceKey.create(Registries.ENCHANTMENT, node.enchantment())).orElse(null);
        if (root == null) {
            return book;
        }
        setStored(book, root, level);
        StarlightComponents.setEvolution(book, new EvolutionData(treeId, id, List.of()));
        return book;
    }

    private static void setStored(ItemStack book, Holder<Enchantment> holder, int level) {
        ItemEnchantments.Mutable table = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        table.set(holder, Mth.clamp(level, 1, 255));
        book.set(DataComponents.STORED_ENCHANTMENTS, table.toImmutable());
    }

    /** 节点 id = 树 id + '/' + 阶段名，树 id 本身可能有多段路径。 */
    private static ResourceLocation treeIdForNode(ResourceLocation nodeId) {
        String path = nodeId.getPath();
        int slash = path.lastIndexOf('/');
        if (slash <= 0) {
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath(nodeId.getNamespace(), path.substring(0, slash));
    }

    /** 脚本级直接进化；成功返回 true，物品被原地修改。 */
    public static boolean evolve(ItemStack stack, String targetNode) {
        TreeContext ctx = context(stack);
        HolderLookup.RegistryLookup<Enchantment> enchants = enchants();
        if (ctx == null || ctx.current() == null || enchants == null) {
            return false;
        }
        ResourceLocation targetId = ResourceLocation.tryParse(targetNode);
        if (targetId == null) {
            return false;
        }
        TreeNode target = ctx.tree().node(targetId).orElse(null);
        TreeEdge edge = ctx.tree().edge(ctx.current().id(), targetId).orElse(null);
        if (target == null || edge == null) {
            return false;
        }
        if (EvolutionService.levelOf(stack, ctx.current(), enchants) < edge.requiredLevel()) {
            return false;
        }
        Holder<Enchantment> targetHolder = enchants.get(
                ResourceKey.create(Registries.ENCHANTMENT, target.enchantment())).orElse(null);
        if (targetHolder == null) {
            return false;
        }
        EvolutionService.applyEvolutionDirect(stack, enchants, ctx.treeId(),
                ctx.current(), target, targetHolder);
        return true;
    }

    private record TreeContext(ResourceLocation treeId, TreeDefinition tree, TreeNode current) {}

    private static TreeContext context(ItemStack stack) {
        ResourceLocation treeId = TreeIndex.treeIdOf(stack).orElse(null);
        if (treeId == null) {
            return null;
        }
        TreeDefinition tree = TreeIndex.tree(treeId).orElse(null);
        if (tree == null) {
            return null;
        }
        ResourceLocation currentId = EvolutionService.currentNode(treeId, tree, stack).orElse(null);
        TreeNode current = currentId == null ? null : tree.node(currentId).orElse(null);
        return new TreeContext(treeId, tree, current);
    }

    private static HolderLookup.RegistryLookup<Enchantment> enchants() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null
                : server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
    }
}
