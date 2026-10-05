package com.lyingice.ultraenchantment.registry;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.logic.loot.ULTBookLootModifier;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 全局战利品修改器的<b>序列化器</b>注册。
 *
 * <h2>注册的是什么</h2>
 *
 * <p>这里注册的不是「修改器实例」（那些写在数据包的 JSON 里），
 * 而是它的 {@link MapCodec}——告诉游戏「{@code ultraenchantment:ultra_book}
 * 这个类型名该怎么解析」。
 *
 * <p>{@link NeoForgeRegistries#GLOBAL_LOOT_MODIFIER_SERIALIZERS} 是 NeoForge
 * 自己的注册表（不在 {@code BuiltInRegistries} 里），因此必须用
 * {@link DeferredRegister} 且指定该键——与 {@code UEProfessions} 同一范式。
 */
public final class UELootModifiers {
    private UELootModifiers() {}

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Ultraenchantment.MODID);

    /** 进阶附魔书掉落。JSON 里写作 {@code "type": "ultraenchantment:ultra_book"}。 */
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<ULTBookLootModifier>>
            ULTRA_BOOK = SERIALIZERS.register("ultra_book", () -> ULTBookLootModifier.CODEC);

    public static void register(net.neoforged.bus.api.IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
