package com.lyingice.ultraenchantment.client;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.registry.UEBlockEntities;
import com.lyingice.ultraenchantment.registry.UEMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * 客户端注册。
 *
 * <p>界面只能挂在<b>客户端</b>：{@code RegisterMenuScreensEvent} 带
 * {@code Dist.CLIENT} 注解，在专用服务端上整个类不会被加载，
 * 因此引用 {@code LibraryScreen} 是安全的。
 */
@EventBusSubscriber(modid = Ultraenchantment.MODID, value = Dist.CLIENT)
public final class UEClientSetup {
    private UEClientSetup() {}

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(UEMenus.ENCHANTMENT_LIBRARY.get(), LibraryScreen::new);
        event.register(UEMenus.ASCENSION_TABLE.get(), AscensionTableScreen::new);
    }

    /** 进阶台的悬浮书（原版同款：原版 BookModel + 原版贴图）。 */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(UEBlockEntities.ASCENSION_TABLE.get(),
                AscensionTableRenderer::new);
    }
}
