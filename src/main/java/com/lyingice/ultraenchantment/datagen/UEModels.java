package com.lyingice.ultraenchantment.datagen;

import com.lyingice.ultraenchantment.Ultraenchantment;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * 附魔书封面模型生成。
 *
 * <p>生成三个独立模型（阶级 1/2/3 的封面），<b>不注册物品</b>。
 * 原版 {@code minecraft:enchanted_book} 的外观由客户端
 * {@code EnchantedBookModelMixin} 在 {@code ItemRenderer.getModel} 返回处替换：
 * 书里存储的阶段附魔属于哪个阶级，就用哪张封面。
 *
 * <p>没有阶段附魔的原版书保持原版外观。
 */
public class UEModels extends ItemModelProvider {

    public UEModels(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Ultraenchantment.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        // 阶级 1 封面（Mixin 在 getModel 返回处选择）。
        singleTexture("enchanted_book", mcLoc("item/generated"), "layer0",
                modLoc("item/enchanted_book"));

        // 阶级 2 / 3 的独立封面模型。
        singleTexture("enchanted_book_super", mcLoc("item/generated"), "layer0",
                modLoc("item/super_enchanted_book"));
        singleTexture("enchanted_book_ultra", mcLoc("item/generated"), "layer0",
                modLoc("item/ultra_enchanted_book"));
    }
}
