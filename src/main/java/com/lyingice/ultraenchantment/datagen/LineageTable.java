package com.lyingice.ultraenchantment.datagen;

import static com.lyingice.ultraenchantment.datagen.UEStageEffects.armorEffectiveness;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.attribute;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.blockExperience;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.crossbowChargeTime;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.damage;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.damageProtection;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.durabilitySave;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.equipmentDrops;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.fishingLuck;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.fishingTimeReduction;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.knockback;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.none;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.mobExperience;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.postAttackIgnite;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.postAttackMobEffect;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.projectileCount;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.projectilePiercing;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.projectileSpawnedIgnite;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.repairWithXp;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.tridentReturnAcceleration;
import static com.lyingice.ultraenchantment.datagen.UEStageEffects.tridentSpinAttackStrength;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.content.LineageTier;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;

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
 * <h2>阶梯是累积的</h2>
 *
 * <p>每条谱系给出 <b>3 个增量补丁</b>，按下标对应高阶 / 超级 / 究极。
 * 某一个阶级实际拿到的补丁是「从第一个到它自己」的全部补丁依次叠加，
 * 所以写表时只写「比上一阶多出来的那一项」，不要重复上一阶已经写过的东西。
 *
 * <pre>
 * 高阶 = 原版效果 + 补丁[0]
 * 超级 = 原版效果 + 补丁[0] + 补丁[1]
 * 究极 = 原版效果 + 补丁[0] + 补丁[1] + 补丁[2]
 * </pre>
 *
 * <h2>加成数值</h2>
 *
 * <p>数值是为「每一阶大约在原版同级强度上再抬 30%~80%」写的初值，
 * 全部是字面量、就地可调（AGENT.md §7 把具体数值列为作者人工微调的开放项）。
 * 参数含义统一为 {@code (1 级时的量, 每级递增的量)}——加成同样随存储等级成长。
 *
 * <h2>收录范围：玩家常用核心（31 条）</h2>
 *
 * <p>刻意<b>不做</b>长尾附魔。要补一条，在下面表单里加一行即可——三阶补丁 + 中英名，
 * 其余（物品标签、槽位、等级上限、原版效果）全部自动从根源附魔取。
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
 *   <tr><td>{@code bane_of_arthropods} 节肢杀手</td><td>与 {@code smite} 功能重叠，实用度低</td></tr>
 *   <tr><td>{@code density} / {@code breach} / {@code wind_burst}</td><td>重锤专属，后期内容</td></tr>
 *   <tr><td>{@code soul_speed} 灵魂疾行</td><td>只在灵魂沙/灵魂土上生效</td></tr>
 *   <tr><td>{@code frost_walker} 冰霜行者</td><td>娱乐向，不参与战斗/生产循环</td></tr>
 *   <tr><td>{@code aqua_affinity} 水下速掘</td><td>上限 1 级，场景窄</td></tr>
 * </table>
 *
 * <p>{@code impaling} 穿刺曾在此表（理由：三叉戟对水生生物、场景窄），
 * 2026-10 按作者要求纳入——<b>三档补丁全为 {@code none()}</b>，
 * 只体现它自己的原版效果，不额外加属性。若以后再想给它加成，在表里补补丁即可。
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
                          List<UEStageEffects.Patch> ladder) {

        public Lineage {
            Objects.requireNonNull(root, "root");
            ladder = List.copyOf(ladder);
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
         * @throws IllegalArgumentException 传入 {@link LineageTier#NATIVE}（原生阶没有阶段条目）
         */
        public List<UEStageEffects.Patch> patchesFor(LineageTier tier) {
            int index = STAGES.indexOf(tier);
            if (index < 0) {
                throw new IllegalArgumentException("原生阶没有阶段条目，不存在阶位加成：" + tier);
            }
            return List.copyOf(this.ladder.subList(0, index + 1));
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // 谱系表（玩家常用核心）
    // ────────────────────────────────────────────────────────────────────

    /**
     * 谱系表。每条一行，三个补丁分别是「高阶 / 超级 / 究极比上一阶多出来的东西」。
     *
     * <p>顺序按「近战武器 → 护甲 → 通用工具 → 弓弩 → 三叉戟 → 钓鱼」分组，便于查阅。
     */
    private static final List<Lineage> ALL = List.of(
            // ── 近战武器 ──────────────────────────────────────────────────
            lineage("sharpness", "锋利", "Sharpness",
                    none(),
                    knockback(0.25f, 0.15f),
                    armorEffectiveness(-0.05f, -0.02f)),
            lineage("smite", "亡灵杀手", "Smite",
                    none(),
                    mobExperience(0.15f, 0.05f),
                    knockback(0.25f, 0.15f)),
            lineage("knockback", "击退", "Knockback",
                    none(),
                    damage(0.5f, 0.25f),
                    // 究极阶给的新效果：命中后让**目标**中缓慢——持续 5 秒起、每级 +5 秒；
                    // 强度 I 级起、每级 +1 级。走 post_attack → victim，与原版节肢杀手同一条管线。
                    postAttackMobEffect(MobEffects.MOVEMENT_SLOWDOWN, 5.0f, 5.0f, 0.0f, 1.0f)),
            lineage("fire_aspect", "火焰附加", "Fire Aspect",
                    postAttackIgnite(40.0f, 20.0f),
                    damage(0.5f, 0.25f),
                    postAttackIgnite(40.0f, 20.0f)),
            lineage("looting", "抢夺", "Looting",
                    none(),
                    mobExperience(0.15f, 0.05f),
                    none()),
            lineage("sweeping_edge", "横扫之刃", "Sweeping Edge",
                    damage(0.5f, 0.25f),
                    knockback(0.25f, 0.15f),
                    armorEffectiveness(-0.03f, -0.015f)),

            // ── 护甲 ─────────────────────────────────────────────────────
            lineage("protection", "保护", "Protection",
                    none(),
                    none(),
                    none()),
            lineage("fire_protection", "火焰保护", "Fire Protection",
                    none(),
                    none(),
                    none()),
            lineage("blast_protection", "爆炸保护", "Blast Protection",
                    none(),
                    none(),
                    none()),
            lineage("projectile_protection", "弹射物保护", "Projectile Protection",
                    none(),
                    none(),
                    none()),
            lineage("feather_falling", "摔落缓冲", "Feather Falling",
                    none(),
                    none(),
                    none()),
            lineage("thorns", "荆棘", "Thorns",
                    damageProtection(0.3f, 0.15f),
                    knockback(0.25f, 0.15f),
                    damageProtection(0.6f, 0.3f)),
            lineage("respiration", "水下呼吸", "Respiration",
                    none(),
                    damageProtection(0.3f, 0.15f),
                    none()),
            lineage("depth_strider", "深海探索者", "Depth Strider",
                    none(),
                    damageProtection(0.3f, 0.15f),
                    none()),
            lineage("swift_sneak", "迅捷潜行", "Swift Sneak",
                    none(),
                    damageProtection(0.3f, 0.15f),
                    none()),

            // ── 通用耐久 / 工具 ───────────────────────────────────────────
            lineage("unbreaking", "耐久", "Unbreaking",
                    durabilitySave(0.10f, 0.05f),
                    durabilitySave(0.10f, 0.05f),
                    durabilitySave(0.15f, 0.05f)),
            lineage("mending", "经验修补", "Mending",
                    repairWithXp(0.5f),
                    repairWithXp(0.5f),
                    repairWithXp(1.0f)),
            lineage("efficiency", "效率", "Efficiency",
                    attribute("efficiency", Attributes.MINING_EFFICIENCY, 1.0f, 1.0f, ADD_VALUE),
                    blockExperience(0.05f, 0.05f),
                    attribute("efficiency/ii", Attributes.MINING_EFFICIENCY, 1.0f, 1.0f, ADD_VALUE)),

            // ── 弓 / 弩 ───────────────────────────────────────────────────
            lineage("power", "力量", "Power",
                    none(),
                    knockback(0.25f, 0.15f),
                    none()),
            lineage("punch", "冲击", "Punch",
                    none(),
                    damage(0.5f, 0.5f),
                    none()),
            lineage("flame", "火矢", "Flame",
                    projectileSpawnedIgnite(60.0f),
                    damage(0.5f, 0.5f),
                    projectileSpawnedIgnite(60.0f)),
            lineage("infinity", "无限", "Infinity",
                    damage(0.5f, 0.5f),
                    knockback(0.25f, 0.15f),
                    damage(0.5f, 0.5f)),
            lineage("multishot", "多重射击", "Multishot",
                    none(),
                    damage(0.5f, 0.5f),
                    projectilePiercing(1.0f, 1.0f)),
            lineage("piercing", "穿透", "Piercing",
                    none(),
                    damage(0.5f, 0.5f),
                    none()),
            lineage("quick_charge", "快速装填", "Quick Charge",
                    crossbowChargeTime(0.05f),
                    crossbowChargeTime(0.10f),
                    crossbowChargeTime(0.15f)),

            // ── 三叉戟 ───────────────────────────────────────────────────
            //
            // 穿刺（impaling）：三档补丁**全是 none()**——它只走自己的原版效果
            // （对水生生物的额外伤害，那个条件写在原版 effects 里，照抄即得），
            // 阶级成长完全由「阶级曲线对自有属性的缩放」体现，
            // 不额外挂伤害/击退之类加成（作者 2026-10 的决定；其余加成以后再加）。
            lineage("impaling", "穿刺", "Impaling",
                    none(),
                    none(),
                    none()),
            lineage("loyalty", "忠诚", "Loyalty",
                    none(),
                    damage(0.5f, 0.5f),
                    none()),
            lineage("riptide", "激流", "Riptide",
                    tridentSpinAttackStrength(2.0f, 1.0f),
                    damage(0.5f, 0.5f),
                    tridentSpinAttackStrength(2.0f, 1.0f)),
            lineage("channeling", "引雷", "Channeling",
                    damage(0.5f, 0.5f),
                    mobExperience(0.2f, 0.1f),
                    damage(0.5f, 0.5f)),

            // ── 钓鱼 ─────────────────────────────────────────────────────
            lineage("lure", "饵钓", "Lure",
                    none(),
                    fishingLuck(0.5f, 0.5f),
                    none()),
            lineage("luck_of_the_sea", "海之眷顾", "Luck of the Sea",
                    none(),
                    fishingTimeReduction(1.0f, 1.0f),
                    none())
    );

    static {
        // 阶梯写漏一个补丁，在类加载时就响亮失败——而不是生成出少一阶的静默产物。
        for (Lineage lineage : ALL) {
            if (lineage.ladder().size() != LADDER_LENGTH) {
                throw new IllegalStateException("谱系 " + lineage.root() + " 的阶梯长度应为 "
                        + LADDER_LENGTH + "（高阶/超级/究极），实际 " + lineage.ladder().size());
            }
        }
    }

    public static List<Lineage> all() {
        return ALL;
    }

    /** 三个增量补丁：高阶 / 超级 / 究极各比上一阶多出来的那一项。 */
    private static Lineage lineage(String path, String cnName, String enName,
                                   UEStageEffects.Patch advanced,
                                   UEStageEffects.Patch superb,
                                   UEStageEffects.Patch ultra) {
        return new Lineage(ResourceLocation.withDefaultNamespace(path), cnName, enName,
                List.of(advanced, superb, ultra));
    }
}
