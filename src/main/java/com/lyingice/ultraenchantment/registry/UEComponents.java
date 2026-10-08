package com.lyingice.ultraenchantment.registry;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.content.AscensionData;
import com.lyingice.ultraenchantment.content.AscensionTier;
import com.lyingice.ultraenchantment.content.BookSpecs;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组的数据组件。
 *
 * <p><b>为什么书必须用自定义组件</b>：{@code EnchantmentHelper.getComponentType()} 对
 * {@code Items.ENCHANTED_BOOK} 返回 {@code STORED_ENCHANTMENTS}，**其它物品一律返回
 * {@code ENCHANTMENTS}**。自定义书物品会落进物品附魔槽，语义错位，且原版铁砧的
 * 「这是不是书」判定（{@code has(STORED_ENCHANTMENTS)}）永远不会对它生效。
 * 自己的强类型载荷是唯一正确解。
 */
public final class UEComponents {
    private UEComponents() {}

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Ultraenchantment.MODID);

    /**
     * 物品上的阶段标识：{@code { <原版附魔 id>: <阶段条目 id> }}。
     *
     * <p>同时充当「受保护」的判定依据——保护级别就是这里记录的那一阶。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AscensionData>> ASCENSION =
            COMPONENTS.register("ascension", () -> DataComponentType.<AscensionData>builder()
                    .persistent(AscensionData.CODEC)
                    .networkSynchronized(AscensionData.STREAM_CODEC)
                    .build());

    /** 进化型书载荷（通用 / 定向共用一个组件，靠 {@code applicable} 是否存在区分）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BookSpecs.Ascension>> ASCENSION_SPEC =
            COMPONENTS.register("ascension_spec", () -> DataComponentType.<BookSpecs.Ascension>builder()
                    .persistent(BookSpecs.Ascension.CODEC)
                    .networkSynchronized(BookSpecs.Ascension.STREAM_CODEC)
                    .build());

    /** 铭刻型书载荷。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BookSpecs.Inscription>> INSCRIPTION_SPEC =
            COMPONENTS.register("inscription_spec", () -> DataComponentType.<BookSpecs.Inscription>builder()
                    .persistent(BookSpecs.Inscription.CODEC)
                    .networkSynchronized(BookSpecs.Inscription.STREAM_CODEC)
                    .build());

    /** 升级型书载荷。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BookSpecs.Upgrade>> UPGRADE_SPEC =
            COMPONENTS.register("upgrade_spec", () -> DataComponentType.<BookSpecs.Upgrade>builder()
                    .persistent(BookSpecs.Upgrade.CODEC)
                    .networkSynchronized(BookSpecs.Upgrade.STREAM_CODEC)
                    .build());

    /**
     * 祛咒石的目标档位。
     *
     * <p>类型是 {@link AscensionTier}（必非基础阶）——祛咒石只作用于已进阶的附魔，
     * 「基础阶祛咒石」是无意义的概念。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AscensionTier>> CURATIVE_TIER =
            COMPONENTS.register("curative_tier", () -> DataComponentType.<AscensionTier>builder()
                    .persistent(AscensionTier.CODEC)
                    .networkSynchronized(AscensionTier.STREAM_CODEC)
                    .build());

    // ── 物品读写便捷方法 ────────────────────────────────────────────────
    // 放在这里而不是 AscensionData：content 包保持纯数据、不反向依赖 registry 包。

    /** 读取物品上的阶段数据；组件不存在时返回 {@link AscensionData#EMPTY}。 */
    public static AscensionData ascensionOf(ItemStack stack) {
        AscensionData data = stack.get(ASCENSION.get());
        return data == null ? AscensionData.EMPTY : data;
    }

    /** 写回物品上的阶段数据；{@code data} 为空时移除组件。 */
    public static void setAscension(ItemStack stack, AscensionData data) {
        if (data.isEmpty()) {
            stack.remove(ASCENSION.get());
        } else {
            stack.set(ASCENSION.get(), data);
        }
    }

    /** 读取祛咒石的档位；组件缺失时按 {@link AscensionTier#ADVANCED} 处理。 */
    public static AscensionTier curativeTierOf(ItemStack stack) {
        AscensionTier tier = stack.get(CURATIVE_TIER.get());
        return tier == null ? AscensionTier.ADVANCED : tier;
    }
}
