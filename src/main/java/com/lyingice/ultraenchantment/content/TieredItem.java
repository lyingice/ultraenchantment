package com.lyingice.ultraenchantment.content;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 基础物品类：本地化名称 <b>不随阶级变化</b>。
 *
 * <p>这是刻意的——三种书与原版附魔书、锻造模板同款做法：物品名恒定，
 * 阶级只体现在 <b>材质</b>（{@code custom_model_data} 切换模型）与 <b>提示框</b>（tooltip）上。
 * 因此本类不做 {@code getDescriptionId} 覆写，走标准的 {@code item.<ns>.<name>} 键。
 *
 * <p>阶级的读写统一走 {@link UETier}。
 */
public class TieredItem extends Item {
    public TieredItem(Properties properties) {
        super(properties);
    }

    /** 便捷读法：取本物品当前的阶级。 */
    public UETier tierOf(ItemStack stack) {
        return UETier.of(stack);
    }
}
