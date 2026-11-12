package com.lyingice.ultraenchantment.registry;

import com.lyingice.ultraenchantment.Ultraenchantment;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * <b>标记类状态效果</b>——火焰附加两阶的「易伤 / 灼烧」标记。
 *
 * <p>它们本身没有任何 tick 行为，只作为<b>伤害结算的开关</b>：
 * {@code UECombatEvents} 在火焰伤害进入时检查受击者身上有没有这些效果，
 * 再决定把本次伤害翻倍或追加剩余生命值 1% 的伤害。
 *
 * <p>之所以做成真正的 {@link MobEffect} 而不是自定义组件：
 * <ul>
 *   <li>{@code post_attack} 的 {@code apply_mob_effect} 能直接施加它，无需额外事件；</li>
 *   <li>玩家能看到剩余时间与图标，符合「标记」的可观察语义；</li>
 *   <li>驱散、免疫、效果时长倍率等原版机制自动生效。</li>
 * </ul>
 */
public final class UEMobEffects {
    private UEMobEffects() {}

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Ultraenchantment.MODID);

    /** 火焰易伤：受到火焰伤害时，本次伤害翻倍。 */
    public static final DeferredHolder<MobEffect, MobEffect> FIRE_VULNERABILITY =
            MOB_EFFECTS.register("fire_vulnerability",
                    () -> new Marker(MobEffectCategory.HARMFUL, 0xFF8C1A));

    /** 灼烧之殇：受到火焰伤害时，额外承受剩余生命值 1% 的伤害。 */
    public static final DeferredHolder<MobEffect, MobEffect> BURN_AGONY =
            MOB_EFFECTS.register("burn_agony",
                    () -> new Marker(MobEffectCategory.HARMFUL, 0x8B0000));

    /** 只有标记作用、不参与每 tick 结算的状态效果。 */
    private static final class Marker extends MobEffect {
        private Marker(MobEffectCategory category, int color) {
            super(category, color);
        }
    }
}
