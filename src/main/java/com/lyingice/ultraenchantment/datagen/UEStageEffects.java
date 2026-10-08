package com.lyingice.ultraenchantment.datagen;

import com.lyingice.ultraenchantment.Ultraenchantment;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.advancements.critereon.DamageSourcePredicate;
import net.minecraft.advancements.critereon.TagPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.storage.loot.predicates.DamageSourceCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.EnchantmentLevelProvider;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentTarget;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.TargetedConditionalEffect;
import net.minecraft.world.item.enchantment.effects.AddValue;
import net.minecraft.world.item.enchantment.effects.ApplyMobEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;
import net.minecraft.world.item.enchantment.effects.Ignite;
import net.minecraft.world.item.enchantment.effects.MultiplyValue;
import net.minecraft.world.item.enchantment.effects.AllOf;
import net.minecraft.world.item.enchantment.effects.DamageEntity;
import net.minecraft.world.item.enchantment.effects.DamageImmunity;
import net.minecraft.world.item.enchantment.effects.DamageItem;
import net.minecraft.world.item.enchantment.effects.RemoveBinomial;
import net.minecraft.world.item.enchantment.effects.SetValue;

import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE;

/**
 * 阶段<b>阶位加成</b>的构造器——把「这一阶比上一阶多给了什么」表达成可组合的补丁。
 *
 * <h2>为什么是「补丁」而不是「整套效果」</h2>
 *
 * <p>阶段条目的 {@code effects} 必须<b>自成一体</b>：结算层会把原版附魔的等级置 0，
 * 所以阶段条目一旦挂上，物品上那条原版附魔就不再产生任何效果。
 * 原版那套效果必须原样出现在阶段条目里，补丁只往上面加东西。
 *
 * <h2>⚠️ 补丁是「声明式」的，不是不透明函数</h2>
 *
 * <p>早期 {@code Patch} 就是 {@code UnaryOperator<DataComponentMap>}——一个黑盒 lambda，
 * <b>没人能说出「这一阶到底加了什么」</b>，除非把结果与原版逐条比对去反推。
 * 反推会错：{@link #damage} 的加成恰好与原版锋利同式时，比对法根本认不出哪条是加成的。
 *
 * <p>因此改成两个 record（{@link Append} 追加 / {@link Replace} 整条替换），
 * 并把<b>人类可读的描述</b>{@link Patch#describe()} 作为构造的一部分。
 *
 * <h2>⚠️ 能合并就合并：追加 → 覆盖</h2>
 *
 * <p><b>首选覆盖，少用追加。</b>当加成与原版那一条<b>格式相同</b>时
 * （都是无条件的「加法 + 线性」，或者都是同一属性同一运算的修饰符），
 * 应该直接把它们<b>合并成一条</b>，用 {@link Replace} 覆盖原式，
 * 而不是在旁边再挂一条 {@link Append}。理由：
 *
 * <ul>
 *   <li>结算时两条 {@code add} 会依次作用，数学上等价，但条目多了一倍、读起来要自己加</li>
 *   <li>合并后「这一阶最终是多少」是<b>写在数据里的一句线性式</b>，不需要任何计算</li>
 * </ul>
 *
 * <p>合并由 {@link #mergeIntoVanilla} 在生成阶段自动完成，规则很严格——只要有一条
 * 带条件的、或者格式不一致的，就<b>整组放弃合并</b>（宁可不合并，也不能悄悄改变语义）。
 * 最典型的「不能合并」：亡灵杀手的伤害带「目标属于亡灵」条件，
 * 把它和我们的无条件伤害合并，会顺手把我们的加成也变成有条件的。
 */
public final class UEStageEffects {
    private UEStageEffects() {}

    /**
     * 一个阶位加成动作。
     *
     * <p>两种形态：
     * <ul>
     *   <li>{@link Append} —— 往列表型组件末尾再挂一条（与原版<b>叠加</b>）</li>
     *   <li>{@link Replace} —— 把整个组件<b>换掉</b>（标量组件是换那一个值，
     *       列表组件是换整张表，合并式覆盖用的就是它）</li>
     * </ul>
     */
    public sealed interface Patch {
        /** 把本补丁作用到一份效果表上，返回新表（原表不变）。 */
        DataComponentMap apply(DataComponentMap base);

        /** 作用的效果组件。 */
        DataComponentType<?> component();

        /** 人类可读的效果描述——效果总表直接用它，不做任何反推。 */
        String describe();

        /** {@code true} = 追加（与原版叠加）；{@code false} = 覆盖（整条替换）。 */
        boolean appends();

        /** 空补丁（该阶级只改数值、不新增任何效果）——效果总表与创造栏都要跳过它。 */
        default boolean isNoOp() {
            return false;
        }
    }

    /**
     * 空补丁：<b>该阶级只改数值（覆盖），不新增效果</b>。
     *
     * <p>大多数谱系的高阶就是这种情况——它和基础阶的差别只在「公式换成了自己那一套」，
     * 没有多出任何新东西（保护、火焰保护、摔落缓冲等都属于此类）。
     */
    public static Patch none() {
        return new None();
    }

    /** 见 {@link #none()}。 */
    record None() implements Patch {
        @Override
        public DataComponentMap apply(DataComponentMap base) {
            return base;
        }

        @Override
        public DataComponentType<?> component() {
            return null;
        }

        @Override
        public String describe() {
            return "（本阶级只改数值，不新增效果）";
        }

        @Override
        public boolean appends() {
            return false;
        }

        @Override
        public boolean isNoOp() {
            return true;
        }
    }

    /**
     * 把同一阶级要改的多个组件合成一个补丁。
     *
     * <p>效果总表 v3 里一阶常常同时改好几件事（例如究极锋利 = 增伤 + 攻击伤害属性 + 穿甲），
     * 但每阶只声明<b>一个</b>补丁；这里按顺序应用并拼接描述。
     */
    public static Patch join(Patch... patches) {
        return new Composite(List.of(patches));
    }

    /** 顺序应用多个补丁；效果总表逐条展示（空补丁不占位）。 */
    record Composite(List<Patch> parts) implements Patch {
        @Override
        public DataComponentMap apply(DataComponentMap base) {
            DataComponentMap result = base;
            for (Patch part : this.parts) {
                result = part.apply(result);
            }
            return result;
        }

        @Override
        public DataComponentType<?> component() {
            return null;
        }

        @Override
        public String describe() {
            return this.parts.stream().filter(part -> !part.isNoOp()).map(Patch::describe)
                    .collect(java.util.stream.Collectors.joining("；"));
        }

        @Override
        public boolean appends() {
            return false;
        }

        @Override
        public boolean isNoOp() {
            return this.parts.stream().allMatch(Patch::isNoOp);
        }
    }

    /** 往列表型组件末尾追加一条条目。 */
    public record Append<T>(DataComponentType<List<T>> type, T entry, String describe) implements Patch {
        @Override
        public DataComponentMap apply(DataComponentMap base) {
            List<T> existing = base.get(this.type);
            List<T> merged = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            merged.add(this.entry);
            return DataComponentMap.builder().addAll(base).set(this.type, List.copyOf(merged)).build();
        }

        @Override
        public DataComponentType<?> component() {
            return this.type;
        }

        @Override
        public boolean appends() {
            return true;
        }
    }

    /**
     * 把某个组件整条换成给定值。
     *
     * <p>标量组件（{@code crossbow_charge_time}）用它做「整条替换」；
     * 列表组件（{@code attributes}）用它做「合并式覆盖」——把原版那条与加成合成一条后写回去。
     */
    public record Replace<T>(DataComponentType<T> type, T value, String describe) implements Patch {
        @Override
        public DataComponentMap apply(DataComponentMap base) {
            return DataComponentMap.builder().addAll(base).set(this.type, this.value).build();
        }

        @Override
        public DataComponentType<?> component() {
            return this.type;
        }

        @Override
        public boolean appends() {
            return false;
        }
    }

    // ── 覆盖：把原版的数值整条换成该阶级自己的公式 ──────────────────────

    /**
     * <b>把「原版里所有「加法 + 线性」的数值」按阶级倍率整体换成该阶自己的公式。</b>
     *
     * <p>这是本模组的主力机制（{@code 覆盖}）：阶段条目的数值<b>完全由该阶自己决定</b>，
     * 原版的数字一点不参与。以锋利为例（原版 {@code 1 + 0.5×(n-1)}）：
     *
     * <pre>
     *   高阶 = 3 + 1×(n-1)      ← 首级值 ×3、每级增量 ×2
     *   超级 = 6 + 1.5×(n-1)    ← ×6、×3
     *   究极 = 10 + 2.5×(n-1)   ← ×10、×5
     * </pre>
     *
     * <p>只换数值，<b>不动条件</b>：亡灵杀手的伤害依然只打亡灵、保护类依然只挡特定伤害来源。
     * 条件是「这个附魔是什么」的一部分，不是数值；把它一起换掉等于换了个附魔。
     *
     * <p>只处理两种形状——{@code ConditionalEffect&lt;?&gt;} 里的 {@code AddValue(Linear)}
     * 与属性修饰符的 {@code Linear}。其余形状（{@code remove_binomial} / {@code multiply} /
     * {@code LevelsSquared} / 实体效果 / 单值组件）没有「首级值 + 每级增量」这种结构，
     * 硬套倍率只会算出没有意义的数，因此原样保留，由谱系自己用追加补丁给成长性。
     *
     * @param baseMul  首级值的倍率
     * @param slopeMul 每级增量的倍率
     */
    public static DataComponentMap scaleCore(DataComponentMap vanilla, float baseMul, float slopeMul) {
        DataComponentMap.Builder builder = DataComponentMap.builder();
        boolean changed = false;
        for (net.minecraft.core.component.TypedDataComponent<?> component : vanilla) {
            Object scaled = scaleValue(component.value(), baseMul, slopeMul);
            put(builder, component.type(), scaled == null ? component.value() : scaled);
            changed |= scaled != null;
        }
        return changed ? builder.build() : vanilla;
    }

    /** 列表型组件：逐条尝试缩放；整条都不需要缩放时返回 {@code null}（表示「没动」）。 */
    private static Object scaleValue(Object value, float baseMul, float slopeMul) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        List<Object> out = new ArrayList<>(list.size());
        boolean any = false;
        for (Object element : list) {
            Object scaled = scaleElement(element, baseMul, slopeMul);
            out.add(scaled == null ? element : scaled);
            any |= scaled != null;
        }
        return any ? List.copyOf(out) : null;
    }

    /** 单条条目：加法的线性值效果、属性修饰符、以及「有指定目标」的条件效果。 */
    private static Object scaleElement(Object element, float baseMul, float slopeMul) {
        if (element instanceof EnchantmentAttributeEffect attribute
                && attribute.amount() instanceof LevelBasedValue.Linear linear) {
            return new EnchantmentAttributeEffect(attribute.id(), attribute.attribute(),
                    scaleLinear(linear, baseMul, slopeMul), attribute.operation());
        }
        if (element instanceof ConditionalEffect<?> conditional
                && conditional.effect() instanceof AddValue add
                && add.value() instanceof LevelBasedValue.Linear linear) {
            return new ConditionalEffect<>(new AddValue(scaleLinear(linear, baseMul, slopeMul)),
                    conditional.requirements());
        }
        if (element instanceof TargetedConditionalEffect<?> targeted
                && targeted.effect() instanceof AddValue add
                && add.value() instanceof LevelBasedValue.Linear linear) {
            return new TargetedConditionalEffect<>(targeted.enchanted(), targeted.affected(),
                    new AddValue(scaleLinear(linear, baseMul, slopeMul)), targeted.requirements());
        }
        return null;
    }

    private static LevelBasedValue.Linear scaleLinear(LevelBasedValue.Linear linear, float baseMul, float slopeMul) {
        return new LevelBasedValue.Linear(linear.base() * baseMul, linear.perLevelAboveFirst() * slopeMul);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void put(DataComponentMap.Builder builder, DataComponentType type, Object value) {
        builder.set(type, value);
    }

    // ── 增量（可选）：不推荐用于本模组自己的谱系 ────────────────────────

    /**
     * 尝试把「格式相同的追加」并进原式子里，改成一条覆盖。
     *
     * <p>规则（三条必须同时满足，否则整组放弃）：
     * <ol>
     *   <li>该组件在原版里<b>恰好一条</b>条目——多条的语义是「按条件分流」
     *       （如耐久的护甲 / 非护甲两条），合并会糊掉这个结构。</li>
     *   <li>原版那条与<b>全部</b>加成条目都<b>没有条件</b>。
     *       只要有一条带条件就不合并：合并会把条件抹掉或扩散，语义被悄悄改写。</li>
     *   <li>格式一致：值效果侧要求都是 {@code add + linear}；
     *       属性侧要求属性与运算方式相同、且都是 {@code linear}。</li>
     * </ol>
     *
     * @param vanilla 原版那份效果表
     * @param patches 该阶级的累积补丁
     * @return 合并后的补丁列表；不满足条件时原样返回
     */
    public static List<Patch> mergeIntoVanilla(DataComponentMap vanilla, List<Patch> patches) {
        Map<DataComponentType<?>, List<Append<?>>> appends = new LinkedHashMap<>();
        for (Patch patch : patches) {
            if (patch instanceof Append<?> append) {
                appends.computeIfAbsent(append.component(), key -> new ArrayList<>()).add(append);
            }
        }
        if (appends.isEmpty()) {
            return patches;
        }

        Map<DataComponentType<?>, Replace<?>> merged = new LinkedHashMap<>();
        for (Map.Entry<DataComponentType<?>, List<Append<?>>> entry : appends.entrySet()) {
            Replace<?> replace = tryMerge(vanilla, entry.getKey(), entry.getValue());
            if (replace != null) {
                merged.put(entry.getKey(), replace);
            }
        }
        if (merged.isEmpty()) {
            return patches;
        }

        // 保住补丁原来的顺序：合并结果落在该组件第一条追加的位置上。
        Set<DataComponentType<?>> emitted = new HashSet<>();
        List<Patch> result = new ArrayList<>(patches.size());
        for (Patch patch : patches) {
            if (patch instanceof Append<?> append && merged.containsKey(append.component())) {
                if (emitted.add(append.component())) {
                    result.add(merged.get(append.component()));
                }
                continue;
            }
            result.add(patch);
        }
        return List.copyOf(result);
    }

    private static Replace<?> tryMerge(DataComponentMap vanilla, DataComponentType<?> component,
                                       List<Append<?>> appends) {
        if (component == EnchantmentEffectComponents.ATTRIBUTES) {
            return mergeAttributes(vanilla, appends);
        }
        return mergeValueEffects(vanilla, component, appends);
    }

    /**
     * 值效果侧：原版恰好一条「无条件的效果条目」，且全部加成都是同式。
     *
     * <p>⚠️ 先用通配读取把「这个组件到底装什么」问清楚，再决定要不要按值效果处理。
     * 直接 {@code (DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>>) component}
     * 强转是 <b>unchecked</b> 的——编译期一声不吭，但 {@code post_attack} 那种装
     * {@code TargetedConditionalEffect} 的组件会在取值处抛 {@code ClassCastException}，
     * 而且是在 datagen 跑到一半才炸。
     */
    @SuppressWarnings("unchecked")
    private static Replace<?> mergeValueEffects(DataComponentMap vanilla, DataComponentType<?> component,
                                                List<Append<?>> appends) {
        Object raw = rawValue(vanilla, component);
        if (!(raw instanceof List<?> list) || list.size() != 1) {
            return null;
        }
        if (!(list.get(0) instanceof ConditionalEffect<?> conditional)
                || !conditional.requirements().isEmpty()
                || !(conditional.effect() instanceof EnchantmentValueEffect baseEffect)) {
            return null;
        }

        DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> type =
                (DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>>) component;

        // ① 加法 + 线性 → 系数相加
        if (baseEffect instanceof AddValue add && add.value() instanceof LevelBasedValue.Linear linear) {
            float totalBase = linear.base();
            float totalStep = linear.perLevelAboveFirst();
            for (Append<?> append : appends) {
                LevelBasedValue.Linear step = unconditionalLinear(append.entry());
                if (step == null) {
                    return null;
                }
                totalBase += step.base();
                totalStep += step.perLevelAboveFirst();
            }
            return new Replace<>(type,
                    List.of(new ConditionalEffect<>(add(totalBase, totalStep), Optional.empty())),
                    amount(totalBase, totalStep));
        }

        // ② 乘法 + 常数 → 系数相乘（经验修补：原版 ×2 与追加 ×1.5 合成 ×3）
        if (baseEffect instanceof MultiplyValue multiply
                && multiply.factor() instanceof LevelBasedValue.Constant constant) {
            float factor = constant.value();
            for (Append<?> append : appends) {
                Float step = unconditionalMultiply(append.entry());
                if (step == null) {
                    return null;
                }
                factor *= step;
            }
            return new Replace<>(type,
                    List.of(new ConditionalEffect<>(
                            new MultiplyValue(new LevelBasedValue.Constant(factor)), Optional.empty())),
                    "×" + num(factor));
        }

        return null;
    }

    /** 该条目是不是「无条件 + 乘法 + 常数」；是则返回那个常数。 */
    private static Float unconditionalMultiply(Object entry) {
        if (entry instanceof ConditionalEffect<?> conditional
                && conditional.requirements().isEmpty()
                && conditional.effect() instanceof MultiplyValue multiply
                && multiply.factor() instanceof LevelBasedValue.Constant constant) {
            return constant.value();
        }
        return null;
    }

    /** 属性侧：原版恰好一条属性修饰符，且全部加成是同属性、同运算、同式。 */
    private static Replace<?> mergeAttributes(DataComponentMap vanilla, List<Append<?>> appends) {
        List<EnchantmentAttributeEffect> base = vanilla.get(EnchantmentEffectComponents.ATTRIBUTES);
        if (base == null || base.size() != 1) {
            return null;
        }
        EnchantmentAttributeEffect first = base.get(0);
        if (!(first.amount() instanceof LevelBasedValue.Linear linear)) {
            return null;
        }

        float totalBase = linear.base();
        float totalStep = linear.perLevelAboveFirst();
        for (Append<?> append : appends) {
            if (!(append.entry() instanceof EnchantmentAttributeEffect effect)) {
                return null;
            }
            if (!effect.attribute().equals(first.attribute()) || effect.operation() != first.operation()) {
                return null;
            }
            if (!(effect.amount() instanceof LevelBasedValue.Linear step)) {
                return null;
            }
            totalBase += step.base();
            totalStep += step.perLevelAboveFirst();
        }

        EnchantmentAttributeEffect merged = new EnchantmentAttributeEffect(
                first.id(), first.attribute(), new LevelBasedValue.Linear(totalBase, totalStep), first.operation());
        return new Replace<>(EnchantmentEffectComponents.ATTRIBUTES, List.of(merged),
                shortAttributeName(first.attribute()) + " " + operationName(first.operation())
                        + " " + amount(totalBase, totalStep));
    }

    /** 通配读取某个组件的原始值；没有则返回 {@code null}。 */
    private static Object rawValue(DataComponentMap map, DataComponentType<?> type) {
        for (net.minecraft.core.component.TypedDataComponent<?> component : map) {
            if (component.type() == type) {
                return component.value();
            }
        }
        return null;
    }

    /** 该条目是不是「无条件 + 加法 + 线性」；是则返回那条线性式。 */
    private static LevelBasedValue.Linear unconditionalLinear(Object entry) {
        if (entry instanceof ConditionalEffect<?> conditional
                && conditional.requirements().isEmpty()
                && conditional.effect() instanceof AddValue add
                && add.value() instanceof LevelBasedValue.Linear linear) {
            return linear;
        }
        return null;
    }

    // ── 底层构造 ────────────────────────────────────────────────────────

    /** 线性「加」值效果：{@code base + perLevelAboveFirst * (等级 - 1)}。 */
    private static EnchantmentValueEffect add(float base, float perLevelAboveFirst) {
        return new AddValue(new LevelBasedValue.Linear(base, perLevelAboveFirst));
    }

    /** 把一条无条件的值效果追加到列表型组件上。 */
    private static Patch value(DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> type,
                               EnchantmentValueEffect effect, String describe) {
        return new Append<>(type, new ConditionalEffect<>(effect, Optional.empty()), describe);
    }

    /** 数值的显示形式：整数不拖小数点，每级增量为 0 时不写「×(等级-1)」。 */
    static String amount(float base, float perLevel) {
        String b = num(base);
        return perLevel == 0.0f ? b : b + " + " + num(perLevel) + "×(等级-1)";
    }

    static String num(float value) {
        return value == Math.round(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    // ── 战斗 ─────────────────────────────────────────────────────────────

    /** 额外伤害（命中时结算，与原版锋利同一条管线）。 */
    public static Patch damage(float base, float perLevel) {
        return absoluteValue(EnchantmentEffectComponents.DAMAGE, base, perLevel,
                "命中时额外造成 " + amount(base, perLevel) + " 点伤害");
    }

    /** 额外击退。 */
    public static Patch knockback(float base, float perLevel) {
        return absoluteValue(EnchantmentEffectComponents.KNOCKBACK, base, perLevel,
                "额外击退 " + amount(base, perLevel));
    }

    /** 护甲穿透：把护甲的有效性加上一个负值（与原版 {@code breach} 同管线）。 */
    public static Patch armorEffectiveness(float base, float perLevel) {
        return value(EnchantmentEffectComponents.ARMOR_EFFECTIVENESS, add(base, perLevel),
                "降低目标护甲有效性 " + amount(-base, -perLevel) + "（穿甲）");
    }

    /** 每次伤害减免（无条件，叠加在原版条件化减免之上）。 */
    public static Patch damageProtection(float base, float perLevel) {
        return value(EnchantmentEffectComponents.DAMAGE_PROTECTION, add(base, perLevel),
                "任何来源的伤害都额外减免 " + amount(base, perLevel));
    }

    /** 重锤下落每格额外伤害。 */
    public static Patch smashDamagePerFallenBlock(float base, float perLevel) {
        return value(EnchantmentEffectComponents.SMASH_DAMAGE_PER_FALLEN_BLOCK, add(base, perLevel),
                "下落每格额外伤害 " + amount(base, perLevel));
    }

    /**
     * 近战命中后点燃目标（原版火焰附加用的就是这条）。
     *
     * @param baseTicks 1 级时的点燃时长（tick）
     * @param perLevel  每级递增（tick）
     */
    public static Patch postAttackIgnite(float baseTicks, float perLevel) {
        return new Append<>(EnchantmentEffectComponents.POST_ATTACK,
                new TargetedConditionalEffect<EnchantmentEntityEffect>(
                        EnchantmentTarget.ATTACKER, EnchantmentTarget.VICTIM,
                        new Ignite(new LevelBasedValue.Linear(baseTicks, perLevel)), Optional.empty()),
                "近战命中后额外点燃 " + amount(baseTicks, perLevel) + " tick");
    }

    /**
     * 近战命中后给<b>受击目标</b>施加一个状态效果。
     *
     * <p>与 {@link #postAttackIgnite} 同一落点（{@code post_attack} → {@code victim}），
     * 区别只在效果类型。原版节肢杀手对节肢生物施加缓慢用的也是这条管线。
     *
     * @param effect            要施加的状态效果
     * @param baseSeconds       1 级时的持续秒数
     * @param perLevelSeconds   每级递增秒数
     * @param baseAmplifier     1 级时的强度<b>序号</b>（0 = I 级）
     * @param perLevelAmplifier 每级递增序号
     */
    public static Patch postAttackMobEffect(Holder<MobEffect> effect,
                                            float baseSeconds, float perLevelSeconds,
                                            float baseAmplifier, float perLevelAmplifier) {
        LevelBasedValue duration = new LevelBasedValue.Linear(baseSeconds, perLevelSeconds);
        LevelBasedValue amplifier = new LevelBasedValue.Linear(baseAmplifier, perLevelAmplifier);
        return new Append<>(EnchantmentEffectComponents.POST_ATTACK,
                new TargetedConditionalEffect<EnchantmentEntityEffect>(
                        EnchantmentTarget.ATTACKER, EnchantmentTarget.VICTIM,
                        new ApplyMobEffect(HolderSet.direct(effect), duration, duration, amplifier, amplifier),
                        Optional.empty()),
                "近战命中后使目标获得 " + effectName(effect)
                        + "（持续 " + amount(baseSeconds, perLevelSeconds) + " 秒；"
                        + "强度 " + amount(baseAmplifier + 1.0f, perLevelAmplifier) + " 级）");
    }

    private static String effectName(Holder<MobEffect> effect) {
        return effect.unwrapKey().map(key -> key.location().getPath()).orElse("mob_effect");
    }

    /**
     * 弹射物生成时点燃（原版火矢用的就是这条）。
     *
     * <p>给弓类附魔加点燃必须走这条，走 {@link #postAttackIgnite} 对远程毫无作用。
     */
    public static Patch projectileSpawnedIgnite(float ticks) {
        return new Append<>(EnchantmentEffectComponents.PROJECTILE_SPAWNED,
                new ConditionalEffect<EnchantmentEntityEffect>(
                        new Ignite(new LevelBasedValue.Constant(ticks)), Optional.empty()),
                "射出的弹射物额外点燃 " + num(ticks) + " tick");
    }

    // ── 掉落 / 经验 ──────────────────────────────────────────────────────

    /** 装备掉落加成（原版抢夺用的就是这条）。 */
    public static Patch equipmentDrops(float base, float perLevel) {
        // 绝对值版：原版抢夺那条是「攻击者 → 受击者」的目标化条目，
        // 直接追加会与原版叠加（实测掉落翻倍）。
        return new Rewrite<>(EnchantmentEffectComponents.EQUIPMENT_DROPS, existing -> {
            Optional<LootItemCondition> requirements = existing.isEmpty()
                    ? Optional.empty() : existing.get(0).requirements();
            EnchantmentTarget enchanted = existing.isEmpty()
                    ? EnchantmentTarget.ATTACKER : existing.get(0).enchanted();
            EnchantmentTarget affected = existing.isEmpty()
                    ? EnchantmentTarget.VICTIM : existing.get(0).affected();
            return List.of(new TargetedConditionalEffect<>(enchanted, affected,
                    add(base, perLevel), requirements));
        }, "装备掉落率 " + amount(base, perLevel));
    }

    /** 方块掉落经验加成。 */
    public static Patch blockExperience(float base, float perLevel) {
        return value(EnchantmentEffectComponents.BLOCK_EXPERIENCE, add(base, perLevel),
                "方块掉落经验 +" + amount(base, perLevel));
    }

    /** 生物掉落经验加成。 */
    public static Patch mobExperience(float base, float perLevel) {
        return value(EnchantmentEffectComponents.MOB_EXPERIENCE, add(base, perLevel),
                "生物掉落经验 +" + amount(base, perLevel));
    }

    // ── 耐久 ─────────────────────────────────────────────────────────────

    /**
     * 额外一次「按二项分布免除耐久消耗」（原版耐久用的就是这条）。
     *
     * <p><b>这条永远无法合并</b>：原版是 {@code remove_binomial(fraction)}，
     * 与我们追加的 {@code remove_binomial(linear)} 格式不同，
     * 而且原版有两条（护甲 / 非护甲各一条），合并会糊掉这个分流结构。
     */
    public static Patch durabilitySave(float base, float perLevel) {
        return value(EnchantmentEffectComponents.ITEM_DAMAGE,
                new RemoveBinomial(new LevelBasedValue.Linear(base, perLevel)),
                "额外 " + amount(base, perLevel) + " 的概率免除 1 点耐久消耗");
    }

    /**
     * 经验修补效率乘算（原版经验修补是 {@code multiply 2.0}）。
     *
     * @param extraFactor 追加的乘数增量（{@code 0.5} → 追加 {@code ×1.5}）
     */
    public static Patch repairWithXp(float extraFactor) {
        return value(EnchantmentEffectComponents.REPAIR_WITH_XP,
                new MultiplyValue(new LevelBasedValue.Constant(1.0f + extraFactor)),
                "经验修补效率再 ×" + num(1.0f + extraFactor) + "（与原版的 ×2 连乘）");
    }

    // ── 远程 ─────────────────────────────────────────────────────────────

    /** 额外弹射物数量。 */
    public static Patch projectileCount(float base, float perLevel) {
        return value(EnchantmentEffectComponents.PROJECTILE_COUNT, add(base, perLevel),
                "额外射出 " + amount(base, perLevel) + " 发");
    }

    /** 额外穿透数量。 */
    public static Patch projectilePiercing(float base, float perLevel) {
        return absoluteValue(EnchantmentEffectComponents.PROJECTILE_PIERCING, base, perLevel,
                "额外穿透 " + amount(base, perLevel) + " 个目标");
    }

    /**
     * 弹射物数量的<b>绝对值版</b>（整条替换）。
     *
     * <p>为什么不能沿用 {@link #projectileCount}：它在原版条目已被阶级曲线改写过之后
     * <b>合并不上</b>，结果原版那条与新值同时存在——实测多出了一份（数量 6+4 出现两次）。
     * 效果总表 v3 的语义是「这一阶就是这些数」，所以这里直接整条替换。
     */
    public static Patch projectileCountTotal(float base, float perLevel) {
        return replaceValue(EnchantmentEffectComponents.PROJECTILE_COUNT, base, perLevel, "弹射物数量 " + amount(base, perLevel));
    }

    /** 弹射物散射角的<b>绝对值版</b>（整条替换），理由同 {@link #projectileCountTotal}。 */
    public static Patch projectileSpreadTotal(float base, float perLevel) {
        return replaceValue(EnchantmentEffectComponents.PROJECTILE_SPREAD, base, perLevel, "散射角 " + amount(base, perLevel));
    }

    /**
     * 「无条件线性值」组件的<b>绝对覆盖</b>。
     *
     * <p>效果总表 v3 要求每阶写的都是该阶的<b>总值</b>，所以这条必须覆盖而不是追加：
     * <ul>
     *   <li>原版已有条目 → 沿用它的 {@code requirements}（条件属于附魔身份，例如
     *       「对水生生物」的穿刺增伤），只把数值换成我们的；</li>
     *   <li>原版没有 → 新建一条无条件条目。</li>
     * </ul>
     *
     * <p>⚠️ 老式的 {@code value(...)} 是「能合就合、否则追加」——原版条目带条件时合不上，
     * 于是原版那条与我们的同时存在，数值直接翻倍（实测：穿刺 7.5+5 出现两次、
     * 力量 3+1.5 出现两次、穿透与抢夺同理）。所有 v3 谱系一律走本条。
     */
    private static Patch absoluteValue(DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> type,
                                       float base, float perLevel, String describe) {
        return new Rewrite<>(type, existing -> List.of(new ConditionalEffect<>(add(base, perLevel),
                existing.isEmpty() ? Optional.empty() : existing.get(0).requirements())), describe);
    }

    /** 整条替换一个「无条件线性值」组件。 */
    private static Patch replaceValue(DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> type,
                                      float base, float perLevel, String describe) {
        return new Replace<>(type,
                List.of(new ConditionalEffect<>(add(base, perLevel), Optional.empty())), describe);
    }

    /** 额外散射角度（越大越散）。 */
    public static Patch projectileSpread(float base, float perLevel) {
        return value(EnchantmentEffectComponents.PROJECTILE_SPREAD, add(base, perLevel),
                "散射角 +" + amount(base, perLevel));
    }

    /**
     * 整条替换弩的装填时间减免（原版快速装填是 {@code Linear(-0.25, -0.25)}）。
     *
     * <p><b>覆盖语义</b>：必须比原版<b>整条更强</b>，并用
     * {@link LevelBasedValue.Clamped} 兜住上界——{@code CrossbowItem.getChargeDuration}
     * 会 {@code Mth.floor(f * 20)}，负值会算出负 tick。
     *
     * @param extraReduction 在原版 {@code -0.25} 之上每级多减免多少
     */
    public static Patch crossbowChargeTime(float extraReduction) {
        float amount = 0.25f + extraReduction;
        return new Replace<>(EnchantmentEffectComponents.CROSSBOW_CHARGE_TIME,
                new AddValue(new LevelBasedValue.Clamped(
                        new LevelBasedValue.Linear(-amount, -amount), -0.9f, 0.0f)),
                "-" + num(amount) + "×等级（下限 -0.9）");
    }

    /** 三叉戟回旋加速（原版忠诚；追加语义，可安全叠加）。 */
    public static Patch tridentReturnAcceleration(float base, float perLevel) {
        return value(EnchantmentEffectComponents.TRIDENT_RETURN_ACCELERATION, add(base, perLevel),
                "三叉戟回旋加速 " + amount(base, perLevel));
    }

    /** 整条替换三叉戟旋冲伤害强度（原版激流是 {@code Linear(1.5, 0.75)}）。 */
    public static Patch tridentSpinAttackStrength(float base, float perLevel) {
        return new Replace<>(EnchantmentEffectComponents.TRIDENT_SPIN_ATTACK_STRENGTH, add(base, perLevel),
                amount(base, perLevel));
    }

    // ── 钓鱼 ─────────────────────────────────────────────────────────────

    /** 缩短咬钩等待时间。 */
    public static Patch fishingTimeReduction(float base, float perLevel) {
        return absoluteValue(EnchantmentEffectComponents.FISHING_TIME_REDUCTION, base, perLevel,
                "咬钩等待 -" + amount(base, perLevel));
    }

    /** 提高钓鱼幸运值。 */
    public static Patch fishingLuck(float base, float perLevel) {
        return absoluteValue(EnchantmentEffectComponents.FISHING_LUCK_BONUS, base, perLevel,
                "钓鱼幸运 +" + amount(base, perLevel));
    }

    // ── 属性 ─────────────────────────────────────────────────────────────

    /**
     * 追加一条属性修饰符。
     *
     * <p>若原版在同一属性上有恰好一条同运算的修饰符，{@link #mergeIntoVanilla}
     * 会把两者合并成一条覆盖式——所以这条通常不会以「追加」的形态留在产物里。
     *
     * @param id 修饰符 id，形如 {@code "sharpness"}，实际写为
     *           {@code ultraenchantment:ascension/<id>}
     */
    public static Patch attribute(String id, Holder<Attribute> attribute,
                                  float base, float perLevel, AttributeModifier.Operation operation) {
        return new Append<>(EnchantmentEffectComponents.ATTRIBUTES,
                new EnchantmentAttributeEffect(
                        ResourceLocation.fromNamespaceAndPath(Ultraenchantment.MODID, "ascension/" + id),
                        attribute,
                        new LevelBasedValue.Linear(base, perLevel),
                        operation),
                "属性 " + shortAttributeName(attribute) + " " + operationName(operation)
                        + " " + amount(base, perLevel));
    }

    /**
     * 属性的短名——去掉 {@code generic.} / {@code player.} 这类分类前缀。
     *
     * <p>注册路径是 {@code generic.oxygen_bonus}、{@code player.sweeping_damage_ratio}，
     * 前缀只是原版的历史分类，读表时是纯噪声。
     */
    static String shortAttributeName(Holder<Attribute> attribute) {
        String path = attribute.unwrapKey().map(key -> key.location().getPath()).orElse("attribute");
        int dot = path.indexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }

    private static String operationName(AttributeModifier.Operation operation) {
        return switch (operation) {
            case ADD_VALUE -> "加算";
            case ADD_MULTIPLIED_BASE -> "按基础值乘算";
            case ADD_MULTIPLIED_TOTAL -> "按总值乘算";
        };
    }

    // ── 完全免疫 / 条件减免（效果总表 v3） ──────────────────────────────
    //
    // v3 起，阶级数值是**逐阶给定的绝对值**（不是「原版 × 倍率」），所以这一组
    // 全是**整条覆盖**：某阶要什么数，就直接写成那个数，不依赖任何缩放。

    /**
     * 清空一个列表型组件。
     *
     * <p>用在「换成完全免疫之后，原来那条按点数减免不再需要」这类地方——
     * 留着它既不是作者要的数值，也会让效果总表多出一行看不懂的东西。
     */
    public static <T> Patch clear(DataComponentType<List<T>> type, String why) {
        return new Replace<>(type, List.<T>of(), why);
    }

    /** 伤害来源条件：命中指定伤害类型标签；可选「排除无视无敌的伤害」（原版保护的排除清单）。 */
    private static LootItemCondition damageTagCondition(TagKey<DamageType> tag, boolean excludeBypass) {
        DamageSourcePredicate.Builder builder = DamageSourcePredicate.Builder.damageType();
        builder.tag(TagPredicate.is(tag));
        if (excludeBypass) {
            builder.tag(TagPredicate.isNot(DamageTypeTags.BYPASSES_INVULNERABILITY));
        }
        return DamageSourceCondition.hasDamageSource(builder).build();
    }

    /** 一条「按点数减免」条目：{@code base + perLevel×(n-1)}，限定某个伤害类型标签。 */
    public static ConditionalEffect<EnchantmentValueEffect> damageTypeProtection(
            TagKey<DamageType> tag, float base, float perLevel) {
        return new ConditionalEffect<>(add(base, perLevel), Optional.of(damageTagCondition(tag, true)));
    }

    /** 一条**无条件**减免条目（对应「取消 requirements，全伤害生效」）。 */
    public static ConditionalEffect<EnchantmentValueEffect> unconditionalProtection(float base, float perLevel) {
        return new ConditionalEffect<>(add(base, perLevel), Optional.empty());
    }

    /** 一条「原版保护式」条目：带 {@code bypasses_invulnerability} 排除清单，不限定伤害类型。 */
    public static ConditionalEffect<EnchantmentValueEffect> vanillaStyleProtection(float base, float perLevel) {
        DamageSourcePredicate.Builder builder = DamageSourcePredicate.Builder.damageType();
        builder.tag(TagPredicate.isNot(DamageTypeTags.BYPASSES_INVULNERABILITY));
        return new ConditionalEffect<>(add(base, perLevel),
                Optional.of(DamageSourceCondition.hasDamageSource(builder).build()));
    }

    /**
     * 同一阶同时给「沿用原版条件的一条」与「无条件的一条」（亡灵杀手用）。
     *
     * <p>为什么不能写两次 {@link #damage}：那样第二条会把第一条覆盖掉，条件也会丢。
     * 这里直接整表替换 {@code DAMAGE}：第一条**沿用原版条目的 requirements**（如「目标属于亡灵」
     * 是原版附魔身份的一部分，不自己造），第二条无条件。
     */
    public static Patch conditionalPlusUnconditional(
            DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> type,
            float conditionalBase, float conditionalPerLevel,
            float unconditionalBase, float unconditionalPerLevel,
            String describe) {
        return new Rewrite<>(type, existing -> {
            if (existing.isEmpty()) {
                throw new IllegalStateException("原版没有可沿用的条件条目，无法构造「条件 + 无条件」");
            }
            List<ConditionalEffect<EnchantmentValueEffect>> rebuilt = new ArrayList<>();
            rebuilt.add(new ConditionalEffect<>(add(conditionalBase, conditionalPerLevel),
                    existing.get(0).requirements()));
            rebuilt.add(new ConditionalEffect<>(add(unconditionalBase, unconditionalPerLevel), Optional.empty()));
            return List.copyOf(rebuilt);
        }, describe);
    }

    /** 逐条改写现有列表：保留原版的条件与结构，只换数值。 */
    public record Rewrite<T>(DataComponentType<List<T>> type,
                             java.util.function.UnaryOperator<List<T>> editor,
                             String describe) implements Patch {
        @Override
        public DataComponentMap apply(DataComponentMap base) {
            List<T> existing = base.get(this.type);
            return DataComponentMap.builder().addAll(base)
                    .set(this.type, this.editor.apply(existing == null ? List.of() : existing)).build();
        }

        @Override
        public DataComponentType<?> component() {
            return this.type;
        }

        @Override
        public boolean appends() {
            return false;
        }
    }

    /**
     * 荆棘（效果总表 v3）：反伤范围、耐久消耗、触发概率。
     *
     * <p><b>必须改写原版那条 {@code post_attack}</b>，不能新建：
     * <ul>
     *   <li>反伤的 {@code damageType} 是 {@code Holder<DamageType>}——自己造
     *       {@code Holder.direct} 会写出无法序列化的数据（P0-2），只能沿用原版那个；</li>
     *   <li>概率也不是普通随机数：实测原版是
     *       {@code LootItemRandomChanceCondition(EnchantmentLevelProvider(Linear(0.15, 0.15)))}，
     *       即「按附魔等级算概率」，结构必须原样保留。</li>
     * </ul>
     *
     * <p>v3 起荆棘<b>不再挂</b>伤害减免与击退——只留反伤、耐久消耗、概率三件事。
     */
    public static Patch thorns(float minDamage, float maxDamage, float itemDamage,
                               float chanceBase, float chancePerLevel) {
        return new Rewrite<>(EnchantmentEffectComponents.POST_ATTACK, existing -> {
            if (existing.isEmpty()) {
                throw new IllegalStateException("原版荆棘没有 post_attack，无法改写");
            }
            TargetedConditionalEffect<EnchantmentEntityEffect> vanilla = existing.get(0);
            Holder<DamageType> damageType = null;
            if (vanilla.effect() instanceof AllOf.EntityEffects allOf) {
                for (EnchantmentEntityEffect effect : allOf.effects()) {
                    if (effect instanceof DamageEntity entity) {
                        damageType = entity.damageType();
                        break;
                    }
                }
            }
            if (damageType == null) {
                throw new IllegalStateException("原版荆棘里找不到 damage_entity，无法沿用伤害类型");
            }
            EnchantmentEntityEffect reflect = new AllOf.EntityEffects(List.of(
                    new DamageEntity(new LevelBasedValue.Constant(minDamage),
                            new LevelBasedValue.Constant(maxDamage), damageType),
                    new DamageItem(new LevelBasedValue.Constant(itemDamage))));
            LootItemCondition chance = new LootItemRandomChanceCondition(
                    new EnchantmentLevelProvider(new LevelBasedValue.Linear(chanceBase, chancePerLevel)));
            return List.of(new TargetedConditionalEffect<>(vanilla.enchanted(), vanilla.affected(),
                    reflect, Optional.of(chance)));
        }, "反伤 " + num(minDamage) + "~" + num(maxDamage) + "，每次消耗耐久 " + num(itemDamage)
                + "，触发概率 " + amount(chanceBase, chancePerLevel));
    }

    /** 整条替换 {@code damage_protection}（条目顺序即声明顺序）。 */
    @SafeVarargs
    public static Patch damageProtectionList(String describe,
                                             ConditionalEffect<EnchantmentValueEffect>... entries) {
        return new Replace<>(EnchantmentEffectComponents.DAMAGE_PROTECTION, List.of(entries), describe);
    }

    /**
     * 完全免疫某个伤害类型。
     *
     * <p>{@code DamageImmunity} 是<b>无字段</b>记录，免疫哪种伤害完全由 {@code requirements} 表达
     * （原版就是这么设计的），所以这里必须给条件，否则等于免疫一切。
     */
    public static Patch damageImmunity(TagKey<DamageType> tag, String describe) {
        return new Replace<>(EnchantmentEffectComponents.DAMAGE_IMMUNITY,
                List.of(new ConditionalEffect<>(DamageImmunity.INSTANCE,
                        Optional.of(damageTagCondition(tag, false)))),
                describe);
    }

    // ── 属性（整表替换，避免与原版那条叠加） ─────────────────────────────

    /** 用给定的属性修饰符**整表替换** {@code attributes}。 */
    public static Patch replaceAttributes(String describe, EnchantmentAttributeEffect... entries) {
        return new Replace<>(EnchantmentEffectComponents.ATTRIBUTES, List.of(entries), describe);
    }

    private static EnchantmentAttributeEffect attributeEntry(String id, Holder<Attribute> attribute,
                                                             float base, float perLevel,
                                                             AttributeModifier.Operation operation) {
        return new EnchantmentAttributeEffect(
                ResourceLocation.fromNamespaceAndPath(Ultraenchantment.MODID, "ascension/" + id),
                attribute, new LevelBasedValue.Linear(base, perLevel), operation);
    }

    private static String attributeDescribe(Holder<Attribute> attribute, float base, float perLevel,
                                            AttributeModifier.Operation operation) {
        return "属性 " + shortAttributeName(attribute) + " " + operationName(operation)
                + " " + amount(base, perLevel);
    }

    /** 燃烧时间（原版火焰保护用同一条属性，这里整表替换成我们的数值）。 */
    public static Patch burningTime(float base, float perLevel) {
        return replaceAttributes(attributeDescribe(Attributes.BURNING_TIME, base, perLevel, ADD_MULTIPLIED_BASE),
                attributeEntry("burning_time", Attributes.BURNING_TIME, base, perLevel, ADD_MULTIPLIED_BASE));
    }

    /** 爆炸击退抗性（原版爆炸保护用的就是这条）。 */
    public static Patch knockbackResistance(float base, float perLevel) {
        return replaceAttributes(attributeDescribe(Attributes.KNOCKBACK_RESISTANCE, base, perLevel, ADD_VALUE),
                attributeEntry("knockback_resistance", Attributes.KNOCKBACK_RESISTANCE, base, perLevel, ADD_VALUE));
    }

    /** 攻击伤害百分比（{@code add_multiplied_total}，作用在总值上）。 */
    public static Patch attackDamageTotal(float base, float perLevel) {
        return replaceAttributes(attributeDescribe(Attributes.ATTACK_DAMAGE, base, perLevel, ADD_MULTIPLIED_TOTAL),
                attributeEntry("attack_damage_total", Attributes.ATTACK_DAMAGE, base, perLevel,
                        ADD_MULTIPLIED_TOTAL));
    }

    /** 氧气加成（原版水下呼吸用的就是这条）。 */
    public static Patch oxygenBonus(float base, float perLevel) {
        return replaceAttributes(attributeDescribe(Attributes.OXYGEN_BONUS, base, perLevel, ADD_VALUE),
                attributeEntry("oxygen_bonus", Attributes.OXYGEN_BONUS, base, perLevel, ADD_VALUE));
    }

    // ── 弹药 / 经验修补（整条替换） ──────────────────────────────────────

    /** 弹药消耗归零，且**不限定箭种**（原版无限只对普通箭生效）。 */
    public static Patch ammoUseUnconditional() {
        return new Replace<>(EnchantmentEffectComponents.AMMO_USE,
                List.of(new ConditionalEffect<EnchantmentValueEffect>(
                        new SetValue(new LevelBasedValue.Constant(0.0f)), Optional.empty())),
                "弹药不消耗（所有箭种，取消原版的普通箭限制）");
    }

    /** 经验修补总倍率（整条替换原版的 {@code ×2}）。 */
    public static Patch repairWithXpTotal(float factor) {
        return new Replace<>(EnchantmentEffectComponents.REPAIR_WITH_XP,
                List.of(new ConditionalEffect<EnchantmentValueEffect>(
                        new MultiplyValue(new LevelBasedValue.Constant(factor)), Optional.empty())),
                "经验修补效率 ×" + num(factor));
    }

    // ── 自检 ─────────────────────────────────────────────────────────────

    /**
     * 断言同一阶段内没有重复的属性修饰符 id。
     *
     * <p>把「运行期 {@code AttributeInstance} 抛异常」提前成 datagen 报错。
     */
    public static void assertUniqueAttributeIds(ResourceLocation stageId, DataComponentMap effects) {
        List<EnchantmentAttributeEffect> attributes = effects.get(EnchantmentEffectComponents.ATTRIBUTES);
        if (attributes == null) {
            return;
        }

        Set<ResourceLocation> seen = new HashSet<>();
        for (EnchantmentAttributeEffect effect : attributes) {
            if (!seen.add(effect.id())) {
                throw new IllegalStateException("阶段 " + stageId
                        + " 内出现重复的属性修饰符 id：" + effect.id()
                        + "——同名修饰符会在运行期让 AttributeInstance.addModifier 抛异常");
            }
        }
    }
}
