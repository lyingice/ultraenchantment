package com.lyingice.ultraenchantment.event;

import com.lyingice.ultraenchantment.content.AscensionTier;
import com.lyingice.ultraenchantment.logic.ProtectionLogic;
import com.lyingice.ultraenchantment.registry.UEComponents;
import com.lyingice.ultraenchantment.registry.UEItems;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.GrindstoneEvent;

/**
 * <b>砂轮链路</b>——受保护附魔的守护者与祛咒石的使用入口。
 *
 * <h2>为什么必须接管</h2>
 *
 * <p>原版 {@code GrindstoneMenu.removeNonCursesFrom} 会剥除**所有非诅咒附魔**，
 * 不区分保护状态：
 * <pre>
 * m.removeIf(e -&gt; !e.is(EnchantmentTags.CURSE))
 * </pre>
 * 因此只要物品带受保护附魔，就必须**无条件接管** {@code OnPlaceItem}，
 * 自行计算输出。必须覆盖三条原版分支：
 * <ol>
 *   <li>单物品祛魔</li>
 *   <li>双物品合并修复</li>
 *   <li>双物品合并同堆叠</li>
 * </ol>
 *
 * <h2>两个语义</h2>
 *
 * <p><b>第二槽为空</b>：执行「保护性祛魔」——去掉原生阶附魔，保留受保护的那些。
 * （即规格里的「原生阶可由普通砂轮去除；高阶及以上需用祛咒石」。）
 *
 * <p><b>第二槽是祛咒石</b>：只移除与该祛咒石**同阶级**的受保护附魔。
 * 没有匹配项时不消耗耐久（通过不设置输出实现——原版不会产出结果，自然不会消耗）。
 *
 * <h2>耐久消耗</h2>
 *
 * <p>消耗发生在取件时，由 {@code OnTakeItem} 回写两个输入槽完成
 * （{@code CommonHooks.onGrindstoneTake} 会把 {@code newTop/newBottom} 写回去）。
 */
public final class GrindstoneEvents {
    private GrindstoneEvents() {}

    /** 单例监听器，供 game bus 注册。 */
    public static final GrindstoneEvents INSTANCE = new GrindstoneEvents();

    @SubscribeEvent
    public void onPlaceItem(GrindstoneEvent.OnPlaceItem event) {
        ItemStack top = event.getTopItem();
        ItemStack bottom = event.getBottomItem();

        boolean topProtected = ProtectionLogic.hasProtected(top);
        boolean bottomProtected = ProtectionLogic.hasProtected(bottom);
        boolean hasStone = isCurativeStone(top) || isCurativeStone(bottom);

        // 两条都不沾：完全不介入，交给原版。
        if (!topProtected && !bottomProtected && !hasStone) {
            return;
        }

        HolderLookup.RegistryLookup<Enchantment> enchants = CommonHooks.resolveLookup(Registries.ENCHANTMENT);
        if (enchants == null) {
            return;
        }

        // 三条路径都返回「本次真正被移除的附魔」，用于重算经验。
        //
        // ⚠️ 为什么必须自己设 xp：原版按**输入槽**发经验，不看输出。而保护性祛魔
        // 会把受保护附魔原样留在输出里——若不接管经验，玩家可以反复取出领经验、
        // 放回再领，受保护附魔永不消失 → 无限刷经验（AGENT.md P0-11）。
        if (isCurativeStone(bottom) && !top.isEmpty()) {
            applyAndReport(event, applyCurative(top, bottom, enchants), enchants);
            return;
        }

        if (isCurativeStone(top) && !bottom.isEmpty()) {
            applyAndReport(event, applyCurative(bottom, top, enchants), enchants);
            return;
        }

        if (topProtected || bottomProtected) {
            applyAndReport(event, protectiveScrub(top, bottom, enchants), enchants);
        }
    }

    /**
     * 设置输出并同步设置经验。
     *
     * <p>事件 javadoc 明确：当 {@code xp >= 0}、事件未取消、且输出非空时，
     * <b>原版不再自行计算经验</b>，而是直接采用 {@code getXp()}。
     * 这正是我们拦截经验刷取的入口。
     *
     * <p>输出为空（操作无效）时不设 xp，此时原版会照常走自己的逻辑——
     * 但那条路径下输出也是空的，玩家拿不到东西，不会产生收益。
     */
    private static void applyAndReport(GrindstoneEvent.OnPlaceItem event,
                                       RemovalResult result,
                                       HolderLookup.RegistryLookup<Enchantment> enchants) {
        ItemStack out = result.stack();
        event.setOutput(out);

        if (out.isEmpty()) {
            return;
        }

        int total = ProtectionLogic.removedEnchantmentCost(result.removedRoots(), enchants);
        event.setXp(ProtectionLogic.experienceFromCost(total));
    }

    /**
     * 一次祛除操作的结果。
     *
     * @param stack        输出物品栈；空表示操作无效
     * @param removedRoots 本次被移除的附魔（用于精确计算经验）
     */
    private record RemovalResult(ItemStack stack, java.util.Set<ResourceLocation> removedRoots) {
        static RemovalResult invalid() {
            return new RemovalResult(ItemStack.EMPTY, java.util.Set.of());
        }
    }

    @SubscribeEvent
    public void onTakeItem(GrindstoneEvent.OnTakeItem event) {
        ItemStack top = event.getTopItem();
        ItemStack bottom = event.getBottomItem();

        // 只有祛咒石需要消耗耐久；其余情形不干预原版取件行为。
        boolean topIsStone = isCurativeStone(top);
        boolean bottomIsStone = isCurativeStone(bottom);
        if (!topIsStone && !bottomIsStone) {
            return;
        }

        // 判断本次是否产出了结果（结果槽由 OnPlaceItem 决定）。
        // 若没有匹配项，我们不会设置输出，原版也不会产出 → 不消耗耐久。
        if (!didProduceResult(event, top, bottom)) {
            return;
        }

        // 回写两槽：物品槽清空，祛咒石扣 1 点耐久。
        HolderLookup.RegistryLookup<Enchantment> enchants = CommonHooks.resolveLookup(Registries.ENCHANTMENT);
        if (enchants == null) {
            return;
        }

        if (topIsStone) {
            event.setNewTopItem(damageStone(top));
            event.setNewBottomItem(ItemStack.EMPTY);
        } else {
            event.setNewTopItem(ItemStack.EMPTY);
            event.setNewBottomItem(damageStone(bottom));
        }
    }

    // ── 实现 ────────────────────────────────────────────────────────────

    /** 祛咒石：扣 1 点耐久，耐久耗尽则销毁。 */
    private static ItemStack damageStone(ItemStack stone) {
        ItemStack out = stone.copy();
        int next = out.getDamageValue() + 1;
        if (next >= out.getMaxDamage()) {
            return ItemStack.EMPTY;
        }
        out.setDamageValue(next);
        return out;
    }

    /**
     * 本次取件是否应当消耗祛咒石。
     *
     * <p>判定标准：物品槽上的受保护附魔里，存在与该祛咒石同阶级的项。
     * 没有匹配项 = 操作无效 = 不消耗耐久（规格要求）。
     */
    private static boolean didProduceResult(GrindstoneEvent.OnTakeItem event, ItemStack top, ItemStack bottom) {
        boolean topIsStone = isCurativeStone(top);
        ItemStack item = topIsStone ? bottom : top;
        ItemStack stone = topIsStone ? top : bottom;

        if (item.isEmpty() || stone.isEmpty()) {
            return false;
        }

        AscensionTier stoneTier = UEComponents.curativeTierOf(stone);
        return !ProtectionLogic.removableBy(item, stoneTier).isEmpty();
    }

    /** 用祛咒石移除与其同阶级的受保护附魔。 */
    private RemovalResult applyCurative(ItemStack item, ItemStack stone,
                                        HolderLookup.RegistryLookup<Enchantment> enchants) {
        AscensionTier stoneTier = UEComponents.curativeTierOf(stone);
        Map<ResourceLocation, ResourceLocation> removable = ProtectionLogic.removableBy(item, stoneTier);

        // 无匹配项 → 操作无效（也因此不消耗耐久，且不发经验）。
        if (removable.isEmpty()) {
            return RemovalResult.invalid();
        }

        return new RemovalResult(
                ProtectionLogic.disenchant(item, removable, enchants),
                removable.keySet());
    }

    /**
     * 保护性祛魔：去掉物品上的**原生阶**附魔，保留受保护的。
     *
     * <p>这是规格里「原生阶附魔可由普通砂轮去除；高阶及以上附魔普通砂轮无效」的落地——
     * 普通砂轮仍然可用，只是不再能洗掉进阶附魔。
     *
     * <p><b>经验只按被移除的原生阶附魔计算</b>——这正是堵住刷经验漏洞的关键：
     * 受保护附魔留在输出里，它们不该产生任何经验收益。
     */
    private RemovalResult protectiveScrub(ItemStack top, ItemStack bottom,
                                          HolderLookup.RegistryLookup<Enchantment> enchants) {
        // 双物品合并：原版会合并附魔与耐久，这里直接放弃合并（返回空），
        // 让玩家明确知道「带进阶附魔的物品不能这样合并」——避免出现难以预期的结果。
        if (!top.isEmpty() && !bottom.isEmpty()) {
            return RemovalResult.invalid();
        }

        ItemStack item = top.isEmpty() ? bottom : top;
        if (item.isEmpty()) {
            return RemovalResult.invalid();
        }

        ItemStack out = item.copy();
        ItemEnchantments.Mutable table = new ItemEnchantments.Mutable(
                EnchantmentHelper.getEnchantmentsForCrafting(out));

        // 记录被移除的附魔，供经验计算。
        java.util.Set<ResourceLocation> removed = new java.util.LinkedHashSet<>();

        // 只移除「非诅咒 且 不受保护」的附魔。
        table.removeIf(holder -> {
            if (holder.is(EnchantmentTags.CURSE)) {
                return false;   // 诅咒附魔本来就不能被砂轮去除
            }
            ResourceLocation id = holder.unwrapKey().map(k -> k.location()).orElse(null);
            boolean shouldRemove = id != null && !ProtectionLogic.isProtected(out, id);
            if (shouldRemove) {
                removed.add(id);
            }
            return shouldRemove;
        });

        EnchantmentHelper.setEnchantments(out, table.toImmutable());

        // 原版行为：附魔书被清空后变成普通书。
        //
        // 单例化之后「是不是附魔书」统一走 UEItems 判定——
        // 三种科目共用一个物品 id，因此这里一个判断就够了。
        if (UEItems.isAdvancedBook(out)) {
            return new RemovalResult(ItemStack.EMPTY, removed);
        }
        return new RemovalResult(out, removed);
    }

    /** 判断是不是祛咒石。 */
    private static boolean isCurativeStone(ItemStack stack) {
        return UEItems.isCurativeStone(stack);
    }
}
