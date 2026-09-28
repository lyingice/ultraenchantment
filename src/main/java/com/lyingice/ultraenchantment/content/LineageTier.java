package com.lyingice.ultraenchantment.content;

import com.mojang.serialization.Codec;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.util.StringRepresentable;

/**
 * <b>谱系阶级</b>——某条谱系在成长链上所处的档位。
 *
 * <p>与 {@link UETier} 的区别（两者职责不同，不要混用）：
 * <ul>
 *   <li>{@link UETier} —— <b>物品形态阶级</b>。书 / 祛咒石的外观档位，由 {@code custom_model_data} 承载。</li>
 *   <li>{@link LineageTier} —— <b>谱系状态阶级</b>。某条谱系推进到了哪一档，由
 *       {@code ultraenchantment:ascension} 组件承载。</li>
 * </ul>
 *
 * <p>多出一个 {@link #NATIVE}（原生阶）：原版附魔本身所处的档位，没有对应的阶段条目。
 * 有它才能表达「从原生阶进阶到高阶」这一动作。
 */
public enum LineageTier implements StringRepresentable {
    NATIVE("native"),
    ADVANCED("advanced"),
    SUPER("super"),
    ULTRA("ultra");

    /** 数据包阶段条目中 {@code tier} 字段的取值。注意 {@code native} 不会出现在条目里。 */
    public static final Codec<LineageTier> CODEC = StringRepresentable.fromEnum(LineageTier::values);

    private static final LineageTier[] VALUES = values();

    private final String id;

    LineageTier(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }

    /** 下一阶级；已是最高阶时返回空。 */
    public Optional<LineageTier> next() {
        return this.ordinal() + 1 < VALUES.length
                ? Optional.of(VALUES[this.ordinal() + 1])
                : Optional.empty();
    }

    /** 是否已被进阶过（即受保护）。原生阶返回 false。 */
    public boolean isAscended() {
        return this != NATIVE;
    }

    /** 转成物品形态阶级；原生阶没有对应形态，返回空。 */
    public Optional<UETier> asViewTier() {
        return switch (this) {
            case NATIVE -> Optional.empty();
            case ADVANCED -> Optional.of(UETier.ADVANCED);
            case SUPER -> Optional.of(UETier.SUPER);
            case ULTRA -> Optional.of(UETier.ULTRA);
        };
    }

    /** 由物品形态阶级转成谱系阶级。 */
    public static LineageTier of(UETier viewTier) {
        return switch (viewTier) {
            case ADVANCED -> ADVANCED;
            case SUPER -> SUPER;
            case ULTRA -> ULTRA;
        };
    }

    @Nullable
    public static LineageTier byId(String id) {
        for (LineageTier tier : VALUES) {
            if (tier.id.equals(id)) {
                return tier;
            }
        }
        return null;
    }
}
