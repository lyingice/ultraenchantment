package com.lyingice.ultraenchantment.registry;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.starlight.block.StarlightTableBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 方块注册——Lightstar 只保留星光附魔台。
 */
public final class UEBlocks {
    private UEBlocks() {}

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, Ultraenchantment.MODID);

    /** 星光附魔台——进化图的交互载体，无方块实体（物品槽由菜单自持）。 */
    public static final DeferredHolder<Block, StarlightTableBlock> STARLIGHT_TABLE =
            BLOCKS.register("starlight_table", StarlightTableBlock::new);

    /** 星光台属性：照原版附魔台的硬度/抗爆/音效。 */
    public static BlockBehaviour.Properties tableProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_RED)
                .strength(5.0F, 1200.0F)
                .sound(SoundType.STONE);
    }
}
