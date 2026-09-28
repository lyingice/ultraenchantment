package com.lyingice.ultraenchantment.content;

import java.util.Optional;
import net.minecraft.util.StringRepresentable;

/**
 * 进阶附魔书的<b>科目</b>——同一物品 {@code advanced_enchanted_book} 的三种变种。
 *
 * <h2>为什么是「一个物品 + 组件」而不是「三个物品」</h2>
 *
 * <p>原版 {@code enchanted_book} 就是单例：一个物品 id，靠 {@code stored_enchantments}
 * 决定它承载什么。锻造模板同理——{@code smithing_template} 单例，
 * 靠 {@code smithing_template} 组件区分是谁。
 *
 * <p>本模组沿用同一范式：<b>科目由载荷组件决定，不由物品 id 决定</b>。
 * 这样物品名、材质基底、创造栏条目都是单一的，新增科目不需要新增注册项。
 *
 * <h2>与阶级的关系</h2>
 *
 * <p>科目（subject）与阶级（tier）是<b>正交</b>的两轴：
 * <ul>
 *   <li>科目 = 这本书干什么（{@link #ASCENSION} 进化 / {@link #INSCRIPTION} 铭刻 / {@link #UPGRADE} 升级）</li>
 *   <li>阶级 = 它作用于哪一档（由 {@code AscensionTier} 或 {@code LineageTier}
 *       写在载荷里，同时用 {@code custom_model_data} 切材质）</li>
 * </ul>
 *
 * <p>因此「高阶进化书」「超级进化书」「究极升级书」都是同一个物品注册项。
 */
public enum BookSubject implements StringRepresentable {
    /** 进化型：把目标附魔沿谱系推进一阶。载荷 {@link BookSpecs.Ascension}。 */
    ASCENSION("ascension"),

    /** 铭刻型：直接向物品写入附魔组件。载荷 {@link BookSpecs.Inscription}。 */
    INSCRIPTION("inscription"),

    /** 升级型：仅提升同一附魔的等级。载荷 {@link BookSpecs.Upgrade}。 */
    UPGRADE("upgrade");

    private final String id;

    BookSubject(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }

    /** 序列化用字符串，也是翻译键的中段。 */
    public String id() {
        return this.id;
    }

    /** 翻译键：{@code subject.ultraenchantment.<id>}——用于 tooltip 标题。 */
    public String translationKey() {
        return "subject.ultraenchantment." + this.id;
    }

    public static Optional<BookSubject> byId(String id) {
        for (BookSubject subject : values()) {
            if (subject.id.equals(id)) {
                return Optional.of(subject);
            }
        }
        return Optional.empty();
    }
}
