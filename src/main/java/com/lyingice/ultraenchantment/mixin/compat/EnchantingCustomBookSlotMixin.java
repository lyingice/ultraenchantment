package com.lyingice.ultraenchantment.mixin.compat;

import com.lyingice.ultraenchantment.logic.AscensionBookIO;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <b>附魔编辑台的「书槽」放行我们的载体书</b>。
 *
 * <h2>为什么单独要这一条</h2>
 *
 * <p>反编译实证：它的两个书槽（匿名内部类 {@code EnchantingCustomMenu$3} / {@code $4}）的
 * {@code mayPlace} 是<b>先判物品类型、再问菜单</b>：
 *
 * <pre>
 * return stack.getItem() == Items.ENCHANTED_BOOK
 *     && !handler.getStackInSlot(0).isEmpty()
 *     && (Config.ignoreEnchantmentLevelLimit || menu.checkCanPlaceEnchantedBook(stack));
 * </pre>
 *
 * <p>也就是说：我们只钩 {@code checkCanPlaceEnchantedBook} <b>根本轮不到</b>——
 * 我们的载体书是另一个物品（{@code advanced_enchanted_book}），
 * 第一关就被 {@code getItem() == ENCHANTED_BOOK} 拒了。这正是作者实测「还是放不进去」的根因。
 *
 * <h2>这里做什么</h2>
 *
 * <p>HEAD 处拦下：只要是<b>我们的载体书</b>，就按它<b>其余的前置条件</b>（台子里已经有物品）
 * 给结果，跳过物品类型那一关。其余情况原样放行。
 *
 * <p>⚠️ 目标写的是<b>匿名内部类的编号名</b>（{@code $3} / {@code $4}）。对方一旦增删内部类，
 * 编号可能移位 ⇒ mixin 在<b>加载期</b>报错（不是静默失效），届时按新的编号改这一处即可。
 */
@Mixin(targets = {
        "com.river_quinn.enchantment_custom_table.world.inventory.EnchantingCustomMenu$3",
        "com.river_quinn.enchantment_custom_table.world.inventory.EnchantingCustomMenu$4"
})
public abstract class EnchantingCustomBookSlotMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void ue$acceptInscriptionBook(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!AscensionBookIO.isInscriptionBook(stack)) {
            return;
        }
        IItemHandler handler = ((SlotItemHandler) (Object) this).getItemHandler();
        cir.setReturnValue(!handler.getStackInSlot(0).isEmpty());
    }

    // 「书一放进来就把条目落到物品上」的钩子只在 $3 上存在（$4 没有 setByPlayer），
    // 见 EnchantingCustomBookSlotApplyMixin。混在这里会让 $4 的注入在加载期报错。
}
