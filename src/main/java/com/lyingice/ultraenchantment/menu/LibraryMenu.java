package com.lyingice.ultraenchantment.menu;

import com.lyingice.ultraenchantment.block.entity.EnchantmentLibraryBlockEntity;
import com.lyingice.ultraenchantment.content.AscensionTier;
import com.lyingice.ultraenchantment.content.BookSpecs;
import com.lyingice.ultraenchantment.content.CodexData;
import com.lyingice.ultraenchantment.content.LibraryKey;
import com.lyingice.ultraenchantment.content.StageDefinition;
import com.lyingice.ultraenchantment.logic.BookFactory;
import com.lyingice.ultraenchantment.logic.StageLookup;
import com.lyingice.ultraenchantment.registry.UEAttachments;
import com.lyingice.ultraenchantment.registry.UEBlocks;
import com.lyingice.ultraenchantment.registry.UEComponents;
import com.lyingice.ultraenchantment.registry.UEItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
 * <b>附魔图书馆的容器菜单</b>。
 *
 * <h2>交互模型</h2>
 *
 * <ul>
 *   <li><b>存入</b>：把铭刻书放进输入槽 → 立刻吸收进图书馆，槽位清空</li>
 *   <li><b>取出</b>：点列表某一行 → 产出一本该 (谱系, 阶级) 的铭刻书到输出槽，扣点数</li>
 *   <li><b>滚动</b>：列表可滚动（滚轮或两侧箭头）</li>
 * </ul>
 *
 * <h2>「合并等级」是怎么发生的</h2>
 *
 * <p>点数按 {@code 2^(level-1)} 累加，取出时按同一公式扣。于是
 * 「两本 2 级（各 2 点）→ 4 点 → 取出一本 3 级（4 点）」——
 * <b>合并不需要任何特殊代码，它是点数换算的自然结果</b>。
 *
 * <h2>⚠️ 存入的原子性</h2>
 *
 * <p>整本书<b>要么全部吸收、要么原样留在槽里</b>。逐条吸收会让
 * 「部分条目已达上限」的书被吞掉一半价值——玩家看不见这个损失，
 * 属于静默吞钱。因此先 {@code canAccept} 全量校验，通过才逐条写入。
 *
 * <h2>同步：只用 ContainerData，不加网络包</h2>
 *
 * <p>列表内容通过 {@link ContainerData}（int 数组）同步：
 * 每行 4 个 int =「附魔注册表数字 id / 阶级序号 / 点数 / 已达等级」，
 * 外加表头的「总数 / 滚动偏移」。附魔用<b>数字 id</b> 而不是 id 字符串，
 * 因为 ContainerData 只能传 int——而注册表数字 id 恰好是稳定且两侧一致的。
 *
 * <p>服务端每次改动后调 {@link #refreshData()}；原版
 * {@code ServerPlayer} 每 tick 调 {@code broadcastChanges()}，
 * 差异会被自动下发。因此<b>不需要自定义网络包</b>。
 */
public class LibraryMenu extends AbstractContainerMenu {

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int PLAYER_INV_START = 2;

    /** 界面里可见的行数。 */
    public static final int ROWS = 8;

    // ── 按钮 id 分区（走原版 clickMenuButton，无需自定义包）──
    /** {@code [0, ROWS)}：取出（用已达最高等级）。 */
    public static final int BTN_EXTRACT_MAX = 0;
    /** {@code [100, 100+ROWS)}：取出（固定 1 级）。 */
    public static final int BTN_EXTRACT_ONE = 100;
    public static final int BTN_SCROLL_UP = 200;
    public static final int BTN_SCROLL_DOWN = 201;

    private static final int DATA_HEADER = 2;
    private static final int DATA_STRIDE = 4;
    private static final int DATA_SIZE = DATA_HEADER + ROWS * DATA_STRIDE;
    private static final int DATA_TOTAL = 0;
    private static final int DATA_SCROLL = 1;

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

    /** 一行列表内容。两侧都从这里读，保证渲染与服务端判定同源。 */
    public record Row(Holder<Enchantment> root, AscensionTier tier, int points, int maxLevel) {}

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

        this.addSlot(new Slot(this.input, 0, 10, 18) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isDepositable(stack);
            }
        });
        this.addSlot(new Slot(this.output, 0, 34, 18) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;   // 只出不进
            }
        });

        // 面板 220×230：9 格宽 162 → 左边距 (220-162)/2 = 29
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 29 + col * 18, 150 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 29 + col * 18, 206));
        }

        this.addDataSlots(this.data);
        if (!this.clientSide) {
            this.refreshData();
        }
    }

    // ── 存入 ────────────────────────────────────────────────────────────

    /** 这本书能否被图书馆吸收——槽位的 {@code mayPlace} 也用它。 */
    public static boolean isDepositable(ItemStack stack) {
        if (!UEItems.isAdvancedBook(stack)) {
            return false;
        }
        BookSpecs.Inscription payload = stack.get(UEComponents.INSCRIPTION_SPEC.get());
        return payload != null && !payload.isEmpty();
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

    /** 把输入槽的铭刻书整本吸收。任一条目无法吸收则整本留下。 */
    private void tryDeposit() {
        ItemStack stack = this.input.getItem(0);
        if (stack.isEmpty()) {
            return;
        }
        EnchantmentLibraryBlockEntity library = this.library();
        if (library == null) {
            return;
        }

        BookSpecs.Inscription payload = stack.get(UEComponents.INSCRIPTION_SPEC.get());
        if (payload == null || payload.isEmpty()) {
            return;
        }
        HolderLookup.RegistryLookup<StageDefinition> lookup = StageLookup.lookup();

        List<LibraryKey> keys = new ArrayList<>(payload.entries().size());
        List<Integer> levels = new ArrayList<>(payload.entries().size());
        List<Integer> caps = new ArrayList<>(payload.entries().size());
        for (BookSpecs.Inscription.Entry entry : payload.entries()) {
            LibraryKey key = new LibraryKey(entry.enchantment(), payload.tier());
            keys.add(key);
            levels.add(entry.level());
            caps.add(StageLookup.maxLevelOf(lookup, payload.tier().asLineageTier(),
                    entry.enchantment(), entry.level()));
        }

        // 先全量校验再写入：部分吸收会静默吞掉玩家的书
        for (int i = 0; i < keys.size(); i++) {
            if (!library.canAccept(keys.get(i), levels.get(i), caps.get(i))) {
                return;
            }
        }
        for (int i = 0; i < keys.size(); i++) {
            library.deposit(keys.get(i), levels.get(i), caps.get(i));
        }
        this.input.setItem(0, ItemStack.EMPTY);

        this.onDeposited(keys);
    }

    /**
     * 存入成功后<b>解锁图鉴</b>。
     *
     * <p>解锁是幂等的（{@link CodexData#with} 已解锁时返回自身），
     * 因此这里只在真的发生变化时才写回玩家数据——否则每次存入都会
     * 触发一次无意义的玩家数据写入。
     *
     * <p><b>解锁与存入是两件事</b>：存入改变的是图书馆的库存（方块），
     * 解锁改变的是玩家的图鉴（玩家）。同一本书会同时影响两者。
     */
    protected void onDeposited(List<LibraryKey> keys) {
        if (this.clientSide) {
            return;
        }
        CodexData before = this.player.getData(UEAttachments.CODEX);
        CodexData after = before;
        for (LibraryKey key : keys) {
            after = after.with(key.root(), key.tier());
        }
        if (after != before) {
            this.player.setData(UEAttachments.CODEX, after);
        }
    }

    // ── 取出 ────────────────────────────────────────────────────────────

    private void extract(int rowIndex, boolean lowestLevel) {
        if (this.clientSide || !this.output.getItem(0).isEmpty()) {
            return;
        }
        LibraryKey key = this.keyAt(rowIndex);
        EnchantmentLibraryBlockEntity library = this.library();
        if (key == null || library == null) {
            return;
        }
        int seen = library.maxLevelOf(key);
        if (seen <= 0) {
            return;
        }
        int stageMax = this.stageMaxLevelOf(key, seen);
        // 目标等级 = min(见过的天花板, 点数够到的等级)。
        // 由点数反推是「合并」的关键——详见 EnchantmentLibraryBlockEntity.affordableLevel。
        int target = lowestLevel
                ? 1
                : Math.min(seen, EnchantmentLibraryBlockEntity.affordableLevel(library.pointsOf(key), 0));
        if (target < 1) {
            return;
        }
        if (!library.canExtract(key, target, 0, stageMax)) {
            return;
        }
        library.extract(key, target, 0, stageMax);
        this.output.setItem(0, BookFactory.inscription(new BookSpecs.Inscription(
                key.tier(), List.of(new BookSpecs.Inscription.Entry(key.root(), target)))));
        this.refreshData();
    }

    /** 该 (谱系, 阶级) 的等级上限——取自数据包，取不到时退化为图书馆已见上限。 */
    private int stageMaxLevelOf(LibraryKey key, int fallback) {
        HolderLookup.RegistryLookup<StageDefinition> lookup = StageLookup.lookup();
        if (lookup == null) {
            return fallback;
        }
        return StageLookup.stageOf(lookup, key.root(), key.tier().asLineageTier())
                .map(stage -> stage.definition().maxLevel())
                .orElse(fallback);
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
        if (id >= BTN_EXTRACT_ONE && id < BTN_EXTRACT_ONE + ROWS) {
            this.extract(id - BTN_EXTRACT_ONE, true);
            return true;
        }
        if (id >= BTN_EXTRACT_MAX && id < BTN_EXTRACT_MAX + ROWS) {
            this.extract(id - BTN_EXTRACT_MAX, false);
            return true;
        }
        return false;
    }

    // ── 列表同步 ────────────────────────────────────────────────────────

    /** 服务端重算列表并写入 ContainerData。 */
    public void refreshData() {
        EnchantmentLibraryBlockEntity library = this.library();
        List<LibraryKey> keys = new ArrayList<>();
        if (library != null) {
            keys.addAll(library.keys());
        }
        // 稳定排序：谱系字典序 → 阶级枚举序。不排序会让行位置随机跳动
        keys.sort(Comparator.comparing((LibraryKey k) -> k.root().toString())
                .thenComparingInt(k -> k.tier().ordinal()));

        this.clampScrollTo(keys.size());
        this.data.set(DATA_TOTAL, keys.size());
        this.data.set(DATA_SCROLL, this.scroll);

        Registry<Enchantment> registry =
                this.player.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (int i = 0; i < ROWS; i++) {
            int index = this.scroll + i;
            int base = DATA_HEADER + i * DATA_STRIDE;
            if (library == null || index >= keys.size()) {
                this.data.set(base, -1);
                this.data.set(base + 1, 0);
                this.data.set(base + 2, 0);
                this.data.set(base + 3, 0);
                continue;
            }
            LibraryKey key = keys.get(index);
            this.data.set(base, registry.getId(key.root()));
            this.data.set(base + 1, key.tier().ordinal());
            this.data.set(base + 2, library.pointsOf(key));
            this.data.set(base + 3, library.maxLevelOf(key));
        }
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
        return new Row(root, tiers[tierOrdinal], this.data.get(base + 2), this.data.get(base + 3));
    }

    /** 第 {@code rowIndex} 行对应的键（服务端取出用）。 */
    @Nullable
    public LibraryKey keyAt(int rowIndex) {
        Row row = this.rowAt(rowIndex);
        return row == null ? null : new LibraryKey(row.root().unwrapKey().map(k -> k.location()).orElse(null),
                row.tier());
    }

    public int totalEntries() {
        return this.data.get(DATA_TOTAL);
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
