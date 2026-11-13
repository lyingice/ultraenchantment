package com.lyingice.ultraenchantment.datagen;

import java.util.List;
import java.util.Set;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * 数据生成入口——Lightstar 只生成三样：
 * <ol>
 *   <li>附魔书的三档物品模型（{@link UEModels}）；</li>
 *   <li>中英语言键（{@link UELang}）；</li>
 *   <li>星光附魔台的方块掉落（{@link UEBlockLoot}）。</li>
 * </ol>
 *
 * <p>星光的内容数据（阶段附魔 / 阶级 / 进化树）是手写数据包 JSON，不在这里生成。
 */
public final class UEDataGen {
    private UEDataGen() {}

    public static void onGatherData(GatherDataEvent event) {
        event.addProvider(new UEModels(event.getGenerator().getPackOutput(), event.getExistingFileHelper()));
        event.addProvider(new UELang(event.getGenerator().getPackOutput(), "en_us", false));
        event.addProvider(new UELang(event.getGenerator().getPackOutput(), "zh_cn", true));

        event.addProvider(new LootTableProvider(
                event.getGenerator().getPackOutput(),
                Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(UEBlockLoot::new, LootContextParamSets.BLOCK)),
                event.getLookupProvider()));
    }
}
