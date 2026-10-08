package com.lyingice.ultraenchantment.registry;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.menu.AscensionTableMenu;
import com.lyingice.ultraenchantment.menu.LibraryMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 容器菜单类型注册。
 *
 * <p>用 {@link IMenuTypeExtension#create} 而不是原版 {@code new MenuType<>(...)}：
 * 图书馆菜单需要知道<b>方块坐标</b>（才能找到方块实体读库存），
 * 而坐标要随「打开界面」的包一起发过来。
 * {@code IMenuTypeExtension} 的工厂带一个 {@code RegistryFriendlyByteBuf}，
 * 配合 {@code player.openMenu(provider, pos)} 即可把坐标同步到客户端。
 */
public final class UEMenus {
    private UEMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Ultraenchantment.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<LibraryMenu>> ENCHANTMENT_LIBRARY =
            MENUS.register("advanced_enchantment_library", () -> IMenuTypeExtension.create(
                    (windowId, inventory, data) -> new LibraryMenu(windowId, inventory, data.readBlockPos())));

    public static final DeferredHolder<MenuType<?>, MenuType<AscensionTableMenu>> ASCENSION_TABLE =
            MENUS.register("ascension_table", () -> IMenuTypeExtension.create(
                    (windowId, inventory, data) -> new AscensionTableMenu(windowId, inventory, data.readBlockPos())));
}
