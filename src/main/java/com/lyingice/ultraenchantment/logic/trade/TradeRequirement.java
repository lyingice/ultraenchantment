package com.lyingice.ultraenchantment.logic.trade;

import com.lyingice.ultraenchantment.registry.UEComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;

/**
 * 「收走该谱系**满级原生附魔**」这条成本规则的载荷与校验。
 *
 * <h2>为什么不能用组件谓词（源码实证）</h2>
 *
 * <p>直觉做法是 {@code ItemCost.withComponents(...)} 挂一个
 * {@code minecraft:enchantments} 谓词。但 {@code DataComponentPredicate.test} 是：
 *
 * <pre>
 *   for (TypedDataComponent&lt;?&gt; expected : this.expectedComponents) {
 *       Object actual = map.get(expected.type());
 *       if (!Objects.equals(expected.value(), actual)) return false;
 *   }
 * </pre>
 *
 * <p>整条组件用 {@code Objects.equals} <b>精确比对</b>，而
 * {@code ItemEnchantments.equals} 比的是<b>整张 map</b>。
 * 于是谓词 {@code enchantments = {sharpness: 5}} 只匹配
 * <b>恰好只带这一条附魔</b>的物品——任何还带耐久/经验的剑都不匹配。
 * <b>谓词无法表达「包含」语义。</b>
 *
 * <h2>为什么不能继承 ItemCost（javap 实测）</h2>
 *
 * <p>{@code javap} 输出：<b>{@code public final class ItemCost}</b>，不可继承。
 * 所以「重写 test」这条路是死的。
 * （{@code sources} jar 里看不到 {@code final}，以 {@code javap} 为准。）
 *
 * <h2>因此：校验落在 MerchantOffer.satisfiedBy 上</h2>
 *
 * <p>成交判定链：{@code MerchantContainer.updateSellItem → MerchantOffers.getRecipeFor
 * → MerchantOffer.satisfiedBy(ItemStack, ItemStack)}。
 * {@code MerchantOffer} 是<b>非 final 类</b>、{@code satisfiedBy} 是<b>非 final 方法</b>
 * （均为 {@code javap} 实测），因此由 {@code MerchantOfferMixin} 在其返回前追加本类校验。
 *
 * <h2>规则存在哪</h2>
 *
 * <p>存在成本物品的组件 {@link UEComponents#TRADE_REQUIREMENT} 上，<b>不是</b> offer 的 Java 字段：
 * {@code MerchantOffer} 会被序列化并经网络同步（{@code MerchantOffer.CODEC} 的 buy 是
 * {@code ItemCost.CODEC}，而 {@code ItemCost.itemStack} 是完整 {@code ItemStack}），
 * 只有组件能完整往返；挂 Java 字段在同步/重载后就丢了。
 */
public record TradeRequirement(ResourceLocation enchantment, int level) {
    /**
     * ⚠️ <b>必须是 record（或显式实现 equals/hashCode）</b>。
     *
     * <p>实测：用普通类会抛
     * <pre>
     *   IllegalArgumentException: Data components must implement equals and hashCode.
     *   Keep in mind they must also be immutable.
     * </pre>
     * 数据组件按<b>值</b>比较（堆叠、同步、谓词匹配都依赖它），
     * record 自动提供按字段的 equals/hashCode，正好满足。
     */

    public static final Codec<TradeRequirement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("enchantment").forGetter(TradeRequirement::enchantment),
            Codec.INT.fieldOf("level").forGetter(TradeRequirement::level)
    ).apply(instance, TradeRequirement::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TradeRequirement> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    /**
     * 把规则写进成本物品。
     *
     * @param marker 成本里的展示物品（{@code ItemCost.itemStack()}）
     */
    public static void bind(ItemStack marker, ResourceLocation root, int requiredLevel) {
        marker.set(UEComponents.TRADE_REQUIREMENT.get(), new TradeRequirement(root, requiredLevel));
    }

    /**
     * 校验成本是否被满足。
     *
     * <p>没有规则组件 → 返回 {@code true}（「不额外否决」，交给原版 {@code ItemCost.test}）。
     *
     * <p>规则本身的三条：
     * <ol>
     *   <li>物品带有该谱系的附魔，且等级 ≥ 要求；</li>
     *   <li>该谱系<b>尚未进阶</b>——看 {@code ascension} 组件，<b>不看等级</b>：
     *       升阶后存储等级不变（AGENT.md P1-20），只看等级区分不出「原生满级」与「已进阶」；</li>
     *   <li>物品非空。</li>
     * </ol>
     *
     * @return {@code true} = 通过（或本成本没有规则）
     */
    public static boolean accepts(ItemCost cost, ItemStack stack) {
        TradeRequirement requirement = cost.itemStack().get(UEComponents.TRADE_REQUIREMENT.get());
        if (requirement == null) {
            return true;
        }
        if (stack.isEmpty()) {
            return false;
        }
        if (UEComponents.ascensionOf(stack).stageOf(requirement.enchantment()).isPresent()) {
            return false;
        }

        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (var entry : enchantments.entrySet()) {
            ResourceLocation id = entry.getKey().unwrapKey().map(ResourceKey::location).orElse(null);
            if (requirement.enchantment().equals(id) && entry.getIntValue() >= requirement.level()) {
                return true;
            }
        }
        return false;
    }
}
