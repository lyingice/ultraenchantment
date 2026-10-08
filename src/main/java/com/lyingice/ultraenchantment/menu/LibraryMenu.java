package com.lyingice.ultraenchantment.menu;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.block.entity.EnchantmentLibraryBlockEntity;
import com.lyingice.ultraenchantment.content.AscensionTier;
import com.lyingice.ultraenchantment.content.BookSpecs;
import com.lyingice.ultraenchantment.content.CodexData;
import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.content.StageDefinition;
import com.lyingice.ultraenchantment.logic.BookFactory;
import com.lyingice.ultraenchantment.logic.EnergyMath;
import com.lyingice.ultraenchantment.logic.StageLookup;
import com.lyingice.ultraenchantment.logic.UERoots;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import com.lyingice.ultraenchantment.registry.UEAttachments;
import com.lyingice.ultraenchantment.registry.UEBlocks;
import com.lyingice.ultraenchantment.registry.UEComponents;
import com.lyingice.ultraenchantment.registry.UEItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * <b>附魔图书馆的容器菜单</b>（v5）。
 *
 * <h2>v5 改了什么</h2>
 *
 * <p>存入「进阶书」不再折算成点数，而是<b>记一本库存</b>：
 *
 * <table border="1">
 *   <tr><th>书</th><th>产出</th></tr>
 *   <tr><td>原版附魔书（基础阶）</td><td>等级单位·基础 += {@code Σ f(level)}</td></tr>
 *   <tr><td>铭刻书</td><td>等级单位·该阶级 += {@code Σ f(entry.level)}</td></tr>
 *   <tr><td>升级书</td><td>等级单位·该阶级 += {@code f(target) × 4}</td></tr>
 *   <tr><td>进阶书 · 通用</td><td><b>通用进阶书·目标阶级 ×1</b></td></tr>
 *   <tr><td>进阶书 · 定向</td><td><b>定向进阶书·目标阶级·该谱系 ×1</b></td></tr>
 * </table>
 *
 * <p>图书馆<b>不再有「取出阶级单位」这件事</b>：书库存只能被进阶台消耗。
 * 等级单位仍然按「神化那条 +1 机制」一级级取出来。
 *
 * <h2>⚠️ 存入的原子性</h2>
 *
 * <p>整本书<b>要么全部吸收、要么原样留在槽里</b>（等级单位有上限、书库存也有上限）。
 * 逐条吸收会让「部分条目已满」的书被吞掉一半价值——玩家看不见这个损失。
 *
 * <h2>同步：只用 ContainerData，不加网络包</h2>
 *
 * <p>头部：总数 / 滚动偏移 / <b>4 个等级单位桶</b>；
 * 每行 4 个 int：附魔数字 id / 阶级序号 / 该阶级等级单位 / 该阶级上限；
 * 尾部：<b>书库存</b>——每个升阶阶级一段（1 个通用计数 + 最多 {@link #STOCK_DETAIL_MAX} 条定向明细）。
 */
public class LibraryMenu extends AbstractContainerMenu {

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int PLAYER_INV_START = 2;

    /** 界面里可见的行数。 */
    // ⚠️ 必须与 LibraryScreen 的可用高度一致。面板 236 高，扣掉标题/槽位/能量条/
    // 列表头/玩家背包后，列表只剩 60px ⇒ 3 行 × 20px。曾经写 8，列表直接画到面板外。
    public static final int ROWS = 3;

    // ── 按钮 id 分区（走原版 clickMenuButton，无需自定义包）──
    //
    // v6：一行 = 一条**谱系**（不再是 谱系×阶级），所以每行自带四个调节钮 + 一个取出钮。
    // 分区必须互不重叠：0-9 / 10-19 / 20-29 / 30-39 / 40-49 / 200-201。
    /** {@code [0, ROWS)}：取出这一行当前选定的书。 */
    public static final int BTN_EXTRACT = 0;
    /** {@code [10, 10+ROWS)} / {@code [20, 20+ROWS)}：阶级 + / −。 */
    public static final int BTN_TIER_UP = 10;
    public static final int BTN_TIER_DOWN = 20;
    /** {@code [30, 30+ROWS)} / {@code [40, 40+ROWS)}：等级 + / −。 */
    public static final int BTN_LEVEL_UP = 30;
    public static final int BTN_LEVEL_DOWN = 40;
    public static final int BTN_SCROLL_UP = 200;
    public static final int BTN_SCROLL_DOWN = 201;

    /** 头部：总数 / 滚动 / 4 个等级单位桶 / 状态（v5）。 */
    private static final int DATA_HEADER = 10;
    private static final int DATA_STRIDE = 7;
    private static final int DATA_TOTAL = 0;
    private static final int DATA_SCROLL = 1;
    /** 4 个等级单位桶的起点，顺序 = {@link LineageTier#ordinal()}。 */
    private static final int DATA_LEVEL = 2;
    /** 最近一次操作的结果（取出被拒时给玩家一句人话）。 */
    private static final int DATA_STATUS = 6;

    // 每行 7 个 int
    private static final int F_ENCH = 0;
    /** 当前选定的阶级序号。 */
    private static final int F_TIER = 1;
    /** 该阶级的等级单位余额。 */
    private static final int F_ENERGY = 2;
    /** 该阶级的等级上限（数据包定义），0 = 这一阶不存在。 */
    private static final int F_MAX = 3;
    /** 当前选定的等级。 */
    private static final int F_LEVEL = 4;
    /** 取出这一行要花的等级单位。 */
    private static final int F_COST = 5;
    /** 这条谱系**可选**的阶级掩码（图鉴解锁 且 数据包有这一阶）。 */
    private static final int F_TIER_MASK = 6;

    public static final int STATUS_NONE = 0;
    public static final int STATUS_OK = 1;
    /** 输出槽里还有没拿走的书——那是玩家已经付过钱的，绝不能覆盖。 */
    public static final int STATUS_OUTPUT_BUSY = 2;
    public static final int STATUS_NOT_ENOUGH = 3;
    public static final int STATUS_NO_TIER = 4;

    /** 书库存块的起点（紧跟列表行之后）。 */
    private static final int DATA_STOCK = DATA_HEADER + ROWS * DATA_STRIDE;
    /** 每个升阶阶级最多下发多少条定向书明细（按数量降序）。 */
    public static final int STOCK_DETAIL_MAX = 6;
    /** 每个升阶阶级占的 int 数：通用计数 + <b>定向总数</b> + N 条（附魔数字 id, 数量）。 */
    // ⚠️ 定向总数必须单独下发：明细是截断的（最多 STOCK_DETAIL_MAX 条），
    //    拿明细求和当总数会在超过上限时少算。
    private static final int STOCK_STRIDE = 2 + STOCK_DETAIL_MAX * 2;
    private static final int DATA_SIZE = DATA_STOCK + 3 * STOCK_STRIDE;

    private final Player player;
    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final boolean clientSide;

    // ⚠️ 必须用「匿名子类重写 setChanged」这个原版惯用法，而不是 addListener。
    // 原版 AbstractContainerMenu 并不把自己注册成容器的监听器，
    // 因此只 new SimpleContainer(1) 的话 slotsChanged 永远不会被调用——
    // 玩家把书放进槽位后什么都不会发生，且不报任何错。
    // 原版 CartographyTableMenu / StonecutterMenu / EnchantmentMenu 都是这个写法。
    private final Container input = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            LibraryMenu.this.slotsChanged(this);
        }
    };

    private final Container output = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            LibraryMenu.this.slotsChanged(this);
        }
    };
    private final LibraryData data = new LibraryData();
    private int scroll = 0;
    private int status = STATUS_NONE;

    /**
     * 每条谱系<b>当前选定的阶级 / 等级</b>（只活在这个菜单实例里，不下发、不存盘）。
     *
     * <p>为什么不放 {@code Row} 里：Row 是每帧从 ContainerData 读出来的投影，
     * 而「我想取哪一档」是玩家的编辑状态，必须活过刷新。
     */
    private final Map<ResourceLocation, LineageTier> selectedTier = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> selectedLevel = new LinkedHashMap<>();

    /**
     * 一行列表内容——<b>图鉴里已解锁的一条 (谱系, 阶级)</b>。
     *
     * <p>v4 起列表来源从「图书馆里有哪些键」换成「<b>图鉴解锁了什么</b>」：
     * 图鉴决定「能取什么」，能量决定「能取多高」。
     *
     * @param energy   该阶级的<b>等级单位</b>余额（升阶是进阶台的事，图书馆只取书）
     * @param maxLevel 该阶级的等级上限（数据包定义）
     */
    public record Row(Holder<Enchantment> root, ResourceLocation rootId, AscensionTier tier,
                      int energy, int maxLevel, int level, int cost, int tierMask) {

        /** 余额够不够取出这一本（余量校验：界面、按钮可用态、服务端扣减同源）。 */
        public boolean affordable() {
            return this.maxLevel > 0 && this.energy >= this.cost;
        }
    }

    /** 列表来源：去重后的谱系 + 每条谱系可选的阶级掩码。 */
    private record ListSource(List<ResourceLocation> roots, Map<ResourceLocation, Integer> tierMasks) {}

    /**
     * 一次存入会产出的一笔东西。
     *
     * <p>v5 起只有两种：<b>等级单位</b>，或<b>一本进阶书</b>。
     */
    private record Yield(LineageTier tier, int levelPoints, boolean genericBook,
                         @Nullable ResourceLocation targetedRoot) {
        static Yield ofLevel(LineageTier tier, int points) {
            return new Yield(tier, points, false, null);
        }

        static Yield ofGenericBook(LineageTier tier) {
            return new Yield(tier, 0, true, null);
        }

        static Yield ofTargetedBook(LineageTier tier, ResourceLocation rootId) {
            return new Yield(tier, 0, false, rootId);
        }
    }

    public LibraryMenu(int windowId, Inventory inventory, BlockPos pos) {
        this(windowId, inventory, pos, inventory.player.level().isClientSide()
                ? ContainerLevelAccess.NULL
                : ContainerLevelAccess.create(inventory.player.level(), pos));
    }

    public LibraryMenu(int windowId, Inventory inventory, BlockPos pos, ContainerLevelAccess access) {
        super(com.lyingice.ultraenchantment.registry.UEMenus.ENCHANTMENT_LIBRARY.get(), windowId);
        this.player = inventory.player;
        this.pos = pos;
        this.access = access;
        this.clientSide = inventory.player.level().isClientSide();

        this.addSlot(new Slot(this.input, 0, 10, 14) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isDepositable(stack);
            }
        });
        this.addSlot(new Slot(this.output, 0, 34, 14) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;   // 只出不进
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 49 + col * 18, 149 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 49 + col * 18, 207));
        }

        this.addDataSlots(this.data);
        if (!this.clientSide) {
            this.refreshData();
        }
    }

    // ── 存入 ────────────────────────────────────────────────────────────

    /**
     * 这本书能否被图书馆吸收——槽位的 {@code mayPlace} 也用它。
     *
     * <h2>⚠️ 必须与 {@link #tryDeposit} 支持的种类一致</h2>
     *
     * <p>曾经这里<b>只认铭刻书</b>，而 {@code tryDeposit} 已经写好了各种书的折算公式——
     * 结果是<b>升级书和进阶书根本放不进输入槽</b>，功能写了但入口没开。
     *
     * <p>⇒ 准入判定与折算逻辑<b>必须成对修改</b>。
     */
    public static boolean isDepositable(ItemStack stack) {
        // ⚠️ 原版附魔书也必须收！它天然就是一本「基础阶」的书。
        if (isVanillaEnchantedBook(stack)) {
            return true;
        }
        if (!UEItems.isAdvancedBook(stack)) {
            return false;
        }
        BookSpecs.Inscription inscription = stack.get(UEComponents.INSCRIPTION_SPEC.get());
        if (inscription != null && !inscription.isEmpty()) {
            return true;
        }
        if (stack.get(UEComponents.UPGRADE_SPEC.get()) != null) {
            return true;
        }
        return stack.get(UEComponents.ASCENSION_SPEC.get()) != null;
    }

    /** 原版附魔书：带至少一条附魔的 {@code minecraft:enchanted_book}。 */
    public static boolean isVanillaEnchantedBook(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK)
                && !EnchantmentHelper.getEnchantmentsForCrafting(stack).isEmpty();
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == this.input && !this.clientSide) {
            this.tryDeposit();
        }
        if (!this.clientSide) {
            this.refreshData();
        }
    }

    /**
     * 把输入槽的书整本吸收（v5）。
     *
     * <p>四种书都收。进阶书按「通用 / 定向」分别记一本库存；
     * <b>{@code toTier == NATIVE} 的进阶书拒绝存入</b>（基础阶不是可推进到的目标，
     * 那是原版附魔书的领域），书留在槽里并记 WARN。
     *
     * <p><b>整本吸收</b>：任一桶装不下就整本留下。
     */
    private void tryDeposit() {
        ItemStack stack = this.input.getItem(0);
        if (stack.isEmpty()) {
            return;
        }
        EnchantmentLibraryBlockEntity library = this.library();
        if (library == null) {
            return;
        }

        List<Yield> yields = new ArrayList<>();
        List<CodexData.UnlockedTier> unlocks = new ArrayList<>();
        // 「见过这条原版附魔」——手里有过它的书就算见过。基础阶不凭空解锁（作者要求）。
        List<ResourceLocation> nativeRoots = new ArrayList<>();

        BookSpecs.Inscription insc = stack.get(UEComponents.INSCRIPTION_SPEC.get());
        BookSpecs.Upgrade upgrade = stack.get(UEComponents.UPGRADE_SPEC.get());
        BookSpecs.Ascension ascension = stack.get(UEComponents.ASCENSION_SPEC.get());

        if (isVanillaEnchantedBook(stack)) {
            // 原版附魔书 = 基础阶的书：产出「等级单位·基础」，并让这些谱系记为「见过」
            int sum = 0;
            for (var e : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) {
                ResourceLocation rootId = e.getKey().unwrapKey().map(k -> k.location()).orElse(null);
                if (rootId == null) {
                    continue;
                }
                sum += EnergyMath.levelToPoints(e.getIntValue());
                nativeRoots.add(rootId);
            }
            if (sum <= 0) {
                return;
            }
            yields.add(Yield.ofLevel(LineageTier.NATIVE, sum));
        } else if (insc != null && !insc.isEmpty()) {
            // 铭刻书：对标原版附魔书，是「具体附魔 + 等级」的载体
            LineageTier tier = insc.tier().asLineageTier();
            int sum = 0;
            for (BookSpecs.Inscription.Entry entry : insc.entries()) {
                sum += EnergyMath.levelToPoints(entry.level());
                unlocks.add(new CodexData.UnlockedTier(entry.enchantment(), insc.tier()));
                nativeRoots.add(entry.enchantment());
            }
            yields.add(Yield.ofLevel(tier, sum));
        } else if (upgrade != null) {
            // 升级书 = 不分附魔种类的铭刻书 ⇒ 通用加成 ×4
            yields.add(Yield.ofLevel(upgrade.tier().asLineageTier(),
                    EnergyMath.scaled(EnergyMath.levelToPoints(upgrade.targetLevel()),
                            EnergyMath.GENERIC_MULTIPLIER)));
        } else if (ascension != null) {
            // v5：进阶书不再是燃料，而是「钥匙」——记一本库存
            // ⚠️ BookSpecs.Ascension.toTier() 是 LineageTier（不是 AscensionTier），与另两种书不一致
            LineageTier to = ascension.toTier();
            if (!EnchantmentLibraryBlockEntity.isBookTier(to)) {
                Ultraenchantment.LOGGER.warn("[library] 拒绝存入 toTier={} 的进阶书：基础阶不是可存入的目标阶级",
                        to.id());
                return;   // 整本留在输入槽
            }
            if (ascension.applicable().isPresent()) {
                yields.add(Yield.ofTargetedBook(to, ascension.applicable().get()));
            } else {
                yields.add(Yield.ofGenericBook(to));
            }
            AscensionTier unlockTier = AscensionTier.of(to).orElse(null);
            if (unlockTier != null) {
                ascension.applicable().ifPresent(root -> {
                    unlocks.add(new CodexData.UnlockedTier(root, unlockTier));
                    nativeRoots.add(root);
                });
            }
        } else {
            return;
        }

        // 先全量校验再写入：部分吸收会静默吞掉玩家的书
        for (Yield yield : yields) {
            if (yield.levelPoints() > 0 && library.roomForLevel(yield.tier()) < yield.levelPoints()) {
                return;
            }
            if (yield.genericBook() && !library.roomForGenericBook(yield.tier())) {
                return;
            }
            if (yield.targetedRoot() != null
                    && !library.roomForTargetedBook(yield.tier(), yield.targetedRoot())) {
                return;
            }
        }
        for (Yield yield : yields) {
            if (yield.levelPoints() > 0) {
                library.addLevelEnergy(yield.tier(), yield.levelPoints());
            } else if (yield.genericBook()) {
                library.addGenericBook(yield.tier());
            } else if (yield.targetedRoot() != null) {
                library.addTargetedBook(yield.tier(), yield.targetedRoot());
            }
        }
        this.input.setItem(0, ItemStack.EMPTY);

        this.onDeposited(unlocks, nativeRoots);
    }

    /**
     * 存入成功后<b>解锁图鉴</b>。
     *
     * <p>解锁是幂等的（{@link CodexData#with} 已解锁时返回自身），
     * 因此这里只在真的发生变化时才写回玩家数据。
     *
     * <p><b>解锁与存入是两件事</b>：存入改变的是图书馆的库存（方块），
     * 解锁改变的是玩家的图鉴（玩家）。同一本书会同时影响两者。
     */
    protected void onDeposited(List<CodexData.UnlockedTier> unlocks, List<ResourceLocation> nativeRoots) {
        if (this.clientSide) {
            return;
        }
        CodexData before = this.player.getData(UEAttachments.CODEX);
        CodexData after = before;
        for (CodexData.UnlockedTier unlock : unlocks) {
            after = after.with(unlock.root(), unlock.tier());
        }
        // 基础阶：手里有过这条附魔的书 ⇒ 记为「见过」
        for (ResourceLocation root : nativeRoots) {
            after = after.withNative(root);
        }
        if (after != before) {
            this.player.setData(UEAttachments.CODEX, after);
        }
    }

    // ── 取出 ────────────────────────────────────────────────────────────

    /**
     * 取出一本铭刻书——<b>阶级与等级都由玩家在行内自己选</b>（v6）。
     *
     * <h2>为什么不再是「输出槽那本书 +1 级」</h2>
     *
     * <p>那个模型把「已经取到几级」这个状态<b>藏在输出槽的物品里</b>。
     * 玩家一点别的条目，那本书被覆盖，<b>状态连同已经花掉的钱一起消失</b>
     * ——而且界面上什么都不显示，看起来就是「书被吞了」。
     *
     * <p>v6 改成「选等级 → 一次性付款 → 得到一本」：每次取出都是独立的一笔买卖，
     * 没有隐藏状态，也就没有吞掉的可能。
     */
    private void extract(int rowIndex) {
        if (this.clientSide) {
            return;
        }
        Row row = this.rowAt(rowIndex);
        EnchantmentLibraryBlockEntity library = this.library();
        if (row == null || library == null) {
            return;
        }
        // ⚠️ 输出槽里还有没拿走的书 ⇒ 拒绝这次取出。那一本是玩家已经付过钱的，
        //    覆盖它等于凭空吞掉一次花费（玩家还看不出为什么少了一本）。
        //    这是本轮修的 bug：以前点一下别的条目就把上一天的产出冲掉了。
        if (!this.output.getItem(0).isEmpty()) {
            this.status = STATUS_OUTPUT_BUSY;
            this.refreshData();
            return;
        }
        if (row.maxLevel() <= 0) {
            this.status = STATUS_NO_TIER;
            this.refreshData();
            return;
        }
        if (!library.spendLevelEnergy(row.tier().asLineageTier(), row.cost())) {
            this.status = STATUS_NOT_ENOUGH;
            this.refreshData();
            return;
        }
        this.output.setItem(0, BookFactory.inscription(new BookSpecs.Inscription(
                row.tier(), List.of(new BookSpecs.Inscription.Entry(row.rootId(), row.level())))));
        this.status = STATUS_OK;
        this.refreshData();
    }

    /**
     * 阶级 + / −：在<b>这条谱系可选的</b>阶级之间循环。
     *
     * <p>「可选」= 图鉴解锁了 <b>且</b> 数据包里真有这一阶（有 `max_level`）。
     * 少一个条件就会出现「切到一档，上限 0，永远取不出来」的死格。
     */
    private void cycleTier(int rowIndex, boolean up) {
        Row row = this.rowAt(rowIndex);
        if (row == null) {
            return;
        }
        List<LineageTier> options = new ArrayList<>();
        for (LineageTier tier : LineageTier.values()) {
            if ((row.tierMask() & (1 << tier.ordinal())) != 0) {
                options.add(tier);
            }
        }
        if (options.size() < 2) {
            return;
        }
        int at = options.indexOf(row.tier().asLineageTier());
        int next = Math.floorMod(at + (up ? 1 : -1), options.size());
        this.selectedTier.put(row.rootId(), options.get(next));
        this.refreshData();
    }

    /** 等级 + / −：只在这条谱系<b>当前阶级的上限</b>内移动。 */
    private void bumpLevel(int rowIndex, int delta) {
        Row row = this.rowAt(rowIndex);
        if (row == null || row.maxLevel() <= 0) {
            return;
        }
        this.selectedLevel.put(row.rootId(),
                Math.max(1, Math.min(row.maxLevel(), row.level() + delta)));
        this.refreshData();
    }

    // ── 按钮 ────────────────────────────────────────────────────────────

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BTN_SCROLL_UP || id == BTN_SCROLL_DOWN) {
            this.scroll = Math.max(0, id == BTN_SCROLL_UP ? this.scroll - 1 : this.scroll + 1);
            this.clampScroll();
            this.refreshData();
            return true;
        }
        if (id >= BTN_EXTRACT && id < BTN_EXTRACT + ROWS) {
            this.extract(id - BTN_EXTRACT);
            return true;
        }
        if (id >= BTN_TIER_UP && id < BTN_TIER_UP + ROWS) {
            this.cycleTier(id - BTN_TIER_UP, true);
            return true;
        }
        if (id >= BTN_TIER_DOWN && id < BTN_TIER_DOWN + ROWS) {
            this.cycleTier(id - BTN_TIER_DOWN, false);
            return true;
        }
        if (id >= BTN_LEVEL_UP && id < BTN_LEVEL_UP + ROWS) {
            this.bumpLevel(id - BTN_LEVEL_UP, 1);
            return true;
        }
        if (id >= BTN_LEVEL_DOWN && id < BTN_LEVEL_DOWN + ROWS) {
            this.bumpLevel(id - BTN_LEVEL_DOWN, -1);
            return true;
        }
        return false;
    }

    // ── 列表同步 ────────────────────────────────────────────────────────

    /** 服务端重算列表并写入 ContainerData。 */
    public void refreshData() {
        EnchantmentLibraryBlockEntity library = this.library();
        Registry<Enchantment> registry =
                this.player.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        HolderLookup.RegistryLookup<StageDefinition> lookup = StageLookup.lookup();
        // 列表来源 = 【图鉴解锁了什么】，但**按谱系去重**：
        // 一条谱系一行，行内用「阶级 −/+」挑档次（v6）。
        ListSource source = this.listSource(lookup);
        List<ResourceLocation> roots = source.roots();

        this.clampScrollTo(roots.size());
        this.data.set(DATA_TOTAL, roots.size());
        this.data.set(DATA_SCROLL, this.scroll);
        this.data.set(DATA_STATUS, this.status);
        // 等级单位：4 个桶（客户端没有方块实体，只能靠 ContainerData）
        for (LineageTier tier : LineageTier.values()) {
            this.data.set(DATA_LEVEL + tier.ordinal(),
                    library == null ? 0 : library.levelEnergy(tier));
        }
        this.writeStock(library);

        for (int i = 0; i < ROWS; i++) {
            int base = DATA_HEADER + i * DATA_STRIDE;
            int index = this.scroll + i;
            if (library == null || index >= roots.size()) {
                this.clearRow(base);
                continue;
            }
            ResourceLocation rootId = roots.get(index);
            Enchantment value = registry.get(rootId);
            if (value == null) {
                this.clearRow(base);
                continue;
            }
            int mask = source.tierMasks().getOrDefault(rootId, 0);
            LineageTier tier = this.effectiveTier(rootId, mask);
            if (tier == LineageTier.NATIVE) {
                this.clearRow(base);
                continue;
            }
            int maxLevel = StageLookup.maxLevelOf(lookup, tier, rootId, 0);
            int level = Math.max(1,
                    Math.min(Math.max(1, maxLevel), this.selectedLevel.getOrDefault(rootId, 1)));
            this.data.set(base + F_ENCH, registry.getId(value));
            this.data.set(base + F_TIER, tier.ordinal() - 1);   // AscensionTier 的序号
            this.data.set(base + F_ENERGY, library.levelEnergy(tier));
            this.data.set(base + F_MAX, maxLevel);
            this.data.set(base + F_LEVEL, level);
            this.data.set(base + F_COST, EnergyMath.costToReach(level, 0));
            this.data.set(base + F_TIER_MASK, mask);
        }
    }

    /** 空行：附魔 id 写 −1（{@code rowAt} 靠它判空），其余清零。 */
    private void clearRow(int base) {
        for (int f = 0; f < DATA_STRIDE; f++) {
            this.data.set(base + f, 0);
        }
        this.data.set(base + F_ENCH, -1);
    }

    /**
     * 去重后的谱系顺序 + 每条谱系<b>可选</b>的阶级掩码。
     *
     * <p>「可选」= 图鉴解锁了 <b>且</b> 数据包里真有这一阶（{@code max_level > 0}）。
     */
    private ListSource listSource(HolderLookup.RegistryLookup<StageDefinition> lookup) {
        List<ResourceLocation> roots = new ArrayList<>();
        Map<ResourceLocation, Integer> masks = new LinkedHashMap<>();
        for (CodexData.UnlockedTier entry : this.player.getData(UEAttachments.CODEX).unlockedTiers()) {
            ResourceLocation root = entry.root();
            LineageTier tier = entry.tier().asLineageTier();
            if (StageLookup.maxLevelOf(lookup, tier, root, 0) <= 0) {
                continue;
            }
            if (!masks.containsKey(root)) {
                roots.add(root);
                masks.put(root, 0);
            }
            masks.put(root, masks.get(root) | (1 << tier.ordinal()));
        }
        return new ListSource(roots, masks);
    }

    /** 这条谱系当前显示哪一档：玩家选过的、且仍然可选；否则取<b>最高</b>可选档。 */
    private LineageTier effectiveTier(ResourceLocation rootId, int mask) {
        LineageTier stored = this.selectedTier.get(rootId);
        if (stored != null && (mask & (1 << stored.ordinal())) != 0) {
            return stored;
        }
        LineageTier best = LineageTier.NATIVE;
        for (LineageTier tier : LineageTier.values()) {
            if ((mask & (1 << tier.ordinal())) != 0) {
                best = tier;
            }
        }
        return best;
    }

    /**
     * 把<b>书库存</b>写进 ContainerData。
     *
     * <p>每个升阶阶级一段：通用计数 + 最多 {@link #STOCK_DETAIL_MAX} 条定向明细
     * （附魔<b>数字 id</b>，因为 ContainerData 只能传 int）。按数量降序，
     * 多出来的条目不下发——图书馆界面只做「一眼看到有多少」，逐条查请在进阶台看「缺书」。
     */
    private void writeStock(@Nullable EnchantmentLibraryBlockEntity library) {
        Registry<Enchantment> registry =
                this.player.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (LineageTier tier : LineageTier.values()) {
            if (!EnchantmentLibraryBlockEntity.isBookTier(tier)) {
                continue;
            }
            int base = DATA_STOCK + bookTierIndex(tier) * STOCK_STRIDE;
            this.data.set(base, library == null ? 0 : library.genericBooks(tier));
            this.data.set(base + 1, library == null ? 0 : library.targetedBookTotal(tier));
            List<Map.Entry<ResourceLocation, Integer>> entries =
                    library == null ? List.of() : library.targetedEntries(tier);
            for (int i = 0; i < STOCK_DETAIL_MAX; i++) {
                int slot = base + 2 + i * 2;
                if (i < entries.size()) {
                    Enchantment value = registry.get(entries.get(i).getKey());
                    this.data.set(slot, value == null ? -1 : registry.getId(value));
                    this.data.set(slot + 1, entries.get(i).getValue());
                } else {
                    this.data.set(slot, -1);
                    this.data.set(slot + 1, 0);
                }
            }
        }
    }

    /** 升阶阶级 → 库存块下标（基础阶没有书库存）。 */
    private static int bookTierIndex(LineageTier tier) {
        return tier.ordinal() - 1;
    }

    /** 第 {@code rowIndex} 行的内容；空行为 {@code null}。两侧通用。 */
    @Nullable
    public Row rowAt(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= ROWS) {
            return null;
        }
        int base = DATA_HEADER + rowIndex * DATA_STRIDE;
        int enchantmentId = this.data.get(base);
        if (enchantmentId < 0) {
            return null;
        }
        AscensionTier[] tiers = AscensionTier.values();
        int tierOrdinal = this.data.get(base + 1);
        if (tierOrdinal < 0 || tierOrdinal >= tiers.length) {
            return null;
        }
        Holder<Enchantment> root = this.player.level().registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT).getHolder(enchantmentId).orElse(null);
        if (root == null) {
            return null;
        }
        ResourceLocation rootId = root.unwrapKey().map(k -> k.location()).orElse(null);
        if (rootId == null) {
            return null;
        }
        return new Row(root, rootId, tiers[tierOrdinal],
                this.data.get(base + F_ENERGY), this.data.get(base + F_MAX),
                this.data.get(base + F_LEVEL), this.data.get(base + F_COST),
                this.data.get(base + F_TIER_MASK));
    }

    /** 最近一次操作的结果（取出被拒时给玩家一句人话）。两侧通用。 */
    public int status() {
        return this.data.get(DATA_STATUS);
    }

    public int totalEntries() {
        return this.data.get(DATA_TOTAL);
    }

    /** 某个阶级的<b>等级单位</b>余额。两侧通用（客户端读的是同步过来的副本）。 */
    public int levelEnergy(LineageTier tier) {
        return this.data.get(DATA_LEVEL + tier.ordinal());
    }

    /** 某个升阶阶级的<b>通用进阶书</b>本数。两侧通用。 */
    public int genericBooks(LineageTier tier) {
        return EnchantmentLibraryBlockEntity.isBookTier(tier)
                ? this.data.get(DATA_STOCK + bookTierIndex(tier) * STOCK_STRIDE) : 0;
    }

    /** 某个升阶阶级的<b>定向进阶书总数</b>（明细可能被截断，所以单独下发）。两侧通用。 */
    public int targetedBookTotal(LineageTier tier) {
        return EnchantmentLibraryBlockEntity.isBookTier(tier)
                ? this.data.get(DATA_STOCK + bookTierIndex(tier) * STOCK_STRIDE + 1) : 0;
    }

    /** 图书馆界面的一条定向书明细。 */
    public record StockEntry(Holder<Enchantment> root, int count) {}

    /** 某个升阶阶级的<b>定向进阶书</b>明细（按数量降序，最多 {@link #STOCK_DETAIL_MAX} 条）。 */
    public List<StockEntry> targetedBooks(LineageTier tier) {
        if (!EnchantmentLibraryBlockEntity.isBookTier(tier)) {
            return List.of();
        }
        Registry<Enchantment> registry =
                this.player.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        int base = DATA_STOCK + bookTierIndex(tier) * STOCK_STRIDE;
        List<StockEntry> out = new ArrayList<>();
        for (int i = 0; i < STOCK_DETAIL_MAX; i++) {
            int enchantmentId = this.data.get(base + 2 + i * 2);
            int count = this.data.get(base + 2 + i * 2 + 1);
            if (enchantmentId < 0 || count <= 0) {
                continue;
            }
            Holder<Enchantment> root = this.player.level().registryAccess()
                    .registryOrThrow(Registries.ENCHANTMENT).getHolder(enchantmentId).orElse(null);
            if (root != null) {
                out.add(new StockEntry(root, count));
            }
        }
        return out;
    }

    public int scrollOffset() {
        return this.data.get(DATA_SCROLL);
    }

    public boolean canScrollUp() {
        return this.scrollOffset() > 0;
    }

    public boolean canScrollDown() {
        return this.scrollOffset() + ROWS < this.totalEntries();
    }

    public boolean hasOutput() {
        return !this.output.getItem(0).isEmpty();
    }

    private void clampScroll() {
        this.clampScrollTo(this.data.get(DATA_TOTAL));
    }

    private void clampScrollTo(int total) {
        int max = Math.max(0, total - ROWS);
        if (this.scroll > max) {
            this.scroll = max;
        }
        if (this.scroll < 0) {
            this.scroll = 0;
        }
    }

    @Nullable
    private EnchantmentLibraryBlockEntity library() {
        if (this.clientSide) {
            return null;
        }
        return this.player.level().getBlockEntity(this.pos) instanceof EnchantmentLibraryBlockEntity be ? be : null;
    }

    // ── 容器契约 ────────────────────────────────────────────────────────

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, UEBlocks.ENCHANTMENT_LIBRARY.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index == OUTPUT_SLOT || index == INPUT_SLOT) {
            if (!this.moveItemStackTo(stack, PLAYER_INV_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (isDepositable(stack)) {
            if (!this.moveItemStackTo(stack, INPUT_SLOT, INPUT_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, pos) -> {
            clearContainer(player, this.input);
            clearContainer(player, this.output);
        });
    }

    /** {@link ContainerData} 的朴素实现：一个定长 int 数组。 */
    private static final class LibraryData implements ContainerData {
        private final int[] values = new int[DATA_SIZE];

        @Override
        public int get(int index) {
            return index >= 0 && index < this.values.length ? this.values[index] : 0;
        }

        @Override
        public void set(int index, int value) {
            if (index >= 0 && index < this.values.length) {
                this.values[index] = value;
            }
        }

        @Override
        public int getCount() {
            return this.values.length;
        }
    }
}
