package com.lyingice.ultraenchantment.registry;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.starlight.block.StarlightTableBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 方块实体注册——只保留星光台（悬浮书动画状态）。
 */
public final class UEBlockEntities {
    private UEBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Ultraenchantment.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StarlightTableBlockEntity>> STARLIGHT_TABLE =
            BLOCK_ENTITIES.register("starlight_table", () -> BlockEntityType.Builder
                    .of(StarlightTableBlockEntity::new, UEBlocks.STARLIGHT_TABLE.get())
                    .build(null));
}
