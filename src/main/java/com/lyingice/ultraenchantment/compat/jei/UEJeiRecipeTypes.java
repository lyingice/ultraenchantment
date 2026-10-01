package com.lyingice.ultraenchantment.compat.jei;

import com.lyingice.ultraenchantment.Ultraenchantment;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.resources.ResourceLocation;

/** JEI 配方类型。 */
public final class UEJeiRecipeTypes {
    private UEJeiRecipeTypes() {}

    /** 铁砧上的三种操作共用一个类型。 */
    public static final RecipeType<AnvilDisplay> ANVIL =
            new RecipeType<>(ResourceLocation.fromNamespaceAndPath(Ultraenchantment.MODID, "anvil"),
                    AnvilDisplay.class);
}
