package com.lyingice.ultraenchantment.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;

/**
 * 一个「阶段条目」——某条谱系上的某一阶。
 *
 * <p>这是模组进阶体系的最小数据单元，存放在自定义数据包注册表
 * {@code ultraenchantment:ultraenchantment} 中。**它本身不是附魔**，不进
 * {@code Registries.ENCHANTMENT}，因此不占用等级刻度、不与其他模组冲突。
 *
 * <p>字段设计：
 * <ul>
 *   <li>{@code root} —— 谱系根源，指向被进阶的原版附魔。这是「深度捆绑」的落点。</li>
 *   <li>{@code tier} —— 本条目所处的阶级。</li>
 *   <li>{@code next} —— 谱系链的下一阶；缺省表示本条即谱系终点。</li>
 *   <li>{@code requiredLevel} —— <b>进阶到本条</b>所需的源附魔等级。门槛由更高阶自己定义，
 *       实际生效值取 {@code min(requiredLevel, 源阶段上限)}（反死锁）。</li>
 *   <li>{@code definition} —— 与原版 {@code EnchantmentDefinition} 同构的数值定义。</li>
 *   <li>{@code effects} —— 效果组件，语法与原版附魔 JSON 的 {@code effects} 完全一致。</li>
 * </ul>
 *
 * <p>示例（{@code data/ultraenchantment/ultraenchantment/super/sharpness.json}）：
 * <pre>{@code
 * {
 *   "root": "minecraft:sharpness",
 *   "tier": "super",
 *   "next": "ultraenchantment:ultra/sharpness",
 *   "required_level": 4,
 *   "definition": { "weight": 1, "max_level": 5, ... },
 *   "effects": { "minecraft:damage": [ ... ] }
 * }
 * }</pre>
 */
public record StageDefinition(
        ResourceLocation root,
        LineageTier tier,
        Optional<ResourceLocation> next,
        int requiredLevel,
        Definition definition,
        DataComponentMap effects) {

    public static final Codec<StageDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("root").forGetter(StageDefinition::root),
            LineageTier.CODEC.fieldOf("tier").forGetter(StageDefinition::tier),
            ResourceLocation.CODEC.optionalFieldOf("next").forGetter(StageDefinition::next),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("required_level", 1).forGetter(StageDefinition::requiredLevel),
            Definition.CODEC.fieldOf("definition").forGetter(StageDefinition::definition),
            // 直接复用原版附魔效果组件的 codec：完整解析 27 种效果类型，
            // 因此本字段的 JSON 与原版附魔的 "effects" 一字不差。
            EnchantmentEffectComponents.CODEC.optionalFieldOf("effects", DataComponentMap.EMPTY)
                    .forGetter(StageDefinition::effects)
    ).apply(instance, StageDefinition::new));

    /**
     * 网络同步用 codec。
     *
     * <p>注意：数据包注册表的 {@code DataPackRegistryEvent.NewRegistry.dataPackRegistry}
     * 第三个参数要的是 {@code Codec<T>} 而**不是** {@code StreamCodec}——
     * 所以本字段供物品组件等场景使用，注册表侧传的是 {@link #CODEC}。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, StageDefinition> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    /** 本条是否为谱系终点。 */
    public boolean isTerminal() {
        return this.next.isEmpty();
    }

    /** 数值定义。与原版 {@code Enchantment.EnchantmentDefinition} 同构。 */
    public record Definition(
            int weight,
            int maxLevel,
            Enchantment.Cost minCost,
            Enchantment.Cost maxCost,
            int anvilCost,
            HolderSet<Item> supportedItems,
            Optional<HolderSet<Item>> primaryItems,
            List<EquipmentSlotGroup> slots) {

        public static final Codec<Definition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.intRange(1, 1024).fieldOf("weight").forGetter(Definition::weight),
                ExtraCodecs.intRange(1, 255).fieldOf("max_level").forGetter(Definition::maxLevel),
                Enchantment.Cost.CODEC.fieldOf("min_cost").forGetter(Definition::minCost),
                Enchantment.Cost.CODEC.fieldOf("max_cost").forGetter(Definition::maxCost),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("anvil_cost").forGetter(Definition::anvilCost),
                RegistryCodecs.homogeneousList(Registries.ITEM).fieldOf("supported_items").forGetter(Definition::supportedItems),
                RegistryCodecs.homogeneousList(Registries.ITEM).optionalFieldOf("primary_items").forGetter(Definition::primaryItems),
                EquipmentSlotGroup.CODEC.listOf().fieldOf("slots").forGetter(Definition::slots)
        ).apply(instance, Definition::new));
    }
}
