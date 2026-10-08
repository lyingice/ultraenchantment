package com.lyingice.ultraenchantment.mixin.compat;

import com.lyingice.ultraenchantment.Ultraenchantment;
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

    /**
     * 开头兜底算出来的台面数值（结尾用它）。
     *
     * <p>⚠️ 为什么不能结尾再算/再读：它们的 {@code clickMenuButton} 在结尾会调
     * {@code slotsChanged(this.enchantSlots)} → {@code gatherStats()}，
     * 而那个方法走 {@code access.evaluate(...)}（access 可能是 NULL）⇒ 会把
     * {@code stats} **重新写回 INVALID**。所以必须在开头就把好值缓存下来。
     */
    @Unique
    private EnchantmentTableStats ue$freshStats;

    /** 兜底提示只报一次，避免每次点击都刷屏。 */
    @Unique
    private static boolean ue$warnedStats;

    /** 台面数值是不是「空」（= INVALID 的特征：位阶/阿卡那/量子化 全 0）。 */
    @Unique
    private static boolean ue$statsEmpty(EnchantmentTableStats stats) {
        return stats == null
                || (stats.eterna() == 0.0F && stats.arcana() == 0.0F && stats.quanta() == 0.0F);
    }

    @Inject(method = "clickMenuButton", at = @At("HEAD"))
    private void ue$snapshotBeforeEnchant(Player player, int id,
                                          CallbackInfoReturnable<Boolean> cir) {
        this.ue$before = TableAscension.snapshot(
                ((EnchantmentMenu) (Object) this).getSlot(0).getItem());
        // ⚠️ 台面数值兜底（这是「满位阶满阿卡那也永不进阶」的根因）。
        //
        //    它的 gatherStats() 是 this.access.evaluate((world, pos) -> ...).orElse(this)，
        //    而**第一个构造函数传的正是 ContainerLevelAccess.NULL** ⇒ 那个 lambda 根本不执行
        //    ⇒ stats 永远停在 EnchantmentTableStats.INVALID（位阶/阿卡那/量子化 全是 0）
        //    ⇒ 我们算出来的概率恒为 0%。实测日志坐实：
        //      「行=2 附魔能力/位阶=0.0 阿卡那=0.0 量子化=0.0 量子稳定=false 神化=true ⇒ 概率=0.0%」
        //
        //    兜底做法：stats 看起来是空的时候，用 **player.level() + pos** 直接重算一遍
        //    （与它内部 gatherStats 同一个 API，只是绕开那个可能为 NULL 的 access）。
        //    只在「空」时覆盖，所以不会跟它自己的同步/刷新打架。
        this.ue$freshStats = null;
        if (this.pos != null && player != null && ue$statsEmpty(this.stats)) {
            EnchantmentTableStats fresh = EnchantmentTableStats.gatherStats(player.level(), this.pos);
            if (fresh != null) {
                this.stats = fresh;                 // 也写回它的字段：它自己的附魔逻辑同样读这里
                this.ue$freshStats = fresh;         // 并缓存一份给结尾用（结尾那份会被它自己覆盖）
            }
        }
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
        if (stack.isEmpty()) {
            return;
        }
        // ⚠️ 结尾这份 stats 很可能已经被它自己的 slotsChanged() → gatherStats() 覆盖回 INVALID，
        //    所以「空」时优先用开头缓存的好值。
        EnchantmentTableStats snapshot = this.stats;
        if (ue$statsEmpty(snapshot)) {
            // 第二层保险：开头缓存的好值优先；万一连它也没有（开头 stats 本来是好的、
            // 中途被写回 INVALID），就地在结尾用 player.level() + pos 重算一次。
            snapshot = this.ue$freshStats;
            if (ue$statsEmpty(snapshot) && this.pos != null && player != null) {
                snapshot = EnchantmentTableStats.gatherStats(player.level(), this.pos);
                if (!ue$warnedStats) {          // 它这个 bug 每次点击都会触发 ⇒ 整个会话只提醒一次
                    ue$warnedStats = true;
                    Ultraenchantment.LOGGER.info(
                            "[UE] 神化台面数值取不到（对方 stats 为 INVALID），已自行兜底重算 ⇒"
                                    + " 位阶={} 阿卡那={} 量子化={}（本会话只提醒这一次）",
                            snapshot.eterna(), snapshot.arcana(), snapshot.quanta());
                }
            }
        }
        if (snapshot == null) {
            return;
        }
        TableAscension.apply(player.level(), stack, player,
                TableAscension.Params.apothic(id, snapshot.eterna(), snapshot.arcana(),
                        snapshot.quanta(), snapshot.stable()),
                before, player.level().random);
    }
}
