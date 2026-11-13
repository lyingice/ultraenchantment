package com.lyingice.ultraenchantment.registry;

import com.lyingice.ultraenchantment.starlight.registry.StarlightRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * 数据包注册表注册——只注册星光系统的 {@code starlight_tier} 与 {@code starlight_tree}。
 *
 * <p>旧的 {@code ultraenchantment:enchantment} 阶段注册表已随旧体系移除；
 * 进化后的阶段附魔现在是真正的 {@code minecraft:enchantment} 条目。
 */
public final class UEDataPackRegistries {
    private UEDataPackRegistries() {}

    public static final UEDataPackRegistries INSTANCE = new UEDataPackRegistries();

    @SubscribeEvent
    public void registerRegistries(DataPackRegistryEvent.NewRegistry event) {
        StarlightRegistries.register(event);
    }
}
