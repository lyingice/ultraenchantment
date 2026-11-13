package com.lyingice.ultraenchantment.starlight.registry;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.starlight.content.TierDefinition;
import com.lyingice.ultraenchantment.starlight.content.TreeDefinition;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * 星光系统的数据包注册表。
 *
 * <p>只有两个：阶级（tier）与进化树（tree）。
 * <b>阶段附魔不在这里</b>——它们是真正的 {@code minecraft:enchantment} 条目，
 * 由内容包以标准附魔 JSON 提供。
 *
 * <p>目录规则（见 AGENT.md P0-8）：
 * <pre>
 * data/&lt;ns&gt;/ultraenchantment/starlight_tier/&lt;id&gt;.json
 * data/&lt;ns&gt;/ultraenchantment/starlight_tree/&lt;id&gt;.json
 * </pre>
 */
public final class StarlightRegistries {
    private StarlightRegistries() {}

    public static final ResourceKey<Registry<TierDefinition>> TIER = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(Ultraenchantment.MODID, "starlight_tier"));

    public static final ResourceKey<Registry<TreeDefinition>> TREE = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(Ultraenchantment.MODID, "starlight_tree"));

    /** 在 {@code DataPackRegistryEvent.NewRegistry} 里调用。第三个参数必须是 Codec（不是 StreamCodec）。 */
    public static void register(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(TIER, TierDefinition.CODEC, TierDefinition.CODEC);
        event.dataPackRegistry(TREE, TreeDefinition.CODEC, TreeDefinition.CODEC);
        Ultraenchantment.LOGGER.info("Registered starlight registries: {} / {}",
                TIER.location(), TREE.location());
    }
}
