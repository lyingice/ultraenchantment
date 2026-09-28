package com.lyingice.ultraenchantment.event;

import com.lyingice.ultraenchantment.content.AscensionTier;
import com.lyingice.ultraenchantment.content.BookSpecs;
import com.lyingice.ultraenchantment.content.BookSubject;
import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.content.UETier;
import com.lyingice.ultraenchantment.logic.BookFactory;
import net.minecraft.world.item.component.CustomModelData;
import com.lyingice.ultraenchantment.registry.UEComponents;
import com.lyingice.ultraenchantment.registry.UEItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * <b>创造栏投放</b>。
 *
 * <h2>两个标签页</h2>
 *
 * <ul>
 *   <li>{@code INGREDIENTS}（原材料）——进阶附魔书。原版附魔书区块是该标签的最后一块
 *       （源码实证：{@code generateEnchantmentBookTypes*} 之后即 {@code build()}），
 *       因此直接 {@code accept} 追加，落点天然就是「附魔书最后面」。</li>
 *   <li>{@code TOOLS_AND_UTILITIES}（工具与实用物品）——祛咒石。
 *       它在语义上是<b>工具</b>（可复用、消耗耐久），不是原材料。</li>
 * </ul>
 *
 * <h2>分层原则：主标签放样本，搜索标签放全规格</h2>
 *
 * <p>照原版附魔书的先例（源码实证 {@code CreativeModeTabs}）：
 * <pre>
 *   generateEnchantmentBookTypesOnlyMaxLevel(..., PARENT_TAB_ONLY);  // 主标签：每附魔一本
 *   generateEnchantmentBookTypesAllLevels(...,    SEARCH_TAB_ONLY);  // 搜索标签：每附魔每级
 * </pre>
 *
 * <p><b>主标签放「每一格一本」的可读集合，搜索标签放完整矩阵</b>——
 * 只有「等级」这一维算隐藏维度，所以主标签只收各格的满级。
 * 数据包铺开后矩阵是「谱系 × 阶级 × 等级」，动辄数百条，绝不能全塞主标签。
 *
 * <table>
 *   <tr><th>科目</th><th>主标签</th><th>搜索标签</th><th>理由</th></tr>
 *   <tr><td><b>进化书 · 通用</b></td><td>3 品质</td><td>3 品质</td>
 *       <td>与谱系无关，本来就只有 3 条</td></tr>
 *   <tr><td><b>进化书 · 定向</b></td><td><b>全部（90）</b></td><td><b>全部（90）</b></td>
 *       <td>矩阵是「谱系 × 阶级」= 30 × 3 = 90，<b>没有「等级」这一维</b>，
 *           不存在需要靠搜索才查得到的隐藏规格 → 不需要分层</td></tr>
 *   <tr><td><b>铭刻书</b></td><td>谱系 × 阶级（<b>90</b>，各格满级）</td>
 *       <td>谱系 × 阶级 × 等级（273）+ 3 条多条目样本</td>
 *       <td>「等级」是隐藏维度 → 主标签只放满级；
 *           <b>顺序必须是谱系外层、阶级内层</b>——否则同一附魔的三个阶级被拆散，
 *           读起来像「高阶X / 超级Y / 究极Z」，会被当成数据串行（见 P1-29 的废弃说明）</td></tr>
 *   <tr><td><b>升级书</b></td><td><b>全部等级</b></td><td><b>全部等级</b></td>
 *       <td>不区分附魔种类，只有「阶级 × 等级」两维，全展开占位可控</td></tr>
 *   <tr><td><b>祛咒石</b></td><td>3 档</td><td>3 档</td>
 *       <td>本来就只有 3 条</td></tr>
 * </table>
 *
 * <p><b>要不要分层，只看矩阵有没有隐藏维度</b>：升级书（阶级 × 等级）与定向书
 * （谱系 × 阶级）都是「二维、每维都很小」，全展开也就几十条，玩家一眼看全反而更好；
 * 只有铭刻书是三维（谱系 × 阶级 × 等级 = 273），才必须分层。
 *
 * <p><b>铭刻书的样本怎么取</b>：<b>每条谱系在主标签里都露面一次</b>，阶级沿谱系顺序轮转
 * （见 {@code inscriptionSamples}），于是「不同谱系」与「不同阶级」两个维度同时被覆盖。
 *
 * <p>⚠️ 这里前后踩过三个坑，方向各不相同，都记下来：
 * <ol>
 *   <li>最初<b>每档都用锋利</b> → 主标签全是「锋利系列」，玩家以为只支持锋利。</li>
 *   <li>改成「<b>每个阶级换一条</b>」后，主标签里就只有 <b>3 条</b>谱系——
 *       30 条谱系里 27 条在主标签里根本不存在，等于没覆盖。</li>
 *   <li>把轮转那套<b>套到定向书上</b> → 每条谱系只露出<b>一个随机阶级</b>，
 *       90 条变体里主标签只见 30 条，玩家看到的是「三档只有一档」。
 *       定向书本来就没有隐藏维度，根本不该走样本那条路。</li>
 * </ol>
 * **「覆盖」要按谱系总数来算，不能按阶级数来算**；
 * **而「该不该分层」只取决于矩阵有没有隐藏维度，不取决于条数看起来多不多。**
 *
 * <h2>⚠️ 重复校验</h2>
 *
 * <p>{@code accept} 内部会 {@code assertNewEntryDoesNotAlreadyExists}，
 * 且 {@code ItemStack} 的 {@code equals} 比较<b>组件</b>。因此同一物品的不同变种
 * 只要组件不同就不冲突；反之若两次投出完全相同的组件组合会直接抛异常。
 * 展开时务必保证每个条目的组件组合唯一。
 *
 * <h2>总线</h2>
 *
 * <p>{@code BuildCreativeModeTabContentsEvent} 实现 {@code IModBusEvent}，
 * 必须在 <b>mod bus</b> 上注册。挂到 game bus 永远不会触发且不报错（P2-5）。
 */
public final class CreativeTabEvents {
    private CreativeTabEvents() {}

    /** 单例监听器，供 mod bus 注册。 */
    public static final CreativeTabEvents INSTANCE = new CreativeTabEvents();

    @SubscribeEvent
    public void onBuildTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) {
            books(event);
            return;
        }
        if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
            curativeStones(event);
        }
    }

    /**
     * 原材料标签：进阶附魔书的各科目变种。
     *
     * <h2>内容由数据包驱动，不是硬编码</h2>
     *
     * <p><b>数据包是事实源，创造栏只是它的视图。</b>
     * 有哪些谱系、每条谱系铺了哪些阶级，一律从
     * {@code data/ultraenchantment/ultraenchantment/enchantment/} 下的阶段条目枚举，
     * 见 {@link ReloadEvents#lineages()}。
     *
     * <p>把谱系写成常量（如只认 {@code minecraft:sharpness}）会导致
     * 数据包新增谱系后创造栏不跟随——玩家看不到新谱系的铭刻书，
     * 而铁砧那边其实是认的（它现场查注册表），于是出现「能用但拿不到」的割裂。
     */
    private void books(BuildCreativeModeTabContentsEvent event) {
        int maxLevel = ReloadEvents.globalMaxLevel();

        // ── 进化型 · 通用：三个品质，与谱系无关 ──
        //
        // 「通用进阶」按定义就不限定附魔种类，因此它**不受数据包谱系影响**——
        // 数据包一条谱系都没铺时，这三本依然是合法条目。
        accept(event, ascensionBook(LineageTier.NATIVE, LineageTier.ADVANCED, null));
        accept(event, ascensionBook(LineageTier.ADVANCED, LineageTier.SUPER, null));
        accept(event, ascensionBook(LineageTier.SUPER, LineageTier.ULTRA, null));

        // ── 定向 / 铭刻：按数据包谱系枚举 ──
        //
        // 遍历「谱系 × 阶级」的完整矩阵（见下方 for 循环），
        // 再按标签分流：
        //   · 主标签 —— 只放**样本**，让玩家看到每种变种长什么样
        //   · 搜索标签 —— 放**全规格**，玩家能查到任意组合
        //
        // 这个分层照原版附魔书的先例（源码实证 generateEnchantmentBookTypes*）：
        //   generateEnchantmentBookTypesOnlyMaxLevel(..., PARENT_TAB_ONLY);
        //   generateEnchantmentBookTypesAllLevels(...,    SEARCH_TAB_ONLY);
        for (ResourceLocation root : ReloadEvents.roots()) {
            for (AscensionTier tier : AscensionTier.values()) {
                if (!ReloadEvents.hasTier(root, tier)) {
                    continue;   // 数据包没铺这个阶级 → 不产出对应的书
                }

                // 定向进阶书：把 root 从 tier 的前一阶推进到 tier。
                LineageTier from = previousTierOf(tier);

                // **两个标签都投**：定向书总共只有「谱系数 × 阶级」= 30 × 3 = 90 条，
                // 没有「等级」这一维，也就不存在需要靠搜索才能查到的隐藏规格。
                // 早期把它按搜索标签独占，主标签里每条谱系只靠轮转露一个阶级，
                // 玩家看到的是「三十本里只有一本、还只有一档」——等于不全。
                accept(event, ascensionBook(from, tier.asLineageTier(), root));

                // 铭刻书：矩阵是「谱系 × 阶级 × 等级」（30 × 3 × 最多 5 = 273），
                // 规模最大 → 必须分层：主标签放样本，搜索标签放全规格。
                //
                // ⚠️ 上限是**逐谱系**的（耐久 3、保护 4、锋利 5），
                // 用全局最大值会产出永远拿不到的条目（P1-28）。
                int lineageMax = ReloadEvents.maxLevelOf(root, tier, maxLevel);
                for (int level = 1; level <= lineageMax; level++) {
                    acceptSearch(event, inscriptionBook(tier, root, level));
                }
            }
        }

        // ── 铭刻书 · 主标签：每条谱系 × 每个阶级各一本（满级）──
        //
        // 顺序刻意与搜索标签同构：**谱系为外层、阶级为内层**，
        // 于是主标签读起来就是「高阶X / 超级X / 究极X → 高阶Y / 超级Y / 究极Y」。
        //
        // ⚠️ 这里曾经用「轮转」取样（第 i 条谱系取第 i%3 个阶级，每条谱系只露面一次，
        // 见 AGENT.md P1-29）。代价是玩家读到的是「高阶X / 超级Y / 究极Z」——
        // 数据没错，是**排布**错了：同一附魔的三个阶级被拆散，看起来像阶级串了行。
        // 轮转的初衷（每条谱系都要露面）在这里不但保留、而且更强：现在每条谱系露面三次。
        //
        // 只放**满级**那一本（照原版附魔书的先例：主标签放满级、搜索标签放全等级），
        // 并且放在定向书之后，让「先看进阶书、再看载体书」两段各自成序。
        for (BookSpecs.Inscription payload : mainTabInscriptions()) {
            acceptParent(event, inscriptionBook(payload));
        }

        // ── 多条目样本：**只进搜索标签**（v2）──
        //
        // 载体书从 v2 起可以携带多条附魔、可以两本合并（规格 §5.4）。这个能力要能被发现，
        // 但它**不能放主标签**：多条目书与单条目书共用同一个材质（区分维度只有「科目 × 阶级」），
        // 摆在主标签里看上去就是「某条附魔的书莫名多了一条别的附魔」——查不出错，
        // 只会让人以为数据乱了（实测：排序后前两条谱系是爆炸保护与引雷，于是三条样本
        // 全是「爆炸保护 + 引雷」，读起来像引雷的书被塞进了爆炸保护）。
        // 放进搜索标签（全规格区）：搜得到，又不干扰主标签的整齐。
        for (AscensionTier tier : AscensionTier.values()) {
            List<BookSpecs.Inscription.Entry> pair = mergedSample(tier);
            if (pair.size() >= 2) {
                acceptSearch(event, inscriptionBook(tier, pair));
            }
        }

        // ── 升级型：三档 × 全部等级，两个标签都进 ──
        //
        // **刻意不压缩**：升级书不区分附魔种类，只有「阶级 × 目标等级」两个维度，
        // 全部展开占位可控，玩家能一眼看全规格，不必去搜索标签翻。
        // （规格：「因为这个没有条目细分，所以我认为全部显示不占太多位置，是可行的」）
        for (AscensionTier tier : AscensionTier.values()) {
            for (int level = 1; level <= maxLevel; level++) {
                accept(event, upgradeBook(tier, level));
            }
        }
    }

    /**
     * 主标签铭刻书计划：<b>谱系（外层，字典序）× 阶级（内层，枚举序）</b>，
     * 只含数据包真铺了的格子，每格取该谱系该阶级的满级。
     *
     * <h2>顺序就是契约</h2>
     *
     * <p>内层必须是阶级递增，外层必须是谱系——这样主标签读起来才是
     * 「高阶X / 超级X / 究极X → 高阶Y / 超级Y / 究极Y」。
     * v2.0 之前这里用的是「阶级轮转」取样，于是同一附魔的三个阶级被拆散成
     * 「高阶X / 超级Y / 究极Z」，看起来像阶级串了行（见 AGENT.md P1-29 的废弃说明）。
     *
     * <p>抽成独立的包级方法是为了**可测**：这条顺序没有任何界面可以自动断言，
     * 只能靠自检直接断计划表。
     */
    public static List<BookSpecs.Inscription> mainTabInscriptions() {
        List<BookSpecs.Inscription> plan = new ArrayList<>();
        for (ResourceLocation root : ReloadEvents.roots()) {
            for (AscensionTier tier : AscensionTier.values()) {
                if (!ReloadEvents.hasTier(root, tier)) {
                    continue;
                }
                plan.add(new BookSpecs.Inscription(tier, List.of(new BookSpecs.Inscription.Entry(root,
                        ReloadEvents.maxLevelOf(root, tier, ReloadEvents.globalMaxLevel())))));
            }
        }
        return plan;
    }

    /** 多条目样本：该谱系该阶级的载体书（单条目）——由计划表复用。 */
    private static ItemStack inscriptionBook(BookSpecs.Inscription payload) {
        return BookFactory.inscription(payload);
    }

    /**
     * 取两条谱系组成「已合并」样本：该阶级下前两条真的铺了阶段条目的谱系，各自取该阶级满级。
     *
     * <p>不做假数据——样本要能真的用出去；数据包把某条谱系从某阶级撤掉时，
     * 这里也跟着换人（与 {@link #inscriptionSamples()} 同一条原则：数据包是事实源）。
     */
    private static List<BookSpecs.Inscription.Entry> mergedSample(AscensionTier tier) {
        List<BookSpecs.Inscription.Entry> entries = new ArrayList<>();
        for (ResourceLocation root : ReloadEvents.roots()) {
            if (!ReloadEvents.hasTier(root, tier)) {
                continue;
            }
            entries.add(new BookSpecs.Inscription.Entry(root, ReloadEvents.maxLevelOf(root, tier, 1)));
            if (entries.size() == 2) {
                break;
            }
        }
        return entries;
    }

    /**
     * 某阶级的「前一阶」——定向进阶书的 {@code from_tier}。
     *
     * <pre>
     *   ADVANCED ← NATIVE   （基础 → 高阶）
     *   SUPER    ← ADVANCED （高阶 → 超级）
     *   ULTRA    ← SUPER    （超级 → 究极）
     * </pre>
     *
     * <p>用 {@code AscensionTier.ordinal()} 索引 {@link LineageTier}：后者多一个
     * 排在最前的 {@code NATIVE}，因此「前一阶」正好是 {@code ordinal()} 号元素。
     */
    private static LineageTier previousTierOf(AscensionTier tier) {
        return LineageTier.values()[tier.ordinal()];
    }

    /** 工具与实用物品标签：三档祛咒石。 */
    private void curativeStones(BuildCreativeModeTabContentsEvent event) {
        for (AscensionTier tier : AscensionTier.values()) {
            accept(event, curativeStone(tier));
        }
    }

    /**
     * 只进主标签（原版 {@code PARENT_TAB_ONLY}）。
     *
     * <p>照原版附魔书的做法：主标签只放<b>满级</b>那一本，
     * 让玩家看到「这个变种长什么样」，而不会被全等级条目淹没。
     *
     * <p>目前只有铭刻型用它——升级书刻意全展开（见类注释）。
     */
    private static void acceptParent(BuildCreativeModeTabContentsEvent event, ItemStack stack) {
        event.accept(stack, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
    }

    /**
     * 只进搜索标签（原版 {@code SEARCH_TAB_ONLY}）。
     *
     * <p>完整规格枚举——玩家搜索时能查到每档每一级。
     * 目前只有铭刻型用它。
     */
    private static void acceptSearch(BuildCreativeModeTabContentsEvent event, ItemStack stack) {
        event.accept(stack, CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
    }

    /** 两个标签都进。用于没有「等级」维度、或刻意全展开的条目。 */
    private static void accept(BuildCreativeModeTabContentsEvent event, ItemStack stack) {
        event.accept(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }

    // ── 物品构造 ────────────────────────────────────────────────────────

    /**
     * 进化型：{@code applicable} 为空即通用进阶，非空即定向进阶。
     *
     * <p>外观按<b>目标档位</b>显示——「基础→高阶」的书看着像高阶书，
     * 与「阶级只体现在材质上」的约定一致。
     */
    private static ItemStack ascensionBook(LineageTier from, LineageTier to, ResourceLocation applicable) {
        ItemStack book = BookFactory.create(BookSubject.ASCENSION, to.asViewTier().orElse(UETier.ADVANCED));
        book.set(UEComponents.ASCENSION_SPEC.get(),
                new BookSpecs.Ascension(from, to, Optional.ofNullable(applicable)));
        return book;
    }

    /**
     * 铭刻型：载荷显式携带阶级与等级。
     *
     * <p>阶级不再是「从附魔 id 反推」——那是错的（铭刻的是原版 id，路径里没有阶级）。
     * 阶级是本书自身的属性，必须写进载荷，tooltip 与铁砧校验都直接读它。
     */
    /** 多条目载体书——主标签样本与「已合并」样本用它。 */
    private static ItemStack inscriptionBook(AscensionTier tier, List<BookSpecs.Inscription.Entry> entries) {
        return BookFactory.inscription(new BookSpecs.Inscription(tier, entries));
    }

    /** 单条目载体书——搜索标签的全规格矩阵用它。 */
    private static ItemStack inscriptionBook(AscensionTier tier, ResourceLocation enchantment, int level) {
        return inscriptionBook(tier, List.of(new BookSpecs.Inscription.Entry(enchantment, level)));
    }

    private static ItemStack upgradeBook(AscensionTier tier, int targetLevel) {
        ItemStack book = BookFactory.create(BookSubject.UPGRADE, tier.asViewTier());
        book.set(UEComponents.UPGRADE_SPEC.get(), new BookSpecs.Upgrade(tier, targetLevel));
        return book;
    }

    private static ItemStack curativeStone(AscensionTier tier) {
        ItemStack stone = new ItemStack(UEItems.CURATIVE_STONE.get());
        stone.set(UEComponents.CURATIVE_TIER.get(), tier);
        // 祛咒石是单层模型，谓词直接编码阶级。
        stone.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(tier.asViewTier().modelData()));
        return stone;
    }

    // 书的构造（物品 + 载荷 + 材质谓词）统一走 BookFactory，本类不再自己拼 custom_model_data。
}
