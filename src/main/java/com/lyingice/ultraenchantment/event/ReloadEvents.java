package com.lyingice.ultraenchantment.event;

import com.lyingice.ultraenchantment.starlight.logic.TreeIndex;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/**
 * 数据包重载：重建星光进化图索引。
 *
 * <p>用事件自带的 {@code RegistryAccess}，不要用 {@code CommonHooks.resolveLookup}——
 * 事件触发时当前服务器实例可能还没挂上（AGENT.md P0-13 的历史教训）。
 */
public final class ReloadEvents {
    private ReloadEvents() {}

    public static final ReloadEvents INSTANCE = new ReloadEvents();

    @SubscribeEvent
    public void onTagsUpdated(TagsUpdatedEvent event) {
        TreeIndex.rebuild(event.getRegistryAccess());
    }
}
