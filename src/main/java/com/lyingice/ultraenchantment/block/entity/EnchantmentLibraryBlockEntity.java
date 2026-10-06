package com.lyingice.ultraenchantment.block.entity;

import com.lyingice.ultraenchantment.content.LibraryKey;
import com.lyingice.ultraenchantment.registry.UEBlockEntities;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * <b>附魔图书馆的存储</b>——按「谱系 × 阶级」分池记点。
 *
 * <h2>数据结构</h2>
 *
 * <p>两张表，键都是 {@link LibraryKey}：
 * <ul>
 *   <li>{@code points} —— 该 (谱系, 阶级) 已存的<b>点数</b></li>
 *   <li>{@code maxLevels} —— 该 (谱系, 阶级) 见过的<b>最高等级</b></li>
 * </ul>
 *
 * <p>两张表都必须按阶级分池：{@code 锋利·高阶} 的点数<b>不</b>进
 * {@code 锋利·超级} 的池子。跨阶级「晋升」是独立的设计决策，不在这里隐式发生。
 *
 * <h2>点数换算</h2>
 *
 * <p>{@code levelToPoints(L) = 2^(L-1)}，照神化图书馆的指数换算——
 * 高等级自然比低等级贵得多，于是「囤低级书堆满级」不划算。
 *
 * <p>⚠️ 位移<b>上限 30</b>（{@code 2^30}）：阶段定义的 {@code max_level} 上限是 255，
 * 直接 {@code 1 << 254} 会溢出成负数。上限取 30 与神化的「末影图书馆 31 级」同量级，
 * 也保证点数始终能安全放进 int。
 *
 * <h2>上限来自数据包，不写死</h2>
 *
 * <p>每格的等级上限由调用方从 {@code StageDefinition.definition().maxLevel()} 传入
 * （见 {@code StageLookup}），<b>不是</b>本类的常量——数据包改一个阶段的
 * {@code max_level}，图书馆立刻跟随。
 *
 * <h2>读盘容错</h2>
 *
 * <p>解析不出来的键（未知阶级、非法 ResourceLocation）<b>静默丢弃</b>。
 * 数据包移除某阶级后旧存档里会留下这类死键，丢弃比抛异常正确。
 */
public class EnchantmentLibraryBlockEntity extends BlockEntity {

    private static final String TAG_POINTS = "points";
    private static final String TAG_MAX_LEVELS = "max_levels";

    /** 点数换算的最大位移，防 int 溢出。 */
    private static final int MAX_SHIFT = 30;

    private final Object2IntMap<LibraryKey> points = new Object2IntOpenHashMap<>();
    private final Object2IntMap<LibraryKey> maxLevels = new Object2IntOpenHashMap<>();

    public EnchantmentLibraryBlockEntity(BlockPos pos, BlockState state) {
        super(UEBlockEntities.ENCHANTMENT_LIBRARY.get(), pos, state);
    }

    // ── 换算 ────────────────────────────────────────────────────────────

    /** {@code 2^(level-1)}，位移夹在 30 以内。 */
    public static int levelToPoints(int level) {
        if (level <= 0) {
            return 0;
        }
        return 1 << Math.min(level - 1, MAX_SHIFT);
    }

    /** 把某格从 {@code currentLevel} 提到 {@code targetLevel} 所需的点数。 */
    public static int costToReach(int targetLevel, int currentLevel) {
        return Math.max(0, levelToPoints(targetLevel) - levelToPoints(currentLevel));
    }

    /**
     * 现有点数<b>够取到的最高等级</b>（不考虑「见过没见过」）。
     *
     * <p>取出的目标等级 = {@code min(见过的最高等级, 本方法)}。
     *
     * <h2>为什么必须由点数反推，而不是直接取「见过的最高级」</h2>
     *
     * <p>「合并等级」的全部意义就在这里：见过的等级是<b>天花板</b>，
     * 点数决定<b>这次能到多高</b>。若直接取见过的最高级，
     * 存入一本 5 级后再取出来要付满额点数，而点数不够时只能<b>拒绝</b>——
     * 玩家攒了一堆低级书却什么也取不出来。
     *
     * <p>由点数反推后：见过 5 级 + 攒够点数 ⇒ 取出 5 级；
     * 点数只够 2 级 ⇒ 取出 2 级（而不是报错）。
     *
     * @param currentLevel 输出物品上该附魔已有的等级（新书为 0）
     */
    public static int affordableLevel(int points, int currentLevel) {
        long budget = (long) points + levelToPoints(currentLevel);
        if (budget <= 0) {
            return 0;
        }
        int level = 1;
        while (level < MAX_SHIFT + 1 && levelToPoints(level + 1) <= budget) {
            level++;
        }
        return level;
    }

    // ── 存入 ────────────────────────────────────────────────────────────

    /**
     * 存入一本 {@code (root, tier)}、等级为 {@code level} 的书。
     *
     * @param stageMaxLevel 该阶段的等级上限（来自数据包）
     * @return 是否真的改变了内容
     */
    public boolean deposit(LibraryKey key, int level, int stageMaxLevel) {
        if (level <= 0 || stageMaxLevel <= 0) {
            return false;
        }
        boolean changed = false;

        int cap = levelToPoints(stageMaxLevel);
        int current = this.points.getInt(key);
        // 用 long 中间量：current 与增量都可能接近 2^30，直接相加会溢出
        int next = (int) Math.min(cap, (long) current + levelToPoints(level));
        if (next != current) {
            this.points.put(key, next);
            changed = true;
        }

        int seenBefore = this.maxLevels.getInt(key);
        int seen = Math.min(stageMaxLevel, Math.max(seenBefore, level));
        if (seen != seenBefore) {
            this.maxLevels.put(key, seen);
            changed = true;
        }

        if (changed) {
            this.setChanged();
        }
        return changed;
    }

    /**
     * 这次存入是否<b>真的会增加内容</b>。
     *
     * <p>菜单用它做「整本吸收」的原子性校验：若任一条目已满（点数到顶且等级没涨），
     * 整本书留在槽里不消耗——否则玩家会静默损失那一条的价值。
     */
    public boolean canAccept(LibraryKey key, int level, int stageMaxLevel) {
        if (level <= 0 || stageMaxLevel <= 0) {
            return false;
        }
        boolean pointsRoom = this.points.getInt(key) < levelToPoints(stageMaxLevel);
        boolean levelRoom = Math.min(stageMaxLevel, level) > this.maxLevels.getInt(key);
        return pointsRoom || levelRoom;
    }

    // ── 查询 ────────────────────────────────────────────────────────────

    public int pointsOf(LibraryKey key) {
        return this.points.getInt(key);
    }

    public int maxLevelOf(LibraryKey key) {
        return this.maxLevels.getInt(key);
    }

    /** 两张表的键并集。 */
    public Set<LibraryKey> keys() {
        Set<LibraryKey> all = new LinkedHashSet<>(this.points.keySet());
        all.addAll(this.maxLevels.keySet());
        return all;
    }

    // ── 取出 ────────────────────────────────────────────────────────────

    /** 是否够取：目标等级不超过见过的上限，且点数足够。 */
    public boolean canExtract(LibraryKey key, int targetLevel, int currentLevel, int stageMaxLevel) {
        if (targetLevel < 1 || targetLevel > stageMaxLevel) {
            return false;
        }
        if (this.maxLevels.getInt(key) < targetLevel) {
            return false;
        }
        return this.points.getInt(key) >= costToReach(targetLevel, currentLevel);
    }

    /** 扣点。调用前必须先过 {@link #canExtract}。 */
    public boolean extract(LibraryKey key, int targetLevel, int currentLevel, int stageMaxLevel) {
        if (!canExtract(key, targetLevel, currentLevel, stageMaxLevel)) {
            return false;
        }
        int left = this.points.getInt(key) - costToReach(targetLevel, currentLevel);
        this.points.put(key, Math.max(0, left));
        this.setChanged();
        return true;
    }

    /**
     * 直接扣点——<b>跨图书馆池化消费</b>用。
     *
     * <p>与 {@link #extract} 的区别：{@code extract} 表达的是「从这一座图书馆取出一本书」，
     * 因此要校验「见过该等级」；而池化消费表达的是「把点数花掉」，
     * 校验（库存够不够）由调用方在<b>所有</b>图书馆上聚合后统一做。
     *
     * @return 实际扣掉的数量（不足时就是剩下的全部）
     */
    public int spend(LibraryKey key, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int have = this.points.getInt(key);
        int take = Math.min(have, amount);
        if (take > 0) {
            this.points.put(key, have - take);
            this.setChanged();
        }
        return take;
    }

    // ── 持久化 ──────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_POINTS, writeMap(this.points));
        tag.put(TAG_MAX_LEVELS, writeMap(this.maxLevels));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // 先清空：本方法可能被调用两次（setPlacedBy 兜底 + 原版 BlockItem），必须幂等
        this.points.clear();
        this.maxLevels.clear();
        readMap(tag.getCompound(TAG_POINTS), this.points);
        readMap(tag.getCompound(TAG_MAX_LEVELS), this.maxLevels);
    }

    private static CompoundTag writeMap(Object2IntMap<LibraryKey> map) {
        CompoundTag tag = new CompoundTag();
        for (Object2IntMap.Entry<LibraryKey> entry : map.object2IntEntrySet()) {
            tag.putInt(entry.getKey().storageKey(), entry.getIntValue());
        }
        return tag;
    }

    private static void readMap(CompoundTag tag, Object2IntMap<LibraryKey> out) {
        for (String raw : tag.getAllKeys()) {
            LibraryKey key = LibraryKey.parseStorageKey(raw);
            if (key == null) {
                continue;   // 死键：数据包移除阶级后的残留，丢弃
            }
            out.put(key, tag.getInt(raw));
        }
    }
}
