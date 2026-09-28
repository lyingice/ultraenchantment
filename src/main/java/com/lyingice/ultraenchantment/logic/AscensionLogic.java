package com.lyingice.ultraenchantment.logic;

import com.lyingice.ultraenchantment.content.AscensionTier;
import com.lyingice.ultraenchantment.content.BookSpecs;
import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.content.StageDefinition;
import com.lyingice.ultraenchantment.registry.UEComponents;
import com.lyingice.ultraenchantment.registry.UERegistries;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * 铁砧进阶的判定逻辑（纯函数）。
 *
 * <p>本类**不接** {@code Level} / {@code Player}，只处理数据，以便客户端与服务端两侧
 * 复用同一套判定——铁砧的 {@code createResult} 在两侧都会执行（见 AGENT.md P0-4）。
 */
public final class AscensionLogic {
    private AscensionLogic() {}

    /**
     * 一次可执行的进阶。
     *
     * @param root    被进阶的原版附魔
     * @param stageId 目标阶段条目 id
     * @param stage   目标阶段定义
     * @param level   保留的等级
     * @param cost    铁砧等级成本
     */
    public record Ascension(Holder<Enchantment> root, ResourceLocation stageId,
                            StageDefinition stage, int level, int cost) {}

    /**
     * 判定一次「进化型」进阶（通用 / 定向共用）。
     *
     * <p>校验链：
     * <ol>
     *   <li>物品上有附魔</li>
     *   <li>定向书：附魔必须等于书的 {@code applicable}</li>
     *   <li>该谱系当前阶级必须等于书的 {@code fromTier}</li>
     *   <li>目标阶段存在（原生阶扫表找 {@code root + toTier}；已有阶段走 {@code next}）</li>
     *   <li>等级 ≥ {@code min(目标阶段 requiredLevel, 源阶段上限)}——反死锁</li>
     * </ol>
     *
     * @param bypassGate 创造模式旁路：{@code true} 时跳过第 5 步的等级门槛。
     *                   由调用方（{@code AnvilEvents}）判定玩家游戏模式后传入——
     *                   本类是纯函数，不碰 {@code Player}，以保证客户端/服务端两侧行为一致。
     */
    public static Optional<Ascension> resolveAscension(
            HolderLookup.RegistryLookup<StageDefinition> stages,
            HolderLookup.RegistryLookup<Enchantment> enchantments,
            ItemStack left, BookSpecs.Ascension spec, boolean bypassGate) {

        ItemEnchantments present = EnchantmentHelper.getEnchantmentsForCrafting(left);
        if (present.isEmpty()) {
            return Optional.empty();
        }

        for (var entry : present.entrySet()) {
            Holder<Enchantment> ench = entry.getKey();
            if (entry.getIntValue() <= 0) {
                continue;
            }

            ResourceLocation rootId = ench.unwrapKey().map(ResourceKey::location).orElse(null);
            if (rootId == null) {
                continue;
            }

            // 定向书：只认铭刻的那个附魔
            if (spec.isTargeted() && !spec.applicable().orElseThrow().equals(rootId)) {
                continue;
            }

            // 进阶书描述「谱系链上的一条边」：from_tier → to_tier。
            // 谱系当前状态必须**正好等于** fromTier。
            // fromTier 可以是 NATIVE，表示这本书用于尚未进阶的原始附魔（基础→高阶品质）。
            LineageTier currentTier = currentTierOf(stages, left, rootId);
            if (currentTier != spec.fromTier()) {
                continue;
            }

            Optional<ResourceLocation> targetId = findTargetStageId(stages, left, rootId, spec);
            if (targetId.isEmpty()) {
                continue;
            }

            StageDefinition stage = stages.get(ResourceKey.create(UERegistries.STAGE, targetId.get()))
                    .map(Holder.Reference::value).orElse(null);
            if (stage == null) {
                continue;
            }

            // 门槛：目标附魔等级 ≥ min(目标阶段定义, 源阶段上限)。
            //
            // ⚠️ 比的是「进阶曲线等级」（tierLevel），不是存储等级。
            // 升阶后 tierLevel 归 1，玩家必须先用升级书把它提上来才能再升阶——
            // 这正是规格「前阶等级达标才能进阶」的落地。
            // 存储等级升阶后不变（锋利 5 仍是 5），拿它比会让门槛永远满足、形同虚设。
            if (!bypassGate) {
                int tierLevel = UEComponents.ascensionOf(left).tierLevelOf(rootId);
                // 未进阶的谱系没有 tierLevel 记录 → 走原生阶路线，用存储等级。
                int gauge = currentTier == LineageTier.NATIVE ? entry.getIntValue() : tierLevel;
                int sourceMax = sourceMaxLevel(stages, enchantments, left, rootId, currentTier);
                if (gauge < Math.min(stage.requiredLevel(), sourceMax)) {
                    continue;
                }
            }

            // 进阶后曲线等级重置为 1，但存储等级原样保留。
            return Optional.of(new Ascension(ench, targetId.get(), stage, entry.getIntValue(),
                    Math.max(1, stage.definition().anvilCost())));
        }

        return Optional.empty();
    }

    /** 取该谱系当前所处阶级；物品上无记录即原生阶。 */
    public static LineageTier currentTierOf(HolderLookup.RegistryLookup<StageDefinition> stages,
                                            ItemStack stack, ResourceLocation rootId) {
        ResourceLocation stageId = UEComponents.ascensionOf(stack).stages().get(rootId);
        if (stageId == null) {
            return LineageTier.NATIVE;
        }
        return stages.get(ResourceKey.create(UERegistries.STAGE, stageId))
                .map(h -> h.value().tier())
                .orElse(LineageTier.NATIVE);
    }

    /**
     * 定位目标阶段条目——<b>只认「父子」这一条边</b>。
     *
     * <p>进化型书描述的是谱系链上的一条边 {@code from_tier → to_tier}
     * （定向书只是把这条边钉在了某一条谱系上）。所以目标阶段必须与当前位置父子相连：
     *
     * <ul>
     *   <li><b>已有阶段记录</b>：只能取该阶段的 {@code next()}——链条自己声明的下一阶，
     *       并且它的阶级必须正好是书本写的 {@code to_tier}。
     *       数据包若把 {@code next} 接到了跳阶的位置，这本书<b>不生效</b>，
     *       而不是「跟着跳到三阶」。</li>
     *   <li><b>原生阶</b>（物品上没有记录）：「上一阶」是谱系链的<b>表头</b>——
     *       即<b>没有任何阶段的 {@code next} 指向它</b>的那一条。
     *       这样才排除了「定向书把原生阶直接推到超级」这种跳阶
     *       （AGENT.md §2 被否方案 #8：定向书恒定 +1 阶）。</li>
     * </ul>
     *
     * <p>表头用「无人指向」判定，而不是「阶级最小」——后者在多条谱系交错、
     * 或数据包故意从超级起步时都会判错；前者只依赖 {@code next} 这张图本身。
     */
    private static Optional<ResourceLocation> findTargetStageId(
            HolderLookup.RegistryLookup<StageDefinition> stages,
            ItemStack left, ResourceLocation rootId, BookSpecs.Ascension spec) {

        ResourceLocation currentId = UEComponents.ascensionOf(left).stages().get(rootId);

        if (currentId != null) {
            StageDefinition current = stages.get(ResourceKey.create(UERegistries.STAGE, currentId))
                    .map(Holder.Reference::value).orElse(null);
            if (current == null) {
                return Optional.empty();
            }
            // 已是谱系终点（next 缺失）→ 不可再进阶；
            // 阶级对不上（链条跳阶）→ 也不生效。
            return current.next().filter(nextId -> tierOf(stages, nextId) == spec.toTier());
        }

        Set<ResourceLocation> referenced = stages.listElements()
                .flatMap(h -> h.value().next().stream())
                .collect(Collectors.toSet());

        return stages.listElements()
                .filter(h -> h.value().root().equals(rootId))
                .filter(h -> h.value().tier() == spec.toTier())
                .map(Holder.Reference::key)
                .map(ResourceKey::location)
                .filter(id -> !referenced.contains(id))
                .findFirst();
    }

    /** 某个阶段条目的阶级；查不到返回 {@code null}（调用方的 {@code ==} 比较自然不成立）。 */
    private static LineageTier tierOf(HolderLookup.RegistryLookup<StageDefinition> stages,
                                      ResourceLocation stageId) {
        return stages.get(ResourceKey.create(UERegistries.STAGE, stageId))
                .map(h -> h.value().tier())
                .orElse(null);
    }

    /**
     * 源阶段的等级上限。
     *
     * <p>原生阶取**原版附魔自己**的 {@code max_level}（锋利 5、耐久 3…各不相同）；
     * 其余取阶段定义的 {@code max_level}。
     *
     * <p>取不到附魔时返回 1（最保守的门槛），不返回 0——避免把门槛算成 0 后
     * 让不合格的进阶通过。
     */
    private static int sourceMaxLevel(HolderLookup.RegistryLookup<StageDefinition> stages,
                                      HolderLookup.RegistryLookup<Enchantment> enchantments,
                                      ItemStack left, ResourceLocation rootId, LineageTier fromTier) {
        if (fromTier == LineageTier.NATIVE) {
            return enchantments.get(ResourceKey.create(Registries.ENCHANTMENT, rootId))
                    .map(h -> h.value().getMaxLevel())
                    .orElse(1);
        }

        ResourceLocation currentId = UEComponents.ascensionOf(left).stages().get(rootId);
        if (currentId == null) {
            return 1;
        }
        return stages.get(ResourceKey.create(UERegistries.STAGE, currentId))
                .map(h -> h.value().definition().maxLevel())
                .orElse(1);
    }
}
