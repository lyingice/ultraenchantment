package com.lyingice.ultraenchantment.logic;

import com.lyingice.ultraenchantment.content.AscensionTier;
import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.content.StageDefinition;
import com.lyingice.ultraenchantment.registry.UEComponents;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * <b>附魔台「进阶」的应用层</b> —— 两条路径（原版附魔台 / 神化的附魔台）共用同一份。
 *
 * <h2>为什么单独抽出来</h2>
 *
 * <p>概率模型在 {@link AscensionChance}，而「把进阶真的写到物品上」在这里。
 * 原版与神化各有一条 mixin，但它们<b>只负责把参数凑齐</b>（行号 / 附魔能力 / 阿卡那 / 量子稳定），
 * 判定与落盘全走这里 —— 否则两边迟早漂移（本项目已经栽过一次「判定与显示不一致」）。
 *
 * <h2>「本次附魔出的附魔」怎么认</h2>
 *
 * <p>附魔前后各拍一张快照，取<b>等级被抬高</b>的那些。
 * 不能在结束时直接读物品：物品上可能本来就有附魔，那会把旧的也算成「本次出的」。
 */
public final class TableAscension {
    private TableAscension() {}

    /**
     * 一次附魔台操作的参数。
     *
     * @param row     玩家点的第几行（0/1/2）
     * @param power   附魔能力（无神化 = 书架点数；有神化 = 位阶 Eterna）
     * @param arcana  阿卡那（无神化恒 0）
     * @param stable  量子稳定（无神化恒 false）
     * @param apothic 这一笔是不是神化的附魔台
     */
    public record Params(int row, double power, double arcana, double quanta,
                         boolean stable, boolean apothic) {

        /** 无神化：没有阿卡那/量子化/量子稳定可言。 */
        public static Params vanilla(int row, double power) {
            return new Params(row, power, 0.0D, 0.0D, false, false);
        }

        /** 有神化：四个数值都来自 {@code EnchantmentTableStats}。 */
        public static Params apothic(int row, double eterna, double arcana, double quanta, boolean stable) {
            return new Params(row, eterna, arcana, quanta, stable, true);
        }
    }

    /** 附魔写入<b>之前</b>拍一张快照：附魔 → 等级。 */
    public static Map<Holder<Enchantment>, Integer> snapshot(ItemStack stack) {
        Map<Holder<Enchantment>, Integer> out = new LinkedHashMap<>();
        if (stack.isEmpty()) {
            return out;
        }
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            out.put(holder, enchantments.getLevel(holder));
        }
        return out;
    }

    /**
     * 附魔写入<b>之后</b>调用：掷一次概率，命中就把随机的若干条推进一阶。
     *
     * @param before    {@link #snapshot} 的结果（附魔前）
     * @param random    随机源 —— <b>可注入</b>，探针要能钉死它
     * @return 实际进阶了几条
     *
     * <p><b>等级与条数两套规则</b>（都来自 {@link AscensionChance} / {@code UEConfig}）：
     * 无神化 = 条数 {@code vanillaCountMin..Max}（默认 1..2）、目标阶级按权重 70/25/5、
     * 等级 = 当前等级 × 该阶级系数（60/40/20%，阶级越高越不吃附魔能力）；
     * 神化 = 按位阶分档定条数、按量子化定等级。
     */
    public static int apply(Level level, ItemStack stack, @Nullable Player player, Params params,
                            Map<Holder<Enchantment>, Integer> before, RandomSource random) {
        if (level.isClientSide || stack.isEmpty()) {
            return 0;
        }
        HolderLookup.RegistryLookup<StageDefinition> lookup = StageLookup.lookup();
        if (lookup == null) {
            return 0;
        }

        // ① 本次附魔新写上去（或等级被抬高）的那些
        List<Map.Entry<Holder<Enchantment>, Integer>> fresh = new ArrayList<>();
        ItemEnchantments now = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (Holder<Enchantment> holder : now.keySet()) {
            if (now.getLevel(holder) > before.getOrDefault(holder, 0)) {
                fresh.add(Map.entry(holder, now.getLevel(holder)));
            }
        }
        if (fresh.isEmpty()) {
            return 0;
        }

        // ② 只留【可进阶】的（作者原话：仅限可进阶附魔）
        List<Map.Entry<Holder<Enchantment>, Integer>> pool = new ArrayList<>();
        for (Map.Entry<Holder<Enchantment>, Integer> entry : fresh) {
            if (nextStageOf(stack, entry.getKey()).isPresent()) {
                pool.add(entry);
            }
        }
        if (pool.isEmpty()) {
            return 0;
        }

        // ③ 掷概率。指令给的「必进阶」次数直接跳过这一掷（用在测试低概率机制时）。
        boolean guaranteed = player != null
                && com.lyingice.ultraenchantment.registry.UEAttachments.guaranteedAscensions(player) > 0;
        double chance = AscensionChance.chance(params.row(), params.power(), params.arcana(),
                params.stable(), params.apothic());
        if (!guaranteed && random.nextDouble() >= chance) {
            return 0;
        }

        // ④ 进阶几条：无神化可配 min..max（默认 1..2）；神化按位阶随机（不够的按作者要求作废）
        int want = params.apothic()
                ? AscensionChance.count(params.power(), random)
                : AscensionChance.vanillaCount(random);
        int done = 0;
        for (int i = 0; i < Math.min(want, pool.size()); i++) {
            Map.Entry<Holder<Enchantment>, Integer> picked = pool.remove(random.nextInt(pool.size()));
            ResourceLocation rootId = UERoots.rootOf(picked.getKey().value()).orElse(null);
            if (rootId == null) {
                continue;
            }
            ResourceLocation currentStage = UEComponents.ascensionOf(stack).stages().get(rootId);
            LineageTier currentTier = currentStage == null
                    ? LineageTier.NATIVE
                    : UERoots.lineageTier(UERoots.tierOfStage(currentStage)).orElse(LineageTier.NATIVE);
            // 两条路径共用同一套「掷目标阶级 → 落到实际存在的阶段」：
            // 无神化按 70/25/5；神化默认 1/0/0（等价于旧的「只进一阶」）。
            AscensionTier rolled = params.apothic()
                    ? AscensionChance.apothicTier(random)
                    : AscensionChance.vanillaTier(random);
            Optional<ResourceLocation> next = stageFor(lookup, rootId, currentTier, rolled);
            if (next.isEmpty()) {
                continue;
            }
            ResourceLocation stageId = next.get();
            Optional<LineageTier> tier = UERoots.lineageTier(UERoots.tierOfStage(stageId));
            if (tier.isEmpty() || tier.get() == LineageTier.NATIVE) {
                continue;
            }
            int maxLevel = StageLookup.maxLevelOf(lookup, tier.get(), rootId, 0);
            if (maxLevel <= 0) {
                continue;
            }
            AscensionTier targetTier = AscensionTier.values()[tier.get().ordinal() - 1];
            int target;
            if (params.apothic()) {
                // 神化：等级先由量子化给出，再乘该阶级的等级系数（默认 1.0 = 不变）
                // 上限 = min(该阶曲线上限, 位阶推出的上限)，**先夹上限再掷**（否则结果几乎全被夹到顶格）
                int cap = Math.min(maxLevel, AscensionChance.levelCapFromEterna(params.power()));
                int raw = AscensionChance.level(cap, params.quanta(), random);
                target = (int) Math.max(1, Math.round(raw * AscensionChance.apothicLevelFactor(targetTier)));
            } else {
                // 无神化：等级 = 这条附魔**当前等级** × 目标阶级系数（阶级越高越不吃附魔能力）
                int base = currentStage == null
                        ? Math.max(1, picked.getValue())
                        : Math.max(1, UEComponents.ascensionOf(stack).tierLevelOf(rootId));
                target = AscensionChance.vanillaLevel(base, targetTier);
            }
            AscensionLogic.writeAscension(stack, picked.getKey(), rootId, stageId, maxLevel,
                    Math.min(target, Math.max(1, maxLevel)));
            done++;
        }
        // 只有「确实进阶了」才扣一次：附了几次没料的白板不该白白消耗玩家的次数。
        if (guaranteed && done > 0) {
            com.lyingice.ultraenchantment.registry.UEAttachments.setGuaranteedAscensions(
                    player, com.lyingice.ultraenchantment.registry.UEAttachments.guaranteedAscensions(player) - 1);
        }
        return done;
    }

    /**
     * 掷到的目标阶级 → 落到<b>实际存在</b>的阶段（两条路径共用）。
     *
     * <p>规则：
     * <ol>
     *   <li>目标至少比当前高一阶（绝不原地不动、绝不降级）；</li>
     *   <li>从目标档往上找第一个存在条目的阶级；</li>
     *   <li>目标档完全没铺（数据包只铺了部分阶级，例如 {@code protection} 只有高阶）时，
     *       退到「比当前高、且存在」的最高档 —— <b>保住进阶，但不凭空造条目</b>；</li>
     *   <li>都不存在 ⇒ 这条不进阶。</li>
     * </ol>
     */
    private static Optional<ResourceLocation> stageFor(
            HolderLookup.RegistryLookup<StageDefinition> lookup, ResourceLocation rootId,
            LineageTier currentTier, AscensionTier rolledTier) {
        LineageTier lowest = currentTier.next().orElse(null);
        if (lowest == null) {
            return Optional.empty();                       // 已是最高档
        }
        LineageTier rolled = rolledTier.asLineageTier();
        LineageTier want = rolled.ordinal() >= lowest.ordinal() ? rolled : lowest;
        for (LineageTier tier = want; tier != null; tier = tier.next().orElse(null)) {
            Optional<ResourceLocation> stage = StageLookup.stageIdOf(lookup, rootId, tier);
            if (stage.isPresent()) {
                return stage;
            }
        }
        LineageTier best = null;
        for (LineageTier tier = lowest; tier != null; tier = tier.next().orElse(null)) {
            if (StageLookup.stageIdOf(lookup, rootId, tier).isPresent()) {
                best = tier;
            }
        }
        return best == null ? Optional.empty() : StageLookup.stageIdOf(lookup, rootId, best);
    }

    /**
     * 这条附魔在物品上「下一阶」的阶段 id。
     *
     * <p>物品上没有记录 = 基础阶 ⇒ 找该谱系的 {@code ADVANCED}；
     * 已有记录 ⇒ 走 {@link LineageTier#next()}。已是最高档则返回空（不可进阶）。
     */
    public static Optional<ResourceLocation> nextStageOf(ItemStack stack, Holder<Enchantment> root) {
        HolderLookup.RegistryLookup<StageDefinition> lookup = StageLookup.lookup();
        if (lookup == null) {
            return Optional.empty();
        }
        ResourceLocation rootId = UERoots.rootOf(root.value()).orElse(null);
        if (rootId == null) {
            return Optional.empty();
        }
        ResourceLocation current = UEComponents.ascensionOf(stack).stages().get(rootId);
        if (current == null) {
            return StageLookup.stageIdOf(lookup, rootId, LineageTier.ADVANCED);
        }
        return UERoots.lineageTier(UERoots.tierOfStage(current))
                .flatMap(LineageTier::next)
                .flatMap(tier -> StageLookup.stageIdOf(lookup, rootId, tier));
    }
}
