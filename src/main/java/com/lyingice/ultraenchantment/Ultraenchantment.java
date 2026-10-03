package com.lyingice.ultraenchantment;

import com.lyingice.ultraenchantment.datagen.UEDataGen;
import com.lyingice.ultraenchantment.event.AnvilEvents;
import com.lyingice.ultraenchantment.event.AnvilTakeEvents;
import com.lyingice.ultraenchantment.event.BookTooltipEvents;
import com.lyingice.ultraenchantment.event.CreativeTabEvents;
import com.lyingice.ultraenchantment.event.EnchantmentLevelEvents;
import com.lyingice.ultraenchantment.event.GrindstoneEvents;
import com.lyingice.ultraenchantment.event.ReloadEvents;
import com.lyingice.ultraenchantment.event.TooltipEvents;
import com.lyingice.ultraenchantment.registry.UEComponents;
import com.lyingice.ultraenchantment.registry.UEDataPackRegistries;
import com.lyingice.ultraenchantment.registry.UEItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(Ultraenchantment.MODID)
public class Ultraenchantment {
    public static final String MODID = "ultraenchantment";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Ultraenchantment(IEventBus modEventBus) {
        // ── mod bus：注册与数据生成 ──
        UEItems.ITEMS.register(modEventBus);
        UEComponents.COMPONENTS.register(modEventBus);
        modEventBus.register(UEDataPackRegistries.INSTANCE);
        // 创造栏投放（BuildCreativeModeTabContentsEvent 是 mod bus 事件）。
        modEventBus.register(CreativeTabEvents.INSTANCE);
        modEventBus.addListener(UEDataGen::onGatherData);

        // ── game bus：运行期事件 ──
        // 结算层：屏蔽原版附魔 + 注入阶段定义。
        NeoForge.EVENT_BUS.register(EnchantmentLevelEvents.INSTANCE);
        // 数据包重载后清理组装缓存。
        NeoForge.EVENT_BUS.register(ReloadEvents.INSTANCE);
        // 铁砧链路：三类书的使用入口 + 书合并 + 转印 + 同名装备合并。
        NeoForge.EVENT_BUS.register(AnvilEvents.INSTANCE);
        // 铁砧取件：交付载体书的「剩菜书」（服务端，槽位清空前）。
        NeoForge.EVENT_BUS.register(AnvilTakeEvents.INSTANCE);
        // 砂轮链路：受保护附魔守护 + 祛咒石。
        NeoForge.EVENT_BUS.register(GrindstoneEvents.INSTANCE);
        // 显示层：把附魔行改写成带阶级的样子。
        NeoForge.EVENT_BUS.register(TooltipEvents.INSTANCE);
        // 显示层：进阶附魔书自身的四套 tooltip（通用/定向/铭刻/升级）。
        NeoForge.EVENT_BUS.register(BookTooltipEvents.INSTANCE);

        // 可选兼容：提示框渲染栈。未安装时这里什么都不做（不加载对方任何类）。
        com.lyingice.ultraenchantment.compat.tooltip.TooltipStackCompat.init();

        LOGGER.info("Ultra Enchantment loaded ({} items, {} components)",
                UEItems.ITEMS.getEntries().size(), UEComponents.COMPONENTS.getEntries().size());
    }
}
