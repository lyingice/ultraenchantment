package com.lyingice.ultraenchantment.logic;

import com.lyingice.ultraenchantment.api.event.UltraEnchantLockChangeEvent;
import com.lyingice.ultraenchantment.api.event.UltraEnchantTierUpgradeEvent;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 对外事件的<b>唯一发射点</b>——所有会改进阶状态的路径都从这里发。
 *
 * <p>集中一处的好处：铁砧（进阶书/升级书）、同名装备合并、祛咒石、以及 API 调用
 * 触发的事件字段与顺序完全一致，监听者不必区分来源（来源在事件里的 {@code source}）。
 */
public final class UEEvents {
    private UEEvents() {}

    /**
     * 发「进阶 / 提级」事件。
     *
     * @return {@code false} = 被监听者取消，<b>调用方必须放弃本次写入</b>（不落盘、不扣材料）
     */
    public static boolean fireTierUpgrade(ItemStack stack, ResourceLocation rootId,
                                          @Nullable Holder<Enchantment> enchantment,
                                          int oldTier, int newTier,
                                          int oldCurveLevel, int newCurveLevel,
                                          UltraEnchantTierUpgradeEvent.Source source) {
        UltraEnchantTierUpgradeEvent event = new UltraEnchantTierUpgradeEvent(stack, rootId, enchantment,
                oldTier, newTier, oldCurveLevel, newCurveLevel, source);
        NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled();
    }

    /** 发「锁定状态变化」事件（通知型，不可取消）。 */
    public static void fireLockChange(ItemStack stack, ResourceLocation rootId,
                                      @Nullable Holder<Enchantment> enchantment,
                                      boolean locked, UltraEnchantLockChangeEvent.Cause cause) {
        NeoForge.EVENT_BUS.post(new UltraEnchantLockChangeEvent(stack, rootId, enchantment, locked, cause));
    }
}
