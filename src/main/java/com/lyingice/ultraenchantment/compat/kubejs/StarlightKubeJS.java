package com.lyingice.ultraenchantment.compat.kubejs;

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
 * UltraEnchantment.tierOfEnchantment('ultraenchantment:starlight/super_sharpness_blood')
 * UltraEnchantment.tierColor('ultraenchantment:super')
 * UltraEnchantment.canEvolve(stack, 'ultraenchantment:blood')
 * UltraEnchantment.evolve(stack, 'ultraenchantment:blood')
 * }</pre>
 *
 * <p>{@code evolve} 是<b>脚本级直接进化</b>：不掷概率、不扣经验/催化剂，
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
     * 造一本带阶段附魔的原版附魔书——封面由客户端 Mixin 按阶段阶级自动切换。
     *
     * <pre>{@code
     * event.player.give(UltraEnchantment.stageBook('ultraenchantment:starlight/super_sharpness_blood', 1))
     * }</pre>
     */
    public static ItemStack stageBook(String enchantmentId, int level) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        HolderLookup.RegistryLookup<Enchantment> enchants = enchants();
        ResourceLocation id = ResourceLocation.tryParse(enchantmentId);
        if (enchants == null || id == null) {
            return book;
        }
        Holder<Enchantment> holder = enchants.get(
                ResourceKey.create(Registries.ENCHANTMENT, id)).orElse(null);
        if (holder == null) {
            return book;
        }
        ItemEnchantments.Mutable table = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        table.set(holder, Mth.clamp(level, 1, 255));
        book.set(DataComponents.STORED_ENCHANTMENTS, table.toImmutable());
        return book;
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
