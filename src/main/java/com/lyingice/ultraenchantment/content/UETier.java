package com.lyingice.ultraenchantment.content;

import com.mojang.serialization.Codec;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/**
 * 阶段阶级。枚举顺序即进阶方向（高阶 → 超级 → 究极）。
 *
 * <p>阶级在物品上由 {@code minecraft:custom_model_data} 承载：它同时是模型谓词的取值来源，
 * 因此只有一份数据，不存在「语义字段与渲染字段不一致」的可能。
 * 这与 1.21.1 的物品模型体系（{@code overrides} + {@code predicate}）天然契合。
 *
 * <p>取值约定：1 = 高阶，2 = 超级，3 = 究极。
 */
public enum UETier implements StringRepresentable {
    ADVANCED("advanced", 1),
    SUPER("super", 2),
    ULTRA("ultra", 3);

    /** 数据包 JSON 中 {@code tier} 字段的取值（小写 id）。 */
    public static final Codec<UETier> CODEC = StringRepresentable.fromEnum(UETier::values);
    public static final StreamCodec<RegistryFriendlyByteBuf, UETier> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    /** 物品上没有 {@code custom_model_data} 时认定的阶级。 */
    public static final UETier DEFAULT = ADVANCED;

    private static final UETier[] VALUES = values();
    private static final UETier[] BY_MODEL_DATA;

    static {
        int max = 0;
        for (UETier tier : VALUES) {
            max = Math.max(max, tier.modelData);
        }
        BY_MODEL_DATA = new UETier[max + 1];
        for (UETier tier : VALUES) {
            BY_MODEL_DATA[tier.modelData] = tier;
        }
    }

    private final String id;
    private final int modelData;

    UETier(String id, int modelData) {
        this.id = id;
        this.modelData = modelData;
    }

    /** 用于资源路径与翻译键后缀，例如 {@code advanced}。 */
    public String id() {
        return this.id;
    }

    /** {@link StringRepresentable}：数据包 JSON 中的序列化名。 */
    @Override
    public String getSerializedName() {
        return this.id;
    }

    /** 写进 {@code custom_model_data} 的数值，同时是模型谓词的阈值。 */
    public int modelData() {
        return this.modelData;
    }

    /** 下一阶级；已是最高阶时返回 {@code null}。 */
    @Nullable
    public UETier next() {
        return this.ordinal() + 1 < VALUES.length ? VALUES[this.ordinal() + 1] : null;
    }

    @Nullable
    public static UETier byModelData(int modelData) {
        return modelData > 0 && modelData < BY_MODEL_DATA.length ? BY_MODEL_DATA[modelData] : null;
    }

    /** 读取物品上的阶级；缺失或非法时返回 {@link #DEFAULT}。 */
    public static UETier of(ItemStack stack) {
        CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        UETier tier = data == null ? null : byModelData(data.value());
        return tier == null ? DEFAULT : tier;
    }

    /** 把阶级写进物品的 {@code custom_model_data}，模型谓词据此切换。 */
    public static void apply(ItemStack stack, UETier tier) {
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(tier.modelData));
    }
}
