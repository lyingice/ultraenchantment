package com.lyingice.ultraenchantment.mixin.compat;

import com.lyingice.ultraenchantment.logic.AscensionBookIO;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>附魔编辑台：书一放进来就把条目落到物品上。</b>
 *
 * <p>作者实测：外面的载体书放进槽位后，物品上出现的仍是<b>普通附魔</b>。原因是
 * 「书放进槽位」这一步<b>不走</b> {@code addEnchantment}（那是点网格条目才走的路径），
 * 所以挂在 {@code addEnchantment} 上的写回钩子不会被触发。
 *
 * <p>这里在槽位内容变化的 {@code setByPlayer} 末尾补一刀：放进来的是我们的载体书，
 * 就把它的条目（阶级 + 曲线等级）写到槽 0 的物品上。{@code applyTo} 只对
 * 「物品上确实已经有这条附魔」的条目生效，所以这一刀不会无中生有；
 * 而且它取的是物品自己组件里的 Holder，两侧跑都安全（不会踩 P0-17）。
 *
 * <h2>为什么单独一个 mixin</h2>
 *
 * <p>该菜单有两个书槽内部类：{@code $3} 有 {@code setByPlayer}，{@code $4} 没有。
 * 把这条注入和 {@code mayPlace} 写在同一个 {@code @Mixin(targets={$3,$4})} 里，
 * 会在 {@code $4} 上以「找不到目标方法」失败（加载期 InvalidInjectionException）。
 * 因此 {@link EnchantingCustomBookSlotMixin} 只管两个槽都有的 {@code mayPlace}，
 * 本类只打 {@code $3}。
 */
@Mixin(targets = "com.river_quinn.enchantment_custom_table.world.inventory.EnchantingCustomMenu$3")
public abstract class EnchantingCustomBookSlotApplyMixin {

    @Inject(method = "setByPlayer", at = @At("TAIL"))
    private void ue$applyBookOnPlace(ItemStack newStack, ItemStack oldStack, CallbackInfo ci) {
        ItemStack book = newStack != null && !newStack.isEmpty() ? newStack : oldStack;
        if (!AscensionBookIO.isInscriptionBook(book)) {
            return;
        }
        IItemHandler handler = ((SlotItemHandler) (Object) this).getItemHandler();
        AscensionBookIO.applyTo(handler.getStackInSlot(0), book);
    }
}
