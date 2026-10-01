package com.lyingice.ultraenchantment.logic;

import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * <b>按「谁生成、就用谁的注册表」取附魔注册表</b>——写入物品组件时必须用它。
 *
 * <h2>为什么不能只用 {@link CommonHooks#resolveLookup}</h2>
 *
 * <p>{@code CommonHooks.resolveLookup} 的第一优先级是
 * {@code ServerLifecycleHooks.getCurrentServer()}。在<b>单机（集成服务器）</b>里，
 * 那台服务器一直存在，于是在<b>客户端线程</b>上它也会返回<b>服务端</b>的注册表——
 * 里面的 {@code Holder.Reference} 属于服务端那张 ID 表。
 *
 * <p>而物品组件最终是<b>按生成它的那一侧</b>去序列化的：
 * <ul>
 *   <li>客户端本地生成的栈（铁砧结果槽）→ 由 {@code serverbound/container_click} 发出，用<b>客户端</b>注册表；</li>
 *   <li>服务端生成的栈 → 由容器同步包发出，用<b>服务端</b>注册表（集成服务器的服务端线程也一样）。</li>
 * </ul>
 *
 * <p>把服务端 Holder 写进客户端栈，客户端一发包就是
 * {@code IllegalArgumentException: Can't find id for 'Reference{...}' in map}
 * → {@code EncoderException: Failed to encode packet} → <b>玩家掉线</b>。
 * 真正的现场与完整栈见 docs/book-system-spec.md §17.4。
 *
 * <p>另外：本类<b>不能</b>无条件引用 {@code ClientHooks}——那是客户端专属类，
 * 专用服务器上会 {@code NoClassDefFoundError}（P0-16）。所以客户端分支被包在
 * 一个独立的嵌套类里，只有真的在客户端执行到时才会被 JVM 加载。
 */
public final class UELookups {
    private UELookups() {}

    /**
     * 取「写进物品组件」该用的附魔注册表。
     *
     * @param clientSide 生成这个栈的一侧是不是<b>逻辑客户端</b>
     *                   （{@code level.isClientSide()}／{@code player.level().isClientSide()}）。
     *                   注意集成服务器上两侧同时在跑：服务端线程传 {@code false}。
     */
    @Nullable
    public static HolderLookup.RegistryLookup<Enchantment> enchantmentsForItemWrites(boolean clientSide) {
        if (clientSide && FMLEnvironment.dist.isClient()) {
            HolderLookup.RegistryLookup<Enchantment> client = ClientOnly.enchantments();
            if (client != null) {
                return client;
            }
        }
        return CommonHooks.resolveLookup(Registries.ENCHANTMENT);
    }

    /**
     * 附魔注册表<b>本体</b>（不是 lookup）——需要一个「值 → key」反查时用。
     *
     * <p>{@code minecraft:enchantment} 是<b>数据包注册表</b>，不在 {@code BuiltInRegistries} 里，
     * 必须从对应当前侧的 registry access 取。没有服务器时（标题界面等）返回 {@code null}：
     * 我们只覆盖服务端那条崩服路径，客户端不做通用判定。
     */
    @Nullable
    public static Registry<Enchantment> enchantmentRegistry() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        return server.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
    }

    /** 只在逻辑客户端被加载（引用客户端专属的 {@code ClientHooks}）。 */
    private static final class ClientOnly {
        static HolderLookup.RegistryLookup<Enchantment> enchantments() {
            return net.neoforged.neoforge.client.ClientHooks.resolveLookup(Registries.ENCHANTMENT);
        }
    }
}
