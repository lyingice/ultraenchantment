package com.lyingice.ultraenchantment.logic;

import com.lyingice.ultraenchantment.UEConfig;
import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.content.UEEffect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * <b>`ue:*` 效果的执行辅助</b>——监听器里反复出现的那几件事。
 *
 * <p>这里不承载任何「哪种效果怎么算」的业务逻辑（那在各监听器里），只负责把
 * 通用动作做对：施加状态效果、打真实伤害、换位、驱散、服务端判定、上限收紧。
 */
public final class UEEffectExecution {
    private UEEffectExecution() {}

    /** 动能伤害的伤害类型 id（数据包 {@code data/ultraenchantment/damage_type/kinetic.json}）。 */
    public static final ResourceKey<DamageType> KINETIC_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(Ultraenchantment.MODID, "kinetic"));

    // ── 上限收紧 ───────────────────────────────────────────────────────

    /** 概率 / 比例：clamp 到 [0,1]。 */
    public static float clamp01(double value) {
        return (float) Math.max(0.0, Math.min(1.0, value));
    }

    /** 真实伤害比例：先 clamp 到 [0,1]，再被全局上限压住。 */
    public static float clampRealRatio(double value) {
        return (float) Math.min(clamp01(value), UEConfig.MAX_REAL_DAMAGE_CAP.get());
    }

    /** 双重保护单件减伤：clamp 到 [0, 全局单件上限]。 */
    public static float clampProtection(double value) {
        return (float) Math.min(Math.max(0.0, value), UEConfig.MAX_DOUBLE_PROTECTION_PER_ITEM.get());
    }

    // ── 状态效果 ───────────────────────────────────────────────────────

    /**
     * 施加一条 {@link UEEffect.AppliedEffect}。
     *
     * <p>{@code target} 决定作用对象：{@code SELF} = 装备者，{@code ATTACKER} = 攻击者。
     * 攻击者缺席（环境伤害）时该条被跳过——不静默作用于装备者，那会改变语义。
     */
    public static void applyEffect(UEEffect.AppliedEffect applied, int level,
                                   LivingEntity self, LivingEntity attacker) {
        LivingEntity target = applied.target() == UEEffect.EffectTarget.SELF ? self : attacker;
        if (target == null || !target.isAlive()) {
            return;
        }
        int duration = Math.max(1, applied.duration().atInt(level));
        int amplifier = Math.max(0, applied.amplifier().atInt(level));
        target.addEffect(new MobEffectInstance(applied.effect(), duration, amplifier));
    }

    /** 施加一组状态效果。 */
    public static void applyEffects(List<UEEffect.AppliedEffect> effects, int level,
                                    LivingEntity self, LivingEntity attacker) {
        for (UEEffect.AppliedEffect applied : effects) {
            applyEffect(applied, level, self, attacker);
        }
    }

    /** 清除目标全部状态效果（乐观语义，负面也一并清除）。 */
    public static void clearEffects(LivingEntity target) {
        for (Holder<MobEffect> effect : new ArrayList<>(target.getActiveEffectsMap().keySet())) {
            target.removeEffect(effect);
        }
    }

    /**
     * 驱散目标的正面状态效果，最多 {@code count} 个。
     *
     * @return 实际移除的数量
     */
    public static int stripBuffs(LivingEntity target, int count) {
        if (count <= 0) {
            return 0;
        }
        int removed = 0;
        for (Holder<MobEffect> effect : new ArrayList<>(target.getActiveEffectsMap().keySet())) {
            if (removed >= count) {
                break;
            }
            // 只拿有益的：负面效果是「我方施加的惩罚」，驱散它等于帮敌人。
            if (effect.value().isBeneficial() && target.removeEffect(effect)) {
                removed++;
            }
        }
        return removed;
    }

    // ── 真实伤害 ───────────────────────────────────────────────────────

    /**
     * 打出一次<b>无视护甲/保护/无敌帧</b>的真实伤害。
     *
     * <p>用 {@code setHealth(getHealth() - amount)} 而不是 {@code hurt(...)}：
     * 后者的护甲、保护、无敌帧、限伤都会重新参与，就不是「真实伤害」了。
     *
     * <p><b>不主动调用 {@code die()}</b>：本方法只在 {@code LivingDamageEvent.Post} 里被调用，
     * 而那发生在原版 {@code hurt()} 内部、{@code actuallyHurt()} 的末尾——
     * 紧接着 {@code hurt()} 自己就会检查 {@code isDeadOrDying()} 并调用 {@code die(source)}。
     * 这里再调一次会让 {@code LivingDeathEvent} 触发两遍（事件在 die 的实死判定之前发出），
     * 所以把「死亡」交还给原版流程，只负责把血量扣掉。
     *
     * @param amount 伤害点数（正数）
     */
    public static void applyTrueDamage(LivingEntity target, float amount) {
        if (amount <= 0.0f || !target.isAlive()) {
            return;
        }
        target.setHealth(target.getHealth() - amount);
    }

    // ── 动能伤害 ───────────────────────────────────────────────────────

    /**
     * 打出一次<b>动能类型</b>的伤害。
     *
     * <p>与 {@link #applyTrueDamage} 不同：这是一次完整的 {@code hurt}，
     * 会正常经过护甲、保护、附魔减伤与死亡流程；它只是拥有独立的伤害类型
     * （{@code ultraenchantment:kinetic}），因此可以与「真实伤害」并存且互不混淆。
     *
     * <p>命中前清零无敌帧：这次伤害是「命中后的追加」，不应该被刚吃过的原伤害挡掉。
     * 数据包缺少该伤害类型时退回 {@code generic}，保证功能不因数据缺失而静默消失。
     *
     * @return 是否真的造成了伤害
     */
    public static boolean applyKineticDamage(LivingEntity attacker, LivingEntity victim, float amount) {
        if (attacker == null || victim == null || amount <= 0.0f
                || victim.level().isClientSide() || !victim.isAlive()) {
            return false;
        }

        Holder<DamageType> type = victim.level().registryAccess()
                .registry(Registries.DAMAGE_TYPE)
                .flatMap(registry -> registry.getHolder(KINETIC_TYPE))
                .orElse(null);
        DamageSource source = type == null
                ? attacker.damageSources().generic()
                : new DamageSource(type, attacker, attacker);

        victim.invulnerableTime = 0;
        victim.hurtTime = 0;
        return victim.hurt(source, amount);
    }

    // ── 换位 ───────────────────────────────────────────────────────────

    /**
     * 安全换位：两个实体互换位置与朝向。
     *
     * <p>前置检查（任一不满足即放弃并返回 {@code false}）：
     * <ul>
     *   <li>同维度、都存活、非同一实体；</li>
     *   <li>都未骑乘、未载人——否则互换会把乘客/载具位置搞乱；</li>
     *   <li>服务端。</li>
     * </ul>
     */
    public static boolean swap(LivingEntity a, LivingEntity b) {
        if (a == b || a.level() != b.level() || a.level().isClientSide()) {
            return false;
        }
        if (!a.isAlive() || !b.isAlive() || a.isPassenger() || b.isPassenger()) {
            return false;
        }
        if (!a.getPassengers().isEmpty() || !b.getPassengers().isEmpty()) {
            return false;
        }

        double ax = a.getX();
        double ay = a.getY();
        double az = a.getZ();
        float ayRot = a.getYRot();
        float axRot = a.getXRot();

        a.moveTo(b.getX(), b.getY(), b.getZ(), b.getYRot(), b.getXRot());
        b.moveTo(ax, ay, az, ayRot, axRot);
        // 位移同步交给本 tick 的实体追踪：ServerEntity 侦测到大位移时会下发传送包，
        // 因此这里不需要也不应该手工广播任何实体事件。
        return true;
    }

    /** 该实体是否正载着乘客（换位前置检查用）。 */
    public static boolean carriesPassengers(LivingEntity entity) {
        return !entity.getPassengers().isEmpty();
    }

    /** 该效果是否应当被全局开关禁用。 */
    public static boolean disabled() {
        return !UEConfig.UE_EFFECTS_ENABLED.get();
    }

    /** 是否在服务端（扩展效果只在服务端结算，防双端重复）。 */
    public static boolean serverSide(LivingEntity entity) {
        return entity != null && !entity.level().isClientSide();
    }
}
