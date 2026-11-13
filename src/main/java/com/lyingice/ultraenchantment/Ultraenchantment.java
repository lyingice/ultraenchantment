package com.lyingice.ultraenchantment;

import com.lyingice.ultraenchantment.datagen.UEDataGen;
import com.lyingice.ultraenchantment.event.CreativeTabEvents;
import com.lyingice.ultraenchantment.event.ReloadEvents;
import com.lyingice.ultraenchantment.registry.UEBlockEntities;
import com.lyingice.ultraenchantment.registry.UEBlocks;
import com.lyingice.ultraenchantment.registry.UEDataPackRegistries;
import com.lyingice.ultraenchantment.registry.UEItems;
import com.lyingice.ultraenchantment.registry.UEMenus;
import com.lyingice.ultraenchantment.starlight.registry.StarlightComponents;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * <b>Lightstar</b>——只保留「进化图 + 星光附魔台」的版本。
 *
 * <p>旧的进阶附魔书体系（阶级阶段注册表、铁砧流程、图书馆、旧进阶台、图鉴、祛咒石、
 * 村民交易、战利品注入、旧 API/兼容层）已整体移除。
 * 物品侧只剩 {@code starlight_table}；附魔书使用原版
 * {@code minecraft:enchanted_book}，书皮由客户端 Mixin 根据存储的阶段附魔阶级替换。
 */
@Mod(Ultraenchantment.MODID)
public class Ultraenchantment {
    public static final String MODID = "ultraenchantment";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Ultraenchantment(IEventBus modEventBus) {
        // 物品 / 方块 / 菜单 / 组件
        UEItems.ITEMS.register(modEventBus);
        UEBlocks.BLOCKS.register(modEventBus);
        UEBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        UEMenus.MENUS.register(modEventBus);
        StarlightComponents.COMPONENTS.register(modEventBus);

        // 数据包注册表（starlight_tier / starlight_tree）
        modEventBus.register(UEDataPackRegistries.INSTANCE);

        // 创造栏 / datagen
        modEventBus.register(CreativeTabEvents.INSTANCE);
        modEventBus.addListener(UEDataGen::onGatherData);

        // 数据包重载后重建进化图索引
        NeoForge.EVENT_BUS.register(ReloadEvents.INSTANCE);

        LOGGER.info("Ultra Enchantment (Lightstar) loaded: {} items / {} blocks / {} menus",
                UEItems.ITEMS.getEntries().size(),
                UEBlocks.BLOCKS.getEntries().size(),
                UEMenus.MENUS.getEntries().size());
    }
}
