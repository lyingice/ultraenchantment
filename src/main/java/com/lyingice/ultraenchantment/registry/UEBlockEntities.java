package com.lyingice.ultraenchantment.registry;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.block.entity.EnchantmentLibraryBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 方块实体注册。
 *
 * <p>只有<b>一个</b>方块实体：附魔图书馆。进阶台不需要——
 * 它的物品槽由菜单自持（照原版附魔台的 {@code EnchantmentMenu} 做法），
 * 关界面时原版 {@code AbstractContainerMenu} 会把槽内物品还给玩家。
 */
public final class UEBlockEntities {
    private UEBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Ultraenchantment.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnchantmentLibraryBlockEntity>> ENCHANTMENT_LIBRARY =
            BLOCK_ENTITIES.register("enchantment_library", () -> BlockEntityType.Builder
                    .of(EnchantmentLibraryBlockEntity::new, UEBlocks.ENCHANTMENT_LIBRARY.get())
                    .build(null));
}
