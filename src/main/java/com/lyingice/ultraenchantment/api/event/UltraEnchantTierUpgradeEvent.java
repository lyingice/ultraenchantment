package com.lyingice.ultraenchantment.api.event;

import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * <b>附魔进阶 / 提级</b>事件：某件物品上某条附魔即将改成更高的阶级，或更高级的曲线等级。
 *
 * <p><b>可取消</b>：{@link #setCanceled(boolean) 取消}后调用方放弃本次写入
 * （物品不变、材料不扣）。取消是「整次操作作废」，不是「改小一点」。
 *
 * <h2>触发点</h2>
 * <ul>
 *   <li>铁砧：进阶书升阶（{@link Source#ANVIL_BOOK}）</li>
 *   <li>铁砧：升级书提级（{@link Source#UPGRADE_BOOK}）</li>
 *   <li>铁砧：同名装备合并提级（{@link Source#MERGE}）</li>
 *   <li>对外 API 调用（{@link Source#API}）</li>
 * </ul>
 *
 * <p>⚠️ <b>本事件只描述「即将发生的改变」</b>：{@link #oldTier()} / {@link #oldCurveLevel()}
 * 是物品当前的状态，物品还<b>没有</b>被修改。要在监听里读"改完之后"的样子，请自行按 new 值推算。
 */
public class UltraEnchantTierUpgradeEvent extends Event implements ICancellableEvent {

    /** 触发来源。 */
    public enum Source {
        /** 铁砧 + 进阶附魔书（阶级 +1，曲线等级归 1）。 */
        ANVIL_BOOK,
        /** 铁砧 + 升级书（阶级不变，曲线等级提高）。 */
        UPGRADE_BOOK,
        /** 铁砧 + 同名装备合并（曲线等级提高）。 */
        MERGE,
        /** 附魔进阶台（消耗图书馆库存，不消耗经验）。 */
        ASCENSION_TABLE,
        /** 其它模组通过 {@link com.lyingice.ultraenchantment.api.UltraEnchantmentApi} 调用。 */
        API
    }

    private final ItemStack stack;
    private final ResourceLocation rootId;
    @Nullable
    private final Holder<Enchantment> enchantment;
    private final int oldTier;
    private final int newTier;
    private final int oldCurveLevel;
    private final int newCurveLevel;
    private final Source source;

    public UltraEnchantTierUpgradeEvent(ItemStack stack, ResourceLocation rootId,
                                        @Nullable Holder<Enchantment> enchantment,
                                        int oldTier, int newTier,
                                        int oldCurveLevel, int newCurveLevel,
                                        Source source) {
        this.stack = stack;
        this.rootId = rootId;
        this.enchantment = enchantment;
        this.oldTier = oldTier;
        this.newTier = newTier;
        this.oldCurveLevel = oldCurveLevel;
        this.newCurveLevel = newCurveLevel;
        this.source = source;
    }

    /** 被改的物品（<b>尚未修改</b>；监听者若需要副本请自行 {@code copy()}）。 */
    public ItemStack stack() {
        return this.stack;
    }

    /** 谱系根源 id，例如 {@code minecraft:sharpness}。 */
    public ResourceLocation rootId() {
        return this.rootId;
    }

    /** 谱系根源的附魔 holder；两侧任一侧拿不到注册表时为 {@code null}（此时用 {@link #rootId()}）。 */
    @Nullable
    public Holder<Enchantment> enchantment() {
        return this.enchantment;
    }

    /** 改动前的阶级：0=基础（未进阶）1=高阶 2=超级 3=究极。 */
    public int oldTier() {
        return this.oldTier;
    }

    /** 改动后的阶级（同上取值）。 */
    public int newTier() {
        return this.newTier;
    }

    /** 改动前该阶的曲线等级；未进阶时为 0。 */
    public int oldCurveLevel() {
        return this.oldCurveLevel;
    }

    /** 改动后该阶的曲线等级（升阶时为 1）。 */
    public int newCurveLevel() {
        return this.newCurveLevel;
    }

    public Source source() {
        return this.source;
    }
}
