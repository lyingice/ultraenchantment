package com.lyingice.ultraenchantment.content;

import com.mojang.serialization.Codec;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.util.StringRepresentable;

/**
 * <b>进阶档位</b>——模组引入的三个进阶阶级。
 *
 * <p>与 {@link LineageTier} 的区别（三者的职责必须分清，混用会污染数据契约）：
 *
 * <table>
 *   <tr><th>类型</th><th>语义</th><th>能否是基础阶</th><th>出现在哪</th></tr>
 *   <tr><td>{@link UETier}</td><td>物品形态阶级</td><td>否</td><td>书 / 祛咒石的外观与载荷</td></tr>
 *   <tr><td>{@code AscensionTier}</td><td>进阶档位</td><td>否</td><td>书的 {@code from_tier} / {@code to_tier}</td></tr>
 *   <tr><td>{@link LineageTier}</td><td>谱系当前状态</td><td><b>是</b></td><td>{@code ascension} 组件的反查结果</td></tr>
 * </table>
 *
 * <p><b>为什么书里不能出现基础阶</b>：书的职责是「把附魔推进到某个进阶档」。
 * 「基础阶」不是一个可推进到的目标，也不是一个有物品形态的档位——
 * 它只是「还没进阶」这个状态的代称。把它放进书的载荷会让
 * {@code from_tier: native, to_tier: native} 这类无意义组合变成合法输入。
 */
public enum AscensionTier implements StringRepresentable {
    ADVANCED("advanced"),
    SUPER("super"),
    ULTRA("ultra");

    public static final Codec<AscensionTier> CODEC = StringRepresentable.fromEnum(AscensionTier::values);

    public static final net.minecraft.network.codec.StreamCodec<
            net.minecraft.network.RegistryFriendlyByteBuf, AscensionTier> STREAM_CODEC =
            net.minecraft.network.codec.ByteBufCodecs.fromCodecWithRegistries(CODEC);

    private static final AscensionTier[] VALUES = values();

    private final String id;

    AscensionTier(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    /**
     * 本阶级的<b>语言键</b>——界面与 tooltip 共用同一份实现。
     *
     * <p>放在枚举上而不是放在 datagen 的 {@code UELang} 里：界面要读它，
     * 而界面不应该依赖 datagen 包。{@code UELang} 反过来委托到这里，
     * 于是「键怎么拼」只有一处定义。
     */
    public String translationKey() {
        return "tier." + com.lyingice.ultraenchantment.Ultraenchantment.MODID + "." + this.id;
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }

    /** 下一档；已是最高档时返回空。 */
    public Optional<AscensionTier> next() {
        return this.ordinal() + 1 < VALUES.length
                ? Optional.of(VALUES[this.ordinal() + 1])
                : Optional.empty();
    }

    /** 转成谱系状态阶级（必然非基础阶）。 */
    public LineageTier asLineageTier() {
        return switch (this) {
            case ADVANCED -> LineageTier.ADVANCED;
            case SUPER -> LineageTier.SUPER;
            case ULTRA -> LineageTier.ULTRA;
        };
    }

    /** 由谱系状态阶级转来；基础阶没有对应档位，返回空。 */
    public static Optional<AscensionTier> of(LineageTier lineageTier) {
        return switch (lineageTier) {
            case NATIVE -> Optional.empty();
            case ADVANCED -> Optional.of(ADVANCED);
            case SUPER -> Optional.of(SUPER);
            case ULTRA -> Optional.of(ULTRA);
        };
    }

    /** 由物品形态阶级转来（必然有对应档位）。 */
    public static AscensionTier of(UETier viewTier) {
        return switch (viewTier) {
            case ADVANCED -> ADVANCED;
            case SUPER -> SUPER;
            case ULTRA -> ULTRA;
        };
    }

    /** 转成物品形态阶级，用于驱动模型谓词与祛咒石匹配。 */
    public UETier asViewTier() {
        return switch (this) {
            case ADVANCED -> UETier.ADVANCED;
            case SUPER -> UETier.SUPER;
            case ULTRA -> UETier.ULTRA;
        };
    }

    @Nullable
    public static AscensionTier byId(String id) {
        for (AscensionTier tier : VALUES) {
            if (tier.id.equals(id)) {
                return tier;
            }
        }
        return null;
    }
}
