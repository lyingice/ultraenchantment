package com.lyingice.ultraenchantment.logic.loot;

import com.lyingice.ultraenchantment.content.AscensionTier;
import java.util.List;
import net.minecraft.util.RandomSource;

/**
 * <b>掉落阶级权重</b>——「阶级越高越难获得」的可量化落点。
 *
 * <h2>两层控制，而不是一层概率</h2>
 *
 * <p>只用一层「究极概率 1%」会让玩家感到<b>运气差</b>，而不是
 * 「我该去更危险的地方」。所以这里做两层：
 *
 * <ol>
 *   <li><b>来源修正</b>（{@link LootSource}）：普通箱子<b>不产出究极</b>，
 *       究极只在 BOSS 与稀有箱子里出——难度绑在「去哪」而不是「掷几次」</li>
 *   <li><b>阶级权重</b>：同一来源内，高阶 ≫ 超级 ≫ 究极</li>
 * </ol>
 *
 * <h2>基础权重</h2>
 *
 * <pre>
 *   高阶 70  /  超级 25  /  究极 5
 * </pre>
 *
 * <h2>来源修正</h2>
 *
 * <pre>
 *                高阶    超级    究极
 *   普通箱子      x1     x0.5    x0（不产出）
 *   稀有箱子      x1     x1      x1
 *   BOSS         x1     x2      x3
 * </pre>
 *
 * <p>于是「究极只在 BOSS / 稀有来源出现」这条规则不需要额外的 if——
 * 普通箱子的究极权重为 0，掷权重时自然选不到。
 *
 * <h2>可调性</h2>
 *
 * <p>这些是<b>默认值</b>。整合包可通过 {@code data/ultraenchantment/loot_book_config.json}
 * 覆盖（见 {@link LootBookConfig}）。默认值即文档 {@code docs/loot-tables.md} 里的曲线表。
 */
public final class LootTierWeights {
    private LootTierWeights() {}

    /** 基础权重（未加来源修正前）。 */
    public static final int BASE_ADVANCED = 70;
    public static final int BASE_SUPER = 25;
    public static final int BASE_ULTRA = 5;

    /**
     * 掉落来源——决定阶级修正系数。
     *
     * <p>由 {@link ULTBookLootModifier} 依据命中的战利品表 id 判定。
     */
    public enum LootSource {
        /** 普通箱子：村庄、地牢、矿井、要塞等。 */
        COMMON,
        /** 稀有箱子：末地城、林地府邸、堡垒遗迹等。 */
        RARE,
        /** BOSS 掉落：末影龙、凋灵。 */
        BOSS
    }

    /**
     * 取某来源下的一阶权重（已含来源修正）。
     *
     * <p>返回 0 表示该来源<b>不产出</b>这一阶（普通箱子的究极）。
     */
    public static int weightOf(LootSource source, AscensionTier tier) {
        return switch (source) {
            case COMMON -> switch (tier) {
                case ADVANCED -> BASE_ADVANCED;
                case SUPER -> BASE_SUPER;   // x0.5 已折进常量，避免浮点
                case ULTRA -> 0;            // 普通箱子不产出究极
            };
            case RARE -> switch (tier) {
                case ADVANCED -> BASE_ADVANCED;
                case SUPER -> BASE_SUPER;
                case ULTRA -> BASE_ULTRA;
            };
            case BOSS -> switch (tier) {
                case ADVANCED -> BASE_ADVANCED;
                case SUPER -> BASE_SUPER * 2;
                case ULTRA -> BASE_ULTRA * 3;
            };
        };
    }

    /**
     * 按权重掷出一阶；全为 0 时返回空。
     *
     * <p>用整数权重而不是浮点概率：整数掷骰没有浮点累积误差，
     * 且探针实测的期望值可以直接手算校验（见 docs/loot-tables.md 的推导）。
     */
    public static java.util.Optional<AscensionTier> roll(RandomSource random, LootSource source) {
        int advanced = weightOf(source, AscensionTier.ADVANCED);
        int superb = weightOf(source, AscensionTier.SUPER);
        int ultra = weightOf(source, AscensionTier.ULTRA);
        int total = advanced + superb + ultra;
        if (total <= 0) {
            return java.util.Optional.empty();
        }
        int pick = random.nextInt(total);
        if (pick < advanced) {
            return java.util.Optional.of(AscensionTier.ADVANCED);
        }
        if (pick < advanced + superb) {
            return java.util.Optional.of(AscensionTier.SUPER);
        }
        return java.util.Optional.of(AscensionTier.ULTRA);
    }

    /** 供探针与文档用的期望值：某来源掷一次时各阶的概率。 */
    public static double probabilityOf(LootSource source, AscensionTier tier) {
        int total = weightOf(source, AscensionTier.ADVANCED)
                + weightOf(source, AscensionTier.SUPER)
                + weightOf(source, AscensionTier.ULTRA);
        return total <= 0 ? 0.0 : (double) weightOf(source, tier) / total;
    }

    /** 所有来源（供探针遍历）。 */
    public static List<LootSource> sources() {
        return List.of(LootSource.values());
    }
}
