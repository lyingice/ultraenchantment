package com.lyingice.ultraenchantment.event;

import com.lyingice.ultraenchantment.content.StageDefinition;
import com.lyingice.ultraenchantment.logic.EnchantmentFactory;
import com.lyingice.ultraenchantment.registry.UEComponents;
import com.lyingice.ultraenchantment.registry.UERegistries;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;

/**
 * <b>结算层</b>——整套系统的核心。
 *
 * <p>这是「屏蔽原版 + 注入阶段」的唯一落点。原版所有附魔效果结算
 * （伤害、击退、保护、耐久、tick…）都经过
 * {@code EnchantmentHelper.runIterationOnItem} → {@code stack.getAllEnchantments(lookup)}
 * → 本事件，因此在这里改写附魔表即可完全接管效果计算。
 *
 * <h2>两种触发形态（源码实证，必须区别对待）</h2>
 *
 * <table>
 *   <tr><th>触发源</th><th>{@code getTargetEnchant()}</th><th>可变表内容</th></tr>
 *   <tr><td>{@code getAllEnchantments()}（效果结算）</td><td>{@code null}</td><td>物品上全部附魔</td></tr>
 *   <tr><td>{@code getEnchantmentLevel(h)}（单点查询）</td><td>非 null</td><td><b>只含 h 一个</b></td></tr>
 * </table>
 *
 * <p>单点查询时可变表是**新建的空表只塞了目标附魔**
 * （{@code EventHooks.getEnchantmentLevelSpecific} 的实现），不能假设表里有别的附魔。
 *
 * <h2>设计取舍：单点查询不屏蔽</h2>
 *
 * <p>全量枚举时屏蔽原版附魔并注入阶段；**单点查询保留原等级**。
 * 于是第三方模组问「这把剑锋利几级」会得到真实等级，而游戏内部按更高阶的定义结算。
 * 对外语义是「一把锋利 N 级的剑，内部按更高阶算」——兼容性最好，
 * 也避免别的模组拿到 0 级做除零之类的怪事。
 *
 * <h2>为什么用 {@link CommonHooks#resolveLookup}</h2>
 *
 * <p>事件只给 {@code RegistryLookup<Enchantment>}（仅能查附魔），拿不到我们的阶段注册表。
 * {@code CommonHooks.resolveLookup} 是 NeoForge 自己的取法——服务端优先、客户端兜底，
 * 与 {@code EnchantmentHelper.runIterationOnItem} 用的是同一条路径，两侧行为一致。
 *
 * <h2>热路径注意</h2>
 *
 * <p>本事件在每次伤害结算、装备遍历、附魔等级查询时都会触发。
 * **第一行必须是组件判空 return**——没有阶段数据的物品（绝大多数）直接走人。
 */
public final class EnchantmentLevelEvents {
    private EnchantmentLevelEvents() {}

    /** 单例监听器，供 game bus 注册。 */
    public static final EnchantmentLevelEvents INSTANCE = new EnchantmentLevelEvents();

    @SubscribeEvent
    public void onGetEnchantmentLevel(GetEnchantmentLevelEvent event) {
        // ── 热路径早退：没有阶段数据的物品占绝大多数 ──
        Map<ResourceLocation, ResourceLocation> recorded = UEComponents.ascensionOf(event.getStack()).stages();
        if (recorded.isEmpty()) {
            return;
        }

        // 单点查询：保留原等级，不屏蔽（见类文档的设计取舍）。
        if (event.getTargetEnchant() != null) {
            return;
        }

        // 阶段注册表：与 EnchantmentHelper 同一条取法，服务端优先、客户端兜底。
        HolderLookup.RegistryLookup<StageDefinition> stages = CommonHooks.resolveLookup(UERegistries.STAGE);
        if (stages == null) {
            return;
        }

        ItemEnchantments.Mutable table = event.getEnchantments();

        recorded.forEach((rootId, stageId) -> {
            StageDefinition stage = stages.get(ResourceKey.create(UERegistries.STAGE, stageId))
                    .map(Holder.Reference::value)
                    .orElse(null);
            if (stage == null) {
                return;
            }

            Holder<Enchantment> root = event.getHolder(ResourceKey.create(Registries.ENCHANTMENT, rootId))
                    .map(h -> (Holder<Enchantment>) h)
                    .orElse(null);
            if (root == null) {
                return;
            }

            int storageLevel = table.getLevel(root);
            if (storageLevel <= 0) {
                return;
            }

            // ① 屏蔽原版：set(holder, 0) 等价于从表中删除（Mutable.set 的源码行为）。
            table.set(root, 0);

            // ② 注入阶段的等级 = **进阶曲线等级（tierLevel）**，不是原版存储等级。
            //
            // 这是整套进阶体系成立的前提：升阶时存储等级是**不变**的
            // （锋利 5 升阶后仍是 5，见 P1-20），拿它当效果等级的话，
            // 「高阶锋利 1 级」会按 5 级算，显示与实强完全脱节，
            // 而且升级书（把 tierLevel 从 1 提到 N）不会带来任何强度变化——形同虚设。
            //
            // tierLevel 就是「这个进阶形态自己的附魔等级」，数据包里每一阶的效果曲线
            // （原版那部分 + 阶位加成）都是按这条曲线写的，所以必须用它来结算。
            //
            // 兜底：记录缺失或为 0 时退回存储等级——宁可退回也不要注入 0 级。
            int tierLevel = UEComponents.ascensionOf(event.getStack()).tierLevelOf(rootId);
            int effectLevel = tierLevel > 0 ? tierLevel : storageLevel;

            table.set(EnchantmentFactory.holder(stage, stageId), effectLevel);
        });
    }
}
