package com.lyingice.ultraenchantment.starlight.registry;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.starlight.content.EvolutionData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 星光系统的数据组件。
 *
 * <p>只有一个：{@link #EVOLUTION}，记录物品的进化履历。
 * 附魔书不注册任何自定义组件——阶级直接从书上的原版 {@code stored_enchantments}
 * 推导，外观由客户端 Mixin 在物品模型解析处替换。
 */
public final class StarlightComponents {
    private StarlightComponents() {}

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Ultraenchantment.MODID);

    /** 物品上的进化履历。缺省 = 这件物品还没进化过。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<EvolutionData>> EVOLUTION =
            COMPONENTS.register("evolution", () -> DataComponentType.<EvolutionData>builder()
                    .persistent(EvolutionData.CODEC)
                    .networkSynchronized(EvolutionData.STREAM_CODEC)
                    .build());


    /** 读履历；没有时返回 {@code null}（调用方据此判断「这是根源状态」）。 */
    public static EvolutionData evolutionOf(ItemStack stack) {
        return stack.get(EVOLUTION.get());
    }

    /** 写履历；{@code data == null} 时移除组件。 */
    public static void setEvolution(ItemStack stack, EvolutionData data) {
        if (data == null) {
            stack.remove(EVOLUTION.get());
        } else {
            stack.set(EVOLUTION.get(), data);
        }
    }
}
