package com.lyingice.ultraenchantment.compat.apotheosis;

import dev.shadowsoffire.apothic_enchanting.ApothicEnchanting;
import dev.shadowsoffire.apothic_enchanting.EnchantmentInfo;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 神化上限的<b>真实读取实现</b>——本类是全项目<b>唯一</b> import 神化类型的地方。
 *
 * <h2>类加载隔离（AGENT.md P1-40）</h2>
 *
 * <p>本类只会从 {@link ApothCaps#vanillaCapOf} 里、且<b>仅在 {@code present()} 为真时</b>被调用。
 * 神化缺席时它一个字节都不会被链接，玩家不会因此抛 {@code NoClassDefFoundError}。
 * <b>不要</b>在其它任何地方直接引用本类。
 *
 * <h2>为什么读的是 EnchantmentInfo 而不是 EnchHooks</h2>
 *
 * <p>{@code EnchHooks.getMaxLevel(Enchantment)} 是 coremod 的注入目标，签名收的是
 * {@code Enchantment} 而非 {@code Holder}，它内部还要用
 * {@code MiscUtil.findHolder(registries, ench)} 反查 Holder——多一次查找。
 *
 * <p>我们手上本来就是 {@code Holder<Enchantment>}，直接用
 * {@code ApothicEnchanting.getEnchInfo(holder).getMaxLevel()} 是同一份数据、更少一环。
 */
final class ApothCapsImpl {

    private ApothCapsImpl() {
    }

    /**
     * 取神化给这条附魔配置的上限。
     *
     * <p>{@code EnchantmentInfo.getMaxLevel()} 内部还会被
     * {@code ApothicEnchanting.ENCH_HARD_CAPS} 夹一次（默认 127），所以这里拿到的已是最终值。
     */
    static int capOf(Holder<Enchantment> root) {
        EnchantmentInfo info = ApothicEnchanting.getEnchInfo(root);
        return info.getMaxLevel();
    }
}
