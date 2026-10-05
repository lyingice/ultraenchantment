package com.lyingice.ultraenchantment.datagen;

import com.lyingice.ultraenchantment.Ultraenchantment;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * 数据生成入口。
 *
 * <p>由 {@code Ultraenchantment} 构造器挂到 mod bus（{@link GatherDataEvent} 实现
 * {@code IModBusEvent}，挂到 game bus 永远不会触发且不报错）。仅在 {@code runData} 时执行。
 *
 * <p>这里**不按 includeClient()/includeServer() 过滤**：在 1.21.1 的
 * {@code net.minecraft.data.Main} 单入口下，各 provider 无条件注册是安全的，
 * 按开关过滤反而会让它们全部被跳过。
 */
public final class UEDataGen {
    private UEDataGen() {}

    public static void onGatherData(GatherDataEvent event) {
        Ultraenchantment.LOGGER.debug("Gathering generated assets for {}", event.getMods());

        // ── 资源包 ──
        event.addProvider(new UEModels(event.getGenerator().getPackOutput(), event.getExistingFileHelper()));
        // 语言：静态键 + 阶梯名走**同一个**提供器。
        // ⚠️ 同 locale 开两个 LanguageProvider 会抛 Duplicate provider（P1-19）。
        event.addProvider(new UELang(event.getGenerator().getPackOutput(), "en_us", false));
        event.addProvider(new UELang(event.getGenerator().getPackOutput(), "zh_cn", true));

        // ── 数据包 ──
        // 阶段条目的 effects 是「原版附魔效果表原样搬用 + 阶级加成」，所以需要
        // minecraft:enchantment 这份数据包注册表。它由 DatagenModLoader 用
        // CompletableFuture.supplyAsync(VanillaRegistries::createLookup, ...) 在后台线程构造，
        // 内部只有纯计算、不依赖主线程，因此 UEStagePack 里 join 只会等它算完，不会死锁。
        //
        // ⚠️ 这份 lookup 必须是「读」与「写」共用的同一份：UEStagePack 用它建 RegistryOps，
        // 引用的 holder 才与序列化上下文的 owner 一致。细节见 UEStages / UEStagePack 的类文档。
        //
        // 落点 data/ultraenchantment/ultraenchantment/enchantment/{advanced,super,ultra}/
        // （数据包注册表的路径是「命名空间 + 注册表路径段」，见 AGENT.md P0-8）。
        event.addProvider(new UEStagePack(event.getGenerator().getPackOutput(), event.getLookupProvider()));

        // 效果总表：把「每一阶有什么效果、数值多少」导出成 markdown，落在仓库 docs/ 下。
        // 它是开发资料，不进 jar（不在 src/generated/resources 里），但必须是生成物——
        // 「原版基础效果」只有从注册表读才知道，手写必然与原版漂移。
        event.addProvider(new UEEffectDoc(event.getGenerator().getPackOutput(), event.getLookupProvider()));

        // 战利品：进阶附魔书的全局掉落挂载。
        // 与其它数据包提供器同处一个 DataGenerator 事件里（P0-12 的约束）。
        event.addProvider(new UEGlobalLootModifiers(event.getGenerator().getPackOutput(), event.getLookupProvider()));
    }
}
