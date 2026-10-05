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

    /** 原版各级卖出的经验值。下标即职业等级（1..5），0 位占位。 */
    public static final int[] XP_SELL = {0, 1, 5, 10, 15, 30};

    /** 偏移：把 1..5 级映射到 {@link #XP_SELL} 的下标。 */
    private static int xp(int villagerLevel) {
        int i = Math.max(1, Math.min(5, villagerLevel));
        return XP_SELL[i];
    }

    /**
     * 造一个「收走某谱系满级原生附魔」的成本。
     *
     * <p>展示物品用<b>普通书</b>（{@code Items.BOOK}）——它只是个占位符：
     * 真正的判定规则写在它的 {@code trade_requirement} 组件上，
     * 由 {@code MerchantOfferMixin} 在成交时校验，与展示物品的类型无关。
     *
     * <h2>为什么是普通书而不是附魔书</h2>
     *
     * <p>曾经用 {@code Items.ENCHANTED_BOOK}，但那是<b>误导</b>：成本物品走的是
     * {@code ItemCost}，其序列化<b>不含 itemStack</b>
     * （只编解码 {@code (item, count, components谓词)}，反序列化走三参构造重建），
     * 所以客户端拿到的永远是<b>空壳</b>——玩家悬停只看到「附魔书」三个字却没有任何附魔，
     * 看起来像 bug。普通书没有「应该带附魔内容」的预期，语义诚实。
     *
     * <p><b>成本的真实含义由出售物品的 tooltip 表达</b>（它走完整 ItemStack 编解码，
     * 组件会同步）；判定则由 {@code MerchantOfferMixin} 做。
     */
    private static ItemCost enchantmentCost(Holder<Enchantment> enchantment, int requiredLevel) {
        ItemStack marker = new ItemStack(Items.BOOK);
        ResourceLocation rootId = enchantment.unwrapKey()
                .map(net.minecraft.resources.ResourceKey::location)
                .orElseThrow();
        TradeRequirement.bind(marker, rootId, requiredLevel);

        // ⚠️ 必须用四参构造把 marker 直接塞进去。
        // 三参构造 (Holder, int, DataComponentPredicate) 会自建一个全新的
        // itemStack，marker 上刚写的规则组件会被丢掉——那样校验永远看到 null，
        // 交易退化成「1 本书换」，静默放宽而不是报错。
        return new ItemCost(Items.BOOK.builtInRegistryHolder(), 1,
                DataComponentPredicate.EMPTY, marker);
    }

    // ── ① 随机铭刻书 ──────────────────────────────────────────────────

    /**
     * 随机铭刻书：随机一条谱系 + 随机等级（1..该谱系高阶上限）。
     *
     * <p>成本 = 对应谱系<b>满级原生附魔</b>（普通书承载）+ <b>回响碎片</b>。
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

            ItemCost costA = enchantmentCost(ench.get(), ench.get().value().getMaxLevel());
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

            ItemCost costA = enchantmentCost(ench.get(), ench.get().value().getMaxLevel());
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
