package com.lyingice.ultraenchantment.registry;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.content.BookSubject;
import com.lyingice.ultraenchantment.content.TieredItem;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 物品注册。
 *
 * <h2>单例设计</h2>
 *
 * <p>只有<b>两个</b>物品注册项：
 * <ol>
 *   <li>{@link #ADVANCED_ENCHANTED_BOOK} —— 进阶附魔书，三大科目的<b>唯一</b>载体</li>
 *   <li>{@link #CURATIVE_STONE} —— 祛咒石</li>
 * </ol>
 *
 * <p>变种一律由<b>数据组件</b>产生，不由物品 id 产生：
 * <ul>
 *   <li>书的科目 → {@code ascension_spec} / {@code inscription_spec} / {@code upgrade_spec}
 *       三者互斥，存在哪个就是哪种科目（见 {@link #subjectOf(ItemStack)}）</li>
 *   <li>书的品质/阶级 → 载荷内部字段（{@code from_tier}/{@code to_tier}/{@code tier}）</li>
 *   <li>材质档位 → {@code custom_model_data}（1/2/3），由载荷派生，仅影响渲染</li>
 * </ul>
 *
 * <p>这与原版 {@code enchanted_book}（单物品 + {@code stored_enchantments}）、
 * {@code smithing_template}（单物品 + 模板组件）是同一范式。
 *
 * <h2>命名约定</h2>
 *
 * <p>书的物品名<b>恒定</b>为「进阶附魔书」/ "Advanced Enchanted Book"，<b>不含科目也不含阶级</b>——
 * 区分只靠提示框与材质。这与原版附魔书、锻造模板的做法一致。
 * 因此本地化只需要<b>一个</b>键：{@code item.ultraenchantment.advanced_enchanted_book}。
 */
public final class UEItems {
    private UEItems() {}

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, Ultraenchantment.MODID);

    /**
     * 进阶附魔书——单例物品，三大科目共用。
     *
     * <p>{@code stacksTo(1)}：与原版附魔书一致，也避免「一本书记两种科目」的歧义。
     */
    public static final DeferredHolder<Item, Item> ADVANCED_ENCHANTED_BOOK =
            ITEMS.register("advanced_enchanted_book",
                    () -> new TieredItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    /**
     * 祛咒石——单例物品，三档阶级由 {@code curative_tier} + {@code custom_model_data} 区分。
     *
     * <p>16 点耐久使其成为「可损坏物品」，这正是它能被放入原版砂轮第二输入槽的原因
     * （槽位的 {@code mayPlace} 判定为 {@code isDamageableItem()}）。
     */
    public static final DeferredHolder<Item, Item> CURATIVE_STONE =
            ITEMS.register("curative_stone",
                    () -> new TieredItem(new Item.Properties().stacksTo(1).durability(16).rarity(Rarity.UNCOMMON)));

    // ── 变种判定 ────────────────────────────────────────────────────────
    // 「这本书是什么」的唯一入口。事件层与 tooltip 层都走这里，
    // 避免各自散落一套 isAscensionBook()/isUpgradeBook() 的重复判断。

    /** 是否为进阶附魔书（不论科目）。 */
    public static boolean isAdvancedBook(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ADVANCED_ENCHANTED_BOOK.get());
    }

    /**
     * 判定这本书的科目。
     *
     * <p>三个载荷组件互斥，按顺序命中第一个。都不存在 → 空白书（无科目）。
     *
     * @return 科目；非进阶附魔书或无任何载荷时为空
     */
    public static Optional<BookSubject> subjectOf(ItemStack stack) {
        if (!isAdvancedBook(stack)) {
            return Optional.empty();
        }
        if (stack.has(UEComponents.ASCENSION_SPEC.get())) {
            return Optional.of(BookSubject.ASCENSION);
        }
        if (stack.has(UEComponents.INSCRIPTION_SPEC.get())) {
            return Optional.of(BookSubject.INSCRIPTION);
        }
        if (stack.has(UEComponents.UPGRADE_SPEC.get())) {
            return Optional.of(BookSubject.UPGRADE);
        }
        return Optional.empty();
    }

    /** 是否为带载荷的进阶附魔书（即「有效」的书）。 */
    public static boolean isLoadedBook(ItemStack stack) {
        return subjectOf(stack).isPresent();
    }

    /** 判断是不是祛咒石。 */
    public static boolean isCurativeStone(ItemStack stack) {
        return !stack.isEmpty() && stack.is(CURATIVE_STONE.get());
    }
}
