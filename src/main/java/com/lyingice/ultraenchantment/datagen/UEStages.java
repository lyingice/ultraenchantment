package com.lyingice.ultraenchantment.datagen;

import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.content.StageDefinition;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 阶段条目的数据生成。
 *
 * <p>产出的 JSON 落在
 * {@code data/ultraenchantment/ultraenchantment/enchantment/{advanced,super,ultra}/}，
 * 由 {@link UEStagePack} 写出。
 *
 * <h2>每个阶段条目的两个字段分别从哪来</h2>
 *
 * <table>
 *   <tr><th>字段</th><th>来源</th></tr>
 *   <tr><td>{@code definition}</td>
 *       <td><b>整段取自该谱系根源附魔自己的定义</b>——{@code weight} /
 *           {@code supported_items} / {@code primary_items} / {@code slots} /
 *           {@code max_level} / {@code min_cost} / {@code max_cost} / {@code anvil_cost} 全部照抄，
 *           只把花费与铁砧代价按阶级抬一档。
 *           所以物品适用范围与<b>等级上限</b>永远与原版一致，
 *           本模组不维护任何物品标签表，也不自己发明等级刻度。</td></tr>
 *   <tr><td>{@code effects}</td>
 *       <td>原版附魔的效果表<b>原样搬过来</b>，再叠上
 *           {@link LineageTable.Lineage#patchFor} 给出的阶级累积加成（见 {@link UEStageEffects}）。
 *           必须搬全：结算层会把物品上那条原版附魔置 0，阶段条目是它唯一的替代品。</td></tr>
 *   <tr><td>{@code required_level} / {@code next} / {@code tier} / {@code root}</td>
 *       <td>本类按阶级与谱系算出。</td></tr>
 * </table>
 *
 * <h2>⚠️ 等级上限沿用原版带来的一个可见后果</h2>
 *
 * <p>原版 {@code Enchantment.getFullname} 里有一条
 * 「{@code level == 1 且 max_level == 1} 时不显示等级数字」。
 * 于是<b>原本就只有 1 级的附魔</b>（{@code mending} / {@code flame} / {@code infinity} /
 * {@code multishot} / {@code channeling}），进阶后显示为「究极经验修补」而不是
 * 「究极经验修补 1」。这与原版对它们的表现一致（原版也只写 "Mending"），是有意接受的结果；
 * 想强制显示数字就得把上限抬到 ≥ 2，那又违背「上限随原版」的约定。
 *
 * <h2>为什么需要数据包注册表 lookup，又为什么不能走 RegistrySetBuilder</h2>
 *
 * <p>「原版附魔的定义与效果表」只能从 {@code minecraft:enchantment} 注册表里读，
 * 而那是数据包注册表，所以必须先把 {@code GatherDataEvent#getLookupProvider()} 解析出来。
 *
 * <p>但这份 lookup <b>不能</b>用 {@code RegistrySetBuilder} + {@code DatapackBuiltinEntriesProvider}
 * 写出去，有两个各自独立的死路：
 * <ol>
 *   <li>{@code ctx.lookup(Registries.ENCHANTMENT)} 拿不到东西——bootstrap 上下文的 lookup 只覆盖
 *       「内置注册表 + 本次 builder 里声明过的注册表」
 *       （{@code RegistrySetBuilder.BuildState.create} 只喂 {@code registryaccess.registries()}），
 *       对未声明的数据包注册表会回落到一个<b>没有绑定值的占位 holder</b>，取 {@code value()} 抛异常。</li>
 *   <li>改用「先解析 lookup，再注册进 builder」也不行：{@code DatapackBuiltinEntriesProvider}
 *       把 <b>patch provider</b> 交给 {@code RegistriesDatapackGenerator} 做序列化，
 *       而 patch provider 的 holder owner 是它自己新建的 {@code UniversalOwner}。
 *       我们引用的 {@code Holder}（例如荆棘的 {@code minecraft:thorns} 伤害类型）属于
 *       <b>外部 provider</b>，编码时 {@code RegistryFileCodec} 的
 *       {@code holder.canSerializeIn(owner)} 直接为 false，报
 *       {@code Element ... is not valid in current registry set}。
 *       而且 {@code Cloner.clone} 本身也是「用源 provider 编码、用目标 provider 解码」，
 *       换成 {@code PatchedRegistries.full()} 只会在克隆阶段以同样的理由炸。</li>
 * </ol>
 *
 * <p>结论：<b>必须用「引用谁、就用谁做序列化上下文」的那份 provider</b>。
 * 于是把写出这一步从 {@code RegistrySetBuilder} 换成 {@link UEStagePack}——
 * 它直接用 {@code GatherDataEvent#getLookupProvider()} 建 {@code RegistryOps}，
 * 所有 holder 的 owner 天然一致。代价是不再支持 NeoForge 的 {@code ICondition}
 * （本模组不需要条件化条目）以及放弃 builder 的重复注册检查——
 * 后者由本类自己的自检与 {@code getOrThrow} 补齐。
 */
public final class UEStages {
    private UEStages() {}

    /**
     * 每个阶级的数值定义——**不包含等级上限**，上限一律沿用根源附魔自己的 {@code max_level}。
     *
     * <p>剩下这三项都是 README「数据包定义项」里列的、留给作者微调的旋钮：
     * <ul>
     *   <li>{@code requiredLevel} —— <b>进阶到该阶级</b>所需的最低等级
     *       （实际生效值取 {@code min(它, 来源阶级上限)}，反死锁。
     *       于是「上限 3 的耐久」就是「耐久 3 才能升阶」，不会因为门槛写 5 而死锁）。</li>
     *   <li>{@code costBonus} —— 附魔台花费（min_cost / max_cost）在根源附魔基础上抬多少。</li>
     *   <li>{@code anvilBonus} —— 铁砧代价额外抬多少。</li>
     * </ul>
     */
    private record TierSpec(int requiredLevel, int costBonus, int anvilBonus,
                            float baseMul, float slopeMul) {}

    /**
     * 各阶级的数值倍率——<b>本模组效果曲线的规格</b>。
     *
     * <p>阶段条目的数值 = 原版数值的「首级值 × {@code baseMul}、每级增量 × {@code slopeMul}」。
     * 以锋利（原版 {@code 1 + 0.5×(n-1)}）为例：
     *
     * <pre>
     *   高阶 = 1×3  + 0.5×2×(n-1) = 3 + 1×(n-1)
     *   超级 = 1×6  + 0.5×3×(n-1) = 6 + 1.5×(n-1)
     *   究极 = 1×10 + 0.5×5×(n-1) = 10 + 2.5×(n-1)
     * </pre>
     *
     * <p>⚠️ 改这四个数就是改**全部谱系**的强度，属于规格级改动，必须同步 README 与效果总表。
     */
    private static final Map<LineageTier, TierSpec> TIERS = Map.of(
            LineageTier.ADVANCED, new TierSpec(5, 10, 2, 3.0f, 2.0f),
            LineageTier.SUPER, new TierSpec(4, 25, 4, 6.0f, 3.0f),
            LineageTier.ULTRA, new TierSpec(3, 45, 6, 10.0f, 5.0f));

    /**
     * 一个生成完毕的阶段条目：条目 id + 内容。
     *
     * <p>{@link StageDefinition} 自身不带 id（id 就是它落盘的路径），
     * 所以这里把它显式配出来，避免写出方再去反推。
     */
    public record GeneratedStage(ResourceLocation id,
                                 StageDefinition stage,
                                 DataComponentMap vanillaEffects,
                                 DataComponentMap scaledEffects,
                                 List<UEStageEffects.Patch> patches) {}

    /**
     * 把 {@link LineageTable} 铺开成全部阶段条目。
     *
     * @param registries 数据包注册表 lookup（必须含 {@code minecraft:enchantment}）；
     *                   也必须是 {@link UEStagePack} 用来建 {@code RegistryOps} 的<b>同一份</b>，
     *                   否则引用的 holder 会因为 owner 不一致而无法序列化。
     * @throws IllegalStateException 谱系根源不存在，或 {@code next} 指向不存在的阶段条目
     */
    public static List<GeneratedStage> generate(HolderLookup.Provider registries) {
        HolderLookup.RegistryLookup<Enchantment> vanillas =
                registries.lookupOrThrow(Registries.ENCHANTMENT);

        List<GeneratedStage> generated = new ArrayList<>();
        for (LineageTable.Lineage lineage : LineageTable.all()) {
            // getOrThrow：谱系根源写错就在这里直接抛，不会生成出指向不存在附魔的 root。
            Enchantment vanilla = vanillas
                    .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, lineage.root()))
                    .value();

            // 按**谱系自己的**阶梯走：保护只有高阶、三系保护只有高阶+超级……
            for (LineageTier tier : lineage.tiers()) {
                generated.add(build(lineage, tier, vanilla));
            }
        }

        assertChainsResolve(generated);
        return List.copyOf(generated);
    }

    private static GeneratedStage build(LineageTable.Lineage lineage, LineageTier tier,
                                        Enchantment vanilla) {
        TierSpec spec = TIERS.get(tier);
        ResourceLocation stageId = lineage.stageId(tier);

        Enchantment.EnchantmentDefinition source = vanilla.definition();
        LineageTable.StageSpec stageSpec = lineage.specFor(tier);

        // 等级上限：默认沿用原版；效果总表 v3 里少数阶段要压到 1
        // （三系保护超级 = 完全免疫、摔落缓冲高阶 = 完全免疫、激流三阶）。
        int maxLevel = stageSpec.maxLevel() != null ? stageSpec.maxLevel() : source.maxLevel();

        // 进阶门槛：默认 = 原版上限（即「满级」）。
        int requiredLevel = stageSpec.requiredLevel() != null
                ? stageSpec.requiredLevel() : source.maxLevel();

        StageDefinition.Definition definition = new StageDefinition.Definition(
                source.weight(),
                maxLevel,
                raise(source.minCost(), spec.costBonus()),
                raise(source.maxCost(), spec.costBonus()),
                source.anvilCost() + spec.anvilBonus(),
                source.supportedItems(),
                source.primaryItems(),
                source.slots());

        // 原版效果原样保留 + 阶级累积加成。
        //
        // 补丁是**声明式**的（Append / Replace），所以这里顺手把「原版那份」与
        // 「补丁列表」一起带出去——效果总表要分别展示它们，不需要靠比对去反推哪条是加成。
        // ① 覆盖：原版的数值整条换成该阶级自己的公式（首级值 ×baseMul、每级增量 ×slopeMul）。
        //    条件是附魔身份的一部分，原样保留。
        DataComponentMap vanillaEffects = vanilla.effects();
        DataComponentMap scaled = UEStageEffects.scaleCore(vanillaEffects, spec.baseMul(), spec.slopeMul());

        // ② 追加：该阶级**新增**的效果（原版没有的组件，如给锋利加击退）。
        //    这些补丁按谱系逐个登记，不做任何合并。
        List<UEStageEffects.Patch> patches = lineage.patchesFor(tier);
        DataComponentMap effects = scaled;
        for (UEStageEffects.Patch patch : patches) {
            effects = patch.apply(effects);
        }
        // 同一阶段内属性修饰符 id 重复会在运行期让 AttributeInstance 抛异常，
        // 在这里挡下来（阶梯累积，低阶用过的 id 在高阶里仍然存在）。
        UEStageEffects.assertUniqueAttributeIds(stageId, effects);

        StageDefinition stage = new StageDefinition(lineage.root(), tier, nextStage(lineage, tier),
                requiredLevel, definition, effects);
        return new GeneratedStage(stageId, stage, vanillaEffects, scaled, patches);
    }

    /** 谱系链的下一阶；{@link LineageTier#ULTRA} 是终点。 */
    private static Optional<ResourceLocation> nextStage(LineageTable.Lineage lineage, LineageTier tier) {
        // 只有本谱系真的拥有下一阶时才写 next——保护没有超级，链就到此为止。
        return tier.next().filter(lineage.tiers()::contains).map(lineage::stageId);
    }

    /** 在根源附魔的花费曲线上整体抬高 {@code bonus}（基值与每级增量同时抬）。 */
    private static Enchantment.Cost raise(Enchantment.Cost cost, int bonus) {
        return new Enchantment.Cost(cost.base() + bonus, cost.perLevelAboveFirst() + bonus);
    }

    /**
     * 自检：每条 {@code next} 都必须指向本次真正生成出来的阶段条目。
     *
     * <p>不走 {@code RegistrySetBuilder} 之后没有注册表替我们做悬空引用检查，
     * 所以这里补上——悬空的 {@code next} 会让铁砧在进阶到该阶之后再也推不动，
     * 而且只在玩到那一步时才被发现。
     */
    private static void assertChainsResolve(List<GeneratedStage> generated) {
        Set<ResourceLocation> ids = new HashSet<>();
        generated.forEach(stage -> ids.add(stage.id()));

        for (GeneratedStage entry : generated) {
            ResourceLocation next = entry.stage().next().orElse(null);
            if (next != null && !ids.contains(next)) {
                throw new IllegalStateException("阶段条目 " + entry.id()
                        + " 的 next 指向不存在的条目：" + next);
            }
        }
    }
}
