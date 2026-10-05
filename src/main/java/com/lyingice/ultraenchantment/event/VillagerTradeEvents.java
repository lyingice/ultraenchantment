package com.lyingice.ultraenchantment.event;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.logic.trade.UEProfessionTrades;
import com.lyingice.ultraenchantment.registry.UEProfessions;
import java.util.List;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

/**
 * 附魔进阶师的交易表挂载。
 *
 * <h2>为什么是事件而不是数据包</h2>
 *
 * <p>1.21.1 <b>没有</b>村民交易的数据包形态——不像 1.21.2+ 有
 * {@code villager_trade} / {@code trade_set} 注册表。实测把客户端资源 jar 里
 * {@code data/minecraft/} 的一级目录全列一遍，只有
 * {@code advancement / enchantment / enchantment_provider / loot_table / recipe / ...}，
 * 没有任何交易目录；{@code Villager.updateTrades()} 读的是 Java 静态表
 * {@code VillagerTrades.TRADES}。
 *
 * <p>因此唯一入口是 NeoForge 的 {@link VillagerTradesEvent}
 * （在 {@code TagsUpdatedEvent} 期间、按<b>每个已注册职业</b>各发一次）。
 *
 * <h2>⚠️ 必须按职业过滤</h2>
 *
 * <p>该事件会为<b>所有</b>职业各发一次，包括农民、图书管理员等。
 * 不过滤就等于给每个村民都塞上我们的书。
 *
 * <h2>等级划分</h2>
 *
 * <p>学徒~专家（1-4 级）→ 铭刻书 + 定向书；大师（5 级）→ 通用进阶书 + 通用升级书。
 * 原版 {@code updateTrades()} 每级从列表里<b>抽 2 条</b>
 * （{@code addOffersFromItemListings(offers, listings, 2)}），所以每级给 2 条即可。
 */
public final class VillagerTradeEvents {
    private VillagerTradeEvents() {}

    /** 单例监听器，供 game bus 注册。 */
    public static final VillagerTradeEvents INSTANCE = new VillagerTradeEvents();

    @SubscribeEvent
    public void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != UEProfessions.ADVANCED_ENCHANTER.get()) {
            return;
        }

        var trades = event.getTrades();

        // 学徒（1）~ 专家（4）：铭刻书 + 定向书，各一条。
        for (int level = 1; level <= 4; level++) {
            List<VillagerTrades.ItemListing> listings = trades.get(level);
            if (listings == null) {
                continue;
            }
            listings.add(new UEProfessionTrades.RandomInscription(level));
            listings.add(new UEProfessionTrades.RandomTargetedAscension(level));
        }

        // 大师（5）：通用进阶书 + 通用升级书。
        List<VillagerTrades.ItemListing> master = trades.get(5);
        if (master != null) {
            master.add(new UEProfessionTrades.GenericAscension());
            master.add(new UEProfessionTrades.GenericUpgrade());
        }

        Ultraenchantment.LOGGER.info("[TRADE] 已为附魔进阶师挂载交易：1-4 级 {} 条，5 级 {} 条",
                4 * 2, master == null ? 0 : 2);
    }
}
