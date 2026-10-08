package com.lyingice.ultraenchantment.block.entity;

import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.logic.EnergyMath;
import com.lyingice.ultraenchantment.registry.UEBlockEntities;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * <b>附魔图书馆的存储</b>——v5「阶级书库存化」。
 *
 * <h2>v5 改了什么</h2>
 *
 * <p>v4 存的是<b>8 类抽象能量</b>（2 家族 × 4 阶级）。其中「阶级单位」（点数）在 v5 被
 * <b>整个删掉</b>，换成<b>进阶书库存计数</b>：
 *
 * <pre>
 *                    基础   高阶   超级   究极
 * 等级单位（提级）   有     有     有     有      ← 仍然是点数，f(L) = 2^(L-1)
 * 通用进阶书          ——     ×N     ×N     ×N     ← 本数，可升任意谱系
 * 定向进阶书          ——     ×N     ×N     ×N     ← 本数，只升它钉住的那条谱系
 * </pre>
 *
 * <p><b>为什么</b>：点数把「一把钥匙」拆成了「五枚硬币」——玩家会去算「我还差 3 点」，
 * 而进阶书的直觉是「我有这本就能跨过去」。书本就是离散的，折成点数是**多余的中间层**。
 * 顺带：4:1 向上兑换也随之删除（它把低阶书的稀缺性抹平了）。
 *
 * <h2>三块存储</h2>
 *
 * <ul>
 *   <li>{@link #levelEnergy} —— 等级单位，按 {@link LineageTier#ordinal()} 索引，<b>4 档</b>
 *       （基础阶也有效：原版附魔书存进来产出的就是「等级单位·基础」）</li>
 *   <li>{@link #genericBooks} —— 通用进阶书，按阶级计数，<b>只有 3 档</b>（基础档没有进阶书）</li>
 *   <li>{@link #targetedBooks} —— 定向进阶书，按（阶级 → 谱系）计数</li>
 * </ul>
 *
 * <h2>旧存档（v4）</h2>
 *
 * <p>{@code energy} 字段里的 {@code level|<tier>} <b>照旧读进来</b>；
 * {@code ascension|<tier>} <b>直接丢弃</b>（点数是「元」，书是「本」，无法无损换算，
 * 强行换算只会引入一堆边界规则——开发期不做）。
 */
public class EnchantmentLibraryBlockEntity extends BlockEntity {

    /** v4 的存档键。<b>只读</b>，只为迁移：只取其中的 {@code level|} 条目。 */
    public static final String TAG_LEGACY_ENERGY = "energy";
    /** 等级单位：{@code {native: n, advanced: n, ...}}，键是 {@link LineageTier#id()}。 */
    public static final String TAG_LEVEL_ENERGY = "level_energy";
    /** 通用进阶书：{@code {advanced: 2}}。 */
    public static final String TAG_GENERIC_BOOKS = "generic_books";
    /** 定向进阶书：{@code {advanced: {sharpness: 1}}}（里层键是完整 {@code namespace:path}）。 */
    public static final String TAG_TARGETED_BOOKS = "targeted_books";

    /** 单条书库存的上限——给 GUI 一个确定的天花板，也让「整本吸收」的原子性有明确判据。 */
    public static final int MAX_BOOKS = 999;

    private final int[] levelEnergy = new int[LineageTier.values().length];
    private final int[] genericBooks = new int[LineageTier.values().length];
    private final EnumMap<LineageTier, Object2IntMap<ResourceLocation>> targetedBooks =
            new EnumMap<>(LineageTier.class);

    public EnchantmentLibraryBlockEntity(BlockPos pos, BlockState state) {
        super(UEBlockEntities.ENCHANTMENT_LIBRARY.get(), pos, state);
    }

    /** 基础阶没有进阶书——所有书库存都只认这三档。 */
    public static boolean isBookTier(LineageTier tier) {
        return tier != LineageTier.NATIVE;
    }

    // ── 等级单位 ────────────────────────────────────────────────────────

    public int levelEnergy(LineageTier tier) {
        return this.levelEnergy[tier.ordinal()];
    }

    /** 该桶还能装多少（到 {@link EnergyMath#MAX_ENERGY} 为止）。 */
    public int roomForLevel(LineageTier tier) {
        return Math.max(0, EnergyMath.MAX_ENERGY - this.levelEnergy[tier.ordinal()]);
    }

    public boolean canSpendLevel(LineageTier tier, int amount) {
        return amount <= 0 || this.levelEnergy[tier.ordinal()] >= amount;
    }

    /** 加等级单位，夹在上限以内；返回实际加进去的数量。 */
    public int addLevelEnergy(LineageTier tier, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int index = tier.ordinal();
        int next = EnergyMath.cappedAdd(this.levelEnergy[index], amount);
        int added = next - this.levelEnergy[index];
        if (added > 0) {
            this.levelEnergy[index] = next;
            this.setChanged();
        }
        return added;
    }

    /** 扣等级单位。<b>全有或全无</b>：不够就一个都不扣。 */
    public boolean spendLevelEnergy(LineageTier tier, int amount) {
        if (amount <= 0) {
            return true;
        }
        int index = tier.ordinal();
        if (this.levelEnergy[index] < amount) {
            return false;
        }
        this.levelEnergy[index] -= amount;
        this.setChanged();
        return true;
    }

    // ── 进阶书库存 ──────────────────────────────────────────────────────

    public int genericBooks(LineageTier tier) {
        return isBookTier(tier) ? this.genericBooks[tier.ordinal()] : 0;
    }

    public int targetedBooks(LineageTier tier, ResourceLocation rootId) {
        if (!isBookTier(tier)) {
            return 0;
        }
        Object2IntMap<ResourceLocation> map = this.targetedBooks.get(tier);
        return map == null ? 0 : map.getInt(rootId);
    }

    /** 该阶级的定向书总数（不分谱系）——GUI 用。 */
    public int targetedBookTotal(LineageTier tier) {
        if (!isBookTier(tier)) {
            return 0;
        }
        Object2IntMap<ResourceLocation> map = this.targetedBooks.get(tier);
        if (map == null) {
            return 0;
        }
        int sum = 0;
        for (int value : map.values()) {
            sum += value;
        }
        return sum;
    }

    /** 该阶级的书库存总量（通用 + 定向）——GUI 的「×N」。 */
    public int totalBooks(LineageTier tier) {
        return this.genericBooks(tier) + this.targetedBookTotal(tier);
    }

    /** 该阶级的定向书明细，按<b>数量降序 → 谱系字典序</b>排序（GUI 行序不跳）。 */
    public List<Map.Entry<ResourceLocation, Integer>> targetedEntries(LineageTier tier) {
        List<Map.Entry<ResourceLocation, Integer>> out = new ArrayList<>();
        Object2IntMap<ResourceLocation> map = this.targetedBooks.get(tier);
        if (map != null) {
            for (Object2IntMap.Entry<ResourceLocation> entry : map.object2IntEntrySet()) {
                if (entry.getIntValue() > 0) {
                    out.add(Map.entry(entry.getKey(), entry.getIntValue()));
                }
            }
        }
        out.sort(Comparator
                .comparingInt((Map.Entry<ResourceLocation, Integer> e) -> -e.getValue())
                .thenComparing(e -> e.getKey().toString()));
        return out;
    }

    public boolean roomForGenericBook(LineageTier tier) {
        return isBookTier(tier) && this.genericBooks[tier.ordinal()] < MAX_BOOKS;
    }

    public boolean roomForTargetedBook(LineageTier tier, ResourceLocation rootId) {
        return isBookTier(tier) && this.targetedBooks(tier, rootId) < MAX_BOOKS;
    }

    /** 收一本通用进阶书；装不下返回 false（调用方必须已经探过容量）。 */
    public boolean addGenericBook(LineageTier tier) {
        if (!this.roomForGenericBook(tier)) {
            return false;
        }
        this.genericBooks[tier.ordinal()]++;
        this.setChanged();
        return true;
    }

    public boolean addTargetedBook(LineageTier tier, ResourceLocation rootId) {
        if (!this.roomForTargetedBook(tier, rootId)) {
            return false;
        }
        Object2IntMap<ResourceLocation> map =
                this.targetedBooks.computeIfAbsent(tier, k -> new Object2IntOpenHashMap<>());
        map.put(rootId, map.getInt(rootId) + 1);
        this.setChanged();
        return true;
    }

    public boolean spendGenericBook(LineageTier tier) {
        if (this.genericBooks(tier) <= 0) {
            return false;
        }
        this.genericBooks[tier.ordinal()]--;
        this.setChanged();
        return true;
    }

    public boolean spendTargetedBook(LineageTier tier, ResourceLocation rootId) {
        Object2IntMap<ResourceLocation> map = this.targetedBooks.get(tier);
        if (map == null || map.getInt(rootId) <= 0) {
            return false;
        }
        map.put(rootId, map.getInt(rootId) - 1);
        this.setChanged();
        return true;
    }

    /** 有没有任何库存（等级单位或书）——tooltip 判空用。 */
    public boolean isEmpty() {
        for (int value : this.levelEnergy) {
            if (value > 0) {
                return false;
            }
        }
        for (int value : this.genericBooks) {
            if (value > 0) {
                return false;
            }
        }
        for (Object2IntMap<ResourceLocation> map : this.targetedBooks.values()) {
            for (int value : map.values()) {
                if (value > 0) {
                    return false;
                }
            }
        }
        return true;
    }

    // ── 持久化 ──────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        CompoundTag levels = new CompoundTag();
        for (LineageTier tier : LineageTier.values()) {
            if (this.levelEnergy[tier.ordinal()] > 0) {
                levels.putInt(tier.id(), this.levelEnergy[tier.ordinal()]);
            }
        }
        tag.put(TAG_LEVEL_ENERGY, levels);

        CompoundTag generic = new CompoundTag();
        CompoundTag targeted = new CompoundTag();
        for (LineageTier tier : LineageTier.values()) {
            if (!isBookTier(tier)) {
                continue;
            }
            if (this.genericBooks[tier.ordinal()] > 0) {
                generic.putInt(tier.id(), this.genericBooks[tier.ordinal()]);
            }
            Object2IntMap<ResourceLocation> map = this.targetedBooks.get(tier);
            if (map == null || map.isEmpty()) {
                continue;
            }
            CompoundTag perTier = new CompoundTag();
            for (Object2IntMap.Entry<ResourceLocation> entry : map.object2IntEntrySet()) {
                if (entry.getIntValue() > 0) {
                    perTier.putInt(entry.getKey().toString(), entry.getIntValue());
                }
            }
            if (!perTier.isEmpty()) {
                targeted.put(tier.id(), perTier);
            }
        }
        tag.put(TAG_GENERIC_BOOKS, generic);
        tag.put(TAG_TARGETED_BOOKS, targeted);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // 先清空：本方法可能被调用两次（setPlacedBy 兜底 + 原版 BlockItem），必须幂等
        java.util.Arrays.fill(this.levelEnergy, 0);
        java.util.Arrays.fill(this.genericBooks, 0);
        this.targetedBooks.clear();

        // ① v5 格式
        CompoundTag levels = tag.getCompound(TAG_LEVEL_ENERGY);
        for (String raw : levels.getAllKeys()) {
            LineageTier tier = LineageTier.byId(raw);
            if (tier != null) {
                this.levelEnergy[tier.ordinal()] = levels.getInt(raw);
            }
        }
        CompoundTag generic = tag.getCompound(TAG_GENERIC_BOOKS);
        for (String raw : generic.getAllKeys()) {
            LineageTier tier = LineageTier.byId(raw);
            if (tier != null && isBookTier(tier)) {
                this.genericBooks[tier.ordinal()] = generic.getInt(raw);
            }
        }
        CompoundTag targeted = tag.getCompound(TAG_TARGETED_BOOKS);
        for (String tierId : targeted.getAllKeys()) {
            LineageTier tier = LineageTier.byId(tierId);
            if (tier == null || !isBookTier(tier)) {
                continue;
            }
            CompoundTag perTier = targeted.getCompound(tierId);
            Object2IntMap<ResourceLocation> map =
                    this.targetedBooks.computeIfAbsent(tier, k -> new Object2IntOpenHashMap<>());
            for (String raw : perTier.getAllKeys()) {
                ResourceLocation rootId = ResourceLocation.tryParse(raw);
                if (rootId != null) {
                    map.put(rootId, perTier.getInt(raw));
                }
            }
        }

        // ② v4 迁移：只取 level| 条目，ascension| 一律丢弃（点数是元，书是本，无法无损换算）
        CompoundTag legacy = tag.getCompound(TAG_LEGACY_ENERGY);
        for (String raw : legacy.getAllKeys()) {
            int bar = raw.indexOf('|');
            if (bar <= 0) {
                continue;
            }
            if (!"level".equals(raw.substring(0, bar))) {
                continue;   // ascension|* —— 刻意丢弃
            }
            LineageTier tier = LineageTier.byId(raw.substring(bar + 1));
            if (tier != null) {
                this.levelEnergy[tier.ordinal()] = legacy.getInt(raw);
            }
        }
    }
}
