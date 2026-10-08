package com.lyingice.ultraenchantment.datagen;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.content.StageDefinition;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.TargetedConditionalEffect;
import net.minecraft.world.item.enchantment.effects.AddValue;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;
import net.minecraft.world.item.enchantment.effects.MultiplyValue;
import net.minecraft.world.item.enchantment.effects.RemoveBinomial;
import net.minecraft.world.item.enchantment.effects.SetValue;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * <b>进阶附魔效果总表</b>——把「每一阶到底有什么效果、数值是多少」导出成一份 markdown。
 *
 * <h2>为什么这是一份产物，而不是手写的文档</h2>
 *
 * <p>表里有两类内容，来路完全不同：
 * <ul>
 *   <li><b>原版基础效果</b>：从该附魔的根源原版定义整段搬来。只有运行期读得到
 *       {@code minecraft:enchantment} 注册表，手写必然与原版漂移。</li>
 *   <li><b>阶位加成</b>：{@link LineageTable} 里唯一的作者手写部分。</li>
 * </ul>
 *
 * <p>所以它和阶段 JSON 一样属于<b>生成物</b>：改代码 → 重跑 runData → 表自动更新。
 * 作者想调数值时改表再看 diff 即可；反过来「改表 → 让 agent 改代码 → 重新生成」
 * 也是一条闭环——<b>重新生成后 diff 必须与手改的完全一致</b>，
 * 这就是「抄写没抄错」的机器证明。
 *
 * <h2>为什么不需要反推</h2>
 *
 * <p>{@link UEStageEffects.Patch} 是声明式的（{@code Append} / {@code Replace}），
 * 自带 {@link UEStageEffects.Patch#describe()}。本类直接读那句原话，
 * 不去和原版逐条比对猜「哪条是加成的」——那种猜法在加成恰好与原版同式时会失效
 * （锋利的加成就是这种情况）。
 *
 * <h2>⚠️ 不要打包进 jar</h2>
 *
 * <p>落在仓库根的 {@code docs/} 下，不在 {@code src/generated/resources} 里，
 * 因此不会随 jar 发布——它是给作者看的开发资料。
 */
public final class UEEffectDoc implements DataProvider {
    /** 输出文件名（仓库 {@code docs/} 下）。 */
    public static final String FILE_NAME = "enchantment-effects.md";

    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public UEEffectDoc(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        this.output = output;
        this.registries = registries;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return this.registries.thenCompose(provider -> {
            Path target = locateDocFile();
            if (target == null) {
                // 找不到仓库根就跳过——这是开发资料，不该让它把 runData 弄失败。
                Ultraenchantment.LOGGER.warn("[UEEffectDoc] 找不到仓库根（build.gradle + settings.gradle），跳过效果总表");
                return CompletableFuture.completedFuture(null);
            }

            String markdown = render(UEStages.generate(provider));
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(target, markdown, StandardCharsets.UTF_8);
                Ultraenchantment.LOGGER.info("[UEEffectDoc] 已写出效果总表 {}", target);
            } catch (IOException ex) {
                throw new IllegalStateException("写出效果总表失败：" + target, ex);
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    @Override
    public String getName() {
        return "Ultra Enchantment effect reference";
    }

    /**
     * 从包输出目录往上找仓库根，返回 {@code <repo>/docs/enchantment-effects.md}。
     *
     * <p>用「同时含有 build.gradle 与 settings.gradle」当判据，而不是数 getParent() 的层数——
     * 层数写死在目录结构一变就错，而这个判据自解释，失效时只是安全跳过。
     */
    private Path locateDocFile() {
        Path cursor = this.output.getOutputFolder().toAbsolutePath();
        while (cursor != null) {
            if (Files.isRegularFile(cursor.resolve("build.gradle"))
                    && Files.isRegularFile(cursor.resolve("settings.gradle"))) {
                return cursor.resolve("docs").resolve(FILE_NAME);
            }
            cursor = cursor.getParent();
        }
        return null;
    }

    // ── 渲染 ────────────────────────────────────────────────────────────

    private static String render(List<UEStages.GeneratedStage> stages) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 进阶附魔效果总表\n\n");
        sb.append("> **本文件由 datagen 生成**（runData），不要手改 —— 改了下次生成会被覆盖。\n");
        sb.append("> 要调数值，请改 LineageTable 里的阶位加成，然后重跑 runData。\n\n");
        sb.append("- **基础阶（原版·参考）**：这条附魔在原版里长什么样，只作对照，**不参与该阶级的计算**。\n");
        sb.append("- **该阶实际公式**：该阶级真正生效的内容 —— 数值就是该阶级自己的公式，原版数字不参与。\n");
        sb.append("  「追加」= 在原版之外再挂一条（与原版叠加）；\n");
        sb.append("  「覆盖」= 把原版那一条整条换掉（结算时用效果算出的值替换当前值，所以数值写小是**变弱**）。\n");
        sb.append("  ✦ 标记的是该阶级**新增**的效果（原版没有这个组件，例如给锋利加击退）。\n");
        sb.append("  三种写法（覆盖 / 追加 / 增量）的边界见 docs/guide-data.md。\n");
        sb.append("  条件是「这个附魔是什么」的一部分，**照原版保留**（亡灵杀手依然只打亡灵）。\n");
        sb.append("- **满级合计**：只对「全部无条件加法 + 线性」的组件求和，上限取该阶级 max_level；\n");
        sb.append("  含条件或非加法效果的组件标 —，那类强度看摘要那句话。\n\n");
        sb.append("| 谱系 | 阶级 | 上限 | 门槛 | 基础阶（原版·参考） | 该阶实际公式（**粗体**=该阶新增的效果） | 满级合计 |\n");
        sb.append("|---|---|---|---|---|---|---|\n");

        for (UEStages.GeneratedStage generated : stages) {
            StageDefinition stage = generated.stage();
            Set<DataComponentType<?>> added = new LinkedHashSet<>();
            generated.patches().stream().filter(patch -> !patch.isNoOp())
                    .forEach(patch -> added.add(patch.component()));

            sb.append("| ").append(stage.root().getPath())
                    .append(" | ").append(stage.tier().id())
                    .append(" | ").append(stage.definition().maxLevel())
                    .append(" | ").append(stage.requiredLevel())
                    .append(" | ").append(describeMap(generated.vanillaEffects(), Set.of()))
                    .append(" | ").append(describeMap(generated.stage().effects(), added))
                    .append(" | ").append(totals(generated.stage().effects()))
                    .append(" |\n");
        }
        return sb.toString();
    }

    /** 阶位加成列：逐条列出该阶级累积的补丁，并标出追加 / 覆盖。 */
    private static String bonusColumn(List<UEStageEffects.Patch> patches) {
        if (patches.isEmpty()) {
            return "—";
        }
        List<String> lines = new ArrayList<>(patches.size());
        for (UEStageEffects.Patch patch : patches) {
            if (patch.isNoOp()) {
                continue;
            }
            lines.add((patch.appends() ? "「追加」" : "「覆盖」") + escape(patch.describe()));
        }
        return lines.isEmpty() ? "—（本阶级只改数值）" : String.join("<br>", lines);
    }

    /**
     * 把一份效果表渲染成「组件：描述」。
     *
     * <p><b>暖体</b>标记的是「该阶级新增的效果」（原版没有这个组件），
     * 其余就是这个阶级自己的数值公式——它已经把原版数字<b>整条换掉</b>了。
     */
    private static String describeMap(net.minecraft.core.component.DataComponentMap map,
                                      Set<DataComponentType<?>> added) {
        List<String> parts = new ArrayList<>();
        for (TypedDataComponent<?> component : map) {
            String text = "<b>" + cnName(component.type()) + "</b> " + describeVanilla(component.value());
            // 「该阶新增」用 ✦ 前缀标记，不再套一层粗体（套了会得到 <b><b>…</b></b> 这种畸形标记）。
            parts.add(added.contains(component.type()) ? ("✦ " + text) : text);
        }
        return parts.isEmpty() ? "—" : String.join("<br>", parts);
    }

    // ── 原版效果的描述（只喂「效果摘要」那一列，不参与生成）─────────────

    private static String describeVanilla(Object value) {
        if (value instanceof EnchantmentValueEffect scalar) {
            return "整条为 " + describeValue(scalar);
        }
        if (value instanceof net.minecraft.world.item.CrossbowItem.ChargingSounds) {
            return "蓄力音效（三段）";
        }
        if (value instanceof List<?> list) {
            // 一串同类的音效/实体引用压成「×N」，避免把摘要挤爆。
            if (!list.isEmpty() && list.stream().allMatch(element -> element instanceof Holder<?>)) {
                String first = list.get(0) instanceof Holder<?> holder
                        ? holder.unwrapKey().map(key -> key.location().getPath()).orElse("?")
                        : "?";
                return "音效 " + first + (list.size() > 1 ? " 等 " + list.size() + " 项" : "");
            }
            List<String> parts = new ArrayList<>(list.size());
            for (Object element : list) {
                parts.add(describeElement(element));
            }
            // 同一句话重复多次就压成「×N」——三段蓄力音效、三段旋冲音效都属于这种。
            if (parts.size() > 1 && parts.stream().distinct().count() == 1L) {
                return parts.get(0) + " ×" + parts.size();
            }
            return String.join("，", parts);
        }
        return "（原版定义）";
    }

    private static String describeElement(Object element) {
        if (element instanceof EnchantmentAttributeEffect attribute) {
            return describeAttribute(attribute);
        }
        if (element instanceof TargetedConditionalEffect<?> targeted) {
            String where = switch (targeted.enchanted()) {
                case ATTACKER -> "攻击方";
                case VICTIM -> "受击方";
                case DAMAGING_ENTITY -> "伤害来源";
            };
            return "作用于" + where + "：" + describeEntity(targeted.effect()) + condition(targeted.requirements());
        }
        if (element instanceof ConditionalEffect<?> conditional) {
            return describeEntity(conditional.effect()) + condition(conditional.requirements());
        }
        if (element instanceof net.minecraft.world.item.CrossbowItem.ChargingSounds) {
            return "蓄力音效";
        }
        if (element instanceof Holder<?> holder) {
            return "音效 " + holder.unwrapKey().map(key -> key.location().getPath()).orElse("?");
        }
        return "（原版定义）";
    }

    private static String describeEntity(Object effect) {
        if (effect instanceof EnchantmentValueEffect value) {
            return describeValue(value);
        }
        if (effect instanceof EnchantmentAttributeEffect attribute) {
            return describeAttribute(attribute);
        }
        if (effect instanceof net.minecraft.world.item.enchantment.effects.Ignite ignite) {
            return "点燃 " + describeLevelValue(ignite.duration()) + " tick";
        }
        if (effect instanceof net.minecraft.world.item.enchantment.effects.DamageEntity damage) {
            return "造成 " + describeLevelValue(damage.minDamage()) + "~"
                    + describeLevelValue(damage.maxDamage()) + " 点伤害";
        }
        if (effect instanceof net.minecraft.world.item.enchantment.effects.DamageItem damageItem) {
            return "消耗 " + describeLevelValue(damageItem.amount()) + " 点耐久";
        }
        if (effect instanceof net.minecraft.world.item.enchantment.effects.AllOf.EntityEffects allOf) {
            return describeAllOf(allOf.effects());
        }
        if (effect instanceof net.minecraft.world.item.enchantment.effects.AllOf.LocationBasedEffects allOf) {
            return describeAllOf(allOf.effects());
        }
        if (effect instanceof net.minecraft.world.item.enchantment.effects.ApplyMobEffect mob) {
            return "施加负面效果（时长从 " + describeLevelValue(mob.minDuration()) + " tick 起）";
        }
        if (effect instanceof net.minecraft.world.item.enchantment.effects.PlaySoundEffect sound) {
            return "播放音效 " + sound.soundEvent().unwrapKey().map(key -> key.location().getPath()).orElse("?");
        }
        if (effect instanceof net.minecraft.world.item.enchantment.effects.SummonEntityEffect summon) {
            return "召唤 "
                    + summon.entityTypes().stream().findFirst()
                            .flatMap(Holder::unwrapKey)
                            .map(key -> key.location().getPath())
                            .orElse("?");
        }
        return "（原版效果）";
    }

    private static String describeAllOf(List<?> effects) {
        List<String> parts = new ArrayList<>(effects.size());
        for (Object inner : effects) {
            parts.add(describeEntity(inner));
        }
        return String.join("，并且 ", parts);
    }

    private static String describeAttribute(EnchantmentAttributeEffect attribute) {
        return "属性 " + UEStageEffects.shortAttributeName(attribute.attribute())
                + " " + describeLevelValue(attribute.amount());
    }

    private static String describeValue(EnchantmentValueEffect effect) {
        if (effect instanceof AddValue addValue) {
            return "加 " + describeLevelValue(addValue.value());
        }
        if (effect instanceof MultiplyValue multiply) {
            return "乘 " + describeLevelValue(multiply.factor());
        }
        if (effect instanceof SetValue setValue) {
            return "设为 " + describeLevelValue(setValue.value());
        }
        if (effect instanceof RemoveBinomial binomial) {
            return "按二项分布免除消耗（概率 " + describeLevelValue(binomial.chance()) + "）";
        }
        return "（数值效果）";
    }

    private static String describeLevelValue(LevelBasedValue value) {
        if (value instanceof LevelBasedValue.Linear linear) {
            return UEStageEffects.amount(linear.base(), linear.perLevelAboveFirst());
        }
        if (value instanceof LevelBasedValue.Constant constant) {
            return UEStageEffects.num(constant.value());
        }
        if (value instanceof LevelBasedValue.Clamped clamped) {
            return "clamp[" + UEStageEffects.num(clamped.min()) + ".." + UEStageEffects.num(clamped.max())
                    + "](" + describeLevelValue(clamped.value()) + ")";
        }
        if (value instanceof LevelBasedValue.LevelsSquared squared) {
            return "等级平方 + " + UEStageEffects.num(squared.added());
        }
        if (value instanceof LevelBasedValue.Fraction fraction) {
            return "(" + describeLevelValue(fraction.numerator()) + ")/("
                    + describeLevelValue(fraction.denominator()) + ")";
        }
        return "（按等级取值）";
    }

    private static String condition(Optional<LootItemCondition> requirements) {
        if (requirements.isEmpty()) {
            return "";
        }
        String name = requirements.get().getClass().getSimpleName();
        String readable = switch (name) {
            case "AllOfCondition" -> "需同时满足多项条件";
            case "AnyOfCondition" -> "满足任一条件即可";
            case "InvertedLootItemCondition" -> "条件取反";
            case "LootItemRandomChanceCondition" -> "按概率触发";
            case "DamageSourceCondition" -> "限定伤害来源";
            case "EntityHasProperty" -> "限定实体";
            case "MatchTool" -> "限定手持工具";
            case "EnchantmentActiveCheck" -> "限定附魔处于激活状态";
            case "LocationCheck" -> "限定位置";
            case "WeatherCheck" -> "限定天气";
            case "BlockStatePropertyLootCondition" -> "限定方块状态";
            default -> "有条件";
        };
        return "（" + readable + "）";
    }

    // ── 合计 ────────────────────────────────────────────────────────────

    /**
     * 满级合计：把某个组件上「原版 + 全部加成」里所有<b>无条件加法 + 线性</b>的效果加起来。
     *
     * <p>只要有一条不是加法线性（multiply、remove_binomial、或带条件的条目），
     * 该组件就放弃合计——那种强度不是「加起来」能表达的，
     * 硬算只会给出一个看着精确、实际错误的值。
     */
    private static String totals(net.minecraft.core.component.DataComponentMap effects) {
        List<String> lines = new ArrayList<>();
        for (TypedDataComponent<?> component : effects) {
            // 上限逐谱系、逐阶级，从该阶级的 max_level 取——每个组件共用同一个等级刻度。
            List<ConditionalEffect<EnchantmentValueEffect>> entries = additiveEntries(component.value());
            if (entries == null || entries.isEmpty()) {
                continue;
            }
            float totalBase = 0.0f;
            float totalStep = 0.0f;
            for (ConditionalEffect<EnchantmentValueEffect> entry : entries) {
                LevelBasedValue.Linear linear = (LevelBasedValue.Linear) ((AddValue) entry.effect()).value();
                totalBase += linear.base();
                totalStep += linear.perLevelAboveFirst();
            }
            lines.add(cnName(component.type()) + " = " + UEStageEffects.num(round3(totalBase)) + " + "
                    + UEStageEffects.num(round3(totalStep)) + "×(n-1)");
        }
        return lines.isEmpty() ? "—（含条件/非加法，见左列）" : String.join("<br>", lines);
    }

    private static float round3(float value) {
        return Math.round(value * 1000.0f) / 1000.0f;
    }

    /**
     * 原版那一份是不是「全部无条件 + 加法 + 线性」；是则返回条目列表，否则 {@code null}。
     *
     * <p>{@code null} = 不能合计；空列表 = 该组件原版没有条目（只有加成）。
     */
    @SuppressWarnings("unchecked")
    private static List<ConditionalEffect<EnchantmentValueEffect>> additiveEntries(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            return null;
        }
        List<ConditionalEffect<EnchantmentValueEffect>> result = new ArrayList<>(list.size());
        for (Object element : list) {
            if (element instanceof ConditionalEffect<?> conditional
                    && conditional.requirements().isEmpty()
                    && conditional.effect() instanceof AddValue add
                    && add.value() instanceof LevelBasedValue.Linear) {
                result.add((ConditionalEffect<EnchantmentValueEffect>) conditional);
            } else {
                return null;
            }
        }
        return result;
    }

    /** 一条加成条目是不是「无条件 + 加法 + 线性」。 */
    private static LevelBasedValue.Linear additiveLinear(Object entry) {
        if (entry instanceof ConditionalEffect<?> conditional
                && conditional.requirements().isEmpty()
                && conditional.effect() instanceof AddValue add
                && add.value() instanceof LevelBasedValue.Linear linear) {
            return linear;
        }
        return null;
    }

    // ── 杂项 ────────────────────────────────────────────────────────────

    private static Map<DataComponentType<?>, Object> vanillaMap(UEStages.GeneratedStage generated) {
        Map<DataComponentType<?>, Object> map = new LinkedHashMap<>();
        for (TypedDataComponent<?> component : generated.vanillaEffects()) {
            map.put(component.type(), component.value());
        }
        return map;
    }

    private static Map<DataComponentType<?>, List<UEStageEffects.Patch>> bonusMap(
            UEStages.GeneratedStage generated) {
        Map<DataComponentType<?>, List<UEStageEffects.Patch>> map = new LinkedHashMap<>();
        for (UEStageEffects.Patch patch : generated.patches()) {
            map.computeIfAbsent(patch.component(), key -> new ArrayList<>()).add(patch);
        }
        return map;
    }

    /** 组件展示顺序：先原版出现过的（保持原版顺序），再补上只有加成的。 */
    private static Set<DataComponentType<?>> orderedComponents(
            Map<DataComponentType<?>, Object> vanilla,
            Map<DataComponentType<?>, List<UEStageEffects.Patch>> bonus) {
        Set<DataComponentType<?>> ordered = new LinkedHashSet<>(vanilla.keySet());
        ordered.addAll(bonus.keySet());
        return ordered;
    }

    /**
     * 组件的中文名；没登记的回退到注册路径。
     *
     * <p>⚠️ 不要用 {@code component.toString()} 取名字：{@code DataComponentType.toString()}
     * 走的是普通数据组件注册表，而附魔效果组件注册在
     * {@code BuiltInRegistries.ENCHANTMENT_EFFECT_COMPONENT_TYPE} 里，
     * 查不到就打印成 {@code [unregistered]}——整个表会变成一列看不懂的东西。
     */
    private static String cnName(DataComponentType<?> component) {
        ResourceLocation id = BuiltInRegistries.ENCHANTMENT_EFFECT_COMPONENT_TYPE.getKey(component);
        String key = id == null ? "unknown" : id.getPath();
        return switch (key) {
            case "damage" -> "伤害";
            case "damage_protection" -> "伤害减免";
            case "knockback" -> "击退";
            case "armor_effectiveness" -> "护甲有效性";
            case "smash_damage_per_fallen_block" -> "下落伤害";
            case "item_damage" -> "耐久消耗";
            case "ammo_use" -> "弹药消耗";
            case "projectile_count" -> "弹射物数量";
            case "projectile_spread" -> "散射角";
            case "projectile_piercing" -> "穿透";
            case "projectile_spawned" -> "弹射物出膛";
            case "post_attack" -> "命中后";
            case "hit_block" -> "击中方块后";
            case "attributes" -> "属性";
            case "equipment_drops" -> "装备掉落";
            case "block_experience" -> "方块经验";
            case "mob_experience" -> "生物经验";
            case "repair_with_xp" -> "经验修补";
            case "fishing_time_reduction" -> "咬钩速度";
            case "fishing_luck_bonus" -> "钓鱼幸运";
            case "trident_return_acceleration" -> "三叉戟回旋";
            case "trident_spin_attack_strength" -> "旋冲强度";
            case "trident_sound" -> "旋冲音效";
            case "crossbow_charge_time" -> "装填时间";
            case "crossbow_charging_sounds" -> "蓄力音效";
            case "damage_immunity" -> "伤害免疫";
            default -> key;
        };
    }

    /** markdown 表格单元里不能出现裸竖线与换行。 */
    private static String escape(String text) {
        return text.replace("|", "\\|").replace("\n", " ");
    }
}
