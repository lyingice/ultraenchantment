package com.lyingice.ultraenchantment.logic;

import com.lyingice.ultraenchantment.api.event.UltraEnchantLockChangeEvent;
import com.lyingice.ultraenchantment.content.AscensionData;
import com.lyingice.ultraenchantment.content.AscensionTier;
import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.registry.UEComponents;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * <b>保护与祛咒</b>的判定（纯函数）。
 *
 * <h2>保护模型</h2>
 *
 * <p>附魔一旦被升阶，就进入**受保护状态**，保护级别 = 它当前的阶级。
 * 受保护的附魔无法被任何常规手段移除，只有**对应阶级**的祛咒石能解除那层保护。
 *
 * <p>保护判定不需要额外数据——它就是 {@code ascension} 组件里记录的那一阶。
 *
 * <h2>祛咒石的匹配规则</h2>
 *
 * <table>
 *   <tr><th>物品上的附魔</th><th>第二槽</th><th>结果</th></tr>
 *   <tr><td>原生阶（未升阶）</td><td>空</td><td>原版行为，可移除</td></tr>
 *   <tr><td>超级阶</td><td>空</td><td><b>受保护，不被移除</b></td></tr>
 *   <tr><td>超级阶</td><td>超级祛咒石</td><td>移除该附魔 + 清除阶段记录</td></tr>
 *   <tr><td>超级阶</td><td>高阶 / 究极祛咒石</td><td><b>无效</b></td></tr>
 *   <tr><td>纯原生阶</td><td>任意祛咒石</td><td><b>无效</b></td></tr>
 * </table>
 */
public final class ProtectionLogic {
    private ProtectionLogic() {}

    /**
     * 该物品上是否存在**受保护**的附魔（即任何一条已进阶的谱系）。
     *
     * <p>受保护 = 砂轮不得剥除它，合成修复不得抹掉它。
     */
    public static boolean hasProtected(ItemStack stack) {
        return !UEComponents.ascensionOf(stack).stages().isEmpty();
    }

    /** 判断某个附魔是否受保护。 */
    public static boolean isProtected(ItemStack stack, ResourceLocation root) {
        return UEComponents.ascensionOf(stack).stages().containsKey(root);
    }

    /**
     * 祛咒判定：该祛咒石能否移除该物品上的受保护附魔？
     *
     * @param stoneTier 祛咒石携带的档位
     * @return 可被移除的「谱系根源 → 阶段条目 id」
     */
    public static Map<ResourceLocation, ResourceLocation> removableBy(
            ItemStack stack, AscensionTier stoneTier) {

        Map<ResourceLocation, ResourceLocation> recorded = UEComponents.ascensionOf(stack).stages();
        if (recorded.isEmpty()) {
            return Map.of();
        }

        Map<ResourceLocation, ResourceLocation> result = new LinkedHashMap<>();
        recorded.forEach((root, stageId) -> {
            if (tierOfStageId(stageId) == stoneTier) {
                result.put(root, stageId);
            }
        });
        return result;
    }

    /**
     * 从阶段条目 id 反推档位。
     *
     * <p>条目 id 形如 {@code ultraenchantment:super/sharpness}——路径的第一段就是档位名。
     * 这样不必查注册表即可判定，让砂轮的结果在**客户端**也能廉价算出
     * （砂轮与铁砧的结果计算在两侧都会执行，见 AGENT.md P0-4）。
     *
     * <p>路径约定由 datagen 保证（{@code advanced/ super/ ultra/} 三个目录）。
     */
    public static AscensionTier tierOfStageId(ResourceLocation stageId) {
        String path = stageId.getPath();
        int slash = path.indexOf('/');
        String segment = slash < 0 ? path : path.substring(0, slash);
        AscensionTier tier = AscensionTier.byId(segment);
        // 兜底不抛异常：异常数据不会通过注册表加载，这里只为让客户端不至于崩溃。
        return tier == null ? AscensionTier.ADVANCED : tier;
    }

    /**
     * 执行祛咒：移除指定谱系，返回新的物品栈。
     *
     * <p>同时做两件事：从附魔表移除该附魔、从 ascension 记录里删掉该谱系
     * （后者自动解除「一脉相承」的已获取状态）。
     */
    public static ItemStack disenchant(ItemStack stack,
                                       Map<ResourceLocation, ResourceLocation> toRemove,
                                       HolderLookup.RegistryLookup<Enchantment> enchants) {
        if (toRemove.isEmpty()) {
            return stack;
        }

        ItemStack out = stack.copy();

        // ① 从附魔表移除
        ItemEnchantments.Mutable table = new ItemEnchantments.Mutable(
                EnchantmentHelper.getEnchantmentsForCrafting(out));
        toRemove.keySet().forEach(root ->
                enchants.get(ResourceKey.create(Registries.ENCHANTMENT, root))
                        .ifPresent(h -> table.set(h, 0)));
        EnchantmentHelper.setEnchantments(out, table.toImmutable());

        // ② 清除阶段记录（解除保护 + 解除谱系独占状态）
        AscensionData data = UEComponents.ascensionOf(out);
        for (ResourceLocation root : toRemove.keySet()) {
            data = data.without(root);
            UEEvents.fireLockChange(out, root, null, false, UltraEnchantLockChangeEvent.Cause.CURSE_STONE);
        }
        UEComponents.setAscension(out, data);

        return out;
    }

    /** 便捷：判断谱系阶级是否为受保护状态。 */
    public static boolean isProtectedTier(LineageTier tier) {
        return tier.isAscended();
    }

    // ── 经验计算 ────────────────────────────────────────────────────────
    //
    // ⚠️ 安全关键：砂轮的经验必须由**我们**算，不能交给原版。
    //
    // 原版 GrindstoneMenu.getExperienceFromItem 按**输入槽里的附魔**发经验：
    //
    //     for (entry : EnchantmentHelper.getEnchantmentsForCrafting(input)) {
    //         if (!holder.is(EnchantmentTags.CURSE)) total += holder.value().getMinCost(level);
    //     }
    //
    // 它完全不看输出结果。而本模组的保护性祛魔会**把受保护附魔原样留在输出里**——
    // 于是玩家能反复「放进去→取出拿经验→放回去」，受保护附魔永不消失，经验无限。
    //
    // 修法：在 OnPlaceItem 里 setXp(...)，按**本次真正被移除的附魔**重算。
    // 事件 javadoc 明确：xp >= 0 且未取消且 output 非空时，原版不再自行计算。

    /**
     * 计算「移除这些附魔」应发的经验总量。
     *
     * <p>沿用原版的求和规则（只累计非诅咒附魔的 {@code getMinCost(level)}），
     * 但只对被移除的那些求和——这是与原版唯一的区别。
     *
     * @param removedRoots 被移除的谱系根源（原版附魔 id）
     * @param registry     附魔注册表；不可用时返回 0（宁可不发，不可多发）
     */
    public static int removedEnchantmentCost(
            Iterable<ResourceLocation> removedRoots,
            HolderLookup.RegistryLookup<Enchantment> registry) {

        if (registry == null) {
            return 0;
        }

        int total = 0;
        for (ResourceLocation root : removedRoots) {
            Holder.Reference<Enchantment> holder = registry
                    .get(ResourceKey.create(Registries.ENCHANTMENT, root)).orElse(null);
            if (holder == null) {
                continue;
            }
            // 诅咒附魔本就不能被砂轮移除，也不会出现在 removedRoots 里；
            // 这里再判一次是为防御未来调用方传入不合规的集合。
            if (holder.is(net.minecraft.tags.EnchantmentTags.CURSE)) {
                continue;
            }
            total += holder.value().getMinCost(1);
        }
        return total;
    }

    /**
     * 由「移除附魔的总花费」换算成实际发放的经验。
     *
     * <h2>为什么不用原版的随机公式</h2>
     *
     * <p>原版是 {@code half + random.nextInt(half)}。但砂轮的
     * {@code createResult} 在<b>客户端与服务端都会执行</b>（AGENT.md P0-4），
     * 若在这里用随机数，两侧抽到不同值 → 客户端预测的经验与实际发放不符。
     *
     * <p>因此改用<b>确定性</b>公式：直接取 {@code ceil(total / 2)}，
     * 即原版随机区间的<b>下界</b>。宁可少发一点，也不要让两侧不一致。
     *
     * @param total 被移除附魔的花费总和
     */
    public static int experienceFromCost(int total) {
        return total <= 0 ? 0 : (int) Math.ceil(total / 2.0);
    }
}
