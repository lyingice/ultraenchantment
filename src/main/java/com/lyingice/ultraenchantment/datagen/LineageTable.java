package com.lyingice.ultraenchantment.datagen;

import static com.lyingice.ultraenchantment.datagen.UEStageEffects.armorEffectiveness;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.conditionalPlusUnconditional;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.damageTypeProtection;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.knockbackResistance;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.vanillaStyleProtection;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.burningTime;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.ammoUseUnconditional;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.attackDamageTotal;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.projectileSpreadTotal;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.projectileCountTotal;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.attribute;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.join;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.blockExperience;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.crossbowChargeTime;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.damage;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.clear;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.damageImmunity;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.damageProtection;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.damageProtectionList;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.oxygenBonus;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.repairWithXpTotal;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.unconditionalProtection;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.durabilitySave;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.equipmentDrops;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.fishingLuck;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.fishingTimeReduction;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.knockback;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.none;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.mobExperience;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.postAttackIgnite;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.postAttackMobEffect;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.chanceCondition;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.entityTypeCondition;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.notEntityTypeCondition;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.projectileCount;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.projectilePiercing;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.projectileSpawnedIgnite;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.repairWithXp;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.tridentReturnAcceleration;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.thorns;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.tridentSpinAttackStrength;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.content.Growth;
import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.content.UEEffect;
import com.lyingice.ultraenchantment.registry.UEMobEffects;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

/**
 * 谱系表——<b>阶段条目、阶梯本地化名、阶位加成的唯一事实源</b>。
 *
 * <h2>为什么要把「有哪些谱系」抽出来</h2>
 *
 * <p>同一个信息有两个消费者：
 * <ol>
 *   <li>{@link UEStages} —— 生成阶段条目的 JSON</li>
 *   <li>{@link UELang} —— 生成阶梯的本地化键</li>
 * </ol>
 *
 * <p>若各自维护一份表，加一条谱系就得改两处，漏一处就是「附魔有阶级但没名字」——
 * 而这种缺陷在 datagen 阶段不会报错，只会在游戏里显示成
 * {@code enchantment.ultraenchantment.super.sharpness}。抽成单一表可根除。
 *
 * <h2>本表只管一件事：这一阶比上一阶多给了什么</h2>
 *
 * <p>{@link UEStages} 生成阶段条目时，{@code definition}（含 <b>{@code max_level}</b>、
 * {@code supported_items}、{@code slots}、花费）与 {@code effects} 的<b>基础部分</b>
 * 全部整段取自该谱系根源附魔。所以：
 * <ul>
 *   <li><b>等级上限永远是原版的</b>——耐久 3 的高阶 / 超级 / 究极上限全都是 3，
 *       锋利 5 的三阶上限全都是 5。本模组不发明自己的等级刻度。</li>
 *   <li>本表<b>不需要</b>写「锋利 +1 伤害」这类基础效果——它们是原版的，照抄即可。</li>
 * </ul>
 *
 * <h2>阶梯是<b>绝对</b>的（v3 起）</h2>
 *
 * <p>每条谱系用 {@link #ladder} 给出 1~3 个阶位规格，<b>每一阶写的都是该阶的总值</b>，
 * 不是「比上一阶多出来的一项」。生成阶段条目时只取该阶自己的补丁，
 * 低阶的补丁<b>不会</b>继续叠加到高阶上。
 *
 * <pre>
 * 高阶 = 原版效果 + 补丁[0]（该阶总值）
 * 超级 = 原版效果 + 补丁[1]（该阶总值，不是 补丁[0]+补丁[1]）
 * 究极 = 原版效果 + 补丁[2]（该阶总值）
 * </pre>
 *
 * <p>旧式 {@link #lineage}（累积补丁）只为历史数据保留；本表已全部改用 {@link #ladder}。
 *
 * <h2>加成数值</h2>
 *
 * <p>数值是为「每一阶大约在原版同级强度上再抬 30%~80%」写的初值，
 * 全部是字面量、就地可调（AGENT.md §7 把具体数值列为作者人工微调的开放项）。
 * 参数含义统一为 {@code (1 级时的量, 每级递增的量)}——加成同样随存储等级成长。
 *
 * <h2>收录范围：玩家常用核心（26 条）</h2>
 *
 * <p>刻意<b>不做</b>长尾附魔。要补一条，在下面表单里加一行即可——三阶补丁 + 中英名，
 * 其余（物品标签、槽位、等级上限、原版效果）全部自动从根源附魔取。
 *
 * <p><b>1.0 调整</b>：
 * <ul>
 *   <li>纳入 {@code bane_of_arthropods}（节肢杀手）——三阶只改节肢增伤，
 *       原版「命中节肢生物附加缓慢」照旧保留。</li>
 *   <li>移除 {@code efficiency}（效率）——三阶都只是挖掘效率/方块经验的小额数值，
 *       究极甚至只是把超级的 +1 效率再追加一遍；相对原版「等级平方 + 1」的基数，
 *       提升不足以支撑一条独立谱系，属于典型的数值填充。</li>
 * </ul>
 *
 * <h3>刻意排除：做了也不会生效</h3>
 *
 * <table>
 *   <tr><th>附魔</th><th>为什么不进表</th></tr>
 *   <tr><td>{@code binding_curse} / {@code vanishing_curse}</td>
 *       <td>诅咒。进阶对玩家永远是好事，给诅咒做成长阶梯是语义矛盾；
 *           且二者没有数值，只有 {@code prevent_armor_change} / {@code prevent_equipment_drop}
 *           两个 Unit 开关，没有可提升的量。</td></tr>
 *   <tr><td>{@code fortune} / {@code silk_touch}</td>
 *       <td><b>没有 {@code effects} 字段。</b>它们的行为由战利品表 / 方块掉落里对
 *           {@code EnchantmentHelper.getEnchantmentLevel(原版附魔, 物品)} 的调用驱动，
 *           而那是<b>单点查询</b>——结算层刻意对单点查询保留原等级（让第三方模组
 *           读到真实等级，见 {@code EnchantmentLevelEvents} 的设计取舍）。
 *           于是给它们挂任何效果都不会改变实际行为。与其生成 6 个
 *           「看起来有、实际无效」的条目，不如不生成：没条目 = 进阶书对它们无效，
 *           这是<b>可观察</b>的正确行为，而不是静默的假象。
 *           要让这两条可进阶，要改的是结算/掉落层的代码，不是数据包。</td></tr>
 * </table>
 *
 * <h3>暂不纳入：长尾 / 场景过窄</h3>
 *
 * <table>
 *   <tr><th>附魔</th><th>理由</th></tr>
 *   <tr><td>{@code density} / {@code breach} / {@code wind_burst}</td><td>重锤专属，后期内容</td></tr>
 *   <tr><td>{@code soul_speed} 灵魂疾行</td><td>只在灵魂沙/灵魂土上生效</td></tr>
 *   <tr><td>{@code frost_walker} 冰霜行者</td><td>娱乐向，不参与战斗/生产循环</td></tr>
 *   <tr><td>{@code aqua_affinity} 水下速掘</td><td>上限 1 级，场景窄</td></tr>
 * </table>
 *
 * <p>{@code impaling} 穿刺曾在此表（理由：三叉戟对水生生物、场景窄），
 * 2026-10 按作者要求纳入——只走它自己的原版效果，不额外加属性；
 * 1.0 起究极阶补上「驱散增益 + 百分比增伤」，让它与同组武器有质变差异。
 *
 * <h2>阶梯命名规则</h2>
 *
 * <p>进阶后的附魔使用<b>独立的本地化键</b>，不复用原版附魔名拼前缀：
 * <pre>
 *   enchantment.ultraenchantment.advanced.sharpness = 高阶锋利
 *   enchantment.ultraenchantment.super.sharpness    = 超级锋利
 *   enchantment.ultraenchantment.ultra.sharpness    = 究极锋利
 * </pre>
 *
 * <p>该键由 {@code EnchantmentFactory.descriptionOf} 在运行期构造，
 * 与本表生成的名字必须严格对应。结构即「{@code enchantment.<ns>.<阶级>.<根源名>}」。
 */
public final class LineageTable {
    private LineageTable() {}

    /** 有阶段条目的三个阶级，下标即阶梯下标。 */
    public static final List<LineageTier> STAGES =
            List.of(LineageTier.ADVANCED, LineageTier.SUPER, LineageTier.ULTRA);

    /** 每条谱系的阶梯长度——{@link #lineage} 会断言，写漏一个补丁在启动 datagen 时就报错。 */
    private static final int LADDER_LENGTH = 3;

    /**
     * 一条谱系条目。
     *
     * @param root   谱系根源（原版附魔 id）
     * @param cnName 中文阶梯基名，如「锋利」——阶级词缀由 {@link UELang} 拼接
     * @param enName 英文阶梯基名，如 "Sharpness"
     * @param ladder 三个<b>增量</b>补丁，下标对应 {@link #STAGES}
     */
    public record Lineage(ResourceLocation root,
                          String cnName,
                          String enName,
                          List<StageSpec> ladder,
                          boolean cumulative) {

        public Lineage {
            Objects.requireNonNull(root, "root");
            ladder = List.copyOf(ladder);
            if (ladder.isEmpty() || ladder.size() > STAGES.size()) {
                throw new IllegalArgumentException("谱系 " + root + " 的阶梯长度应为 1~"
                        + STAGES.size() + "，实际 " + ladder.size());
            }
        }

        /** 本条谱系实际拥有的阶级（阶梯长度的前缀，例如保护只有【高阶】）。 */
        public List<LineageTier> tiers() {
            return STAGES.subList(0, this.ladder.size());
        }

        /** 某阶级的规格（补丁 + 可选的上限/门槛覆盖）。 */
        public StageSpec specFor(LineageTier tier) {
            int index = this.tiers().indexOf(tier);
            if (index < 0) {
                throw new IllegalArgumentException("谱系 " + this.root + " 没有这一阶：" + tier);
            }
            return this.ladder.get(index);
        }

        /** 某阶级的阶段条目 id：{@code ultraenchantment:<tier>/<rootPath>}。 */
        public ResourceLocation stageId(LineageTier tier) {
            return ResourceLocation.fromNamespaceAndPath(
                    Ultraenchantment.MODID, tier.id() + "/" + this.root.getPath());
        }

        /**
         * 某阶级的本地化键。
         *
         * <p>必须与 {@code EnchantmentFactory.descriptionOf} 的构造规则一致——
         * 那里把阶段条目 id 的斜杠换成点，得到
         * {@code enchantment.<ns>.<阶级>.<根源名>}。
         */
        public String langKey(LineageTier tier) {
            return "enchantment." + Ultraenchantment.MODID + "." + tier.id() + "." + this.root.getPath();
        }

        /**
         * 某阶级的<b>累计</b>补丁：把阶梯从头到该阶级依次叠加。
         *
         * <p>返回的是<b>列表</b>而不是一个合体函数——生成阶段条目要按顺序 apply，
         * 生成效果总表要逐条列出「这一阶加了什么」。两件事用的是同一份声明，不会走偏。
         *
         * @throws IllegalArgumentException 传入 {@link LineageTier#NATIVE}（基础阶没有阶段条目）
         */
        public List<UEStageEffects.Patch> patchesFor(LineageTier tier) {
            int index = this.tiers().indexOf(tier);
            if (index < 0) {
                throw new IllegalArgumentException("基础阶没有阶段条目，不存在阶位加成：" + tier);
            }
            // 绝对谱系：每阶的补丁**就是该阶的全部数值**（表里每行都是该阶总值）。
            // 累积谱系（尚未按效果总表 v3 转换的老条目）：仍是「从头叠到本阶」。
            if (this.cumulative) {
                return this.ladder.subList(0, index + 1).stream().map(StageSpec::patch).toList();
            }
            return List.of(this.ladder.get(index).patch());
        }
    }

    /**
     * 一个阶级的规格：<b>该阶级的全部数值</b>（绝对语义）+ 可选的等级上限 / 进阶门槛覆盖。
     *
     * <p>效果总表 v3 起，阶级数值是逐阶给定的——表里每一行的「阶级公式」都是该阶的
     * <b>总值</b>（例如究极亡灵杀手的无条件增伤是 {@code 6 + 1.5/级}，不是「超级再 +3」）。
     * 所以每条谱系的每阶只写一个补丁，且这个补丁直接写成该阶要的数。
     *
     * @param patch         该阶级要写进 {@code effects} 的补丁（整条覆盖式）
     * @param maxLevel      等级上限覆盖；{@code null} = 沿用原版上限
     * @param requiredLevel 进阶到该阶级所需的最低等级；{@code null} = 原版上限（即「满级」）
     * @param levelLock     <b>等级锁（超限上限）</b>；{@code null} = 不锁（完全交给神化）。
     *                      装了神化时原版上限被抬高、附魔获得超限能力；这一项给不该无限超限的
     *                      附魔设<b>绝对天花板</b>：{@code 有效上限 = min(神化上限, levelLock)}。
     */
    public record StageSpec(UEStageEffects.Patch patch, Integer maxLevel, Integer requiredLevel,
                            Integer levelLock, List<UEEffect> ueEffects) {
        public StageSpec {
            Objects.requireNonNull(patch, "patch");
            ueEffects = List.copyOf(ueEffects);
        }

        /** 无扩展效果的构造（绝大多数阶段）。 */
        public StageSpec(UEStageEffects.Patch patch, Integer maxLevel, Integer requiredLevel,
                         Integer levelLock) {
            this(patch, maxLevel, requiredLevel, levelLock, List.of());
        }
    }

    /** 只给数值（上限与门槛都按默认：原版上限 / 满级 / 不锁）。 */
    public static StageSpec stage(UEStageEffects.Patch patch) {
        return new StageSpec(patch, null, null, null);
    }

    /** 数值 + 等级上限覆盖（例如「三系保护超级」上限压到 1）。 */
    public static StageSpec stage(UEStageEffects.Patch patch, int maxLevel) {
        return new StageSpec(patch, maxLevel, null, null);
    }

    /** 数值 + 上限 + 进阶门槛三件套。 */
    public static StageSpec stage(UEStageEffects.Patch patch, Integer maxLevel, int requiredLevel) {
        return new StageSpec(patch, maxLevel, requiredLevel, null);
    }

    /** 四件套：再加<b>等级锁（超限上限）</b>。 */
    public static StageSpec stage(UEStageEffects.Patch patch, Integer maxLevel, int requiredLevel,
                                  int levelLock) {
        return new StageSpec(patch, maxLevel, requiredLevel, levelLock);
    }

    /**
     * 带 <b>`ue:*` 扩展效果</b>的阶段：原版效果补丁 + 一组挂在生命周期事件上的效果。
     *
     * <p>扩展效果（真实伤害、吸血、多段跳、自动拾取……）无法用原版效果组件表达，
     * 于是随阶段条目写进独立的 {@code ue_effects} 字段，由本模组自己的监听器执行。
     */
    public static StageSpec stage(UEStageEffects.Patch patch, UEEffect... ueEffects) {
        return new StageSpec(patch, null, null, null, List.of(ueEffects));
    }

    /** 带扩展效果 + 等级上限覆盖。 */
    public static StageSpec stage(UEStageEffects.Patch patch, int maxLevel, UEEffect... ueEffects) {
        return new StageSpec(patch, maxLevel, null, null, List.of(ueEffects));
    }

    /** 「被命中后对攻击者施加一条状态效果」的扩展效果（`ue:be_attacked`）。 */
    private static UEEffect beAttacked(Holder<MobEffect> effect, float baseSeconds, float perLevelSeconds) {
        return new UEEffect.BeAttacked(List.of(new UEEffect.AppliedEffect(
                effect,
                new Growth(baseSeconds, perLevelSeconds),
                Growth.CONSTANT_ZERO,
                UEEffect.EffectTarget.ATTACKER)));
    }

    /** 「格挡成功后触发」的扩展效果：固定反击伤害 + 按概率反伤（`ue:on_block`）。 */
    private static UEEffect onBlock(float counterDamage, float reflectChance, float reflectRatio) {
        return new UEEffect.OnBlock(
                new UEEffect.DamageBack(Growth.of(counterDamage), Growth.of(reflectChance),
                        Growth.of(reflectRatio)),
                List.of());
    }

    /** 「盾牌被破防时触发」的扩展效果（`ue:on_shield_disabled`）。 */
    private static UEEffect onShieldDisabled(float counterDamage, float reflectChance, float reflectRatio) {
        return new UEEffect.OnShieldDisabled(
                new UEEffect.DamageBack(Growth.of(counterDamage), Growth.of(reflectChance),
                        Growth.of(reflectRatio)),
                List.of());
    }

    /**
     * 谱系表。每条一行，三个补丁分别是「高阶 / 超级 / 究极比上一阶多出来的东西」。
     *
     * <p>顺序按「近战武器 → 护甲 → 通用工具 → 弓弩 → 三叉戟 → 钓鱼」分组，便于查阅。
     */
    private static final List<Lineage> ALL = List.of(
            // ── 近战武器 ──────────────────────────────────────────────────

            ladder("sharpness", "锋利", "Sharpness",
                    stage(damage(3.0f, 1.0f)),
                    stage(join(damage(6.0f, 1.5f), armorEffectiveness(-0.05f, -0.05f))),
                    stage(join(damage(10.0f, 2.5f), armorEffectiveness(-0.25f, -0.10f)),
                            new UEEffect.RealDamage(new Growth(0.20f, 0.20f), Growth.of(1.0f),
                                    Growth.CONSTANT_ZERO, Growth.of(1.0f)),
                            new UEEffect.Hemophagia(new Growth(0.15f, 0.05f)),
                            new UEEffect.SelfVulnerability(Growth.of(0.10f)))),
            ladder("bane_of_arthropods", "节肢杀手", "Bane of Arthropods",
                    stage(damage(10.0f, 5.0f)),
                    stage(damage(25.0f, 7.5f)),
                    stage(damage(50.0f, 10.0f))),
            ladder("smite", "亡灵杀手", "Smite",
                    stage(damage(7.5f, 5.0f)),
                    stage(join(conditionalPlusUnconditional(EnchantmentEffectComponents.DAMAGE,
                                    15.0f, 7.5f, 3.0f, 1.0f, "对亡灵 15+7.5/级；另加无条件 3+1/级"),
                            postAttackMobEffect(MobEffects.HEAL, 0.05f, 0.0f, 0.0f, 1.0f,
                                    entityTypeCondition(EntityTypeTags.UNDEAD)))),
                    stage(join(
                            conditionalPlusUnconditional(EnchantmentEffectComponents.DAMAGE,
                                    25.0f, 12.5f, 6.0f, 1.5f, "对亡灵 25+12.5/级；另加无条件 6+1.5/级"),
                            postAttackMobEffect(MobEffects.HEAL, 0.05f, 0.0f, 5.0f, 1.0f,
                                    entityTypeCondition(EntityTypeTags.UNDEAD)),
                            postAttackMobEffect(MobEffects.HARM, 0.05f, 0.0f, 0.0f, 1.0f,
                                    notEntityTypeCondition(EntityTypeTags.UNDEAD))),
                            new UEEffect.RealDamage(new Growth(0.30f, 0.10f), Growth.of(0.5f),
                                    new Growth(0.05f, 0.05f), Growth.of(0.60f)))),
            ladder("knockback", "击退", "Knockback",
                    stage(join(knockback(3.0f, 2.0f),
                            postAttackMobEffect(MobEffects.MOVEMENT_SLOWDOWN, 10.0f, 10.0f,
                                    1.0f, 0.0f, chanceCondition(0.20f))),
                            new UEEffect.KineticDamage(new Growth(0.10f, 0.10f))),
                    stage(join(knockback(6.0f, 3.0f),
                            postAttackMobEffect(MobEffects.MOVEMENT_SLOWDOWN, 10.0f, 10.0f,
                                    3.0f, 0.0f, chanceCondition(0.50f))),
                            new UEEffect.KineticDamage(new Growth(0.20f, 0.20f))),
                    stage(join(knockback(10.0f, 5.0f),
                            postAttackMobEffect(MobEffects.MOVEMENT_SLOWDOWN, 10.0f, 10.0f,
                                    3.0f, 0.0f)),
                            new UEEffect.KineticDamage(new Growth(0.40f, 0.30f)),
                            new UEEffect.SwapPosition(Growth.of(0.5f), UEEffect.Activation.BOTH))),
            ladder("fire_aspect", "火焰附加", "Fire Aspect",
                    stage(postAttackMobEffect(UEMobEffects.FIRE_VULNERABILITY, 5.0f, 5.0f, 0.0f, 0.0f)),
                    stage(postAttackMobEffect(UEMobEffects.FIRE_VULNERABILITY, 5.0f, 5.0f, 0.0f, 0.0f),
                            new UEEffect.InfernoMight(new Growth(0.50f, 0.50f))),
                    stage(postAttackMobEffect(UEMobEffects.BURN_AGONY, 5.0f, 5.0f, 0.0f, 0.0f),
                            new UEEffect.InfernoMight(new Growth(0.50f, 0.50f)))),
            ladder("looting", "抢夺", "Looting",
                    stage(equipmentDrops(0.03f, 0.02f)),
                    stage(join(equipmentDrops(0.06f, 0.03f), mobExperience(0.15f, 0.05f))),
                    stage(join(equipmentDrops(0.10f, 0.05f), mobExperience(0.30f, 0.10f)),
                            new UEEffect.AutoPickup(),
                            new UEEffect.LootDuplication())),
            ladder("sweeping_edge", "横扫之刃", "Sweeping Edge",
                    stage(none(), new UEEffect.SweepDamage(new Growth(0.05f, 0.05f), 2)),
                    stage(none(), new UEEffect.SweepDamage(new Growth(0.10f, 0.10f), 1)),
                    stage(none(), new UEEffect.SweepDamage(new Growth(0.25f, 0.25f), 1))),

            // ── 盔甲 ─────────────────────────────────────────────────────
            ladder("protection", "保护", "Protection",
                    stage(damageProtectionList("全伤害生效的减免（取消原版的排除清单）",
                            unconditionalProtection(1.25f, 1.25f)),
                            new UEEffect.DoubleProtection(new Growth(0.08f, 0.04f)),
                            new UEEffect.DamageDodge(new Growth(0.05f, 0.03f)))),
            ladder("fire_protection", "火焰保护", "Fire Protection",
                    stage(join(burningTime(-0.25f, -0.20f),
                            damageProtectionList("火焰减免 2 +1/级；追加原版保护 1 +1/级",
                                    damageTypeProtection(DamageTypeTags.IS_FIRE, 2.0f, 1.0f),
                                    vanillaStyleProtection(1.0f, 1.0f))),
                            new UEEffect.SkipDeath(true,
                                    List.of(new UEEffect.AppliedEffect(MobEffects.FIRE_RESISTANCE,
                                            Growth.of(200.0f), Growth.of(1.0f),
                                            UEEffect.EffectTarget.SELF)))),
                    stage(join(damageImmunity(DamageTypeTags.IS_FIRE, "完全免疫火焰伤害（且不再着火）"),
                            burningTime(-1.0f, 0.0f),
                            clear(EnchantmentEffectComponents.DAMAGE_PROTECTION, "已改为完全免疫")), 1)),
            ladder("blast_protection", "爆炸保护", "Blast Protection",
                    stage(join(knockbackResistance(0.15f, 0.15f),
                            damageProtectionList("爆炸减免 2 +1/级；追加原版保护 1 +1/级",
                                    damageTypeProtection(DamageTypeTags.IS_EXPLOSION, 2.0f, 1.0f),
                                    vanillaStyleProtection(1.0f, 1.0f)))),
                    stage(join(damageImmunity(DamageTypeTags.IS_EXPLOSION, "完全免疫爆炸伤害"),
                            knockbackResistance(0.15f, 0.15f),
                            clear(EnchantmentEffectComponents.DAMAGE_PROTECTION, "已改为完全免疫")), 1)),
            ladder("projectile_protection", "弹射物保护", "Projectile Protection",
                    stage(damageProtectionList("弹射物减免 2 +1/级；追加原版保护 1 +1/级",
                            damageTypeProtection(DamageTypeTags.IS_PROJECTILE, 2.0f, 1.0f),
                            vanillaStyleProtection(1.0f, 1.0f))),
                    stage(join(damageImmunity(DamageTypeTags.IS_PROJECTILE, "完全免疫弹射物伤害"),
                            clear(EnchantmentEffectComponents.DAMAGE_PROTECTION, "已改为完全免疫")), 1)),
            ladder("feather_falling", "摔落缓冲", "Feather Falling",
                    stage(join(damageImmunity(DamageTypeTags.IS_FALL, "完全免疫摔落伤害"),
                            clear(EnchantmentEffectComponents.DAMAGE_PROTECTION,
                                    "已改为完全免疫，不再需要按点减免")), 1,
                            new UEEffect.MultiJump(Growth.of(1.0f)))),
            // 荆棘（v3）：只留反伤 / 耐久消耗 / 触发概率，不再挂减免与击退。
            // 扩展效果：数据驱动的 post_attack 反伤之外，再加「受击完全反伤」与「受击施加状态效果」。
            ladder("thorns", "荆棘", "Thorns",
                    stage(thorns(2.0f, 10.0f, 2.0f, 0.30f, 0.30f),
                            new UEEffect.DamageReflect(new Growth(0.15f, 0.10f)),
                            beAttacked(MobEffects.WEAKNESS, 60.0f, 20.0f)),
                    stage(thorns(4.0f, 20.0f, 1.0f, 0.45f, 0.45f),
                            new UEEffect.DamageReflect(new Growth(0.25f, 0.10f)),
                            beAttacked(MobEffects.WEAKNESS, 80.0f, 20.0f)),
                    stage(thorns(8.0f, 40.0f, 0.0f, 0.60f, 0.60f),
                            new UEEffect.DamageReflect(new Growth(0.40f, 0.15f)),
                            beAttacked(MobEffects.WEAKNESS, 100.0f, 20.0f))),

            // ── 通用 / 工具 ───────────────────────────────────────────────
            ladder("respiration", "水下呼吸", "Respiration",
                    stage(oxygenBonus(4.0f, 4.0f))),
            ladder("unbreaking", "耐久", "Unbreaking",
                    stage(durabilitySave(0.10f, 0.05f)),
                    stage(durabilitySave(0.20f, 0.08f),
                            onBlock(2.0f, 0.0f, 0.0f)),
                    stage(durabilitySave(0.35f, 0.10f),
                            onShieldDisabled(6.0f, 0.5f, 1.0f))),
            ladder("mending", "经验修补", "Mending",
                    stage(repairWithXpTotal(8.0f))),

            // ── 弓 / 弩 ───────────────────────────────────────────────────
            ladder("power", "力量", "Power",
                    stage(damage(1.5f, 1.0f)),
                    stage(join(damage(3.0f, 1.5f), knockback(0.25f, 0.15f))),
                    stage(join(damage(5.0f, 2.5f), knockback(0.25f, 0.15f)),
                            new UEEffect.RealDamage(new Growth(0.20f, 0.08f), Growth.of(0.5f),
                                    Growth.CONSTANT_ZERO, Growth.of(0.50f)))),
            ladder("punch", "冲击", "Punch",
                    stage(join(knockback(3.0f, 2.0f), damage(0.5f, 0.5f)))),
            ladder("flame", "火矢", "Flame",
                    stage(projectileSpawnedIgnite(60.0f))),
            ladder("infinity", "无限", "Infinity",
                    stage(ammoUseUnconditional())),
            ladder("multishot", "多重射击", "Multishot",
                    stage(join(projectileCountTotal(6.0f, 4.0f), projectileSpreadTotal(15.0f, 10.0f))),
                    stage(join(projectileCountTotal(12.0f, 6.0f), projectileSpreadTotal(30.0f, 15.0f),
                            damage(0.5f, 0.5f))),
                    stage(join(projectileCountTotal(20.0f, 10.0f), projectileSpreadTotal(50.0f, 25.0f),
                            damage(0.5f, 0.5f), projectilePiercing(1.0f, 1.0f)),
                            new UEEffect.ProjectileSpeed(new Growth(1.5f, 0.25f)))),
            ladder("piercing", "穿透", "Piercing",
                    stage(join(projectilePiercing(3.0f, 2.0f), damage(0.5f, 0.5f))),
                    stage(join(projectilePiercing(6.0f, 3.0f), damage(1.0f, 1.0f),
                            knockback(0.25f, 0.15f))),
                    stage(join(projectilePiercing(10.0f, 5.0f), damage(1.5f, 1.5f),
                            knockback(0.5f, 0.3f)),
                            new UEEffect.BypassIframes(new Growth(0.35f, 0.15f)))),

            // ── 三叉戟 ───────────────────────────────────────────────────
            ladder("impaling", "穿刺", "Impaling",
                    stage(damage(7.5f, 5.0f)),
                    stage(damage(15.0f, 7.5f)),
                    stage(join(damage(25.0f, 12.5f)),
                            new UEEffect.StripBuffs(new Growth(1.0f, 0.5f)),
                            new UEEffect.PercentDamage(new Growth(0.10f, 0.05f)))),
            // 激流：三阶上限都是 1，且门槛就是 1（原版上限是 3，所以门槛必须显式给）。
            ladder("riptide", "激流", "Riptide",
                    stage(tridentSpinAttackStrength(6.0f, 1.5f), 1, 1),
                    stage(tridentSpinAttackStrength(12.0f, 3.0f), 1, 1),
                    stage(tridentSpinAttackStrength(24.0f, 6.0f), 1, 1)),

            // ── 钓鱼 ─────────────────────────────────────────────────────
            ladder("lure", "饵钓", "Lure",
                    stage(fishingTimeReduction(15.0f, 10.0f)),
                    stage(join(fishingTimeReduction(30.0f, 15.0f), fishingLuck(0.5f, 0.5f))),
                    stage(join(fishingTimeReduction(50.0f, 25.0f), fishingLuck(0.5f, 0.5f)))),
            ladder("luck_of_the_sea", "海之眷顾", "Luck of the Sea",
                    stage(fishingLuck(3.0f, 2.0f)),
                    stage(join(fishingLuck(6.0f, 3.0f), fishingTimeReduction(1.0f, 1.0f))),
                    stage(join(fishingLuck(10.0f, 5.0f), fishingTimeReduction(1.0f, 1.0f))))

    );

    static {
        // 阶梯写漏一个补丁，在类加载时就响亮失败——而不是生成出少一阶的静默产物。
        for (Lineage lineage : ALL) {
            int size = lineage.ladder().size();
            if (size < 1 || size > LADDER_LENGTH) {
                throw new IllegalStateException("谱系 " + lineage.root() + " 的阶梯长度应为 1~"
                        + LADDER_LENGTH + "（高阶/超级/究极），实际 " + size);
            }
        }
    }

    public static List<Lineage> all() {
        return ALL;
    }

    /**
     * <b>老式（累积）</b>：三个增量补丁，高阶 / 超级 / 究极各比上一阶多出来的那一项。
     *
     * <p>仅用于尚未按效果总表 v3 转换的谱系——转换后请改用 {@link #ladder}。
     */
    private static Lineage lineage(String path, String cnName, String enName,
                                   UEStageEffects.Patch advanced,
                                   UEStageEffects.Patch superb,
                                   UEStageEffects.Patch ultra) {
        return new Lineage(ResourceLocation.withDefaultNamespace(path), cnName, enName,
                List.of(stage(advanced), stage(superb), stage(ultra)), true);
    }

    /**
     * <b>效果总表 v3（绝对）</b>：逐阶给出该阶的全部数值，阶数可少于三阶。
     *
     * <p>例：{@code ladder("protection", "保护", "Protection", stage(unconditionalProtection...))}
     * ——保护只有高阶，写完一阶即可；没有的阶级既不会生成 JSON，也不会出现在 JEI 与语言文件里。
     */
    private static Lineage ladder(String path, String cnName, String enName, StageSpec... stages) {
        return new Lineage(ResourceLocation.withDefaultNamespace(path), cnName, enName,
                List.of(stages), false);
    }
}
