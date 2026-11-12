package com.lyingice.ultraenchantment.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffect;

/**
 * <b>`ue:*` 扩展效果</b>——那些「原版 {@code EnchantmentEffectComponents} 表达不了、
 * 必须挂在实体/伤害/盾牌/跳跃等生命周期节点上」的效果。
 *
 * <h2>为什么不能全塞进原版 effects</h2>
 *
 * <p>阶段条目的 {@code effects} 字段复用 {@code EnchantmentEffectComponents.CODEC}，
 * 只能表达原版那 27 种效果（伤害、减免、击退、点燃……）。它们都由原版执行器在固定
 * 节点驱动。<b>本清单里有一半的效果没有对应的原版组件</b>：
 *
 * <ul>
 *   <li>真实伤害、闪避、反伤、吸血：需要在伤害结算的<b>不同阶段</b>介入，
 *       而不是「加一点伤害」；</li>
 *   <li>跳过死亡、多段跳、换位、自动拾取：根本不在伤害管线上；</li>
 *   <li>格挡/破防：由盾牌逻辑驱动，原版通过 {@code post_attack} 拿不到「格挡成功」这个事实。</li>
 * </ul>
 *
 * <p>因此这些效果放进阶段条目<b>独立的</b> {@code ue_effects} 字段，由本模组自己的
 * 监听器执行；原版 {@code effects} 字段继续承载数据驱动的原版效果，两者互不干扰。
 *
 * <h2>数值形状</h2>
 *
 * <p>每个数值都是一个 {@link Growth}（{@code base + per_level×(等级-1)}），
 * 等级取 {@code AscensionData.tierLevel}。比例、概率、上限在<b>解析与运行时都 clamp</b>，
 * 所以数据包写出越界值不会让效果失控。
 *
 * <h2>序列化</h2>
 *
 * <p>JSON 用 {@code type} 字段区分 22 种效果，字段名与 {@link #id()} 一致：
 * <pre>{@code
 * { "type": "real_damage", "ratio": {"base": 0.3}, "cap": {"base": 0.6} }
 * }</pre>
 */
public sealed interface UEEffect {

    /** 效果类型 id（JSON 的 {@code type} 字段）。 */
    String id();

    /** 人类可读描述——效果总表与 tooltip 直接用它。 */
    String describe();

    // ── 类型 id 常量（避免散落的字面量） ───────────────────────────────

    String HEMOPHAGIA = "hemophagia";
    String DOUBLE_PROTECTION = "double_protection";
    String BE_ATTACKED = "be_attacked";
    String REAL_DAMAGE = "real_damage";
    String SKIP_DEATH = "skip_death";
    String PERCENT_DAMAGE = "percent_damage";
    String MULTI_JUMP = "multi_jump";
    String DAMAGE_DODGE = "damage_dodge";
    String DAMAGE_REFLECT = "damage_reflect";
    String PROJECTILE_SPEED = "projectile_speed";
    String BYPASS_IFRAMES = "bypass_iframes";
    String STRIP_BUFFS = "strip_buffs";
    String SELF_VULNERABILITY = "self_vulnerability";
    String ON_BLOCK = "on_block";
    String ON_SHIELD_DISABLED = "on_shield_disabled";
    String SWAP_POSITION = "swap_position";
    String AUTO_PICKUP = "auto_pickup";
    String SCALING_DAMAGE = "scaling_damage";
    String KINETIC_DAMAGE = "kinetic_damage";
    String INFERNO_MIGHT = "inferno_might";
    String LOOT_DUPLICATION = "loot_duplication";
    String SWEEP_DAMAGE = "sweep_damage";

    // ── 联合类型与分派 ─────────────────────────────────────────────────

    /** 全部效果的 {@code id → MapCodec}。 */
    Map<String, MapCodec<? extends UEEffect>> TYPES = Map.ofEntries(
            Map.entry(HEMOPHAGIA, Hemophagia.CODEC),
            Map.entry(DOUBLE_PROTECTION, DoubleProtection.CODEC),
            Map.entry(BE_ATTACKED, BeAttacked.CODEC),
            Map.entry(REAL_DAMAGE, RealDamage.CODEC),
            Map.entry(SKIP_DEATH, SkipDeath.CODEC),
            Map.entry(PERCENT_DAMAGE, PercentDamage.CODEC),
            Map.entry(MULTI_JUMP, MultiJump.CODEC),
            Map.entry(DAMAGE_DODGE, DamageDodge.CODEC),
            Map.entry(DAMAGE_REFLECT, DamageReflect.CODEC),
            Map.entry(PROJECTILE_SPEED, ProjectileSpeed.CODEC),
            Map.entry(BYPASS_IFRAMES, BypassIframes.CODEC),
            Map.entry(STRIP_BUFFS, StripBuffs.CODEC),
            Map.entry(SELF_VULNERABILITY, SelfVulnerability.CODEC),
            Map.entry(ON_BLOCK, OnBlock.CODEC),
            Map.entry(ON_SHIELD_DISABLED, OnShieldDisabled.CODEC),
            Map.entry(SWAP_POSITION, SwapPosition.CODEC),
            Map.entry(AUTO_PICKUP, AutoPickup.CODEC),
            Map.entry(SCALING_DAMAGE, ScalingDamage.CODEC),
            Map.entry(KINETIC_DAMAGE, KineticDamage.CODEC),
            Map.entry(INFERNO_MIGHT, InfernoMight.CODEC),
            Map.entry(LOOT_DUPLICATION, LootDuplication.CODEC),
            Map.entry(SWEEP_DAMAGE, SweepDamage.CODEC));

    /** 单个效果：内联 {@code type} 分派。 */
    Codec<UEEffect> CODEC = Codec.STRING.dispatch("type", UEEffect::id,
            id -> {
                MapCodec<? extends UEEffect> codec = TYPES.get(id);
                if (codec == null) {
                    throw new IllegalArgumentException("未知的 ue 效果类型：" + id);
                }
                return codec;
            });

    /** 列表（阶段条目的 {@code ue_effects} 字段）。 */
    Codec<List<UEEffect>> LIST_CODEC = CODEC.listOf();

    // ── 通用子结构 ─────────────────────────────────────────────────────

    /** 状态效果作用对象。 */
    enum EffectTarget implements StringRepresentable {
        /** 装备者自己。 */
        SELF("self"),
        /** 攻击者 / 伤害来源。 */
        ATTACKER("attacker");

        public static final Codec<EffectTarget> CODEC = StringRepresentable.fromEnum(EffectTarget::values);

        private final String id;

        EffectTarget(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }
    }

    /**
     * 一条「施加状态效果」指令——被 {@link BeAttacked}、{@link OnBlock} 等共用。
     *
     * @param effect    要施加的状态效果
     * @param duration  持续 tick（随等级）
     * @param amplifier 强度序号（0 = I 级，随等级）
     * @param target    作用对象
     */
    record AppliedEffect(Holder<MobEffect> effect, Growth duration, Growth amplifier,
                         EffectTarget target) {
        public static final Codec<AppliedEffect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                MobEffect.CODEC.fieldOf("effect").forGetter(AppliedEffect::effect),
                Growth.CODEC.optionalFieldOf("duration", Growth.of(100.0f)).forGetter(AppliedEffect::duration),
                Growth.CODEC.optionalFieldOf("amplifier", Growth.CONSTANT_ZERO).forGetter(AppliedEffect::amplifier),
                EffectTarget.CODEC.optionalFieldOf("target", EffectTarget.ATTACKER).forGetter(AppliedEffect::target)
        ).apply(instance, AppliedEffect::new));

        static final Codec<List<AppliedEffect>> LIST_CODEC = CODEC.listOf();
    }

    /** 「反击/反伤」指令：命中盾牌等节点时打出的一次伤害。 */
    record DamageBack(Growth counterDamage, Growth reflectChance, Growth reflectRatio) {
        static final Codec<DamageBack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Growth.CODEC.optionalFieldOf("counter_damage", Growth.CONSTANT_ZERO)
                        .forGetter(DamageBack::counterDamage),
                Growth.CODEC.optionalFieldOf("reflect_chance", Growth.CONSTANT_ZERO)
                        .forGetter(DamageBack::reflectChance),
                Growth.CODEC.optionalFieldOf("reflect_ratio", Growth.CONSTANT_ZERO)
                        .forGetter(DamageBack::reflectRatio)
        ).apply(instance, DamageBack::new));

        boolean isEmpty() {
            return this.counterDamage.at(1) == 0.0f && this.reflectChance.at(1) == 0.0f;
        }
    }

    // ── 22 种效果记录 ──────────────────────────────────────────────────

    /** 吸血：按目标已损失生命值的比例回复自身。 */
    record Hemophagia(Growth ratio) implements UEEffect {
        public static final MapCodec<Hemophagia> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Growth.CODEC.optionalFieldOf("ratio", Growth.of(0.2f)).forGetter(Hemophagia::ratio)
        ).apply(instance, Hemophagia::new));

        @Override
        public String id() {
            return HEMOPHAGIA;
        }

        @Override
        public String describe() {
            return "命中后按目标已损失生命的 " + pct(ratio) + " 回复自身生命";
        }
    }

    /** 双重保护：原版护甲/保护结算完成后再乘一层独立减伤。 */
    record DoubleProtection(Growth reduction) implements UEEffect {
        public static final MapCodec<DoubleProtection> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Growth.CODEC.optionalFieldOf("reduction", Growth.of(0.1f)).forGetter(DoubleProtection::reduction)
        ).apply(instance, DoubleProtection::new));

        @Override
        public String id() {
            return DOUBLE_PROTECTION;
        }

        @Override
        public String describe() {
            return "原版减伤之后再独立减免 " + pct(reduction) + "（单件上限 50%）";
        }
    }

    /** 受击触发：被命中后对自身或攻击者施加状态效果。 */
    record BeAttacked(List<AppliedEffect> effects) implements UEEffect {
        public static final MapCodec<BeAttacked> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                AppliedEffect.LIST_CODEC.fieldOf("effects").forGetter(BeAttacked::effects)
        ).apply(instance, BeAttacked::new));

        @Override
        public String id() {
            return BE_ATTACKED;
        }

        @Override
        public String describe() {
            return "被命中后施加状态效果（" + effects.size() + " 条）";
        }
    }

    /** 真实伤害：把伤害的一部分转为 setHealth 级真实伤害。 */
    record RealDamage(Growth ratio, Growth chance, Growth bonus, Growth cap) implements UEEffect {
        public static final MapCodec<RealDamage> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Growth.CODEC.optionalFieldOf("ratio", Growth.of(0.3f)).forGetter(RealDamage::ratio),
                Growth.CODEC.optionalFieldOf("chance", Growth.of(1.0f)).forGetter(RealDamage::chance),
                Growth.CODEC.optionalFieldOf("bonus", Growth.CONSTANT_ZERO).forGetter(RealDamage::bonus),
                Growth.CODEC.optionalFieldOf("cap", Growth.of(1.0f)).forGetter(RealDamage::cap)
        ).apply(instance, RealDamage::new));

        @Override
        public String id() {
            return REAL_DAMAGE;
        }

        @Override
        public String describe() {
            return "额外造成相当于本次伤害 " + pct(ratio) + " 的真实伤害（无视护甲/保护/无敌帧）"
                    + (bonus.base() == 0.0f && bonus.perLevel() == 0.0f ? "" : "，另加 " + pct(bonus))
                    + "，总上限 " + pct(cap) + "，触发率 " + pct(chance);
        }
    }

    /** 濒死免疫：血量归零时免疫本次死亡。 */
    record SkipDeath(boolean clearEffects, List<AppliedEffect> effects) implements UEEffect {
        public static final MapCodec<SkipDeath> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("clear_effects", true).forGetter(SkipDeath::clearEffects),
                AppliedEffect.LIST_CODEC.optionalFieldOf("effects", List.of()).forGetter(SkipDeath::effects)
        ).apply(instance, SkipDeath::new));

        @Override
        public String id() {
            return SKIP_DEATH;
        }

        @Override
        public String describe() {
            return "濒死时免疫本次死亡、血量设为 1"
                    + (clearEffects ? "、清除全部状态效果" : "")
                    + (effects.isEmpty() ? "" : "，并施加 " + effects.size() + " 条状态效果");
        }
    }

    /** 百分比增伤。 */
    record PercentDamage(Growth ratio) implements UEEffect {
        public static final MapCodec<PercentDamage> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Growth.CODEC.optionalFieldOf("ratio", Growth.of(0.2f)).forGetter(PercentDamage::ratio)
        ).apply(instance, PercentDamage::new));

        @Override
        public String id() {
            return PERCENT_DAMAGE;
        }

        @Override
        public String describe() {
            return "造成伤害 +" + pct(ratio);
        }
    }

    /** 多段跳：每级增加一段空中跳跃。 */
    record MultiJump(Growth jumps) implements UEEffect {
        public static final MapCodec<MultiJump> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Growth.CODEC.optionalFieldOf("jumps", Growth.of(1.0f)).forGetter(MultiJump::jumps)
        ).apply(instance, MultiJump::new));

        @Override
        public String id() {
            return MULTI_JUMP;
        }

        @Override
        public String describe() {
            return "额外空中跳跃 " + describeInt(jumps) + " 段";
        }
    }

    /** 闪避：按概率完全免疫本次伤害。 */
    record DamageDodge(Growth chance) implements UEEffect {
        public static final MapCodec<DamageDodge> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Growth.CODEC.optionalFieldOf("chance", Growth.of(0.1f)).forGetter(DamageDodge::chance)
        ).apply(instance, DamageDodge::new));

        @Override
        public String id() {
            return DAMAGE_DODGE;
        }

        @Override
        public String describe() {
            return "被攻击时 " + pct(chance) + " 概率完全免疫本次伤害";
        }
    }

    /** 反伤：按概率把本次伤害完全返还攻击者。 */
    record DamageReflect(Growth chance) implements UEEffect {
        public static final MapCodec<DamageReflect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Growth.CODEC.optionalFieldOf("chance", Growth.of(0.2f)).forGetter(DamageReflect::chance)
        ).apply(instance, DamageReflect::new));

        @Override
        public String id() {
            return DAMAGE_REFLECT;
        }

        @Override
        public String describe() {
            return "受击时 " + pct(chance) + " 概率把伤害完全反弹给攻击者";
        }
    }

    /** 弹射物速度倍率。 */
    record ProjectileSpeed(Growth multiplier) implements UEEffect {
        public static final MapCodec<ProjectileSpeed> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Growth.CODEC.optionalFieldOf("multiplier", Growth.of(1.5f)).forGetter(ProjectileSpeed::multiplier)
        ).apply(instance, ProjectileSpeed::new));

        @Override
        public String id() {
            return PROJECTILE_SPEED;
        }

        @Override
        public String describe() {
            return "弹射物飞行速度 ×" + describeFloat(multiplier);
        }
    }

    /** 无敌帧穿透：命中时按概率跳过目标无敌帧。 */
    record BypassIframes(Growth chance) implements UEEffect {
        public static final MapCodec<BypassIframes> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Growth.CODEC.optionalFieldOf("chance", Growth.of(0.5f)).forGetter(BypassIframes::chance)
        ).apply(instance, BypassIframes::new));

        @Override
        public String id() {
            return BYPASS_IFRAMES;
        }

        @Override
        public String describe() {
            return "命中时 " + pct(chance) + " 概率跳过目标无敌帧";
        }
    }

    /** 驱散增益：命中时移除目标若干正面效果。 */
    record StripBuffs(Growth count) implements UEEffect {
        public static final MapCodec<StripBuffs> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Growth.CODEC.optionalFieldOf("count", Growth.of(1.0f)).forGetter(StripBuffs::count)
        ).apply(instance, StripBuffs::new));

        @Override
        public String id() {
            return STRIP_BUFFS;
        }

        @Override
        public String describe() {
            return "命中时驱散目标 " + describeInt(count) + " 个正面状态效果";
        }
    }

    /** 自伤加深：增加自身所受伤害。 */
    record SelfVulnerability(Growth multiplier) implements UEEffect {
        public static final MapCodec<SelfVulnerability> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Growth.CODEC.optionalFieldOf("multiplier", Growth.of(0.1f)).forGetter(SelfVulnerability::multiplier)
        ).apply(instance, SelfVulnerability::new));

        @Override
        public String id() {
            return SELF_VULNERABILITY;
        }

        @Override
        public String describe() {
            return "自身所受伤害 +" + pct(multiplier);
        }
    }

    /** 格挡触发：成功格挡后反击/反伤/施加效果。 */
    record OnBlock(DamageBack back, List<AppliedEffect> effects) implements UEEffect {
        public static final MapCodec<OnBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                DamageBack.CODEC.optionalFieldOf("back", new DamageBack(
                        Growth.CONSTANT_ZERO, Growth.CONSTANT_ZERO, Growth.CONSTANT_ZERO))
                        .forGetter(OnBlock::back),
                AppliedEffect.LIST_CODEC.optionalFieldOf("effects", List.of()).forGetter(OnBlock::effects)
        ).apply(instance, OnBlock::new));

        @Override
        public String id() {
            return ON_BLOCK;
        }

        @Override
        public String describe() {
            return "格挡成功后触发反击 / 反伤 / 施加效果";
        }
    }

    /** 盾牌破防触发：盾牌被斧类打中进入冷却时触发。 */
    record OnShieldDisabled(DamageBack back, List<AppliedEffect> effects) implements UEEffect {
        public static final MapCodec<OnShieldDisabled> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                DamageBack.CODEC.optionalFieldOf("back", new DamageBack(
                        Growth.CONSTANT_ZERO, Growth.CONSTANT_ZERO, Growth.CONSTANT_ZERO))
                        .forGetter(OnShieldDisabled::back),
                AppliedEffect.LIST_CODEC.optionalFieldOf("effects", List.of()).forGetter(OnShieldDisabled::effects)
        ).apply(instance, OnShieldDisabled::new));

        @Override
        public String id() {
            return ON_SHIELD_DISABLED;
        }

        @Override
        public String describe() {
            return "盾牌被破防时触发反击 / 反伤 / 施加效果";
        }
    }

    /** 换位：命中或受击时与对方互换位置。 */
    record SwapPosition(Growth chance, Activation activation) implements UEEffect {
        public static final MapCodec<SwapPosition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Growth.CODEC.optionalFieldOf("chance", Growth.of(1.0f)).forGetter(SwapPosition::chance),
                Activation.CODEC.optionalFieldOf("activation", Activation.BOTH).forGetter(SwapPosition::activation)
        ).apply(instance, SwapPosition::new));

        @Override
        public String id() {
            return SWAP_POSITION;
        }

        @Override
        public String describe() {
            return "命中/受击时瞬移换位（" + activation.getSerializedName() + "，概率 " + pct(chance) + "）";
        }
    }

    /** 自动拾取：掉落物立刻被原主人拾回。 */
    record AutoPickup() implements UEEffect {
        public static final MapCodec<AutoPickup> CODEC = MapCodec.unit(AutoPickup::new);

        @Override
        public String id() {
            return AUTO_PICKUP;
        }

        @Override
        public String describe() {
            return "背包掉出的物品立刻被原主人拾回（需在线且背包有空间）";
        }
    }

    /** 周围数量增伤：伤害随周围实体数量缩放。 */
    record ScalingDamage(Growth base, Growth perEntity, int maxEntities, double radius) implements UEEffect {
        public static final MapCodec<ScalingDamage> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Growth.CODEC.optionalFieldOf("base", Growth.of(1.0f)).forGetter(ScalingDamage::base),
                Growth.CODEC.optionalFieldOf("per_entity", Growth.of(0.1f)).forGetter(ScalingDamage::perEntity),
                Codec.intRange(1, 64).optionalFieldOf("max_entities", 8).forGetter(ScalingDamage::maxEntities),
                Codec.DOUBLE.optionalFieldOf("radius", 8.0).forGetter(ScalingDamage::radius)
        ).apply(instance, ScalingDamage::new));

        @Override
        public String id() {
            return SCALING_DAMAGE;
        }

        @Override
        public String describe() {
            return "周围每有一个实体，伤害 ×(1+" + describeFloat(perEntity) + ")（至多 " + maxEntities + " 个，半径 " + radius + "）";
        }

        /** 伤害倍率 = base + perEntity × min(实体数, 上限)。 */
        public double multiplierFor(int level, int entities) {
            int capped = Math.min(Math.max(0, entities), maxEntities);
            return Math.max(0.0, base.at(level) + perEntity.at(level) * capped);
        }
    }

    /**
     * 动能伤害：命中后按「本次实际造成伤害」的比例追加一次动能类型伤害。
     *
     * <p>走独立伤害类型（{@code ultraenchantment:kinetic}），因此受护甲影响、会正常触发
     * 死亡消息，并<b>不是</b>真实伤害；它和真实伤害是两条不同的赛道。
     */
    record KineticDamage(Growth ratio) implements UEEffect {
        public static final MapCodec<KineticDamage> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Growth.CODEC.optionalFieldOf("ratio", Growth.of(0.1f)).forGetter(KineticDamage::ratio)
        ).apply(instance, KineticDamage::new));

        @Override
        public String id() {
            return KINETIC_DAMAGE;
        }

        @Override
        public String describe() {
            return "命中后追加相当于本次伤害 " + pct(ratio) + " 的动能伤害";
        }
    }

    /**
     * 烈焰之力：持有者（主手）处于下界 / 火中 / 岩浆中时，攻击伤害按比例提升。
     *
     * <p>这是<b>条件增伤</b>，不写入属性修饰符——条件只在这几个场景成立，
     * 写成永久的 attribute 会让 tooltip 与实战数值不一致。
     */
    record InfernoMight(Growth multiplier) implements UEEffect {
        public static final MapCodec<InfernoMight> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Growth.CODEC.optionalFieldOf("multiplier", Growth.of(0.5f)).forGetter(InfernoMight::multiplier)
        ).apply(instance, InfernoMight::new));

        @Override
        public String id() {
            return INFERNO_MIGHT;
        }

        @Override
        public String describe() {
            return "在下界 / 火中 / 岩浆中时，攻击伤害 +" + pct(multiplier);
        }
    }

    /** 战利品复制：击杀时所有掉落物额外复制一份。 */
    record LootDuplication() implements UEEffect {
        public static final MapCodec<LootDuplication> CODEC = MapCodec.unit(LootDuplication::new);

        @Override
        public String id() {
            return LOOT_DUPLICATION;
        }

        @Override
        public String describe() {
            return "击杀时所有战利品额外复制一份";
        }
    }

    /**
     * 横扫增伤：横扫攻击命中超过 {@code baseTargets} 个目标时，每多一个目标就提高横扫伤害。
     *
     * @param perTarget   每个额外目标的伤害比例（随等级成长）
     * @param baseTargets 免增伤的起步目标数（高阶 = 2，超级 / 究极 = 1）
     */
    record SweepDamage(Growth perTarget, int baseTargets) implements UEEffect {
        public static final MapCodec<SweepDamage> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Growth.CODEC.optionalFieldOf("per_target", Growth.of(0.05f)).forGetter(SweepDamage::perTarget),
                Codec.intRange(1, 16).optionalFieldOf("base_targets", 2).forGetter(SweepDamage::baseTargets)
        ).apply(instance, SweepDamage::new));

        @Override
        public String id() {
            return SWEEP_DAMAGE;
        }

        @Override
        public String describe() {
            return "横扫命中超过 " + baseTargets + " 个目标（含主目标）时，每多一个目标横扫伤害 +"
                    + pct(perTarget);
        }

        /** 给定被横扫目标数时的伤害倍率。 */
        public double multiplierFor(int level, int targets) {
            int extra = Math.max(0, targets - baseTargets);
            return 1.0 + Math.max(0.0f, perTarget.at(level)) * extra;
        }
    }

    // ── 小工具 ─────────────────────────────────────────────────────────

    /** 换位的触发方向。 */
    enum Activation implements StringRepresentable {
        /** 装备者命中别人时换位。 */
        HIT("hit"),
        /** 装备者被别人命中时换位。 */
        HURT("hurt"),
        /** 两种情况都换。 */
        BOTH("both");

        public static final Codec<Activation> CODEC = StringRepresentable.fromEnum(Activation::values);

        private final String id;

        Activation(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }

        public boolean onHit() {
            return this != HURT;
        }

        public boolean onHurt() {
            return this != HIT;
        }
    }

    static String pct(Growth growth) {
        return fmt(growth.base() * 100.0f) + "%"
                + (growth.perLevel() == 0.0f ? "" : " +" + fmt(growth.perLevel() * 100.0f) + "%/级");
    }

    static String describeInt(Growth growth) {
        return fmt(growth.base()) + (growth.perLevel() == 0.0f ? "" : " + " + fmt(growth.perLevel()) + "×(等级-1)");
    }

    static String describeFloat(Growth growth) {
        return fmt(growth.base()) + (growth.perLevel() == 0.0f ? "" : " + " + fmt(growth.perLevel()) + "×(等级-1)");
    }

    static String fmt(float value) {
        return value == Math.round(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /** 取可空的效果引用（未配置时为空）。 */
    static Optional<Holder<MobEffect>> effectOf(List<AppliedEffect> effects) {
        return effects.isEmpty() ? Optional.empty() : Optional.of(effects.get(0).effect());
    }
}
