package com.lyingice.ultraenchantment.datagen;

import com.lyingice.ultraenchantment.registry.UEBlocks;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

/**
 * 方块掉落表生成。
 *
 * <p>两个方块都<b>掉落自身</b>。图书馆的库存由 {@code EnchantmentLibraryBlock.getDrops}
 * 在掉落物上附加 {@code block_entity_data} 组件（不走战利品表），
 * 因此这里只需要「掉自己」这一条。
 *
 * <p>{@code getKnownBlocks} 只列我们自己的方块——否则提供器会尝试为原版方块
 * 生成掉落表并因缺少定义而报错。
 */
public class UEBlockLoot extends BlockLootSubProvider {

    public UEBlockLoot(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        this.dropSelf(UEBlocks.ENCHANTMENT_LIBRARY.get());
        this.dropSelf(UEBlocks.ASCENSION_TABLE.get());
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return UEBlocks.BLOCKS.getEntries().stream().map(Holder::value).toList();
    }
}
