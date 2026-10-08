package com.lyingice.ultraenchantment.logic.trade;

import com.lyingice.ultraenchantment.content.AscensionTier;
import com.lyingice.ultraenchantment.content.BookSpecs;
import com.lyingice.ultraenchantment.content.BookSubject;
import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.logic.BookFactory;
import com.lyingice.ultraenchantment.registry.UEComponents;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.entity.npc.VillagerTrades;

/**
 * 附魔进阶师的三个交易实现。
 *
 * <h2>固定谱系：靠 MerchantOffer 承载，不靠 getOffer 重摇</h2>
 *
 * <p>{@link VillagerTrades.ItemListing#getOffer} 在<b>每次补货、每次打开界面</b>时都可能被调用。
 * 若谱系是「当场摇一个」，同一个交易槽位的内容会在玩家眼皮底下变来变去，
 * 与需求里「每条交易绑定一个具体谱系，谱系固定」直接冲突。
 *
 * <p>正确做法：谱系在 {@code getOffer} 里摇一次，然后<b>焊进返回的
 * {@link MerchantOffer} 的 sell 物品里</b>。{@code MerchantOffer} 生成后会被
 * {@code MerchantOffers} 持有并同步到客户端，其生命周期内稳定；
 * 补货只调 {@code resetUses()}，<b>不会</b>重新 {@code getOffer}
 * （{@code Villager.updateTrades()} 只在升级时 {@code addOffersFromItemListings}）。
 *
 * <p>因此语义是：<b>同一次交易的整个生命周期内谱系与等级固定</b>；
 * 职业升级或重刷交易时重新摇——这与原版附魔书完全一致。
 *
 * <h2>原版常量</h2>
 *
 * <p>{@code maxUses = 12}、{@code priceMultiplier = 0.2F} 照抄
 * {@code EnchantBookForEmeralds}；xp 照抄 {@code XP_LEVEL_n_SELL}（1/5/10/15）。
 *
 * <h2>成本用稀缺资源，不用绿宝石</h2>
 *
 * <p>绿宝石<b>可以靠村民刷</b>（它本身就在原版村民交易表里，该表共 206 种物品），
 * 所以它构不成门槛。本模组的交易成本改用<b>不在该表内、也不可再生</b>的资源：
 *
 * <table>
 *   <tr><th>层级</th><th>成本</th><th>获取途径</th></tr>
 *   <tr><td>1-4 级（铭刻书 / 定向书）</td>
 *       <td>普通书（附魔要求）+ <b>回响碎片 ×{@value #ECHO_SHARD_MIN}..{@value #ECHO_SHARD_MAX}</b></td>
 *       <td>远古城市，不可再生</td></tr>
 *   <tr><td>5 级（通用进阶书 / 通用升级书）</td>
 *       <td><b>下界之星 ×{@value #NETHER_STAR_MIN}..{@value #NETHER_STAR_MAX}</b></td>
 *       <td>凋灵掉落，不可农场</td></tr>
 * </table>
 *
 * <p>数量在 {@code getOffer} 里摇——与原先绿宝石定价同一时机，
 * 所以「同一次交易的整个生命周期内成本固定」这条性质不变。
 */
public final class UEProfessionTrades {
    private UEProfessionTrades() {}

    /** 卖书交易的通用参数（照抄原版附魔书交易）。 */
    public static final int BOOK_MAX_USES = 12;
    public static final float BOOK_PRICE_MULTIPLIER = 0.2F;

    /**
     * <b>大师级</b>（5 级）交易的次数上限。
     *
     * <p>作者要求：最高级的交易只能用 1 次——那两条是「通用进阶书 / 通用升级书」，
     * 不限定谱系、随时可买到，若与原版一样 12 次就太廉价。
     * 1-4 级的铭刻/定向书仍用 {@link #BOOK_MAX_USES}（它们随机限定了谱系）。
     */
    public static final int MASTER_MAX_USES = 1;

    /**
     * <b>非满级</b>交易的辅助成本：<b>回响碎片</b>，数量 2..6（含端点）。
     *
     * <h2>为什么不用绿宝石</h2>
     *
     * <p>绿宝石<b>可以靠村民刷</b>——它本身就在原版村民交易表里（该表共 206 种物品），
     * 所以它构不成门槛。回响碎片只产自<b>远古城市</b>，且<b>不可再生</b>，
     * 也不在原版交易表内 ⇒ 真正需要玩家去探索。
     *
     * <p>数量取 2..6 而不是固定 1：单次交易一次要走 2~6 个，
     * 而一座远古城市的产出有限，这就形成了「能换几次」的实际限制。
     */
    public static final int ECHO_SHARD_MIN = 2;
    public static final int ECHO_SHARD_MAX = 6;

    /**
     * <b>满级</b>交易的唯一成本：<b>下界之星</b>，数量 1..3（含端点）。
     *
     * <p>下界之星只从<b>凋灵</b>掉落、不可农场、也不在原版交易表内——
     * 它是本模组交易体系里门槛最高的一档，正对应「大师级」。
     */
    public static final int NETHER_STAR_MIN = 1;
    public static final int NETHER_STAR_MAX = 3;

    /** 摇一个回响碎片数量（2..6，含端点）。 */
    private static int echoShards(RandomSource random) {
        return ECHO_SHARD_MIN + random.nextInt(ECHO_SHARD_MAX - ECHO_SHARD_MIN + 1);
    }

    /** 摇一个下界之星数量（1..3，含端点）。 */
    private static int netherStars(RandomSource random) {
        return NETHER_STAR_MIN + random.nextInt(NETHER_STAR_MAX - NETHER_STAR_MIN + 1);
    }

    /**
     * 各级交易给出的经验值。下标即职业等级（1..5），0 位占位。
     *
     * <h2>取值依据（原版阈值，源码实证）</h2>
     *
     * <p>{@code VillagerData.NEXT_LEVEL_XP_THRESHOLDS = {0, 10, 70, 150, 250}}，
     * 因此每级所需经验是：<b>1→2 需 10、2→3 需 60、3→4 需 80、4→5 需 100</b>。
     *
     * <p>按「约 3~4 本换一级」反推：
     * <pre>
     *   1→2: 10 / 4  = 2.5  → 3 本（12 &gt;= 10）
     *   2→3: 60 / 18 = 3.33 → 4 本（54 &lt; 60）
     *   3→4: 80 / 25 = 3.2  → 4 本（75 &lt; 80）
     *   4→5: 100 / 32 = 3.1 → 4 本（96 &lt; 100）
     * </pre>
     *
     * <p>v1 用的是 {@code {0, 1, 5, 10, 15, 30}}：1 级每次只给 1 点，
     * 升到 2 级要<b>换 10 本</b>——而 1~4 级的 {@code maxUses} 才 12，几乎把整个配额耗光。
     */
    public static final int[] XP_SELL = {0, 4, 18, 25, 32, 40};

    /** 偏移：把 1..5 级映射到 {@link #XP_SELL} 的下标。 */
    private static int xp(int villagerLevel) {
        int i = Math.max(1, Math.min(5, villagerLevel));
        return XP_SELL[i];
    }

    /**
     * 成本里的「书」——<b>普通书</b>，形状照原版图书管理员。
     *
     * <p>交易形状：<b>一本普通书 + 一种稀有货币 → 一本进阶附魔书</b>。
     * 与原版 {@code EnchantBookForEmeralds}（绿宝石 + 书 → 附魔书）同构。
     *
     * <h2>刻意没有任何附魔要求</h2>
     *
     * <p>曾经有一条「必须交出该谱系<b>满级基础附魔</b>」的隐式规则，
     * 由 mixin 在 {@code MerchantOffer.satisfiedBy} 上追加校验。它已被<b>删除</b>，
     * 这是作者的权衡：那条规则只在成交瞬间生效、成本槽里又完全显示不出来，
     * 玩家看到的就是「放本书进去，交易却做不成」。门槛现在完全交给货币
     * （回响碎片 / 下界之星），可见、可预期。
     *
     * <p>⚠️ 成本物品的类型<b>就是</b>玩家必须交出的物品类型（{@code ItemCost.test}
     * 只认 {@code pay1.is(item)}）。所以「收普通书」与「要求交附魔书」这两件事
     * <b>不可能同时成立</b>——当初正是这个矛盾让交易彻底做不成。
     */
    private static ItemCost bookCost() {
        return new ItemCost(Items.BOOK.builtInRegistryHolder(), 1, DataComponentPredicate.EMPTY);
    }

    // ── ① 随机铭刻书 ──────────────────────────────────────────────────

    /**
     * 随机铭刻书：随机一条谱系 + 随机等级（1..该谱系高阶上限）。
     *
     * <p>成本 = <b>一本普通书</b> + <b>回响碎片</b>（照原版图书管理员的形状）。
     */
    public static final class RandomInscription implements VillagerTrades.ItemListing {
        private final int villagerLevel;

        public RandomInscription(int villagerLevel) {
            this.villagerLevel = villagerLevel;
        }

        @Nullable
        @Override
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            Optional<ResourceLocation> picked = LineageTradePool.pick(random);
            if (picked.isEmpty()) {
                return null;
            }
            ResourceLocation rootId = picked.get();
            Optional<Holder<Enchantment>> ench = LineageTradePool.enchantment(trader.registryAccess(), rootId);
            if (ench.isEmpty()) {
                return null;
            }

            int maxLevel = LineageTradePool.advancedMaxLevel(rootId, 1);
            int level = 1 + random.nextInt(Math.max(1, maxLevel));

            ItemStack book = BookFactory.create(BookSubject.INSCRIPTION, AscensionTier.ADVANCED.asViewTier());
            book.set(UEComponents.INSCRIPTION_SPEC.get(),
                    new BookSpecs.Inscription(AscensionTier.ADVANCED,
                            List.of(new BookSpecs.Inscription.Entry(rootId, level))));

            ItemCost costA = bookCost();
            ItemCost costB = new ItemCost(Items.ECHO_SHARD, echoShards(random));
            return new MerchantOffer(costA, Optional.of(costB), book,
                    BOOK_MAX_USES, xp(this.villagerLevel), BOOK_PRICE_MULTIPLIER);
        }
    }

    // ── ② 随机定向升阶书（基础→高阶） ────────────────────────────────

    /** 随机定向升阶书：固定为「基础 → 高阶」。 */
    public static final class RandomTargetedAscension implements VillagerTrades.ItemListing {
        private final int villagerLevel;

        public RandomTargetedAscension(int villagerLevel) {
            this.villagerLevel = villagerLevel;
        }

        @Nullable
        @Override
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            Optional<ResourceLocation> picked = LineageTradePool.pick(random);
            if (picked.isEmpty()) {
                return null;
            }
            ResourceLocation rootId = picked.get();
            Optional<Holder<Enchantment>> ench = LineageTradePool.enchantment(trader.registryAccess(), rootId);
            if (ench.isEmpty()) {
                return null;
            }

            int maxLevel = LineageTradePool.advancedMaxLevel(rootId, 1);
            ItemStack book = BookFactory.create(BookSubject.ASCENSION, AscensionTier.ADVANCED.asViewTier());
            book.set(UEComponents.ASCENSION_SPEC.get(),
                    new BookSpecs.Ascension(LineageTier.NATIVE, LineageTier.ADVANCED, Optional.of(rootId)));

            ItemCost costA = bookCost();
            ItemCost costB = new ItemCost(Items.ECHO_SHARD, echoShards(random));
            return new MerchantOffer(costA, Optional.of(costB), book,
                    BOOK_MAX_USES, xp(this.villagerLevel), BOOK_PRICE_MULTIPLIER);
        }
    }

    // ── ③ 通用进阶书 / 通用升级书（只收下界之星） ─────────────────────

    /** 通用进阶书（基础→高阶），成本只有<b>下界之星</b>。 */
    public static final class GenericAscension implements VillagerTrades.ItemListing {
        @Nullable
        @Override
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            ItemStack book = BookFactory.create(BookSubject.ASCENSION, AscensionTier.ADVANCED.asViewTier());
            book.set(UEComponents.ASCENSION_SPEC.get(),
                    new BookSpecs.Ascension(LineageTier.NATIVE, LineageTier.ADVANCED, Optional.empty()));

            ItemCost costA = new ItemCost(Items.NETHER_STAR, netherStars(random));
            return new MerchantOffer(costA, book, MASTER_MAX_USES, xp(5), BOOK_PRICE_MULTIPLIER);
        }
    }

    /** 通用升级书（高阶形态内提级），成本只有<b>下界之星</b>。 */
    public static final class GenericUpgrade implements VillagerTrades.ItemListing {
        @Nullable
        @Override
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            int target = 1 + random.nextInt(3);
            ItemStack book = BookFactory.create(BookSubject.UPGRADE, AscensionTier.ADVANCED.asViewTier());
            book.set(UEComponents.UPGRADE_SPEC.get(),
                    new BookSpecs.Upgrade(AscensionTier.ADVANCED, target));

            ItemCost costA = new ItemCost(Items.NETHER_STAR, netherStars(random));
            return new MerchantOffer(costA, book, MASTER_MAX_USES, xp(5), BOOK_PRICE_MULTIPLIER);
        }
    }
}
