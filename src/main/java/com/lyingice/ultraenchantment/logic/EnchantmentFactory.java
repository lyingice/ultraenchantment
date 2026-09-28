package com.lyingice.ultraenchantment.logic;

import com.lyingice.ultraenchantment.content.StageDefinition;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 运行时组装 {@link Enchantment} 的**唯一入口**。
 *
 * <p>模组的阶段不进 {@code Registries.ENCHANTMENT}，而是在结算时现场组装成一个
 * {@code Enchantment} 对象注入 {@code ItemEnchantments.Mutable}。可行性依据（源码实证）：
 * <ol>
 *   <li>{@code Enchantment} 是 public record → 可直接 {@code new}</li>
 *   <li>其第 4 个字段就是 {@code DataComponentMap effects}</li>
 *   <li>{@code EnchantmentEffectComponents.CODEC} 是 public，能解析原版 effects JSON</li>
 *   <li>{@code Holder.Direct} 的 {@code isBound()=true}、{@code is(TagKey)} 返回 false 而非抛异常</li>
 * </ol>
 *
 * <h2>⚠️ 铁律：组装出的对象绝不能落回物品组件</h2>
 *
 * <p>它包在 {@link Holder.Direct} 里，没有注册表 ID。一旦写回
 * {@code DataComponents.ENCHANTMENTS}，存档/网络同步会走
 * {@code ByteBufCodecs.holderRegistry(Registries.ENCHANTMENT)} 并**直接崩溃**。
 *
 * <p>因此**所有** {@code new Enchantment(...)} + {@code Holder.direct(...)}
 * 只允许出现在本类中——把风险收敛到一个可审计的文件。
 * 注入用的 {@code ItemEnchantments} 是查询期临时的，用完即弃。
 */
public final class EnchantmentFactory {
    private EnchantmentFactory() {}

    /**
     * 组装结果缓存：{@code 阶段 id → Holder}。
     *
     * <p><b>为什么必须缓存</b>：注入用的 {@code Holder.Direct} 作为
     * {@code ItemEnchantments.Mutable} 内部 {@code Object2IntOpenHashMap} 的 key，
     * 依赖 {@code equals/hashCode}。若每次结算都造新实例，一旦
     * {@code Enchantment} / {@code DataComponentMap} 的 {@code equals} 语义有偏差
     * （record 逐字段比较链），表里就可能出现重复键或查不到键。
     * 缓存后同一阶段**永远是同一个对象**，按引用即可命中，风险归零。
     *
     * <p>缓存对象是我们自己 new 的，不依赖注册表生命周期，
     * 因此不与「数据包重载后 Holder 失效」的问题冲突（见 AGENT.md P1-6）。
     * 但阶段定义本身会随 {@code /reload} 变化，所以在数据包重载时清空。
     */
    private static final java.util.concurrent.ConcurrentHashMap<ResourceLocation, Holder<Enchantment>> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 把一个阶段定义组装成可注入的 {@link Enchantment}。
     *
     * @param stageId 阶段条目 id，仅用于生成可读的 description
     */
    public static Enchantment build(StageDefinition stage, ResourceLocation stageId) {
        StageDefinition.Definition def = stage.definition();

        Enchantment.EnchantmentDefinition vanillaDef = new Enchantment.EnchantmentDefinition(
                def.supportedItems(),
                def.primaryItems(),
                def.weight(),
                def.maxLevel(),
                def.minCost(),
                def.maxCost(),
                def.anvilCost(),
                def.slots());

        return new Enchantment(
                descriptionOf(stageId),
                vanillaDef,
                // 互斥集留空：结算管线不使用它（areCompatible 只在铁砧合并 / 附魔台时走）。
                HolderSet.direct(),
                // 效果直接来自数据包，与原版附魔 JSON 的 effects 同构。
                stage.effects());
    }

    /**
     * 取包成 {@link Holder.Direct} 的注入对象，同一 {@code stageId} 返回同一实例。
     *
     * <p><b>只可用于查询期注入，绝不可序列化</b>——它没有注册表 ID，
     * 写回 {@code DataComponents.ENCHANTMENTS} 会让存档/网络同步直接崩溃。
     */
    public static Holder<Enchantment> holder(StageDefinition stage, ResourceLocation stageId) {
        return CACHE.computeIfAbsent(stageId, id -> Holder.direct(build(stage, id)));
    }

    /** 数据包重载时清空缓存（阶段定义已变，旧的组装结果作废）。 */
    public static void invalidate() {
        CACHE.clear();
    }

    /**
     * 生成阶段的可读名称。
     *
     * <p>条目 id 形如 {@code ultraenchantment:super/sharpness}，斜杠换成点后得到
     * {@code enchantment.ultraenchantment.super.sharpness}。
     */
    private static Component descriptionOf(ResourceLocation stageId) {
        return Component.translatable("enchantment."
                + stageId.getNamespace() + "." + stageId.getPath().replace('/', '.'));
    }

    /** 判定一个 Holder 是否为我们现场组装的（无注册表 ID）。供调试与防御性断言用。 */
    public static boolean isSynthetic(Holder<Enchantment> holder) {
        return holder.unwrapKey().isEmpty();
    }
}
