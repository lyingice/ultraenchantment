package com.lyingice.ultraenchantment.logic;

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
    public record Params(int row, double power, double arcana, boolean stable, boolean apothic) {}

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
     * @param keepLevel 等级策略：{@code true} = 保持原等级（无神化的「等级归一」），
     *                  {@code false} = 按量子化随机（神化）
     * @return 实际进阶了几条
     */
    public static int apply(Level level, ItemStack stack, @Nullable Player player, Params params,
                            Map<Holder<Enchantment>, Integer> before, RandomSource random,
                            boolean keepLevel) {
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

        // ③ 掷概率
        if (random.nextDouble() >= AscensionChance.chance(params.row(), params.power(),
                params.arcana(), params.stable(), params.apothic())) {
            return 0;
        }

        // ④ 进阶几条：无神化恒 1；神化按位阶随机（不够的按作者要求作废）
        int want = params.apothic() ? AscensionChance.count(params.power(), random) : 1;
        int done = 0;
        for (int i = 0; i < Math.min(want, pool.size()); i++) {
            Map.Entry<Holder<Enchantment>, Integer> picked = pool.remove(random.nextInt(pool.size()));
            Optional<ResourceLocation> next = nextStageOf(stack, picked.getKey());
            ResourceLocation rootId = UERoots.rootOf(picked.getKey().value()).orElse(null);
            if (next.isEmpty() || rootId == null) {
                continue;
            }
            Optional<LineageTier> tier = UERoots.lineageTier(UERoots.tierOfStage(next.get()));
            if (tier.isEmpty()) {
                continue;
            }
            int maxLevel = StageLookup.maxLevelOf(lookup, tier.get(), rootId, 0);
            int target = keepLevel
                    ? Math.max(1, picked.getValue())
                    : AscensionChance.level(params.power(), params.arcana(), random);
            AscensionLogic.writeAscension(stack, picked.getKey(), rootId, next.get(), maxLevel, target);
            done++;
        }
        return done;
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
