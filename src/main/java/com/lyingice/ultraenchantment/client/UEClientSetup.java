package com.lyingice.ultraenchantment.client;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.registry.UEBlockEntities;
import com.lyingice.ultraenchantment.registry.UEMenus;
import com.lyingice.ultraenchantment.starlight.client.StarlightTableRenderer;
import com.lyingice.ultraenchantment.starlight.client.StarlightTableScreen;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * 客户端注册：
 * <ul>
 *   <li>星图界面；</li>
 *   <li>星光台悬浮书渲染器；</li>
 *   <li>三张书皮模型参与烘焙——{@code EnchantedBookModelMixin} 在
 *       {@code ItemRenderer.getModel} 返回处直接取它们。</li>
 * </ul>
 */
@EventBusSubscriber(modid = Ultraenchantment.MODID, value = Dist.CLIENT)
public final class UEClientSetup {
    private UEClientSetup() {}

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(UEMenus.STARLIGHT_TABLE.get(), StarlightTableScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(UEBlockEntities.STARLIGHT_TABLE.get(),
                StarlightTableRenderer::new);
        Ultraenchantment.LOGGER.info("[starlight] starlight table renderer registered");
    }

    @SubscribeEvent
    public static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        event.register(cover("enchanted_book"));
        event.register(cover("enchanted_book_super"));
        event.register(cover("enchanted_book_ultra"));
        Ultraenchantment.LOGGER.info("[starlight] registered 3 enchanted book cover models");
    }

    private static ModelResourceLocation cover(String path) {
        return ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(Ultraenchantment.MODID, "item/" + path));
    }
}
