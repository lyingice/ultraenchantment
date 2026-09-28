package com.lyingice.ultraenchantment.datagen;

import com.google.gson.JsonElement;
import com.lyingice.ultraenchantment.content.StageDefinition;
import com.lyingice.ultraenchantment.registry.UERegistries;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;

/**
 * 阶段条目的数据包写出器。
 *
 * <h2>为什么不用 {@code DatapackBuiltinEntriesProvider}</h2>
 *
 * <p>那个提供器会把 {@code RegistrySetBuilder} 的 <b>patch provider</b> 交给
 * {@code RegistriesDatapackGenerator} 做序列化，而 patch provider 的 holder owner 是它自己
 * 新建的 {@code UniversalOwner}。阶段条目的效果表是从<b>原版附魔</b>整段搬来的，
 * 里面带着属于外部 provider 的 {@code Holder}（如荆棘的 {@code minecraft:thorns} 伤害类型），
 * 于是 {@code RegistryFileCodec#encode} 里 {@code holder.canSerializeIn(owner)} 为 false，
 * 直接报 {@code Element ... is not valid in current registry set}。
 * 换成 {@code PatchedRegistries.full()} 也没用——{@code Cloner.clone} 同样是
 * 「用源 provider 编码」，会在克隆阶段以同样的理由失败。
 *
 * <p>所以这里自己写出，并且<b>用与读取时同一份 provider</b> 建 {@code RegistryOps}：
 * 引用谁，就用谁做序列化上下文，owner 天然一致。代价是：
 * <ul>
 *   <li>不支持 NeoForge 的 {@code ICondition}（本模组不需要条件化条目）</li>
 *   <li>没有 builder 的重复注册检查——由 {@link UEStages#generate} 的自检补齐</li>
 * </ul>
 *
 * <p>输出的 JSON 形态与原先完全一致（不带条件包装），见
 * {@code data/ultraenchantment/ultraenchantment/enchantment/<tier>/<附魔>.json}。
 */
public final class UEStagePack implements DataProvider {
    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public UEStagePack(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        this.output = output;
        this.registries = registries;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return this.registries.thenCompose(provider -> {
            // 与 UEStages.generate 用的是同一份 provider —— 这不是巧合，是必要条件。
            RegistryOps<JsonElement> ops = provider.createSerializationContext(JsonOps.INSTANCE);

            List<CompletableFuture<?>> writes = new ArrayList<>();
            for (UEStages.GeneratedStage generated : UEStages.generate(provider)) {
                ResourceLocation id = generated.id();
                DataResult<JsonElement> encoded = StageDefinition.CODEC.encodeStart(ops, generated.stage());
                JsonElement json = encoded.getOrThrow(error ->
                        new IllegalStateException("阶段条目 " + id + " 序列化失败：" + error));

                writes.add(DataProvider.saveStable(cache, json, pathOf(id)));
            }

            return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
        });
    }

    @Override
    public String getName() {
        return "Ultra Enchantment stage definitions";
    }

    /**
     * 条目落盘路径，公式与 {@code Registries.elementsDirPath} 一致（见 AGENT.md P0-8）：
     * <pre>
     * data / &lt;条目命名空间&gt; / prefixNamespace(注册表键) / &lt;条目路径&gt;.json
     *
     * prefixNamespace(k) = k.namespace.equals("minecraft") ? k.path
     *                                                      : k.namespace + "/" + k.path
     * </pre>
     *
     * <p>本模组注册表键是 {@code ultraenchantment:enchantment}，所以中间层是两层
     * {@code ultraenchantment/enchantment}，最终形如
     * {@code data/ultraenchantment/ultraenchantment/enchantment/super/sharpness.json}。
     */
    private Path pathOf(ResourceLocation id) {
        ResourceLocation registryId = UERegistries.STAGE_REGISTRY_ID;
        String prefixNamespace = registryId.getNamespace().equals("minecraft")
                ? registryId.getPath()
                : registryId.getNamespace() + "/" + registryId.getPath();

        return this.output.getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve(id.getNamespace())
                .resolve(prefixNamespace)
                .resolve(id.getPath() + ".json");
    }
}
