package com.lyingice.ultraenchantment.mixin;

import com.lyingice.ultraenchantment.starlight.client.StarlightBookTier;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 附魔书封面切换——<b>不依赖自定义数据组件</b>。
 *
 * <p>在 1.21.1 里 GUI / 背包 / 手持的物品图标最终都经过
 * {@link ItemRenderer#getModel(ItemStack, Level, LivingEntity, int)}。
 * 这里在返回值处检查：如果物品是原版附魔书，且书里存储的阶段附魔属于某个阶级，
 * 就换成该阶级的封面模型；否则原样返回。
 *
 * <p>目标模型由 {@code UEClientSetup.onRegisterAdditional} 注册参与烘焙。
 */
@Mixin(ItemRenderer.class)
public abstract class EnchantedBookModelMixin {

    @ModifyReturnValue(method = "getModel", at = @At("RETURN"))
    private BakedModel ultraenchantment$tierCover(BakedModel original, ItemStack stack,
                                                  Level level, LivingEntity entity, int seed) {
        int tier = StarlightBookTier.tierOf(stack);
        if (tier <= 0) {
            return original;
        }
        ResourceLocation modelId = StarlightBookTier.modelFor(tier);
        if (modelId == null) {
            return original;
        }
        BakedModel model = Minecraft.getInstance().getModelManager()
                .getModel(ModelResourceLocation.standalone(modelId));
        return model == Minecraft.getInstance().getModelManager().getMissingModel()
                ? original : model;
    }
}
