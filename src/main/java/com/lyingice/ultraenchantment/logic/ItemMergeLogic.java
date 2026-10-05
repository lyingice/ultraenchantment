package com.lyingice.ultraenchantment.logic;

import com.lyingice.ultraenchantment.api.event.UltraEnchantTierUpgradeEvent;
import com.lyingice.ultraenchantment.content.AscensionData;
import com.lyingice.ultraenchantment.content.AscensionTier;
import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.content.StageDefinition;
import com.lyingice.ultraenchantment.registry.UEComponents;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * <b>装备层同名合并</b>——原版「同名合并升 1 级」在进阶形态上的对应物（规格 §6）。
 *
 * <h2>为什么必须自己接管</h2>
 *
 * <p>原版合并在 {@code AnvilMenu.createResult} 里只处理 {@code ENCHANTMENTS}（存储等级）。
 * 而升阶要求存储等级已达上限（锋利 5），两边都是 5 → 原版算出 {@code 5+1} 又被
 * {@code getMaxLevel()} 夹回 5——<b>等级一动不动</b>；而 {@code ascension} 组件取的是左槽副本，
 * {@code tierLevel} 也不动。玩家付了全额费用，只换到合并耐久。
 *
 * <p>因此这一条<b>主动放开 AGENT.md P1-13</b>「不在此重写原版的合并数学」的约定：
 * 耐久公式与附魔合并循环逐行照抄 1.21.1 的 {@code AnvilMenu.createResult}，
 * 只在末尾追加阶段记录的合并。
 *
 * <h2>触发面（刻意收窄）</h2>
 *
 * <p>仅当「同一物品 + 可损坏 + <b>两侧都有阶段记录</b>」才接管。其余一律交回原版：
 * 只有一侧有记录时走 {@code AnvilEvents.preventLevelClamp}（修耐久 + 保留进阶数据），
 * 两边都没有时与原版完全一致。
 */
public final class ItemMergeLogic {
    private ItemMergeLogic() {}

    /** 合并结果：产物 + 铁砧成本。 */
    public record Merge(ItemStack output, int cost) {}

    /**
     * 尝试一次同名装备合并。
     *
     * @return 空 = 不适用（调用方应交回原版），非空 = 接管并设置输出
     */
    public static Optional<Merge> merge(HolderLookup.RegistryLookup<StageDefinition> stages,
                                        ItemStack left, ItemStack right,
                                        @javax.annotation.Nullable String name) {
        if (stages == null || left.isEmpty() || right.isEmpty()) {
            return Optional.empty();
        }
        if (!left.is(right.getItem()) || !left.isDamageableItem() || left.is(Items.ENCHANTED_BOOK)) {
            return Optional.empty();
        }
        AscensionData leftData = UEComponents.ascensionOf(left);
        AscensionData rightData = UEComponents.ascensionOf(right);
        if (leftData.isEmpty() || rightData.isEmpty()) {
            return Optional.empty();
        }

        ItemStack out = left.copy();
        int i = 0;

        // ① 耐久合并——原版 AnvilMenu.createResult 161-175 逐行照抄（含 12% 加成）。
        int remainingLeft = left.getMaxDamage() - left.getDamageValue();
        int remainingRight = right.getMaxDamage() - right.getDamageValue();
        int bonus = remainingRight + left.getMaxDamage() * 12 / 100;
        int repaired = remainingLeft + bonus;
        int newDamage = left.getMaxDamage() - repaired;
        if (newDamage < 0) {
            newDamage = 0;
        }
        if (newDamage < out.getDamageValue()) {
            out.setDamageValue(newDamage);
            i += 2;
        }

        // ② 附魔合并——原版 177-220，去掉「右槽是附魔书」分支（接管条件已排除它，故不减半）。
        ItemEnchantments.Mutable table =
                new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(out));
        boolean anyApplied = false;
        boolean anyRejected = false;
        for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(right).entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            int rightLevel = entry.getIntValue();
            int leftLevel = table.getLevel(holder);
            int merged = leftLevel == rightLevel ? rightLevel + 1 : Math.max(rightLevel, leftLevel);
            Enchantment enchantment = holder.value();
            boolean ok = left.supportsEnchantment(holder);
            for (Holder<Enchantment> other : table.keySet()) {
                if (!other.equals(holder) && !Enchantment.areCompatible(holder, other)) {
                    ok = false;
                    i++;
                }
            }
            if (!ok) {
                anyRejected = true;
                continue;
            }
            anyApplied = true;
            if (merged > enchantment.getMaxLevel()) {
                merged = enchantment.getMaxLevel();
            }
            table.set(holder, merged);
            i += enchantment.getAnvilCost() * merged;
        }
        if (anyRejected && !anyApplied) {
            return Optional.empty();   // 原版：全部不兼容 → 不产出
        }
        EnchantmentHelper.setEnchantments(out, table.toImmutable());

        // ③ 阶段记录合并（本模组新增）——只有「两侧同谱系且同阶级」才参与。
        AscensionData mergedData = leftData;
        for (var entry : rightData.lineages().entrySet()) {
            ResourceLocation root = entry.getKey();
            AscensionData.Record fromRight = entry.getValue();
            AscensionData.Record current = leftData.lineages().get(root);
            if (current == null) {
                continue;   // 右槽独有的谱系不搬：避免把进阶形态凭空带进产物
            }
            // 阶级必须完全相等才参与：`tierOfStageId` 返回 AscensionTier（必非原生阶，
            // 查不到时兜底 ADVANCED），所以拿它比较即可。
            AscensionTier leftTier = ProtectionLogic.tierOfStageId(current.stage());
            AscensionTier rightTier = ProtectionLogic.tierOfStageId(fromRight.stage());
            if (leftTier != rightTier) {
                continue;   // 阶级不同不参与（防跳阶 / 防降级）
            }
            LineageTier tier = leftTier.asLineageTier();
            int cap = StageLookup.maxLevelOf(stages, tier, root,
                    Math.max(current.tierLevel(), fromRight.tierLevel()));
            int target = current.tierLevel() == fromRight.tierLevel()
                    ? Math.min(current.tierLevel() + 1, Math.max(1, cap))
                    : Math.max(current.tierLevel(), fromRight.tierLevel());
            if (target == current.tierLevel()) {
                continue;   // 没有提升就不算变化
            }
            // 对外事件（可取消）：取消 = 本条谱系不参与本次合并（其余谱系照常）。
            if (!UEEvents.fireTierUpgrade(out, root, null, tier.ordinal() + 1, tier.ordinal() + 1,
                    current.tierLevel(), target, UltraEnchantTierUpgradeEvent.Source.MERGE)) {
                continue;
            }
            mergedData = mergedData.withTierLevel(root, target);
            ResourceLocation stageId = StageLookup.stageIdOf(stages, root, tier).orElse(null);
            StageDefinition stage = stageId == null ? null : StageLookup.byId(stages, stageId).orElse(null);
            if (stage != null) {
                i += stage.definition().anvilCost() * target;
            }
        }

        // ④ 重命名——原版 230-240 逐行照抄。
        //
        // 这一条是**必须**的：接管之前，同名装备合并走的是原版路径，名称框是生效的；
        // 接管之后若不搬这一段，就等于「边合并边改名」比原版少一个效果——那是回归，不是取舍。
        // （三条书路径不处理名称框，属既有缺口 AGENT.md R5，本轮不动。）
        if (name != null && !StringUtil.isBlank(name)) {
            if (!name.equals(left.getHoverName().getString())) {
                i += 1;
                out.set(DataComponents.CUSTOM_NAME, Component.literal(name));
            }
        } else if (left.has(DataComponents.CUSTOM_NAME)) {
            i += 1;
            out.remove(DataComponents.CUSTOM_NAME);
        }

        if (i <= 0) {
            return Optional.empty();   // 什么都没变 → 与原版一样不产出
        }
        UEComponents.setAscension(out, mergedData);

        // ⑤ 先修惩罚——原版 258-267：取两侧较深者，然后 ×2+1（无重命名时 k != i，必然加深）。
        int priorLeft = left.getOrDefault(DataComponents.REPAIR_COST, 0);
        int priorRight = right.getOrDefault(DataComponents.REPAIR_COST, 0);
        out.set(DataComponents.REPAIR_COST,
                AnvilMenu.calculateIncreasedRepairCost(Math.max(priorLeft, priorRight)));

        return Optional.of(new Merge(out, Math.max(1, priorLeft + priorRight + i)));
    }
}
