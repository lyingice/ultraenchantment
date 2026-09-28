package com.lyingice.ultraenchantment.logic;

import com.lyingice.ultraenchantment.content.BookSpecs;
import com.lyingice.ultraenchantment.content.BookSubject;
import com.lyingice.ultraenchantment.content.UETier;
import com.lyingice.ultraenchantment.datagen.UEModels;
import com.lyingice.ultraenchantment.registry.UEComponents;
import com.lyingice.ultraenchantment.registry.UEItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/**
 * 进阶附魔书的<b>唯一构造入口</b>。
 *
 * <p>一本书 = 单例物品 + 载荷组件 + {@code custom_model_data}（材质档位）。
 * 这个组合在四个地方要用（创造栏、转印产物、剩菜书、调试），
 * 之前散在创造栏的一堆私有方法里，v2 抽出来集中一次。
 *
 * <p>没有把 {@link BookSpecs} 当参数——载荷类型有三种，取值方式不同；
 * 这里只负责「物品 + 外观」，载荷由调用方 {@code set}。
 */
public final class BookFactory {
    private BookFactory() {}

    /** 造一本「某科目的进阶附魔书」，材质切到指定阶级。载荷留给调用方写。 */
    public static ItemStack create(BookSubject subject, UETier viewTier) {
        ItemStack stack = new ItemStack(UEItems.ADVANCED_ENCHANTED_BOOK.get());
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(UEModels.predicateOf(subject.id(), viewTier)));
        return stack;
    }

    /** 造一本载体书（铭刻型）并写入载荷。 */
    public static ItemStack inscription(BookSpecs.Inscription payload) {
        ItemStack stack = create(BookSubject.INSCRIPTION, payload.tier().asViewTier());
        stack.set(UEComponents.INSCRIPTION_SPEC.get(), payload);
        return stack;
    }
}
