package com.lyingice.ultraenchantment.registry;

import com.lyingice.ultraenchantment.Ultraenchantment;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 物品注册——Lightstar 只剩星光附魔台的方块物品。
 *
 * <p>附魔书使用原版 {@code minecraft:enchanted_book}，不再注册自定义书物品。
 * 书皮由客户端 Mixin 根据书上存储的阶段附魔所属阶级替换。
 */
public final class UEItems {
    private UEItems() {}

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, Ultraenchantment.MODID);

    /** 星光附魔台的方块物品（史诗品质）。 */
    public static final DeferredHolder<Item, BlockItem> STARLIGHT_TABLE =
            ITEMS.register("starlight_table",
                    () -> new BlockItem(UEBlocks.STARLIGHT_TABLE.get(),
                            new Item.Properties().rarity(Rarity.EPIC)));
}
