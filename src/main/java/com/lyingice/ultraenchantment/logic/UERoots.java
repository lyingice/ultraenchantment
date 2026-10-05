package com.lyingice.ultraenchantment.logic;

import com.lyingice.ultraenchantment.content.LineageTier;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 「这个附魔属于哪条谱系」——<b>全模组唯一实现</b>（一条规则一份实现）。
 *
 * <p>两种输入都要认：
 * <ul>
 *   <li><b>原版 / 数据包附魔</b>：反查注册表 key；</li>
 *   <li><b>本模组的阶级附魔</b>（运行时组装、没有注册表条目）：它的<b>语义身份</b>就是谱系根源
 *       （阶级穿刺 ≡ {@code minecraft:impaling}），取 {@link EnchantmentFactory#rootOf}。</li>
 * </ul>
 *
 * <p><b>客户端也能用</b>：没有服务器时退回 {@link UELookups#enchantmentsForItemWrites(boolean)}
 * 的 side-aware lookup，靠对象身份比对反查 key（与 AGENT.md P0-17 同一套做法）。
 * 对外 API 与 dummy 兼容补丁都走这里，避免各写一份。
 */
public final class UERoots {
    private UERoots() {}

    /** 附魔 → 谱系根源 id；判不出来返回空。 */
    public static Optional<ResourceLocation> rootOf(@Nullable Enchantment enchantment) {
        if (enchantment == null) {
            return Optional.empty();
        }
        ResourceLocation synthetic = EnchantmentFactory.rootOf(enchantment);
        if (synthetic != null) {
            return Optional.of(synthetic);
        }
        return keyOf(enchantment).map(ResourceKey::location);
    }

    /** 附魔 → 谱系根源 key（合成附魔用它的语义根源当 key）。 */
    public static Optional<ResourceKey<Enchantment>> keyOf(@Nullable Enchantment enchantment) {
        if (enchantment == null) {
            return Optional.empty();
        }
        ResourceLocation synthetic = EnchantmentFactory.rootOf(enchantment);
        if (synthetic != null) {
            return Optional.of(ResourceKey.create(Registries.ENCHANTMENT, synthetic));
        }

        Registry<Enchantment> registry = UELookups.enchantmentRegistry();
        if (registry != null) {
            Optional<ResourceKey<Enchantment>> key = registry.getResourceKey(enchantment);
            if (key.isPresent()) {
                return key;
            }
        }
        // 客户端/无服务器：拿不到注册表对象，退回遍历 side-aware lookup 比对象身份。
        for (Holder.Reference<Enchantment> holder
                : UELookups.enchantmentsForItemWrites(true).listElements().toList()) {
            if (holder.value() == enchantment) {
                return Optional.of(holder.key());
            }
        }
        return Optional.empty();
    }

    /** 阶段条目 id → 阶级序号（0=原生 1=高阶 2=超级 3=究极）。 */
    public static int tierOfStage(ResourceLocation stageId) {
        return ProtectionLogic.tierOfStageId(stageId).ordinal() + 1;
    }

    /** 阶级序号 → {@link LineageTier}；越界返回空。 */
    public static Optional<LineageTier> lineageTier(int tier) {
        LineageTier[] values = LineageTier.values();
        return tier >= 0 && tier < values.length ? Optional.of(values[tier]) : Optional.empty();
    }
}
