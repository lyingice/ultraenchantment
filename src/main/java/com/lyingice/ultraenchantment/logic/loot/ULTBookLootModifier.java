package com.lyingice.ultraenchantment.logic.loot;

import com.lyingice.ultraenchantment.content.AscensionTier;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * 往战利品里<b>追加</b>一本进阶附魔书。
 *
 * <h2>为什么是 GlobalLootModifier 而不是静态战利品表</h2>
 *
 * <p>书的内容是「随机一条谱系 + 随机等级」，静态 JSON 表达不了
 * （{@code set_components} 只能写固定值；原版 {@code EnchantRandomlyFunction}
 * 又只认原版附魔注册表）。详见 {@link LootBookFactory} 的类文档。
 *
 * <h2>「追加」而不是「替换」</h2>
 *
 * <p>{@code GlobalLootModifier} 拿到的 {@code generatedLoot} 是原版战利品表
 * <b>已经算完</b>的结果，我们只是在末尾 {@code add}。因此：
 * <ul>
 *   <li>不修改任何原版战利品表文件 ⇒ 与其它模组<b>零冲突</b></li>
 *   <li>原版掉落一个不少</li>
 * </ul>
 *
 * <h2>失败一律静默跳过，绝不抛异常</h2>
 *
 * <p>战利品生成发生在世界运行的任意时刻（开箱、杀怪），这里抛异常会连累
 * 整个战利品流程。所以池子为空 / 附魔解析失败 / 权重全为 0 时，
 * 一律<b>原样返回</b>，最多记一条 DEBUG。
 *
 * <h2>来源判定</h2>
 *
 * <p>来源（普通箱子 / 稀有箱子 / BOSS）由<b>配置</b>决定而不是本类硬判：
 * datagen 为每种来源生成一个独立的 modifier 实例，各自的
 * {@code loot_table_id} 条件不同、{@code source} 字段不同（见 {@code UEGlobalLootModifiers}）。
 * 这样「哪张表算稀有」是数据，不是代码。
 */
public class ULTBookLootModifier extends LootModifier {

    public static final MapCodec<ULTBookLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst -> codecStart(inst)
            .and(com.mojang.serialization.Codec.STRING.fieldOf("source")
                    .forGetter(m -> m.source.name()))
            .and(com.mojang.serialization.Codec.DOUBLE.optionalFieldOf("chance", 1.0D)
                    .forGetter(m -> m.chance))
            .apply(inst, (conditions, sourceName, chance) ->
                    new ULTBookLootModifier(conditions, parseSource(sourceName), chance)));

    private final LootTierWeights.LootSource source;

    /** 命中该表后<b>再</b>掷一次的产出概率（0..1）。 */
    private final double chance;

    public ULTBookLootModifier(LootItemCondition[] conditions, LootTierWeights.LootSource source, double chance) {
        super(conditions);
        this.source = source;
        this.chance = chance;
    }

    private static LootTierWeights.LootSource parseSource(String name) {
        try {
            return LootTierWeights.LootSource.valueOf(name);
        } catch (IllegalArgumentException e) {
            // 配置写错时退化为最保守的来源（不产出究极），而不是让整张表加载失败。
            return LootTierWeights.LootSource.COMMON;
        }
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        try {
            RandomSource random = context.getRandom();

            // ① 先掷「这次出不出」。
            if (this.chance < 1.0D && random.nextDouble() >= this.chance) {
                return generatedLoot;
            }

            // ② 再掷阶级（来源修正已内含在权重里）。
            Optional<AscensionTier> tier = LootTierWeights.roll(random, this.source);
            if (tier.isEmpty()) {
                return generatedLoot;
            }

            // ③ 造书。注册表查询从 LootContext 的 level 取（战利品跑在服务端）。
            var lookup = context.getLevel().registryAccess();
            Optional<ItemStack> book = LootBookFactory.roll(random, lookup, tier.get());
            book.ifPresent(generatedLoot::add);
        } catch (Throwable t) {
            // 战利品路径上任何异常都不该扩散出去——宁可这次不掉，也不能毁掉整箱战利品。
            com.lyingice.ultraenchantment.Ultraenchantment.LOGGER.debug(
                    "[loot] 进阶附魔书掉落失败，已跳过：{}", t.toString());
        }
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    /** 供 datagen 与探针使用。 */
    public LootTierWeights.LootSource source() {
        return this.source;
    }

    public double chance() {
        return this.chance;
    }

    /** datagen 用的构造辅助：不写条件数组时的空条件。 */
    public static ULTBookLootModifier of(LootTierWeights.LootSource source, double chance, LootItemCondition... conditions) {
        return new ULTBookLootModifier(conditions, source, chance);
    }
}
