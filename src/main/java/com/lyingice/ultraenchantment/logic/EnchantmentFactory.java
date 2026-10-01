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
     * 现场组装出来的 {@link Enchantment} <b>对象本身</b>（按引用判等）。
     *
     * <p>给兼容补丁用：第三方模组常把一个 {@code Enchantment} 反查回注册表 key
     * （{@code registry.getResourceKey(ench)}），遇到我们这些没有注册表条目的对象就会炸
     * （见 AGENT.md P1-38：试验假人模组 {@code Optional.get()} 崩服）。
     * {@link #CACHE} 是「阶段 id → Holder」，反查要遍历；这里是 O(1) 的引用集合。
     */
    private static final java.util.Set<Enchantment> SYNTHETIC =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /**
     * 现场组装出来的附魔 → <b>它对应的原版谱系根源</b>（如 {@code minecraft:impaling}）。
     *
     * <p>合成附魔没有注册表 key，但它<b>有语义身份</b>：阶级「穿刺」就是原版的穿刺。
     * 需要跟第三方模组解释「这个附魔属于哪条谱系」时（见 P1-38 试验假人的特攻判定），
     * 用这张表把根源当 key 交出去。
     */
    private static final java.util.Map<Enchantment, ResourceLocation> ROOTS =
            new java.util.IdentityHashMap<>();

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
        return CACHE.computeIfAbsent(stageId, id -> {
            Enchantment assembled = build(stage, id);
            SYNTHETIC.add(assembled);
            ROOTS.put(assembled, stage.root());
            return Holder.direct(assembled);
        });
    }

    /** 数据包重载时清空缓存（阶段定义已变，旧的组装结果作废）。 */
    public static void invalidate() {
        CACHE.clear();
        SYNTHETIC.clear();
        ROOTS.clear();
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

    /**
     * 判定一个 {@link Enchantment} <b>对象</b>是不是我们现场组装的。
     *
     * <p>兼容补丁在别的模组的方法里只拿得到对象、拿不到 Holder，所以需要这个值版判定。
     */
    public static boolean isSynthetic(Enchantment enchantment) {
        return SYNTHETIC.contains(enchantment);
    }

    /**
     * 合成附魔对应的原版谱系根源；不是我们组装的则返回 {@code null}。
     *
     * <p>用途：向第三方解释「阶级穿刺 = 原版穿刺」。返回的是<b>语义身份</b>，
     * 不是注册表条目——不要拿它去写物品组件。
     */
    @javax.annotation.Nullable
    public static ResourceLocation rootOf(Enchantment enchantment) {
        return ROOTS.get(enchantment);
    }
}
