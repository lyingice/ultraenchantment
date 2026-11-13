package com.lyingice.ultraenchantment.starlight.client;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.starlight.content.TierDefinition;
import com.lyingice.ultraenchantment.starlight.logic.TreeIndex;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * 附魔书封面的阶级推导。
 *
 * <p><b>不注册任何自定义组件</b>：只读原版 {@code minecraft:stored_enchantments}。
 * 书里存的是阶段附魔时，用 {@link TreeIndex} 查它在数据包树里的阶级；
 * 没有阶段附魔的原版书返回 0，保持原版外观。
 */
public final class StarlightBookTier {
    private StarlightBookTier() {}

    /** 书上存储的阶段附魔里最高的阶级序号；没有阶段附魔返回 0。 */
    public static int tierOf(ItemStack stack) {
        if (!stack.is(Items.ENCHANTED_BOOK)) {
            return 0;
        }
        int best = 0;
        for (Holder<Enchantment> holder : EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet()) {
            ResourceLocation id = holder.unwrapKey().map(ResourceKey::location).orElse(null);
            if (id == null) {
                continue;
            }
            TierDefinition tier = TreeIndex.tierOfEnchantment(id).orElse(null);
            if (tier != null) {
                best = Math.max(best, tier.ordinal());
            }
        }
        return best;
    }

    /** 阶级序号 → 封面模型 id；0/未知返回 {@code null}（保持原版书模型）。 */
    public static ResourceLocation modelFor(int tier) {
        return switch (tier) {
            case 1 -> id("item/enchanted_book");
            case 2 -> id("item/enchanted_book_super");
            case 3 -> id("item/enchanted_book_ultra");
            default -> null;
        };
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Ultraenchantment.MODID, path);
    }
}
