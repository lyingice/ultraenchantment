package com.lyingice.ultraenchantment.mixin;

import com.lyingice.ultraenchantment.logic.TableAscension;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.EnchantingTableBlock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <b>原版附魔台的「进阶」注入点</b>（无神化时的路径）。
 *
 * <h2>为什么不用 NeoForge 的 {@code PlayerEnchantItemEvent}</h2>
 *
 * <p>那个事件确实在附魔写入物品之后触发，也带着 {@code instances}，但它<b>不带行号</b>——
 * 而触发概率是按行分的（0.1% / 0.2% / 0.4%）。所以直接注入 {@code clickMenuButton}
 * 拿那个 {@code int buttonId}，与神化路径（注入 {@code ApothEnchantmentMenu} 的同名方法）对称。
 *
 * <h2>两次注入</h2>
 *
 * <ul>
 *   <li>{@code HEAD}：拍快照（附魔前物品上已有的附魔 → 等级）；</li>
 *   <li>{@code RETURN}：附魔已经写完了，diff 出**本次新写的**，再交给
 *       {@link TableAscension#apply} 掷概率、推阶。</li>
 * </ul>
 *
 * <p>⚠️ 附魔能力在 RETURN 现场重算（原版那 6 行书架扫描），而不是从别处缓存 ——
 * 缓存在多玩家/多台子时必然串味。{@code access} 是 {@code EnchantmentMenu} 自己的
 * {@code ContainerLevelAccess}，能同时给出 level 与 pos。
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {

    @Shadow
    @Final
    private ContainerLevelAccess access;

    /** 附魔前的快照；只在一次 {@code clickMenuButton} 内有效。 */
    @Unique
    private Map<Holder<Enchantment>, Integer> ue$before;

    @Inject(method = "clickMenuButton", at = @At("HEAD"))
    private void ue$snapshotBeforeEnchant(Player player, int buttonId,
                                          CallbackInfoReturnable<Boolean> cir) {
        this.ue$before = TableAscension.snapshot(
                ((EnchantmentMenu) (Object) this).getSlot(0).getItem());
    }

    @Inject(method = "clickMenuButton", at = @At("RETURN"))
    private void ue$ascendAfterEnchant(Player player, int buttonId,
                                       CallbackInfoReturnable<Boolean> cir) {
        Map<Holder<Enchantment>, Integer> before = this.ue$before;
        this.ue$before = null;
        if (before == null || !cir.getReturnValueZ()) {
            return;
        }
        ItemStack stack = ((EnchantmentMenu) (Object) this).getSlot(0).getItem();
        if (stack.isEmpty()) {
            return;
        }
        this.access.execute((level, pos) -> {
            // 原版那 6 行：书架环求和 = 附魔能力
            double power = 0.0D;
            for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
                if (EnchantingTableBlock.isValidBookShelf(level, pos, offset)) {
                    BlockPos shelf = pos.offset(offset);
                    power += level.getBlockState(shelf).getEnchantPowerBonus(level, shelf);
                }
            }
            TableAscension.apply(level, stack, player,
                    TableAscension.Params.vanilla(buttonId, power),
                    before, level.random);
        });
    }
}
