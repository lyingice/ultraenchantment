package com.lyingice.ultraenchantment.event;

import com.lyingice.ultraenchantment.registry.UEItems;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * 创造栏投放——只放星光附魔台。
 *
 * <p>不再投放自定义附魔书样本：附魔书就是原版 {@code minecraft:enchanted_book}，
 * 阶级由书上的阶段附魔决定，外观由客户端 Mixin 替换。
 */
public final class CreativeTabEvents {
    private CreativeTabEvents() {}

    public static final CreativeTabEvents INSTANCE = new CreativeTabEvents();

    @SubscribeEvent
    public void onBuildTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) {
            event.accept(new ItemStack(UEItems.STARLIGHT_TABLE.get()),
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }
}
