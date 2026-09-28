package com.lyingice.ultraenchantment.registry;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.content.StageDefinition;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * 模组的自定义数据包注册表。
 *
 * <p><b>唯一的注册表</b>：{@code ultraenchantment:enchantment}。
 * 它承载全部阶段条目（{@link StageDefinition}），阶级由条目自身的 {@code tier} 字段表达，
 * **不拆成多个注册表**。
 *
 * <h2>数据包目录路径</h2>
 *
 * <p>路径公式（源码实证，见 {@code Registries.elementsDirPath} →
 * {@code CommonHooks.prefixNamespace}）：
 * <pre>
 * data / &lt;条目命名空间&gt; / prefixNamespace(注册表键) / &lt;条目路径&gt;.json
 *
 * prefixNamespace(k) = k.namespace.equals("minecraft") ? k.path
 *                                                      : k.namespace + "/" + k.path
 * </pre>
 *
 * <p><b>{@code minecraft} 是特例——命名空间被丢弃</b>，所以原版附魔的注册表键
 * {@code minecraft:enchantment} 只产生一层：
 * <pre>
 * data/minecraft/enchantment/sharpness.json
 * </pre>
 *
 * <p><b>非 minecraft 命名空间的注册表，中间层必然 ≥ 两层</b>
 * （命名空间与路径被 {@code /} 拼接）。因此精确复制原版的「单层」形态对模组注册表
 * 在数学上不可能——只能让<b>路径段与原版同名</b>，视觉上与原版对齐：
 * <pre>
 * data/minecraft/enchantment/sharpness.json                                   ← 原版
 * data/ultraenchantment/ultraenchantment/enchantment/advanced/sharpness.json  ← 本模组
 *                       └── 模组数据包前缀 ──┘ └ 与原版同名 ┘ └ 阶段 ┘
 * </pre>
 *
 * <p>注册表键取 {@code ultraenchantment:enchantment}（而非 {@code :ultraenchantment}），
 * 就是为了让中间层的路径段是 {@code enchantment} —— 与原版同名，读起来一致。
 *
 * <p><b>注册动作</b>在 {@link UEDataPackRegistries}（mod bus 事件，
 * 必须传非 null 的 networkCodec 同步到客户端，否则客户端侧结算层读不到数据）。
 */
public final class UERegistries {
    private UERegistries() {}

    /**
     * 注册表路径段。
     *
     * <p>取 {@code enchantment} 与原版附魔注册表同名，使目录的中间层与原版对齐。
     */
    public static final String STAGE_PATH = "enchantment";

    /** 注册表 ID：{@code ultraenchantment:enchantment}。 */
    public static final ResourceLocation STAGE_REGISTRY_ID =
            ResourceLocation.fromNamespaceAndPath(Ultraenchantment.MODID, STAGE_PATH);

    /** 阶段条目的注册表键。 */
    public static final ResourceKey<Registry<StageDefinition>> STAGE =
            ResourceKey.createRegistryKey(STAGE_REGISTRY_ID);

    /** 由条目路径（如 {@code advanced/sharpness}）构造注册表键。 */
    public static ResourceKey<StageDefinition> key(ResourceLocation id) {
        return ResourceKey.create(STAGE, id);
    }
}
