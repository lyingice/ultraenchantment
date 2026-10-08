package com.lyingice.ultraenchantment.mixin.compat;

import com.lyingice.ultraenchantment.logic.TableAscension;
import dev.shadowsoffire.apothic_enchanting.table.ApothEnchantmentMenu;
import dev.shadowsoffire.apothic_enchanting.table.EnchantmentTableStats;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <b>神化附魔台的「进阶」注入点</b>（装了 Apothic Enchanting 时的路径）。
 *
 * <h2>为什么原版那条 mixin 帮不上忙</h2>
 *
 * <p>{@code ApothEnchantmentMenu} 虽然 {@code extends EnchantmentMenu}，但它
 * <b>重写了 {@code clickMenuButton}</b> —— 重写方法是另一个方法，
 * 打在父类方法上的注入不会跟着跑。所以这里必须再注入一次。
 *
 * <h2>四个数值从哪来</h2>
 *
 * <p>{@code this.stats}（神化自己的字段，类型 {@link EnchantmentTableStats}）：
 * {@code eterna()} = <b>位阶</b>、{@code quanta()} = <b>量子化</b>、
 * {@code arcana()} = <b>阿卡那</b>、{@code stable()} = <b>量子稳定</b>。
 * 它在 {@code slotsChanged} 里由 {@code gatherStats()} 填充；这里读之前再保险地刷新一次。
 *
 * <h2>等级策略</h2>
 *
 * <p>无神化那条走「保持原等级」，这条走 {@code keepLevel = false}（按量子化随机）——
 * 正是作者要求的「神化时等级随机」。
 *
 * <p>⚠️ 本类只允许在 {@code Apothic Enchanting} 装着的时候被加载：
 * 它由 {@code ultraenchantment.compat.mixins.json} + {@code UECompatMixinPlugin} 门控。
 */
@Mixin(ApothEnchantmentMenu.class)
public abstract class ApothEnchantmentMenuMixin {

    @Shadow
    protected EnchantmentTableStats stats;

    @Shadow
    @Final
    protected BlockPos pos;

    @Unique
    private Map<Holder<Enchantment>, Integer> ue$before;

    @Inject(method = "clickMenuButton", at = @At("HEAD"))
    private void ue$snapshotBeforeEnchant(Player player, int id,
                                          CallbackInfoReturnable<Boolean> cir) {
        this.ue$before = TableAscension.snapshot(
                ((EnchantmentMenu) (Object) this).getSlot(0).getItem());
    }

    @Inject(method = "clickMenuButton", at = @At("RETURN"))
    private void ue$ascendAfterEnchant(Player player, int id,
                                       CallbackInfoReturnable<Boolean> cir) {
        Map<Holder<Enchantment>, Integer> before = this.ue$before;
        this.ue$before = null;
        if (before == null || !cir.getReturnValueZ()) {
            return;
        }
        ItemStack stack = ((EnchantmentMenu) (Object) this).getSlot(0).getItem();
        if (stack.isEmpty() || this.stats == null) {
            return;
        }
        EnchantmentTableStats snapshot = this.stats;
        TableAscension.apply(player.level(), stack, player,
                new TableAscension.Params(id, snapshot.eterna(), snapshot.arcana(),
                        snapshot.stable(), true),
                before, player.level().random, false);
    }
}
