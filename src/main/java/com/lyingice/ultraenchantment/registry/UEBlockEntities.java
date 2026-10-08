package com.lyingice.ultraenchantment.registry;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.block.entity.AscensionTableBlockEntity;
import com.lyingice.ultraenchantment.block.entity.EnchantmentLibraryBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 方块实体注册。
 *
 * <p>两个方块实体，都不持有物品：
 * <ul>
 *   <li><b>附魔图书馆</b>——存能量与书库存（本来就是仓库）；</li>
 *   <li><b>附魔进阶台</b>——<b>只存悬浮书的动画状态</b>。
 *       它的物品槽仍然由菜单自持（照原版附魔台的 {@code EnchantmentMenu} 做法），
 *       关界面时 {@code AbstractContainerMenu} 会把槽内物品还给玩家。</li>
 * </ul>
 */
public final class UEBlockEntities {
    private UEBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Ultraenchantment.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnchantmentLibraryBlockEntity>> ENCHANTMENT_LIBRARY =
            BLOCK_ENTITIES.register("advanced_enchantment_library", () -> BlockEntityType.Builder
                    .of(EnchantmentLibraryBlockEntity::new, UEBlocks.ENCHANTMENT_LIBRARY.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AscensionTableBlockEntity>> ASCENSION_TABLE =
            BLOCK_ENTITIES.register("ascension_table", () -> BlockEntityType.Builder
                    .of(AscensionTableBlockEntity::new, UEBlocks.ASCENSION_TABLE.get())
                    .build(null));
}
