package com.lyingice.ultraenchantment.event;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.starlight.content.EvolutionData;
import com.lyingice.ultraenchantment.starlight.content.TierDefinition;
import com.lyingice.ultraenchantment.starlight.content.TreeNode;
import com.lyingice.ultraenchantment.starlight.content.TreeDefinition;
import com.lyingice.ultraenchantment.starlight.logic.EvolutionService;
import com.lyingice.ultraenchantment.starlight.logic.TreeIndex;
import com.lyingice.ultraenchantment.starlight.registry.StarlightComponents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 星光 Tooltip：
 * <ul>
 *   <li>附魔书：显示数据包指定的阶级，并用阶级色；</li>
 *   <li>已进化的物品：当前阶段附魔行改成阶级色；</li>
 *   <li>按住 Shift：显示「进阶自」与「可进阶为」的路线。</li>
 * </ul>
 *
 * <p>客户端事件；{@link Screen#hasShiftDown()} 需要客户端类，所以整个类只在 CLIENT 加载。
 */
@EventBusSubscriber(modid = Ultraenchantment.MODID, value = Dist.CLIENT)
public final class StarlightTooltipEvents {
    private StarlightTooltipEvents() {}

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        HolderLookup.RegistryLookup<Enchantment> enchants =
                CommonHooks.resolveLookup(Registries.ENCHANTMENT);
        if (enchants == null) {
            return;
        }

        // ① 已进化物品：定位树与当前节点
        ResourceLocation treeId = TreeIndex.treeIdOf(stack).orElse(null);
        if (treeId == null) {
            return;
        }
        TreeDefinition tree = TreeIndex.tree(treeId).orElse(null);
        if (tree == null) {
            return;
        }
        ResourceLocation currentId = EvolutionService.currentNode(treeId, tree, stack).orElse(null);
        TreeNode current = currentId == null ? null : tree.node(currentId).orElse(null);
        if (current == null) {
            return;
        }

        // ①a 所有阶段附魔行改成阶级色（按描述名前缀定位，兼容其它模组的行首符号）
        ItemEnchantments present = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (Holder<Enchantment> holder : present.keySet()) {
            ResourceLocation enchantmentId = holder.unwrapKey().map(ResourceKey::location).orElse(null);
            if (enchantmentId == null) {
                continue;
            }
            TierDefinition enchantTier = TreeIndex.tierOfEnchantment(enchantmentId).orElse(null);
            if (enchantTier == null) {
                continue;
            }
            String marker = holder.value().description().getString();
            TextColor tierColor = TextColor.fromRgb(enchantTier.color());
            List<Component> lines = event.getToolTip();
            for (int i = 0; i < lines.size(); i++) {
                if (stripLeadingSymbols(lines.get(i).getString()).startsWith(marker)) {
                    MutableComponent colored = lines.get(i).copy();
                    colored.withStyle(style -> style.withColor(tierColor));
                    lines.set(i, colored);
                    break;
                }
            }
        }

        List<Component> tooltip = event.getToolTip();

        // ①a+ 代码驱动阶段：树上多个节点共用同一个原附魔，
        // tierOfEnchantment 只能查到第一个（native）节点；这里用当前节点的阶级覆盖行色。
        Holder<Enchantment> currentHolder = enchants.get(
                ResourceKey.create(Registries.ENCHANTMENT, current.enchantment())).orElse(null);
        if (currentHolder != null) {
            TextColor currentColor = TextColor.fromRgb(TreeIndex.tier(current.tier()).color());
            String currentMarker = currentHolder.value().description().getString();
            List<Component> lines = event.getToolTip();
            for (int i = 0; i < lines.size(); i++) {
                if (stripLeadingSymbols(lines.get(i).getString()).startsWith(currentMarker)) {
                    MutableComponent colored = lines.get(i).copy();
                    colored.withStyle(style -> style.withColor(currentColor));
                    lines.set(i, colored);
                    break;
                }
            }
        }

        // ①b 未按 Shift：只给提示
        if (!Screen.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.ultraenchantment.starlight.hold_shift")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        // ①c 进阶自：优先用履历；没有履历时从当前节点的入边推断上一节点
        List<Component> history = new ArrayList<>();
        EvolutionData data = StarlightComponents.evolutionOf(stack);
        if (data != null && data.tree().equals(treeId)) {
            for (ResourceLocation nodeId : data.history()) {
                tree.node(nodeId).ifPresent(node -> history.add(nodeName(node, enchants)));
            }
        }
        if (history.isEmpty()) {
            for (var edge : tree.edges()) {
                if (edge.to().equals(current.id())) {
                    tree.node(edge.from()).ifPresent(node -> history.add(nodeName(node, enchants)));
                    break;
                }
            }
        }
        if (!history.isEmpty()) {
            MutableComponent line = Component.translatable(
                    "tooltip.ultraenchantment.starlight.from").withStyle(ChatFormatting.GRAY);
            line.append(join(history, " → "));
            tooltip.add(line);
        }

        // ①d 可进阶为：当前节点的全部出边目标
        List<Component> next = new ArrayList<>();
        for (var edge : tree.edgesFrom(current.id())) {
            tree.node(edge.to()).ifPresent(node -> next.add(nodeName(node, enchants)));
        }
        if (next.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.ultraenchantment.starlight.terminal")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            MutableComponent line = Component.translatable(
                    "tooltip.ultraenchantment.starlight.to").withStyle(ChatFormatting.GRAY);
            line.append(join(next, " / "));
            tooltip.add(line);
        }
    }

    private static Component nodeName(TreeNode node,
                                      HolderLookup.RegistryLookup<Enchantment> enchants) {
        Holder<Enchantment> holder = enchants.get(
                ResourceKey.create(Registries.ENCHANTMENT, node.enchantment())).orElse(null);
        return holder == null
                ? Component.literal(node.enchantment().getPath())
                : holder.value().description();
    }

    private static Component join(List<Component> parts, String separator) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(Component.literal(separator).withStyle(ChatFormatting.DARK_GRAY));
            }
            out.append(parts.get(i));
        }
        return out;
    }

    /** 剥掉行首的非字母数字字符（其它模组的星标、空格等）。 */
    private static String stripLeadingSymbols(String text) {
        int i = 0;
        while (i < text.length() && !Character.isLetterOrDigit(text.charAt(i))) {
            i++;
        }
        return text.substring(i);
    }
}
