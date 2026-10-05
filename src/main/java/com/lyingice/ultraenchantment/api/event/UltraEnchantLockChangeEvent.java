package com.lyingice.ultraenchantment.api.event;

import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.Event;

/**
 * <b>锁定状态变化</b>事件：某条附魔从「未进阶」变成「已进阶（受保护）」，或反过来。
 *
 * <p>进阶附魔受保护：砂轮洗不掉、铁砧合并不会抹除，只能用对应阶级的祛咒石解除。
 * 本事件就是这条状态线的通知。
 *
 * <h2>为什么<b>不可取消</b></h2>
 *
 * <p>解锁基本由玩家主动行为触发（祛咒石已经扣了耐久）。若允许取消，会出现
 * 「石头没了、附魔还在」的不一致状态。要阻止某次操作，请在<b>操作发生前</b>的
 * 对应事件里拦（砂轮/铁砧），不要把本事件当拦截点。
 */
public class UltraEnchantLockChangeEvent extends Event {

    /** 变化的原因。 */
    public enum Cause {
        /** 祛咒石解除同档保护。 */
        CURSE_STONE,
        /** API 主动移除进阶状态（{@code removeUltraEnchant}）。 */
        REMOVE,
        /** 其它模组通过 API 修改（{@code setTierLevel} / {@code unlockEnchant}）。 */
        API
    }

    private final ItemStack stack;
    private final ResourceLocation rootId;
    @Nullable
    private final Holder<Enchantment> enchantment;
    private final boolean locked;
    private final Cause cause;

    public UltraEnchantLockChangeEvent(ItemStack stack, ResourceLocation rootId,
                                       @Nullable Holder<Enchantment> enchantment,
                                       boolean locked, Cause cause) {
        this.stack = stack;
        this.rootId = rootId;
        this.enchantment = enchantment;
        this.locked = locked;
        this.cause = cause;
    }

    /** 状态已变化的物品。 */
    public ItemStack stack() {
        return this.stack;
    }

    /** 谱系根源 id。 */
    public ResourceLocation rootId() {
        return this.rootId;
    }

    /** 谱系根源的 holder；拿不到时为 {@code null}。 */
    @Nullable
    public Holder<Enchantment> enchantment() {
        return this.enchantment;
    }

    /** {@code true} = 现在已锁定（受保护）；{@code false} = 刚刚解除。 */
    public boolean locked() {
        return this.locked;
    }

    public Cause cause() {
        return this.cause;
    }
}
