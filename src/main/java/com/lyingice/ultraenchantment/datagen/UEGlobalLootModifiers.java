package com.lyingice.ultraenchantment.datagen;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.logic.loot.LootTierWeights;
import com.lyingice.ultraenchantment.logic.loot.ULTBookLootModifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.data.GlobalLootModifierProvider;
import net.neoforged.neoforge.common.loot.LootTableIdCondition;

/**
 * 生成进阶附魔书的战利品挂载。
 *
 * <h2>产物</h2>
 *
 * <pre>
 *   data/neoforge/loot_modifiers/global_loot_modifiers.json   <- 索引（NeoForge 读它）
 *   data/ultraenchantment/loot_modifiers/&lt;name&gt;.json          <- 各修改器实例
 * </pre>
 *
 * <p>路径由 {@code GlobalLootModifierProvider} 内部决定——注意是 {@code neoforge}
 * 命名空间而不是 {@code forge}（1.21.1 源码实证）。
 *
 * <h2>来源分级</h2>
 *
 * <p>按「表 id -> 来源」分三组，每组一个 chance：
 * <ul>
 *   <li><b>普通箱子</b>：村庄 / 地牢 / 矿井 / 要塞 —— 究极<b>不产出</b>（权重为 0）</li>
 *   <li><b>稀有箱子</b>：末地城 / 林地府邸 / 堡垒遗迹</li>
 *   <li><b>BOSS</b>：末影龙 / 凋灵</li>
 * </ul>
 *
 * <p>「哪张表算稀有」写在这里（是数据），而不是硬编码进 {@code ULTBookLootModifier}
 * ——这样调整掉落口味只需要改这个清单。
 */
public final class UEGlobalLootModifiers extends GlobalLootModifierProvider {

    /**
     * 普通箱子：高阶为主，超级少量，<b>究极不产出</b>。
     *
     * <p>这些是玩家最容易接触到的箱子，也是「高阶获取难度适中」的实现处。
     */
    private static final List<String> COMMON_TABLES = List.of(
            "minecraft:chests/simple_dungeon",
            "minecraft:chests/abandoned_mineshaft",
            "minecraft:chests/stronghold_corridor",
            "minecraft:chests/stronghold_crossing",
            "minecraft:chests/stronghold_library",
            "minecraft:chests/village/village_armorer",
            "minecraft:chests/village/village_toolsmith",
            "minecraft:chests/village/village_weaponsmith",
            "minecraft:chests/village/village_temple",
            "minecraft:chests/village/village_cartographer");

    /** 稀有箱子：三阶都可能，究极开始出现但概率很低。 */
    private static final List<String> RARE_TABLES = List.of(
            "minecraft:chests/end_city_treasure",
            "minecraft:chests/woodland_mansion",
            "minecraft:chests/bastion_treasure",
            "minecraft:chests/bastion_other",
            "minecraft:chests/ancient_city");

    /** BOSS 掉落：究极的主要来源。 */
    private static final List<String> BOSS_TABLES = List.of(
            "minecraft:entities/ender_dragon",
            "minecraft:entities/wither");

    /** 普通箱子每次的产出概率。 */
    private static final double COMMON_CHANCE = 0.15D;
    /** 稀有箱子每次的产出概率。 */
    private static final double RARE_CHANCE = 0.15D;
    /** BOSS 每次的产出概率。 */
    private static final double BOSS_CHANCE = 0.15D;

    private final PackOutput output;

    public UEGlobalLootModifiers(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, Ultraenchantment.MODID);
        this.output = output;
    }

    @Override
    protected void start() {
        addGroup("common", COMMON_TABLES, LootTierWeights.LootSource.COMMON, COMMON_CHANCE);
        addGroup("rare", RARE_TABLES, LootTierWeights.LootSource.RARE, RARE_CHANCE);
        addGroup("boss", BOSS_TABLES, LootTierWeights.LootSource.BOSS, BOSS_CHANCE);

        Ultraenchantment.LOGGER.info("[loot] 已生成进阶附魔书掉落：普通 {} 表 / 稀有 {} 表 / BOSS {} 表",
                COMMON_TABLES.size(), RARE_TABLES.size(), BOSS_TABLES.size());
    }

    /**
     * 为一组表各生成一个 modifier 实例。
     *
     * <p><b>每张表一个实例</b>而不是「一个实例配多条件」：条件数组在 NeoForge 里是
     * <b>AND</b> 语义，写成多条件会导致「必须同时命中所有表」这种不可能满足的情况。
     */
    private void addGroup(String groupName, List<String> tables,
                          LootTierWeights.LootSource source, double chance) {
        int index = 0;
        for (String tableId : tables) {
            ResourceLocation id = ResourceLocation.parse(tableId);
            String name = groupName + "_" + (index++) + "_" + id.getPath().replace('/', '_');

            LootItemCondition[] conditions = {
                    LootTableIdCondition.builder(id).build()
            };
            // ⚠️ chance 写进 modifier 自己的字段，而不是再加一个 random_chance 条件：
            //    条件的 OR/AND 语义容易写错，放进 doApply 里由我们显式控制更直观。
            add(name, ULTBookLootModifier.of(source, chance, conditions), List.of());
        }
    }

    /** 供探针与文档列出所有被挂载的表（避免两处清单漂移）。 */
    public static List<String> allTables() {
        List<String> all = new ArrayList<>();
        all.addAll(COMMON_TABLES);
        all.addAll(RARE_TABLES);
        all.addAll(BOSS_TABLES);
        return List.copyOf(all);
    }
}
