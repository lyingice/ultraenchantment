package com.lyingice.ultraenchantment.event;

import com.lyingice.ultraenchantment.UEConfig;
import com.lyingice.ultraenchantment.content.UEEffect;
import com.lyingice.ultraenchantment.logic.UEEffectExecution;
import com.lyingice.ultraenchantment.logic.UEEffectLookup;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/**
 * <b>自动拾取</b>（`ue:auto_pickup`）——自己丢出/死亡掉落的物品立刻回到背包。
 *
 * <h2>两个入口</h2>
 *
 * <ul>
 *   <li>{@link ItemTossEvent}：玩家<b>主动丢弃</b>（Q 键、拖出背包）。
 *       若身上有该效果，直接取消掉落并尝试放回背包，失败才真的落地。</li>
 *   <li>{@link ItemEntityPickupEvent.Pre}：物品已经在地上、原主人走回来。
 *       这时不阻止（原版也会拾取），只是当原主人身上有该效果时<b>允许立刻拾取</b>——
 *       即便原版因 {@code pickupDelay} 或 {@code target} 暂时不允许，
 *       被承认的主人仍可即时回收。</li>
 * </ul>
 *
 * <h2>为什么不做「死亡掉落回收」</h2>
 *
 * <p>死亡掉落不属于「丢出」，且{@code dropAllDeathLoot} 阶段物品还未生成实体，
 * 硬拦会与其它模组（墓碑等）冲突。真要做死亡保护，那是另一条独立需求，
 * 不该塞进「自动拾取」——保持这一条效果的语义单纯：<b>掉出的东西被原主人立刻拾回</b>。
 */
public final class UEPickupEvents {
    private UEPickupEvents() {}

    /** 单例监听器。 */
    public static final UEPickupEvents INSTANCE = new UEPickupEvents();

    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        if (UEEffectExecution.disabled()) {
            return;
        }
        Player player = event.getPlayer();
        // ItemTossEvent 只在服务端有意义地处理玩家丢弃。
        if (player.level().isClientSide() || !UEEffectLookup.usablePlayer(player)) {
            return;
        }
        if (!hasAutoPickup(player)) {
            return;
        }

        ItemEntity item = event.getEntity();
        if (item.getItem().isEmpty()) {
            return;
        }

        // 尝试直接放回背包：成功则取消掉落（物品根本不落地）。
        if (player.getInventory().add(item.getItem().copy())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPickupPre(ItemEntityPickupEvent.Pre event) {
        if (UEEffectExecution.disabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.level().isClientSide() || !UEEffectLookup.usablePlayer(player)) {
            return;
        }
        if (!hasAutoPickup(player)) {
            return;
        }

        ItemEntity item = event.getItemEntity();
        // 只优化「原主人回收自己的东西」：别人的掉落不走这条，避免变成磁铁。
        if (item.getOwner() == null || !item.getOwner().getUUID().equals(player.getUUID())) {
            return;
        }
        // 距离检查：防止隔着半张地图把自己远古的掉落吸回来。
        double radius = UEConfig.AUTO_PICKUP_RADIUS.get();
        if (player.distanceToSqr(item) > radius * radius) {
            return;
        }
        // 主人回收自己的掉落：跳过 pickupDelay / target 的等待，立刻可拾。
        event.setCanPickup(TriState.TRUE);
    }

    /** 玩家任一装备槽上是否有自动拾取效果。 */
    private static boolean hasAutoPickup(Player player) {
        for (UEEffectLookup.Resolved entry : UEEffectLookup.fromEquipment(player)) {
            if (entry.effect() instanceof UEEffect.AutoPickup) {
                return true;
            }
        }
        return false;
    }
}
