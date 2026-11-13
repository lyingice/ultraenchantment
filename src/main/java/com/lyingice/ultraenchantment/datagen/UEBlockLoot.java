package com.lyingice.ultraenchantment.datagen;

import com.lyingice.ultraenchantment.registry.UEBlocks;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

/**
 * 方块掉落表——星光附魔台掉自己。
 */
public class UEBlockLoot extends BlockLootSubProvider {

    public UEBlockLoot(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        this.dropSelf(UEBlocks.STARLIGHT_TABLE.get());
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return UEBlocks.BLOCKS.getEntries().stream().map(Holder::value).toList();
    }
}
