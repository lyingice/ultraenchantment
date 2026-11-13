package com.lyingice.ultraenchantment.registry;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.starlight.menu.StarlightTableMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 容器菜单注册——Lightstar 只有星光附魔台。
 *
 * <p>用 {@link IMenuTypeExtension#create} 让方块坐标随 {@code openMenu(provider, pos)} 同步。
 */
public final class UEMenus {
    private UEMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Ultraenchantment.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<StarlightTableMenu>> STARLIGHT_TABLE =
            MENUS.register("starlight_table", () -> IMenuTypeExtension.create(
                    (windowId, inventory, data) -> new StarlightTableMenu(windowId, inventory, data.readBlockPos())));
}
