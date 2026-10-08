package com.lyingice.ultraenchantment.mixin.compat;

import com.lyingice.ultraenchantment.content.AscensionData;
import com.lyingice.ultraenchantment.registry.UEComponents;
import dev.shadowsoffire.apothic_enchanting.objects.TomeItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <b>9 种装备宝典的搬运补丁</b>。
 *
 * <p>{@code TomeItem.use} 会把一本「已附魔的宝典」变成原版附魔书，但它只搬
 * {@code minecraft:enchantments}：
 *
 * <pre>
 * EnchantmentHelper.setEnchantments(book, EnchantmentHelper.getEnchantmentsForCrafting(stack));
 * </pre>
 *
 * <p>它新建了一本 {@code enchanted_book}，所以我们的 {@code ultraenchantment:ascension}
 * <b>不会跟过去</b> —— 玩家手里那本进阶附魔书会「降级」成普通附魔书。
 * 这里在返回处把组件补上。
 *
 * <p>⚠️ 只允许在 Apothic Enchanting 装着时加载（compat 配置 + 插件门控）。
 */
@Mixin(TomeItem.class)
public abstract class TomeItemMixin {

    @Inject(method = "use", at = @At("RETURN"))
    private void ue$carryAscension(Level world, Player player, InteractionHand hand,
                                   CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (world.isClientSide || player == null) {
            return;
        }
        ItemStack result = cir.getReturnValue().getObject();
        if (result.isEmpty()) {
            return;
        }
        AscensionData source = UEComponents.ascensionOf(player.getItemInHand(hand));
        if (source.stages().isEmpty()) {
            return;
        }
        if (UEComponents.ascensionOf(result).stages().isEmpty()) {
            UEComponents.setAscension(result, source);
        }
    }
}
