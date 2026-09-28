package com.lyingice.ultraenchantment.registry;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.content.StageDefinition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * 数据包注册表的注册。
 *
 * <p><b>总线陷阱</b>：{@code DataPackRegistryEvent.NewRegistry} 实现 {@code IModBusEvent}，
 * 必须挂 mod bus。挂到 game bus（{@code NeoForge.EVENT_BUS}）**永远不会触发且不报错**。
 * 这里由 {@code Ultraenchantment} 构造器显式 {@code modEventBus.register(this)}
 * （1.21.1 的 {@code @EventBusSubscriber(bus = ...)} 已标记过时）。
 *
 * <p><b>同步陷阱</b>：第三个参数必须非 null（这里传 {@link StageDefinition#CODEC}）。
 * 铁砧与砂轮的结果计算在客户端也会执行，阶级数据读不到就会算出与服务端不一致的结果。
 */
public final class UEDataPackRegistries {
    private UEDataPackRegistries() {}

    /** 单例监听器实例，供 mod bus 注册。 */
    public static final UEDataPackRegistries INSTANCE = new UEDataPackRegistries();

    @SubscribeEvent
    public void registerRegistries(DataPackRegistryEvent.NewRegistry event) {
        // 第三个参数要的是 Codec<T>（不是 StreamCodec）——NeoForge 内部再据此派生网络编解码。
        event.dataPackRegistry(
                UERegistries.STAGE,
                StageDefinition.CODEC,
                StageDefinition.CODEC);
        Ultraenchantment.LOGGER.info("Registered data pack registry {}", UERegistries.STAGE_REGISTRY_ID);
    }
}
