package com.lyingice.ultraenchantment.registry;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.block.AscensionTableBlock;
import com.lyingice.ultraenchantment.block.EnchantmentLibraryBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 方块注册。
 *
 * <h2>两个方块</h2>
 *
 * <table>
 *   <tr><th>id</th><th>中文名</th><th>职责</th></tr>
 *   <tr><td>{@code ENCHANTMENT_LIBRARY}</td><td>附魔图书馆</td>
 *       <td>按「谱系 × 阶级」存取合并进阶附魔书；解锁图鉴</td></tr>
 *   <tr><td>{@code ascension_table}</td><td>附魔进阶台</td>
 *       <td>阶级 × 等级双轴选择；消耗附近图书馆库存</td></tr>
 * </table>
 *
 * <h2>⚠️ 方块<b>永远注册</b>，不做条件注册</h2>
 *
 * <p>进阶台的配方需要 Enchanting Infuser 的方块作材料，但这<b>不</b>意味着方块本身
 * 应当条件注册。条件注册的后果是：玩家卸载对方模组后，方块 id 消失 →
 * 世界里已放置的方块变成<b>未知方块</b> → 方块丢失，严重时存档报错。
 *
 * <p>正确的门控层次：
 * <ol>
 *   <li><b>方块 + 物品</b> —— 永远注册（本类）</li>
 *   <li><b>配方</b> —— 静态 JSON 里带 {@code neoforge:mod_loaded} 条件</li>
 *   <li><b>创造栏</b> —— 见 {@code CreativeTabEvents}，装了对方模组才投放</li>
 * </ol>
 *
 * <p>配方天然就门控了：中心材料是对方的方块，没装就凑不出来。
 *
 * <h2>方块属性</h2>
 *
 * <p>照原版附魔台：{@code mapColor(COLOR_RED)} + {@code strength(5.0F, 1200.0F)}
 * + {@code sound(STONE)}。抗爆 1200 与附魔台一致（原版附魔台是防爆的）。
 */
public final class UEBlocks {
    private UEBlocks() {}

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, Ultraenchantment.MODID);

    /** 附魔图书馆——带方块实体（存储）。 */
    public static final DeferredHolder<Block, EnchantmentLibraryBlock> ENCHANTMENT_LIBRARY =
            BLOCKS.register("advanced_enchantment_library", EnchantmentLibraryBlock::new);

    /** 附魔进阶台——无方块实体（物品槽由菜单自持，照原版附魔台的做法）。 */
    public static final DeferredHolder<Block, AscensionTableBlock> ASCENSION_TABLE =
            BLOCKS.register("ascension_table", AscensionTableBlock::new);

    /** 两个方块共用的属性。跨包可见——方块类在 {@code block} 包。 */
    public static BlockBehaviour.Properties tableProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_RED)
                .strength(5.0F, 1200.0F)
                .sound(SoundType.STONE);
    }
}
