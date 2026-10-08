package com.lyingice.ultraenchantment.menu;

import com.lyingice.ultraenchantment.api.event.UltraEnchantTierUpgradeEvent;
import com.lyingice.ultraenchantment.block.entity.EnchantmentLibraryBlockEntity;
import com.lyingice.ultraenchantment.content.AscensionData;
import com.lyingice.ultraenchantment.content.CodexData;
import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.content.StageDefinition;
import com.lyingice.ultraenchantment.logic.AscensionLogic;
import com.lyingice.ultraenchantment.logic.EnergyMath;
import com.lyingice.ultraenchantment.logic.KeyBooks;
import com.lyingice.ultraenchantment.logic.StageLookup;
import com.lyingice.ultraenchantment.logic.UEEvents;
import com.lyingice.ultraenchantment.logic.UERoots;
import com.lyingice.ultraenchantment.registry.UEAttachments;
import com.lyingice.ultraenchantment.registry.UEBlocks;
import com.lyingice.ultraenchantment.registry.UEComponents;
import com.lyingice.ultraenchantment.registry.UEMenus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
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
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.EnchantingTableBlock;

/**
 * <b>附魔进阶台的容器菜单</b>——本模组的差异化所在。
 *
 * <h2>与 Enchanting Infuser 的差别：多一个「阶级」轴</h2>
 *
 * <p>灌注台只有「附魔 × 等级」；我们是 <b>附魔 × 阶级 × 等级</b>。
 * 每条附魔旁边可以切换 基础 / 高阶 / 超级 / 究极，再在<b>该阶级内</b>调等级。
 *
 * <h2>代价不是经验，是图书馆库存（v5：两种东西）</h2>
 *
 * <p>「附近」用原版附魔台同一套书架环（{@link EnchantingTableBlock#BOOKSHELF_OFFSETS}），
 * 玩家不需要学新规则。多座图书馆在环内时<b>池化</b>：校验看总量，扣减按顺序摊。
 *
 * <ul>
 *   <li><b>升阶（跨阶级）</b>：消耗一本<b>进阶书</b>——v5 起这是「钥匙」，
 *       不是点数。优先消耗<b>定向</b>书（只对一条谱系有效），其次<b>通用</b>书。
 *       判定走 {@link KeyBooks}，界面显示与实扣同源。</li>
 *   <li><b>提级（阶级内）</b>：扣<b>等级单位</b> {@code f(目标) - f(现有)}，仍是点数。</li>
 * </ul>
 *
 * <h2>⚠️ 先校验、后扣减、再写入</h2>
 *
 * <p>顺序不能反。先写物品再扣库存会留下「白拿」窗口——中间任何异常或取消
 * 都让玩家免费拿到附魔。这里严格：<b>全量校验 → 扣库存 → 写物品</b>。
 * 事件（可取消）在扣减<b>之前</b>发，被取消则什么都不发生。
 *
 * <h2>同步</h2>
 *
 * <p>仍只用 {@code ContainerData}（与图书馆同源做法），不加网络包。
 * 每行 10 个 int：附魔数字 id / 解锁掩码 / 四个阶级的上限 / 选中阶级 / 选中等级 / 花费 / 是否够付。
 */
public class AscensionTableMenu extends AbstractContainerMenu {

    public static final int ITEM_SLOT = 0;
    public static final int PLAYER_INV_START = 1;
    // ⚠️ 必须与 AscensionTableScreen 的可用高度一致。
    // 曾经面板缩到 236 却忘了把 6 改成 4，于是列表底画到 180、而灌注按钮在 140——
    // 第 5 行直接压在按钮上。这种错源码里看不出来，只有截图才暴露。
    public static final int ROWS = 4;

    // ── 按钮 id 分区 ──
    public static final int BTN_TIER_UP = 0;      // [0, ROWS)
    public static final int BTN_TIER_DOWN = 100;
    public static final int BTN_LEVEL_UP = 200;
    public static final int BTN_LEVEL_DOWN = 300;
    public static final int BTN_APPLY = 400;
    public static final int BTN_CLEAR = 401;
    public static final int BTN_SCROLL_UP = 402;
    public static final int BTN_SCROLL_DOWN = 403;
    /** {@code [500, 500+4*100)}：直接选第 j 档 —— 编码 {@code j*100 + row}。 */
    public static final int BTN_TIER_SET = 500;

    // ── 状态码（界面据此显示提示）──
    public static final int STATUS_NONE = 0;
    public static final int STATUS_OK = 1;
    public static final int STATUS_NO_ITEM = 2;
    public static final int STATUS_NO_SELECTION = 3;
    public static final int STATUS_LOCKED = 4;
    public static final int STATUS_NOT_ENOUGH = 5;
    public static final int STATUS_CANCELLED = 6;
    public static final int STATUS_NO_LIBRARY = 7;
    /** 点了<b>图鉴未解锁</b>的阶级方块——必须给出原因，否则玩家只看到「点了没反应」。 */
    public static final int STATUS_TIER_LOCKED = 8;
    /** 点了<b>数据包没铺</b>的阶级方块（这条谱系没有这一档）。 */
    public static final int STATUS_TIER_MISSING = 9;
    /**
     * 缺少对应阶级的<b>进阶书</b>（v5）。
     *
     * <p>编号顺延到 10：8/9 已经被上面两条占用，而状态码是要发给客户端的<b>协议值</b>，
     * 不能为了对齐文档去改旧值。
     */
    public static final int STATUS_NO_KEY_BOOK = 10;

    private static final int DATA_TOTAL = 0;
    private static final int DATA_SCROLL = 1;
    private static final int DATA_STATUS = 2;
    private static final int DATA_HEADER = 3;
    private static final int DATA_STRIDE = 14;
    private static final int DATA_SIZE = DATA_HEADER + ROWS * DATA_STRIDE;

    private static final int F_ENCH = 0;
    private static final int F_UNLOCK = 1;
    private static final int F_MAX_BASE = 2;      // 四个阶级的上限占 2..5
    private static final int F_TIER = 6;          // 选中阶级 ordinal，-1 = 未选
    private static final int F_LEVEL = 7;         // 选中等级，0 = 未选
    /**
     * v5：<b>钥匙书状态</b>，带符号。
     *
     * <ul>
     *   <li>{@code 0} —— 不需要（同阶级 / 往低阶）</li>
     *   <li>{@code +N} —— 需要 N 本，且环内图书馆有</li>
     *   <li>{@code -N} —— 需要 N 本，但<b>缺</b>（界面显示红字「缺书」）</li>
     * </ul>
     *
     * <p>用带符号的数而不是两个布尔量：万一将来把「跨几档收几本」打开，
     * 界面与判定都不用改形状。
     */
    private static final int F_KEY_BOOK = 8;
    /** 钥匙书够 <b>且</b> 等级单位够，才为 1。 */
    private static final int F_AFFORDABLE = 9;
    /** v5：提级花费（等级单位，带符号：负 = 退还）。 */
    private static final int F_LEVEL_COST = 10;
    /** 该阶级、该谱系的**定向**书库存（环内合计）——只给界面 tooltip 做分解用。 */
    private static final int F_KEY_TARGETED = 11;
    /** 该阶级的**通用**书库存（环内合计）。 */
    private static final int F_KEY_GENERIC = 12;
    /** 该阶级的**等级单位**库存（环内合计）——tooltip 要显示「储备 / 还差」。 */
    private static final int F_LEVEL_STOCK = 13;

    private final Player player;
    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final boolean clientSide;

    private final Container itemSlot = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            AscensionTableMenu.this.slotsChanged(this);
        }
    };
    private final TableData data = new TableData();
    private int scroll = 0;
    private int status = STATUS_NONE;

    /** 每个谱系的选中状态（阶级 + 等级）。以谱系为键，因此滚动不会丢选择。 */
    private final Map<ResourceLocation, Selection> selections = new LinkedHashMap<>();

    /**
     * <b>物品上本来就有</b>的谱系——这些行<b>不允许被取消选择</b>（作者要求的保险）。
     *
     * <p>为什么需要它：开台子时会把物品上已有的附魔<b>预选</b>进 {@link #selections}
     * （见 {@link #rebuildSelections()}）。预选是便利，但如果玩家手一抖点了同一档
     * 就会把它撤销 —— 那这次「预选」就白做了，而且物品上那条附魔看起来像是被丢了。
     * 锁定之后：点同一档是空操作，{@code 清空} 也清不掉它们。
     *
     * <p><b>锁定 ≠ 不能改</b>：切到<b>别的</b>阶级仍然允许 —— 那正是升阶要做的事。
     */
    private final Set<ResourceLocation> lockedRoots = new LinkedHashSet<>();

    /**
     * <b>物品缓存</b>：这件物品<b>自己带着</b>的条目 → 它所在阶级及所有下级阶级的解锁位。
     *
     * <p>与图鉴<b>取并集</b>使用，但<b>不写回图鉴</b>：它只是「让玩家能自由编辑自己已有的东西」
     * 这一条便利，不是「见过」。换一件物品就重算。
     */
    private final Map<ResourceLocation, Integer> itemUnlocks = new LinkedHashMap<>();

    private record Selection(int tierOrdinal, int level) {}

    public AscensionTableMenu(int windowId, Inventory inventory, BlockPos pos) {
        this(windowId, inventory, pos, inventory.player.level().isClientSide()
                ? ContainerLevelAccess.NULL
                : ContainerLevelAccess.create(inventory.player.level(), pos));
    }

    public AscensionTableMenu(int windowId, Inventory inventory, BlockPos pos, ContainerLevelAccess access) {
        super(UEMenus.ASCENSION_TABLE.get(), windowId);
        this.player = inventory.player;
        this.pos = pos;
        this.access = access;
        this.clientSide = inventory.player.level().isClientSide();

        // 面板 340×236：9 格宽 162 → 左边距 (340-162)/2 = 89
        this.addSlot(new Slot(this.itemSlot, 0, 10, 18));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 89 + col * 18, 159 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 89 + col * 18, 217));
        }

        this.addDataSlots(this.data);
        if (!this.clientSide) {
            this.refreshData();
        }
    }

    // ── 候选列表 ────────────────────────────────────────────────────────

    /**
     * 当前物品可用的附魔候选。
     *
     * <p>来源与 Enchanting Infuser 同源：<b>注册表</b> + {@code #in_enchanting_table} 标签，
     * 再按物品可用性过滤。刻意<b>不</b>排除物品已有的附魔——排除掉就没法给
     * 已有附魔<b>升阶</b>，而那正是这个方块存在的理由。
     */
    private List<Holder<Enchantment>> candidates() {
        ItemStack item = this.itemSlot.getItem(0);
        if (item.isEmpty()) {
            return List.of();
        }
        HolderLookup.RegistryLookup<Enchantment> lookup =
                this.player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        boolean isBook = item.is(net.minecraft.world.item.Items.BOOK)
                || item.is(net.minecraft.world.item.Items.ENCHANTED_BOOK);

        List<Holder<Enchantment>> out = new ArrayList<>();
        Set<ResourceLocation> added = new LinkedHashSet<>();
        for (Holder.Reference<Enchantment> holder : lookup.listElements().toList()) {
            if (!holder.is(EnchantmentTags.IN_ENCHANTING_TABLE)) {
                continue;
            }
            if (!isBook && !holder.value().canEnchant(item)) {
                continue;
            }
            if (added.add(holder.key().location())) {
                out.add(holder);
            }
        }

        // ⚠️ 物品上【已经有】的谱系必须无条件进列表。
        //
        // `#in_enchanting_table` 不含宝藏附魔（经验修补、灵魂疾行、迅捷潜行…），
        // 而预选会把它们选上并锁定。列表里若没有这一行，玩家就会得到一条
        // <b>看不见、也改不了</b>的选择 —— 这正是「预选」这个功能最不该有的副作用。
        //
        // 判据是「物品上有没有」，不是「能不能再附上去」：已经在物品上的附魔，
        // 按定义就是适用于这件物品的，{@code canEnchant} 不必再问一遍。
        Set<ResourceLocation> present =
                new LinkedHashSet<>(UEComponents.ascensionOf(item).lineages().keySet());
        for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(item).entrySet()) {
            entry.getKey().unwrapKey().map(k -> k.location()).ifPresent(present::add);
        }
        for (ResourceLocation rootId : present) {
            if (!added.add(rootId)) {
                continue;
            }
            Holder<Enchantment> holder = holderOf(rootId);
            if (holder != null) {
                out.add(holder);
            }
        }

        out.sort(Comparator.comparing(h -> h.unwrapKey().map(k -> k.location().toString()).orElse("")));
        return out;
    }

    /** 某谱系在某个阶级的等级上限；0 表示数据包没有铺这个阶级。 */
    private int maxLevelOf(ResourceLocation rootId, LineageTier tier) {
        if (tier == LineageTier.NATIVE) {
            Holder<Enchantment> root = holderOf(rootId);
            return root == null ? 0 : Math.max(1, root.value().getMaxLevel());
        }
        HolderLookup.RegistryLookup<StageDefinition> lookup = StageLookup.lookup();
        int datapackMax = StageLookup.maxLevelOf(lookup, tier, rootId, 0);
        if (datapackMax <= 0) {
            return 0;
        }
        // 装了神化时上限被抬高（附魔获得「超限」能力）；数据包可用 level_lock 设绝对天花板。
        Holder<Enchantment> root = holderOf(rootId);
        return root == null ? datapackMax
                : StageLookup.effectiveCap(root, tier, rootId, datapackMax);
    }

    @Nullable
    private Holder<Enchantment> holderOf(ResourceLocation rootId) {
        return this.player.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(rootId).orElse(null);
    }

    /**
     * 该阶级能不能选。
     *
     * <p>两个来源取并集：
     * <ol>
     *   <li><b>物品缓存</b>（{@link #itemUnlocks}）——这件物品自己带着的条目，
     *       它所在阶级<b>及其下级</b>一律可选；</li>
     *   <li>玩家的<b>图鉴</b>。</li>
     * </ol>
     *
     * <p>为什么要有第 1 条：物品可能是捡来的、别人给的、创造栏拿的 ——
     * 玩家从没「见过」它的书。若只看图鉴，就会出现
     * <b>「我手里这把剑带着高阶锋利，可我就是选不了它的高阶档」</b>，
     * 连改回自己原有的样子都做不到。<b>物品缓存只对这件物品上已有的谱系有效</b>，
     * 不会凭空解锁别的附魔，也不写回图鉴。
     */
    private boolean isUnlocked(ResourceLocation rootId, LineageTier tier) {
        if ((this.itemUnlocks.getOrDefault(rootId, 0) & unlockBit(tier)) != 0) {
            return true;
        }
        return this.player.getData(UEAttachments.CODEX).isUnlocked(rootId, tier);
    }

    // ── 图书馆 ──────────────────────────────────────────────────────────

    /**
     * 书架环内的全部图书馆——<b>多座池化供能</b>。
     *
     * <p>完全照原版附魔台的规矩：候选位置取 {@link EnchantingTableBlock#BOOKSHELF_OFFSETS}
     * （水平 ±2、垂直 ±1 的一圈），所以玩家不需要学新规则——把图书馆当书架摆就行。
     *
     * <p>⚠️ <b>还必须检查「中间那格是空气」</b>，这正是原版
     * {@code EnchantingTableBlock.isValidBookShelf} 的第二条判定：
     * <pre>
     *   level.getBlockState(tablePos.offset(offset)).is(BlockTags.ENCHANTMENT_POWER_PROVIDER)
     *       &amp;&amp; level.getBlockState(tablePos.offset(offset.getX()/2, offset.getY(), offset.getZ()/2)).isAir()
     * </pre>
     * 少了它，隔着一堵墙的图书馆照样供能——和原版不一致，玩家会觉得「怎么隔着墙也算」。
     *
     * <p><b>多对一</b>：环内每一座图书馆都是独立来源，{@link #pooledLevelEnergy} /
     * {@link #pooledTargetedBooks} / {@link #pooledGenericBooks} 把它们求和，
     * {@link #deductLevelEnergy} / {@link #consumeKeyBooks} 按顺序扣。
     * 所以「摆得越多、供得越足」是自然结果，不需要额外机制。
     */
    private List<EnchantmentLibraryBlockEntity> libraries() {
        List<EnchantmentLibraryBlockEntity> out = new ArrayList<>();
        if (this.clientSide) {
            return out;
        }
        for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
            if (!(this.player.level().getBlockEntity(this.pos.offset(offset))
                    instanceof EnchantmentLibraryBlockEntity library)) {
                continue;
            }
            // 原版第二条：中间那格必须是空气（否则隔着墙也供能）
            BlockPos between = this.pos.offset(offset.getX() / 2, offset.getY(), offset.getZ() / 2);
            if (!this.player.level().getBlockState(between).isAir()) {
                continue;
            }
            out.add(library);
        }
        return out;
    }

    /**
     * 环内所有图书馆在某个阶级上的<b>等级单位</b>总和。
     *
     * <p>v5 起只有一个家族（等级），所以不再需要「家族」这一维。
     * 仍然<b>不分谱系</b>：锋利存的和耐久存的进同一个桶。
     */
    private int pooledLevelEnergy(LineageTier tier) {
        int total = 0;
        for (EnchantmentLibraryBlockEntity library : this.libraries()) {
            total += library.levelEnergy(tier);
        }
        return total;
    }

    /** 按顺序从环内图书馆扣等级单位，直到扣满。调用前必须已校验过总额。 */
    private void deductLevelEnergy(LineageTier tier, int amount) {
        if (amount <= 0) {
            return;
        }
        int remaining = amount;
        for (EnchantmentLibraryBlockEntity library : this.libraries()) {
            if (remaining <= 0) {
                break;
            }
            if (library.spendLevelEnergy(tier, remaining)) {
                remaining = 0;
            } else {
                remaining -= library.levelEnergy(tier);
            }
        }
    }

    /** 把等级单位<b>退回</b>环内的图书馆（降级返还）。按顺序填，装不下就留在后面几座。 */
    private void refundLevelEnergy(LineageTier tier, int amount) {
        if (amount <= 0) {
            return;
        }
        int remaining = amount;
        for (EnchantmentLibraryBlockEntity library : this.libraries()) {
            if (remaining <= 0) {
                break;
            }
            remaining -= library.addLevelEnergy(tier, remaining);
        }
    }

    /** 环内图书馆里<b>该谱系</b>的定向进阶书总数。 */
    private int pooledTargetedBooks(ResourceLocation rootId, LineageTier tier) {
        int total = 0;
        for (EnchantmentLibraryBlockEntity library : this.libraries()) {
            total += library.targetedBooks(tier, rootId);
        }
        return total;
    }

    /** 环内图书馆里的<b>通用</b>进阶书总数。 */
    private int pooledGenericBooks(LineageTier tier) {
        int total = 0;
        for (EnchantmentLibraryBlockEntity library : this.libraries()) {
            total += library.genericBooks(tier);
        }
        return total;
    }

    /**
     * 消耗 {@code amount} 本钥匙书：<b>先把定向书用光，再动通用书</b>。
     *
     * <p>顺序必须与 {@link KeyBooks#hasEnough} 一致，否则会出现「显示有书、点了失败」。
     * 跨多座图书馆时也是「先扫一遍环内的定向、再扫一遍通用」——
     * 不是「按图书馆逐座用完」，否则先遇到的那座图书馆会用通用书顶掉后面的定向书。
     *
     * @return 是否全部扣到
     */
    private boolean consumeKeyBooks(ResourceLocation rootId, LineageTier tier, int amount) {
        for (int i = 0; i < amount; i++) {
            boolean taken = false;
            for (EnchantmentLibraryBlockEntity library : this.libraries()) {
                if (library.spendTargetedBook(tier, rootId)) {
                    taken = true;
                    break;
                }
            }
            if (!taken) {
                for (EnchantmentLibraryBlockEntity library : this.libraries()) {
                    if (library.spendGenericBook(tier)) {
                        taken = true;
                        break;
                    }
                }
            }
            if (!taken) {
                return false;
            }
        }
        return true;
    }

    /**
     * <b>物品上某条谱系当前的实际状态</b>（阶级 + 等级）；物品上没有这条谱系时返回 {@code null}。
     *
     * <p>这是<b>预选</b>与<b>差价</b>两件事的共同基准：
     * <ul>
     *   <li>已进阶的谱系：阶级取阶段条目的 {@code tier}，等级取 {@code ascension} 组件的
     *       <b>tierLevel</b>（曲线等级）；</li>
     *   <li>未进阶的谱系：阶级是 {@link LineageTier#NATIVE}，等级取物品
     *       {@code minecraft:enchantments} 里的<b>存储等级</b>。</li>
     * </ul>
     *
     * <p>⚠️ 已进阶的谱系<b>绝不能</b>用存储等级当基准：{@code writeAscension} 刻意把存储等级
     * 写成「该阶上限」（锋利升阶后存储仍是 5），拿它当基准会让玩家一开界面就看见一笔
     * 凭空的差价 —— 或者更糟：把曲线等级 8 当成「从 0 到 8」来收钱。
     */
    @Nullable
    private Selection currentSelectionOf(ResourceLocation rootId) {
        ItemStack item = this.itemSlot.getItem(0);
        if (item.isEmpty()) {
            return null;
        }
        AscensionData data = UEComponents.ascensionOf(item);
        ResourceLocation stageId = data.stages().get(rootId);
        if (stageId != null) {
            LineageTier tier = tierOfStageId(stageId);
            if (tier != null && tier != LineageTier.NATIVE) {
                return new Selection(tier.ordinal(), Math.max(1, data.tierLevelOf(rootId)));
            }
        }
        for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(item).entrySet()) {
            if (entry.getIntValue() <= 0) {
                continue;
            }
            ResourceLocation id = entry.getKey().unwrapKey().map(k -> k.location()).orElse(null);
            if (rootId.equals(id)) {
                return new Selection(LineageTier.NATIVE.ordinal(), entry.getIntValue());
            }
        }
        return null;
    }

    /** 物品上该谱系当前所处的阶级序号；物品上没有这条谱系时算<b>基础阶</b>（0）。 */
    private int currentOrdinalOf(ResourceLocation rootId) {
        Selection current = currentSelectionOf(rootId);
        return current == null ? LineageTier.NATIVE.ordinal() : current.tierOrdinal();
    }

    /**
     * 阶段条目 id → 阶级。
     *
     * <p>先查注册表（权威：数据包在条目里自己声明的 {@code tier}），查不到再退化到
     * 「路径首段就是阶级名」这个约定（{@link UERoots#tierOfStage} → {@code ProtectionLogic}）。
     */
    @Nullable
    private static LineageTier tierOfStageId(ResourceLocation stageId) {
        HolderLookup.RegistryLookup<StageDefinition> lookup = StageLookup.lookup();
        if (lookup != null) {
            LineageTier fromRegistry = StageLookup.byId(lookup, stageId)
                    .map(StageDefinition::tier).orElse(null);
            if (fromRegistry != null) {
                return fromRegistry;
            }
        }
        LineageTier[] values = LineageTier.values();
        int ordinal = UERoots.tierOfStage(stageId);
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    /**
     * <b>升阶要几本钥匙书、现在有没有</b>（v5）。
     *
     * <p>返回值是<b>带符号</b>的：
     * <ul>
     *   <li>{@code 0} —— 不需要（同阶级提级，或往低阶）</li>
     *   <li>{@code +N} —— 需要 N 本，环内图书馆有</li>
     *   <li>{@code -N} —— 需要 N 本，但缺 ⇒ 界面红字「缺书」，灌注会被 {@code STATUS_NO_KEY_BOOK} 拦下</li>
     * </ul>
     *
     * <p>「需要几本」的策略在 {@link KeyBooks#required}（那是唯一的开关）；
     * 「有没有」的判据在 {@link KeyBooks#hasEnough}。
     * <b>界面显示、灌注前校验、实际扣减三处都走这一条链</b>，不允许各写一份。
     */
    private int keyBookStateOf(ResourceLocation rootId, LineageTier tier) {
        return keyStateOf(rootId, tier).signed();
    }

    /**
     * 一行升阶的完整书信息：<b>带符号的本数</b> + <b>定向 / 通用分解</b> + <b>环内储备</b>。
     *
     * @param signed   0 不需要 / 正 N 够 / 负 N 缺
     * @param targeted 定向书库存（这条谱系）
     * @param generic  通用书库存
     */
    private record KeyState(int signed, int targeted, int generic) {
        int stock() {
            return this.targeted + this.generic;
        }
    }

    /** 一次算齐 —— tooltip 要用分解，不能只拿带符号的那个数去猜。 */
    private KeyState keyStateOf(ResourceLocation rootId, LineageTier tier) {
        int need = KeyBooks.required(currentOrdinalOf(rootId), tier.ordinal());
        int targeted = pooledTargetedBooks(rootId, tier);
        int generic = pooledGenericBooks(tier);
        if (need <= 0) {
            return new KeyState(0, targeted, generic);
        }
        return new KeyState(KeyBooks.hasEnough(targeted, generic, need) ? need : -need,
                targeted, generic);
    }

    /**
     * <b>提级花费（等级单位）</b>；带符号：<b>正 = 要付，负 = 退还</b>。
     *
     * <p>{@code = f(目标等级) - f(同阶级已有等级)}。
     *
     * <p>⚠️ v5 把 v4 的「中间阶级满级价」整段<b>删掉</b>了。那一项是为「完整路径定价」
     * 服务的：当时升阶花点数，所以要让「直接跳」不比「逐级走」便宜。现在升阶的代价
     * 是<b>书</b>本身，等级单位只管阶级内的量变 —— 公式没有存在的理由了。
     */
    private int levelCostOf(ResourceLocation rootId, LineageTier tier, int level) {
        Selection current = currentSelectionOf(rootId);
        int existing = current != null && current.tierOrdinal() == tier.ordinal()
                ? current.level() : 0;
        return EnergyMath.netCostToReach(level, existing);
    }

    // ── 预选（把物品上已有的附魔装进选择表）─────────────────────────────

    /**
     * <b>物品当前状态的「基线选择」</b>——每条已经在那件物品上的谱系，连同它现在所处的
     * 阶级与等级。这是预选的来源，也是 {@link #lockedRoots} 的成员表。
     */
    private Map<ResourceLocation, Selection> baselineSelections() {
        Map<ResourceLocation, Selection> out = new LinkedHashMap<>();
        ItemStack item = this.itemSlot.getItem(0);
        if (item.isEmpty()) {
            return out;
        }

        // ① 已进阶的谱系：以 ascension 组件为准（等级是曲线等级，不是存储等级）
        AscensionData data = UEComponents.ascensionOf(item);
        for (Map.Entry<ResourceLocation, AscensionData.Record> entry : data.lineages().entrySet()) {
            LineageTier tier = tierOfStageId(entry.getValue().stage());
            if (tier == null || tier == LineageTier.NATIVE) {
                continue;
            }
            if (maxLevelOf(entry.getKey(), tier) <= 0) {
                continue;   // 数据包不再铺这一档：不预选，否则一开界面就是「已锁定」
            }
            out.put(entry.getKey(), new Selection(tier.ordinal(),
                    Math.max(1, entry.getValue().tierLevel())));
        }

        // ② 基础阶（物品的 minecraft:enchantments）。① 见过的谱系一律跳过 ——
        //    它的存储等级是「该阶上限」，与玩家看到的曲线等级不是一回事。
        for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(item).entrySet()) {
            if (entry.getIntValue() <= 0) {
                continue;
            }
            ResourceLocation rootId = entry.getKey().unwrapKey().map(k -> k.location()).orElse(null);
            if (rootId == null || data.stages().containsKey(rootId)) {
                continue;
            }
            if (maxLevelOf(rootId, LineageTier.NATIVE) <= 0) {
                continue;
            }
            out.put(rootId, new Selection(LineageTier.NATIVE.ordinal(),
                    Math.max(1, entry.getIntValue())));
        }
        return out;
    }

    /**
     * <b>把物品上已有的附魔装进选择表</b>。
     *
     * <p>作者的原话：「往进阶台放一件已经有附魔的物品，选择表是空白的，玩家得手动重新选一遍。」
     * 现在放进去就是选中态，阶级与等级都照物品上的实际状态填。
     *
     * <p>⚠️ 刻意<b>不</b>自动滚动到第一条已有附魔 —— 作者明确说不需要。
     *
     * <p>⚠️ 等级<b>不向下夹取</b>：物品上的 tierLevel 可能高于当前能算出的上限
     * （装过神化的存档、数据包被改小）。把它夹到上限，玩家一按灌注就会把那条附魔
     * <b>降级</b> —— 白白掉等级，且完全看不出为什么。
     */
    private void rebuildSelections() {
        this.selections.clear();
        this.lockedRoots.clear();
        this.itemUnlocks.clear();
        Map<ResourceLocation, Selection> baseline = this.baselineSelections();
        this.selections.putAll(baseline);
        this.lockedRoots.addAll(baseline.keySet());

        // 物品缓存：这件物品自己带的条目，其所在阶级 + 所有下级阶级 ⇒ 可选。
        // ⚠️ 只对【物品上真有】的谱系生效 —— 不能顺手把别的附魔也解锁了。
        for (Map.Entry<ResourceLocation, Selection> entry : baseline.entrySet()) {
            int allowed = 0;
            for (LineageTier tier : LineageTier.values()) {
                if (tier.ordinal() <= entry.getValue().tierOrdinal()) {
                    allowed |= unlockBit(tier);
                }
            }
            this.itemUnlocks.put(entry.getKey(), allowed);
        }
    }

    // ── 按钮 ────────────────────────────────────────────────────────────

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BTN_APPLY) {
            this.apply();
            return true;
        }
        if (id == BTN_CLEAR) {
            // 「清空」只清得掉玩家新加的。物品上本来就有的谱系是锁定项，
            // 清完立刻按物品的实际状态重新填回去（它们不允许被取消选择）。
            this.selections.clear();
            Map<ResourceLocation, Selection> baseline = this.baselineSelections();
            for (ResourceLocation locked : this.lockedRoots) {
                Selection base = baseline.get(locked);
                if (base != null) {
                    this.selections.put(locked, base);
                }
            }
            this.status = STATUS_NONE;
            this.refreshData();
            return true;
        }
        if (id == BTN_SCROLL_UP || id == BTN_SCROLL_DOWN) {
            this.scroll = Math.max(0, id == BTN_SCROLL_UP ? this.scroll - 1 : this.scroll + 1);
            this.clampScroll();
            this.refreshData();
            return true;
        }
        if (id >= BTN_TIER_SET && id < BTN_TIER_SET + 400) {
            int packed = id - BTN_TIER_SET;
            this.selectTier(packed % 100, packed / 100);
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
            this.cycleLevel(id - BTN_LEVEL_UP, true);
            return true;
        }
        if (id >= BTN_LEVEL_DOWN && id < BTN_LEVEL_DOWN + ROWS) {
            this.cycleLevel(id - BTN_LEVEL_DOWN, false);
            return true;
        }
        return false;
    }

    /**
     * 直接选中某一档（界面上点阶级方块走这里）。
     *
     * <p>与 {@link #cycleTier} 的区别：循环是「下一个可用的」，这里是「就要这一档」。
     * 界面上四档并排可见，点哪档选哪档才符合直觉——循环那种做法玩家看不出总共几档。
     *
     * <p>未解锁 / 数据包没铺的档位<b>忽略</b>：界面已经把它画成暗色，
     * 服务端这里再兜一层，防止改包客户端直接发按钮 id。
     */
    private void selectTier(int rowIndex, int tierOrdinal) {
        ResourceLocation rootId = rootIdAt(rowIndex);
        LineageTier[] tiers = LineageTier.values();
        if (rootId == null || tierOrdinal < 0 || tierOrdinal >= tiers.length) {
            return;
        }
        LineageTier tier = tiers[tierOrdinal];
        int max = maxLevelOf(rootId, tier);
        // 点暗色方块必须说明【为什么点不动】——两种情况原因不同：
        //   ① 数据包根本没铺这一档（这条谱系没有这一阶）
        //   ② 铺了，但玩家的图鉴里还没解锁
        // 以前这里是同一个静默 return，玩家只会以为界面坏了。
        if (max <= 0) {
            this.status = STATUS_TIER_MISSING;
            this.refreshData();
            return;
        }
        if (!isUnlocked(rootId, tier)) {
            this.status = STATUS_TIER_LOCKED;
            this.refreshData();
            return;
        }
        Selection current = this.selections.get(rootId);
        // 再点一次同一档 = 取消这一行的选择（界面上点哪档选哪档，再点就该撤销）。
        // ⚠️ 但物品上【本来就有】的谱系是锁定项：不允许被取消（作者要求的保险）——
        //    预选是给玩家的便利，手一抖点掉就白预选了。
        //    切到【别的】阶级仍然允许，那正是升阶要做的事。
        if (current != null && current.tierOrdinal() == tierOrdinal) {
            if (this.lockedRoots.contains(rootId)) {
                return;
            }
            this.selections.remove(rootId);
            this.status = STATUS_NONE;
            this.refreshData();
            return;
        }
        int level = current == null ? 1 : Math.max(1, Math.min(current.level(), max));
        this.selections.put(rootId, new Selection(tierOrdinal, level));
        this.status = STATUS_NONE;
        this.refreshData();
    }

    /** 在「已解锁且数据包铺了」的阶级之间循环——锁着的阶级直接被跳过。 */
    private void cycleTier(int rowIndex, boolean up) {
        ResourceLocation rootId = rootIdAt(rowIndex);
        if (rootId == null) {
            return;
        }
        List<LineageTier> available = new ArrayList<>();
        for (LineageTier tier : LineageTier.values()) {
            if (maxLevelOf(rootId, tier) > 0 && isUnlocked(rootId, tier)) {
                available.add(tier);
            }
        }
        if (available.isEmpty()) {
            return;
        }
        Selection current = this.selections.get(rootId);
        int index = current == null ? -1 : available.indexOf(LineageTier.values()[current.tierOrdinal()]);
        int next = index < 0
                ? (up ? 0 : available.size() - 1)
                : Math.floorMod(index + (up ? 1 : -1), available.size());
        LineageTier tier = available.get(next);
        int level = current == null ? 1 : current.level();
        level = Math.max(1, Math.min(level, maxLevelOf(rootId, tier)));
        this.selections.put(rootId, new Selection(tier.ordinal(), level));
        this.status = STATUS_NONE;
        this.refreshData();
    }

    private void cycleLevel(int rowIndex, boolean up) {
        ResourceLocation rootId = rootIdAt(rowIndex);
        if (rootId == null) {
            return;
        }
        Selection current = this.selections.get(rootId);
        if (current == null) {
            this.cycleTier(rowIndex, true);
            return;
        }
        LineageTier tier = LineageTier.values()[current.tierOrdinal()];
        int max = Math.max(1, maxLevelOf(rootId, tier));
        int next = Math.max(1, Math.min(max, current.level() + (up ? 1 : -1)));
        this.selections.put(rootId, new Selection(current.tierOrdinal(), next));
        this.status = STATUS_NONE;
        this.refreshData();
    }

    // ── 灌注 ────────────────────────────────────────────────────────────

    private void apply() {
        if (this.clientSide) {
            return;
        }
        ItemStack item = this.itemSlot.getItem(0);
        if (item.isEmpty()) {
            this.status = STATUS_NO_ITEM;
            this.refreshData();
            return;
        }
        if (this.selections.isEmpty()) {
            this.status = STATUS_NO_SELECTION;
            this.refreshData();
            return;
        }

        // v5：升阶消耗【进阶书】（钥匙），提级消耗【等级单位】（燃料）。
        //      keyBooks = 带符号的钥匙书状态：>0 够、<0 缺、0 不需要（见 keyBookStateOf）。
        record Pending(ResourceLocation rootId, Holder<Enchantment> root, LineageTier tier,
                       int level, int keyBooks, int levelCost, int stageMax, boolean changed) {}

        List<Pending> pending = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Selection> entry : this.selections.entrySet()) {
            ResourceLocation rootId = entry.getKey();
            LineageTier tier = LineageTier.values()[entry.getValue().tierOrdinal()];
            if (!isUnlocked(rootId, tier)) {
                this.status = STATUS_LOCKED;
                this.refreshData();
                return;
            }
            int stageMax = maxLevelOf(rootId, tier);
            if (stageMax <= 0) {
                this.status = STATUS_LOCKED;
                this.refreshData();
                return;
            }
            Holder<Enchantment> root = holderOf(rootId);
            if (root == null) {
                continue;
            }
            Selection now = currentSelectionOf(rootId);
            int existingLevel = now != null && now.tierOrdinal() == tier.ordinal()
                    ? now.level() : 0;
            // ⚠️ 上限不能低于物品上已有的等级：物品的 tierLevel 可能高于现在算得出的
            //    effectiveCap（装过神化的存档 / 数据包被调小）。夹下去 = 静默降级。
            int ceiling = Math.max(stageMax, existingLevel);
            int level = Math.max(1, Math.min(entry.getValue().level(), ceiling));
            boolean changed = now == null
                    || now.tierOrdinal() != tier.ordinal()
                    || now.level() != level;
            pending.add(new Pending(rootId, root, tier, level,
                    keyBookStateOf(rootId, tier), levelCostOf(rootId, tier, level), stageMax, changed));
        }
        if (pending.isEmpty()) {
            this.status = STATUS_NO_SELECTION;
            this.refreshData();
            return;
        }

        // 预选会把「物品现在的样子」也一并放进选择表。已经处于目标状态的条目
        // 【不参与】这次操作：不发事件、不扣款、不重写组件 ——
        // 否则玩家每次点灌注都会对物品上每一条附魔发一次事件，
        // 第三方监听器一旦取消，整次操作都会被拦下（哪怕什么都没改）。
        List<Pending> todo = new ArrayList<>();
        for (Pending p : pending) {
            if (p.changed()) {
                todo.add(p);
            }
        }
        if (todo.isEmpty()) {
            // 物品已经是选择表描述的样子：成功，但无需任何改动。
            this.status = STATUS_OK;
            this.refreshData();
            return;
        }

        // ① 校验库存——钥匙书与等级单位分别校验。
        //    ⚠️ v5 起【基础阶提级也要花等级单位】（v3 是免费的，那是个平衡漏洞）。
        boolean anyLibrary = !this.libraries().isEmpty();
        for (Pending p : todo) {
            if (p.keyBooks() < 0) {
                // 缺书：这是升阶的<b>唯一</b>代价，没有替代品
                this.status = STATUS_NO_KEY_BOOK;
                this.refreshData();
                return;
            }
            if (p.keyBooks() == 0 && p.levelCost() <= 0) {
                continue;
            }
            if (!anyLibrary) {
                this.status = STATUS_NO_LIBRARY;
                this.refreshData();
                return;
            }
            // 再查一次书：refreshData 之后、apply 之前玩家的库存可能已经变过
            // （另一只手拆了图书馆、或者改包客户端直接发按钮 id）
            if (p.keyBooks() > 0 && !KeyBooks.hasEnough(pooledTargetedBooks(p.rootId(), p.tier()),
                    pooledGenericBooks(p.tier()), p.keyBooks())) {
                this.status = STATUS_NO_KEY_BOOK;
                this.refreshData();
                return;
            }
            if (p.levelCost() > 0 && pooledLevelEnergy(p.tier()) < p.levelCost()) {
                this.status = STATUS_NOT_ENOUGH;
                this.refreshData();
                return;
            }
        }

        // ② 事件（可取消）——必须在扣减之前
        for (Pending p : todo) {
            AscensionData before = UEComponents.ascensionOf(item);
            int oldTier = before.stageOf(p.rootId()).map(UERoots::tierOfStage).orElse(0);
            int oldCurve = before.tierLevelOf(p.rootId());
            if (!UEEvents.fireTierUpgrade(item, p.rootId(), p.root(), oldTier, p.tier().ordinal(),
                    oldCurve, p.level(), UltraEnchantTierUpgradeEvent.Source.ASCENSION_TABLE)) {
                this.status = STATUS_CANCELLED;
                this.refreshData();
                return;
            }
        }

        // ③ 扣库存（先扣后给，避免白拿窗口）——钥匙书与等级单位分别结算。
        //    levelCost 为【带符号】：正 = 扣，负 = 退还（降级返还）。
        for (Pending p : todo) {
            if (p.keyBooks() > 0) {
                consumeKeyBooks(p.rootId(), p.tier(), p.keyBooks());
            }
            if (p.levelCost() > 0) {
                deductLevelEnergy(p.tier(), p.levelCost());
            } else if (p.levelCost() < 0) {
                refundLevelEnergy(p.tier(), -p.levelCost());
            }
        }

        // ④ 写入
        for (Pending p : todo) {
            if (p.tier() == LineageTier.NATIVE) {
                ItemEnchantments.Mutable table =
                        new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(item));
                table.set(p.root(), Math.max(1, Math.min(p.level(), Math.min(p.root().value().getMaxLevel(), 255))));
                EnchantmentHelper.setEnchantments(item, table.toImmutable());
                continue;
            }
            ResourceLocation stageId = StageLookup.stageIdOf(StageLookup.lookup(), p.rootId(), p.tier()).orElse(null);
            if (stageId == null) {
                continue;
            }
            AscensionLogic.writeAscension(item, p.root(), p.rootId(), stageId, p.stageMax(), p.level());
            UEEvents.fireLockChange(item, p.rootId(), p.root(), true,
                    com.lyingice.ultraenchantment.api.event.UltraEnchantLockChangeEvent.Cause.API);
        }

        // 写回物品 ⇒ 触发 slotsChanged ⇒ 选择表按【新的】物品状态重新预选
        this.itemSlot.setChanged();
        this.status = STATUS_OK;
        this.refreshData();
    }

    // ── 同步 ────────────────────────────────────────────────────────────

    /** 服务端重算全部行数据。客户端不调（数据来自 ContainerData）。 */
    public void refreshData() {
        if (this.clientSide) {
            return;
        }
        List<Holder<Enchantment>> list = this.candidates();
        this.clampScrollTo(list.size());
        this.data.set(DATA_TOTAL, list.size());
        this.data.set(DATA_SCROLL, this.scroll);
        this.data.set(DATA_STATUS, this.status);

        for (int i = 0; i < ROWS; i++) {
            int index = this.scroll + i;
            int base = DATA_HEADER + i * DATA_STRIDE;
            if (index >= list.size()) {
                for (int f = 0; f < DATA_STRIDE; f++) {
                    this.data.set(base + f, 0);
                }
                this.data.set(base + F_ENCH, -1);
                this.data.set(base + F_TIER, -1);
                continue;
            }
            Holder<Enchantment> root = list.get(index);
            ResourceLocation rootId = root.unwrapKey().map(k -> k.location()).orElse(null);
            if (rootId == null) {
                this.data.set(base + F_ENCH, -1);
                this.data.set(base + F_TIER, -1);
                continue;
            }
            this.data.set(base + F_ENCH, this.enchantmentId(rootId));
            this.data.set(base + F_UNLOCK, this.unlockMask(rootId));
            for (LineageTier tier : LineageTier.values()) {
                this.data.set(base + F_MAX_BASE + tier.ordinal(), maxLevelOf(rootId, tier));
            }
            Selection selection = this.selections.get(rootId);
            if (selection == null) {
                this.data.set(base + F_TIER, -1);
                this.data.set(base + F_LEVEL, 0);
                this.data.set(base + F_KEY_BOOK, 0);
                this.data.set(base + F_AFFORDABLE, 0);
                this.data.set(base + F_LEVEL_COST, 0);
                this.data.set(base + F_KEY_TARGETED, 0);
                this.data.set(base + F_KEY_GENERIC, 0);
                this.data.set(base + F_LEVEL_STOCK, 0);
            } else {
                LineageTier tier = LineageTier.values()[selection.tierOrdinal()];
                KeyState keys = keyStateOf(rootId, tier);
                int keyBooks = keys.signed();
                int levelCost = levelCostOf(rootId, tier, selection.level());
                this.data.set(base + F_TIER, selection.tierOrdinal());
                this.data.set(base + F_LEVEL, selection.level());
                this.data.set(base + F_KEY_BOOK, keyBooks);
                this.data.set(base + F_LEVEL_COST, levelCost);
                // tooltip 要的三份库存：定向 / 通用 / 等级单位（都是环内合计）
                this.data.set(base + F_KEY_TARGETED, keys.targeted());
                this.data.set(base + F_KEY_GENERIC, keys.generic());
                this.data.set(base + F_LEVEL_STOCK, pooledLevelEnergy(tier));
                // 钥匙书够（或不需要）+ 等级单位够，才算「付得起」
                boolean afford = keyBooks >= 0;
                afford &= levelCost <= 0 || pooledLevelEnergy(tier) >= levelCost;
                this.data.set(base + F_AFFORDABLE, afford ? 1 : 0);
            }
        }
    }

    /** 附魔的注册表数字 id；不在注册表里返回 -1（ContainerData 只能传 int）。 */
    private int enchantmentId(ResourceLocation rootId) {
        Registry<Enchantment> registry =
                this.player.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        Enchantment value = registry.get(rootId);
        return value == null ? -1 : registry.getId(value);
    }

    /**
     * 某个阶级在 {@code ContainerData} 掩码里的位。
     *
     * <p>与 {@link CodexData} 的掩码<b>同一套</b>：三个进阶阶级占 0/1/2 位，
     * 基础阶占 {@link CodexData#NATIVE_BIT}。<b>界面与服务端必须走这一个函数</b> ——
     * 曾经界面那边写死了「基础方块恒亮」（只判 {@code maxLevels[0] > 0}，不看图鉴），
     * 而服务端 {@code selectTier} 照样查图鉴 ⇒ 表现为「所有基础方块都是亮的，可就是点不动」。
     */
    public static int unlockBit(LineageTier tier) {
        return tier == LineageTier.NATIVE ? CodexData.NATIVE_BIT : 1 << (tier.ordinal() - 1);
    }

    /**
     * 该谱系四档的解锁掩码——<b>基础阶也进掩码</b>（v4 起它不再恒解锁）。
     *
     * <p>⚠️ 必须与 {@link #isUnlocked} 的结果一致：界面拿掩码决定方块亮不亮，
     * 服务端拿 {@code isUnlocked} 决定点不点得动。两边不一致就是
     * 「亮着却点不动」（P1-46）或「能点却是暗的」（本轮的第 3 条反馈）。
     */
    private int unlockMask(ResourceLocation rootId) {
        CodexData codex = this.player.getData(UEAttachments.CODEX);
        // 物品缓存打底：这件物品自己带着的条目，其所在阶级及下级一律点亮
        int mask = this.itemUnlocks.getOrDefault(rootId, 0);
        for (LineageTier tier : LineageTier.values()) {
            if (codex.isUnlocked(rootId, tier)) {
                mask |= unlockBit(tier);
            }
        }
        return mask;
    }

    // ── 客户端读取 ──────────────────────────────────────────────────────

    /**
     * 一行显示数据。两侧通用。
     *
     * @param keyBook     v5：进阶书状态。<b>0</b> 不需要 / <b>正 N</b> 需要 N 本且够 / <b>负 N</b> 缺 N 本
     * @param affordable  书够 + 等级单位够
     * @param levelCost   提级花费（带符号：负 = 退还）
     * @param keyTargeted 定向书库存（环内，这条谱系）—— 只给 tooltip
     * @param keyGeneric  通用书库存（环内）—— 只给 tooltip
     * @param levelStock  等级单位库存（环内，该阶级）—— 只给 tooltip
     */
    public record Row(Holder<Enchantment> root, ResourceLocation rootId, int unlockMask,
                      int[] maxLevels, int tierOrdinal, int level,
                      int keyBook, boolean affordable, int levelCost,
                      int keyTargeted, int keyGeneric, int levelStock) {

        /** 环内书的总储备。 */
        public int bookStock() {
            return this.keyTargeted + this.keyGeneric;
        }
    }

    @Nullable
    public Row rowAt(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= ROWS) {
            return null;
        }
        int base = DATA_HEADER + rowIndex * DATA_STRIDE;
        int enchantmentId = this.data.get(base + F_ENCH);
        if (enchantmentId < 0) {
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
        int[] maxLevels = new int[LineageTier.values().length];
        for (int i = 0; i < maxLevels.length; i++) {
            maxLevels[i] = this.data.get(base + F_MAX_BASE + i);
        }
        return new Row(root, rootId, this.data.get(base + F_UNLOCK), maxLevels,
                this.data.get(base + F_TIER), this.data.get(base + F_LEVEL),
                this.data.get(base + F_KEY_BOOK), this.data.get(base + F_AFFORDABLE) != 0,
                this.data.get(base + F_LEVEL_COST),
                this.data.get(base + F_KEY_TARGETED), this.data.get(base + F_KEY_GENERIC),
                this.data.get(base + F_LEVEL_STOCK));
    }

    private ResourceLocation rootIdAt(int rowIndex) {
        Row row = this.rowAt(rowIndex);
        return row == null ? null : row.rootId();
    }

    public int totalEntries() {
        return this.data.get(DATA_TOTAL);
    }

    public int scrollOffset() {
        return this.data.get(DATA_SCROLL);
    }

    public int status() {
        return this.data.get(DATA_STATUS);
    }

    public boolean canScrollUp() {
        return this.scrollOffset() > 0;
    }

    public boolean canScrollDown() {
        return this.scrollOffset() + ROWS < this.totalEntries();
    }

    private void clampScroll() {
        this.clampScrollTo(this.data.get(DATA_TOTAL));
    }

    private void clampScrollTo(int total) {
        this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, total - ROWS)));
    }

    // ── 容器契约 ────────────────────────────────────────────────────────

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (!this.clientSide) {
            // 换物品 = 重算选择表（候选与花费都会变）。
            // ⚠️ 这里【不再】只是 clear()：物品上已有的附魔要自动装进选择表（预选），
            //    并把它们的谱系记成锁定项（不允许被取消选择）。
            this.rebuildSelections();
            this.status = STATUS_NONE;
            this.refreshData();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, UEBlocks.ASCENSION_TABLE.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == ITEM_SLOT) {
            if (!this.moveItemStackTo(stack, PLAYER_INV_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, ITEM_SLOT, ITEM_SLOT + 1, false)) {
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
        this.access.execute((level, pos) -> clearContainer(player, this.itemSlot));
    }

    /** {@link ContainerData} 的朴素实现。 */
    private static final class TableData implements ContainerData {
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
