package com.lyingice.ultraenchantment.menu;

import com.lyingice.ultraenchantment.api.event.UltraEnchantTierUpgradeEvent;
import com.lyingice.ultraenchantment.block.entity.EnchantmentLibraryBlockEntity;
import com.lyingice.ultraenchantment.content.AscensionData;
import com.lyingice.ultraenchantment.content.AscensionTier;
import com.lyingice.ultraenchantment.content.CodexData;
import com.lyingice.ultraenchantment.content.LibraryKey;
import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.content.StageDefinition;
import com.lyingice.ultraenchantment.logic.AscensionLogic;
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
import java.util.List;
import java.util.Map;
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
 * 每条附魔旁边可以切换 原生 / 高阶 / 超级 / 究极，再在<b>该阶级内</b>调等级。
 *
 * <h2>代价不是经验，是图书馆库存</h2>
 *
 * <p>每行的花费按 {@code 2^(level-1)} 折算成点数，从<b>附近</b>的附魔图书馆里扣。
 * 「附近」用原版附魔台同一套书架环（{@link EnchantingTableBlock#BOOKSHELF_OFFSETS}），
 * 玩家不需要学新规则。
 *
 * <p>多座图书馆在环内时<b>池化</b>：校验看总量，扣减按顺序摊。
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

    private static final int DATA_TOTAL = 0;
    private static final int DATA_SCROLL = 1;
    private static final int DATA_STATUS = 2;
    private static final int DATA_HEADER = 3;
    private static final int DATA_STRIDE = 10;
    private static final int DATA_SIZE = DATA_HEADER + ROWS * DATA_STRIDE;

    private static final int F_ENCH = 0;
    private static final int F_UNLOCK = 1;
    private static final int F_MAX_BASE = 2;      // 四个阶级的上限占 2..5
    private static final int F_TIER = 6;          // 选中阶级 ordinal，-1 = 未选
    private static final int F_LEVEL = 7;         // 选中等级，0 = 未选
    private static final int F_COST = 8;
    private static final int F_AFFORDABLE = 9;

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

        // 面板 320×258：9 格宽 162 → 左边距 (320-162)/2 = 79
        this.addSlot(new Slot(this.itemSlot, 0, 10, 18));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 79 + col * 18, 164 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 79 + col * 18, 218));
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
        for (Holder.Reference<Enchantment> holder : lookup.listElements().toList()) {
            if (!holder.is(EnchantmentTags.IN_ENCHANTING_TABLE)) {
                continue;
            }
            if (!isBook && !holder.value().canEnchant(item)) {
                continue;
            }
            out.add(holder);
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
        return StageLookup.maxLevelOf(lookup, tier, rootId, 0);
    }

    @Nullable
    private Holder<Enchantment> holderOf(ResourceLocation rootId) {
        return this.player.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(rootId).orElse(null);
    }

    /** 该阶级对玩家是否已解锁（原生阶恒为真）。 */
    private boolean isUnlocked(ResourceLocation rootId, LineageTier tier) {
        CodexData codex = this.player.getData(UEAttachments.CODEX);
        return codex.isUnlocked(rootId, tier);
    }

    // ── 图书馆 ──────────────────────────────────────────────────────────

    /** 书架环内的全部图书馆。 */
    private List<EnchantmentLibraryBlockEntity> libraries() {
        List<EnchantmentLibraryBlockEntity> out = new ArrayList<>();
        if (this.clientSide) {
            return out;
        }
        for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
            if (this.player.level().getBlockEntity(this.pos.offset(offset))
                    instanceof EnchantmentLibraryBlockEntity library) {
                out.add(library);
            }
        }
        return out;
    }

    /** 环内所有图书馆在该 (谱系, 阶级) 上的点数总和。 */
    private int pooledPoints(ResourceLocation rootId, LineageTier tier) {
        AscensionTier ascent = AscensionTier.of(tier).orElse(null);
        if (ascent == null) {
            return 0;
        }
        LibraryKey key = new LibraryKey(rootId, ascent);
        int total = 0;
        for (EnchantmentLibraryBlockEntity library : this.libraries()) {
            total += library.pointsOf(key);
        }
        return total;
    }

    /** 按顺序从环内图书馆扣减，直到扣满。调用前必须已校验过总额。 */
    private void deduct(ResourceLocation rootId, LineageTier tier, int amount) {
        AscensionTier ascent = AscensionTier.of(tier).orElse(null);
        if (ascent == null || amount <= 0) {
            return;
        }
        LibraryKey key = new LibraryKey(rootId, ascent);
        int remaining = amount;
        for (EnchantmentLibraryBlockEntity library : this.libraries()) {
            if (remaining <= 0) {
                break;
            }
            remaining -= library.spend(key, remaining);
        }
    }

    /** 本次选择的点数花费：按曲线等级折算，同阶级已有等级可抵扣。 */
    private int costOf(ResourceLocation rootId, LineageTier tier, int level) {
        int existing = 0;
        AscensionData data = UEComponents.ascensionOf(this.itemSlot.getItem(0));
        ResourceLocation currentStage = data.stageOf(rootId).orElse(null);
        if (currentStage != null && UERoots.tierOfStage(currentStage) == tier.ordinal()) {
            existing = data.tierLevelOf(rootId);
        }
        return EnchantmentLibraryBlockEntity.costToReach(level, existing);
    }

    // ── 按钮 ────────────────────────────────────────────────────────────

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BTN_APPLY) {
            this.apply();
            return true;
        }
        if (id == BTN_CLEAR) {
            this.selections.clear();
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
        if (max <= 0 || !isUnlocked(rootId, tier)) {
            return;
        }
        Selection current = this.selections.get(rootId);
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

        record Pending(ResourceLocation rootId, Holder<Enchantment> root, LineageTier tier,
                       int level, int cost, int stageMax) {}

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
            int level = Math.max(1, Math.min(entry.getValue().level(), stageMax));
            pending.add(new Pending(rootId, root, tier, level, costOf(rootId, tier, level), stageMax));
        }
        if (pending.isEmpty()) {
            this.status = STATUS_NO_SELECTION;
            this.refreshData();
            return;
        }

        // ① 校验库存（原生阶不消耗库存）
        boolean anyLibrary = !this.libraries().isEmpty();
        for (Pending p : pending) {
            if (p.tier() == LineageTier.NATIVE) {
                continue;
            }
            if (!anyLibrary) {
                this.status = STATUS_NO_LIBRARY;
                this.refreshData();
                return;
            }
            if (pooledPoints(p.rootId(), p.tier()) < p.cost()) {
                this.status = STATUS_NOT_ENOUGH;
                this.refreshData();
                return;
            }
        }

        // ② 事件（可取消）——必须在扣减之前
        for (Pending p : pending) {
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

        // ③ 扣库存（先扣后给，避免白拿窗口）
        for (Pending p : pending) {
            if (p.tier() != LineageTier.NATIVE) {
                deduct(p.rootId(), p.tier(), p.cost());
            }
        }

        // ④ 写入
        for (Pending p : pending) {
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

        this.itemSlot.setChanged();
        this.selections.clear();
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
                this.data.set(base + F_COST, 0);
                this.data.set(base + F_AFFORDABLE, 0);
            } else {
                LineageTier tier = LineageTier.values()[selection.tierOrdinal()];
                int cost = costOf(rootId, tier, selection.level());
                this.data.set(base + F_TIER, selection.tierOrdinal());
                this.data.set(base + F_LEVEL, selection.level());
                this.data.set(base + F_COST, cost);
                this.data.set(base + F_AFFORDABLE,
                        tier == LineageTier.NATIVE || pooledPoints(rootId, tier) >= cost ? 1 : 0);
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

    private int unlockMask(ResourceLocation rootId) {
        CodexData codex = this.player.getData(UEAttachments.CODEX);
        int mask = 0;
        for (AscensionTier tier : AscensionTier.values()) {
            if (codex.isUnlocked(rootId, tier)) {
                mask |= 1 << tier.ordinal();
            }
        }
        return mask;
    }

    // ── 客户端读取 ──────────────────────────────────────────────────────

    /** 一行显示数据。两侧通用。 */
    public record Row(Holder<Enchantment> root, ResourceLocation rootId, int unlockMask,
                      int[] maxLevels, int tierOrdinal, int level, int cost, boolean affordable) {}

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
                this.data.get(base + F_COST), this.data.get(base + F_AFFORDABLE) != 0);
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
            this.selections.clear();   // 换物品 = 清空选择（候选与花费都会变）
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
