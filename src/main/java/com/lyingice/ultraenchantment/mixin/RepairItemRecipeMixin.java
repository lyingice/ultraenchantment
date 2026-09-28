package com.lyingice.ultraenchantment.mixin;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.content.AscensionData;
import com.lyingice.ultraenchantment.logic.ProtectionLogic;
import com.lyingice.ultraenchantment.registry.UEComponents;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <b>全项目唯一的 Mixin</b>——保护工作台合成修复路径上的受保护附魔。
 *
 * <h2>为什么这里必须用 Mixin</h2>
 *
 * <p>{@link RepairItemRecipe#assemble} 的结果是一个**全新物品栈**，只继承诅咒附魔：
 * <pre>
 * ItemStack itemstack2 = new ItemStack(itemstack.getItem());   // 全新栈
 * EnchantmentHelper.updateEnchantments(itemstack2, m -&gt;
 *     lookup.listElements().filter(h -&gt; h.is(EnchantmentTags.CURSE)).forEach(...));  // 只留诅咒
 * </pre>
 *
 * <p>也就是说：玩家把两把带进阶附魔的剑丢进工作台修复 → <b>附魔直接蒸发</b>。
 * 而 NeoForge 在合成结果上**没有任何事件**（只有铁砧与砂轮有）。
 *
 * <p>注入点是 {@code assemble} 的 {@code RETURN}——此时原版已算出结果，
 * 我们只需把两个输入里**受保护**的附魔（连同 {@code ascension} 记录）补回去。
 *
 * <h2>为什么是「合并保留」而不是「禁止修复」</h2>
 *
 * <p>禁止修复会让玩家无法修理带进阶附魔的装备，体验很差；
 * 合并保留则「装备能修，进阶附魔也不会丢」——符合保护语义。
 *
 * <h2>注入面</h2>
 *
 * <p>单一方法、单一注入点、{@code cancellable=false}（只改返回值不回写流程），
 * 签名在 1.21.x 内稳定。这是全项目风险最低的一处增强。
 */
@Mixin(RepairItemRecipe.class)
public abstract class RepairItemRecipeMixin {

    /**
     * 在 {@code assemble} 返回前，把受保护的附魔与阶段记录补进结果。
     *
     * @param input  合成输入（原版已从中取出两件物品）
     * @param registries 注册表 lookup（原版方法参数）
     * @param cir    返回值回调
     */
    @Inject(method = "assemble", at = @At("RETURN"))
    private void ultraenchantment$preserveProtectedEnchantments(
            CraftingInput input, net.minecraft.core.HolderLookup.Provider registries,
            CallbackInfoReturnable<ItemStack> cir) {

        ItemStack result = cir.getReturnValue();
        if (result.isEmpty()) {
            // 原版没产出（不合法的组合），不干预。
            return;
        }

        // 找出本次合成的两个输入里带受保护附魔的那些。
        Pair<ItemStack, ItemStack> pair = ultraenchantment$inputs(input);
        if (pair == null) {
            return;
        }

        ItemStack first = pair.getFirst();
        ItemStack second = pair.getSecond();

        boolean firstProtected = ProtectionLogic.hasProtected(first);
        boolean secondProtected = ProtectionLogic.hasProtected(second);
        if (!firstProtected && !secondProtected) {
            return;   // 没有受保护附魔，纯原版行为。
        }

        // ① 合并「受保护」的附魔（保留两者中较高的等级）。
        ItemEnchantments firstEnch = EnchantmentHelper.getEnchantmentsForCrafting(first);
        ItemEnchantments secondEnch = EnchantmentHelper.getEnchantmentsForCrafting(second);

        ItemEnchantments.Mutable merged = new ItemEnchantments.Mutable(
                EnchantmentHelper.getEnchantmentsForCrafting(result));

        ultraenchantment$mergeProtected(first, firstEnch, merged);
        ultraenchantment$mergeProtected(second, secondEnch, merged);
        EnchantmentHelper.setEnchantments(result, merged.toImmutable());

        // ② 合并阶段记录（受保护 = ascension 里有记录）。
        AscensionData data = AscensionData.EMPTY;
        data = ultraenchantment$mergeAscension(data, first);
        data = ultraenchantment$mergeAscension(data, second);
        if (!data.isEmpty()) {
            UEComponents.setAscension(result, data);
        }

        Ultraenchantment.LOGGER.debug("Preserved protected enchantments through crafting repair");
    }

    /** 把 source 上受保护的附魔并入 target（取等级较高者）。 */
    @Unique
    private static void ultraenchantment$mergeProtected(
            ItemStack source, ItemEnchantments sourceEnch, ItemEnchantments.Mutable target) {

        AscensionData sourceData = UEComponents.ascensionOf(source);
        for (var entry : sourceEnch.entrySet()) {
            var holder = entry.getKey();
            net.minecraft.resources.ResourceLocation id =
                    holder.unwrapKey().map(k -> k.location()).orElse(null);
            if (id == null || !sourceData.stages().containsKey(id)) {
                continue;   // 只处理受保护的（即 ascension 里有记录的）附魔
            }
            target.upgrade(holder, entry.getIntValue());
        }
    }

    /**
     * 合并 ascension 记录：两件物品同谱系时保留进阶程度更高者的整条记录。
     *
     * <p><b>必须连 {@code tierLevel} 一起搬</b>——不能只比较阶段 id 后用
     * {@code with(root, stage)} 重建，那会把进阶曲线等级重置为 1，
     * 让玩家用合成修复白丢升级书提上来的等级。
     */
    @Unique
    private static AscensionData ultraenchantment$mergeAscension(AscensionData base, ItemStack source) {
        AscensionData sourceData = UEComponents.ascensionOf(source);
        if (sourceData.isEmpty()) {
            return base;
        }

        AscensionData result = base;
        for (var entry : sourceData.stages().entrySet()) {
            net.minecraft.resources.ResourceLocation root = entry.getKey();
            net.minecraft.resources.ResourceLocation stage = entry.getValue();
            int sourceTierLevel = sourceData.tierLevelOf(root);

            net.minecraft.resources.ResourceLocation existing = result.stages().get(root);
            if (existing == null) {
                // 新谱系：整条记录搬过来（含 tierLevel）。
                result = result.with(root, stage, sourceTierLevel);
                continue;
            }

            // 已有记录：档位更高者胜；同档则取进阶曲线等级更高者。
            int cmp = ProtectionLogic.tierOfStageId(existing).ordinal()
                    - ProtectionLogic.tierOfStageId(stage).ordinal();
            if (cmp > 0) {
                continue;   // 保留 base
            }
            if (cmp < 0) {
                result = result.with(root, stage, sourceTierLevel);
                continue;
            }
            result = result.with(root, existing,
                    Math.max(result.tierLevelOf(root), sourceTierLevel));
        }
        return result;
    }

    /**
     * 复刻原版的「找出两个可合并物品」逻辑。
     *
     * <p>原版方法是 {@code private}，无法直接调用；这里只做最小复刻，
     * 且仅用于「读输入」，不影响任何输出——即便判定略有出入，
     * 最坏结果也只是少补一个附魔，不会破坏原版行为。
     */
    @Unique
    private static Pair<ItemStack, ItemStack> ultraenchantment$inputs(CraftingInput input) {
        ItemStack first = null;
        ItemStack second = null;

        for (int i = 0; i < input.size(); i++) {
            ItemStack slot = input.getItem(i);
            if (slot.isEmpty()) {
                continue;
            }
            if (first == null) {
                first = slot;
            } else if (second == null) {
                second = slot;
            } else {
                return null;   // 超过两件，原版也不会合并
            }
        }

        if (first == null || second == null) {
            return null;
        }
        // 与原版 canCombine 的关键条件一致（同物品 + 可损坏）
        if (!second.is(first.getItem()) || !first.has(DataComponents.MAX_DAMAGE)) {
            return null;
        }
        return Pair.of(first, second);
    }
}
