package com.lyingice.ultraenchantment.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 三种书的载荷。
 *
 * <h2>阶级字段的类型选择（两种书不同，不要统一）</h2>
 *
 * <table>
 *   <tr><th>书</th><th>字段</th><th>类型</th><th>能否含原生阶</th></tr>
 *   <tr><td><b>进阶书</b>（进化型）</td><td>{@code from_tier} / {@code to_tier}</td>
 *       <td>{@link LineageTier}</td><td><b>能</b>——{@code from_tier: native} 就是「基础→高阶」那一档</td></tr>
 *   <tr><td><b>载体书</b>（铭刻型）</td><td>{@code tier} + {@code entries[]}</td>
 *       <td>{@link AscensionTier}</td><td><b>不能</b>——书级单值，一本书所有条目同阶级</td></tr>
 *   <tr><td><b>升级书</b>（升级型）</td><td>{@code tier}（单一）</td>
 *       <td>{@link AscensionTier}</td><td><b>不能</b>——只作用于已进阶附魔</td></tr>
 * </table>
 *
 * <p>理由：进阶书描述的是「谱系链上的一条边」，边的起点可以是原生阶（链的头部）；
 * 载体书与升级书描述的都是「已经存在的进阶形态」，原生阶没有阶级，出现 native 无意义。
 *
 * <h2>载体书（铭刻型）为什么是多条目</h2>
 *
 * <p>v2 起铭刻型升格为<b>载体书</b>——进阶形态的实物载体（见 docs/book-system-spec.md）：
 * 它像原版附魔书一样可以承载多条附魔、可以两本合并、贴装备时被拒绝的条目可以留成「剩菜书」，
 * 因此载荷从单条改为列表。
 */
public final class BookSpecs {
    private BookSpecs() {}

    /**
     * 进化型（进阶书）：把目标附魔沿谱系推进**一阶**。
     *
     * <p>通用与定向共用本载荷，靠 {@code applicable} 是否为空区分。
     *
     * <p>{@code fromTier} 用 {@link LineageTier}——它可以是 {@code NATIVE}，
     * 表示「这本书要用于尚未进阶的原始附魔」（即「基础→高阶」品质）。
     */
    public record Ascension(
            LineageTier fromTier,
            LineageTier toTier,
            /** 通用进阶为空；定向进阶铭刻具体附魔 id。 */
            Optional<ResourceLocation> applicable) {

        public static final Codec<Ascension> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                LineageTier.CODEC.fieldOf("from_tier").forGetter(Ascension::fromTier),
                LineageTier.CODEC.fieldOf("to_tier").forGetter(Ascension::toTier),
                ResourceLocation.CODEC.optionalFieldOf("applicable").forGetter(Ascension::applicable)
        ).apply(instance, Ascension::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Ascension> STREAM_CODEC =
                ByteBufCodecs.fromCodecWithRegistries(CODEC);

        /** 是否为定向进阶（铭刻了具体附魔）。 */
        public boolean isTargeted() {
            return this.applicable.isPresent();
        }
    }

    /**
     * 铭刻型 = <b>载体书</b>：携带若干条「某阶级的进阶附魔」，可贴装备、可两本合并。
     *
     * <h2>为什么载荷必须自带 {@code tier}</h2>
     *
     * <p>曾尝试「不记阶级、由附魔 id 反推」。那是错的——载体书里记的是<b>原版附魔 id</b>
     * （{@code minecraft:sharpness}），路径里根本没有阶级信息，反推只会得到原生阶。
     *
     * <p>阶级是<b>这本书自身的属性</b>，与它携带哪些附魔无关，必须显式声明。
     *
     * <h2>{@code level} 的语义是 tierLevel（曲线等级），不是存储等级</h2>
     *
     * <p>存储等级升阶后永远是上限，把它 +1 得到的数字对强度毫无作用，合并会变成空转。
     * 选 tierLevel，「两本同级书 → 升 1 级」才真的变强（docs/book-system-spec.md §3.1）。
     * 实际生效值一律夹在该谱系该阶的 {@code max_level} 内。
     *
     * @param tier    本书所属阶级（必非原生阶）。<b>书级单值</b>——一本书里所有条目同阶级
     * @param entries 条目表，至少 1 条
     */
    public record Inscription(AscensionTier tier, List<Entry> entries) {

        /** 一条「进阶附魔」。{@code level} 是曲线等级（tierLevel）。 */
        public record Entry(ResourceLocation enchantment, int level) {
            public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("enchantment").forGetter(Entry::enchantment),
                    Codec.intRange(1, 255).fieldOf("level").forGetter(Entry::level)
            ).apply(instance, Entry::new));
        }

        public static final Codec<Inscription> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                AscensionTier.CODEC.fieldOf("tier").forGetter(Inscription::tier),
                Entry.CODEC.listOf().fieldOf("entries").forGetter(Inscription::entries)
        ).apply(instance, Inscription::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Inscription> STREAM_CODEC =
                ByteBufCodecs.fromCodecWithRegistries(CODEC);

        public Inscription {
            entries = List.copyOf(entries);
        }

        public boolean isEmpty() {
            return this.entries.isEmpty();
        }
    }

    /**
     * 升级型：不改变附魔种类与阶级，仅把等级提升到 {@code targetLevel}。
     *
     * <p>只有已进阶的附魔才有「所属档位」，因此 {@code tier} 也用 {@link AscensionTier}。
     */
    public record Upgrade(AscensionTier tier, int targetLevel) {
        public static final Codec<Upgrade> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                AscensionTier.CODEC.fieldOf("tier").forGetter(Upgrade::tier),
                Codec.intRange(1, 255).fieldOf("target_level").forGetter(Upgrade::targetLevel)
        ).apply(instance, Upgrade::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Upgrade> STREAM_CODEC =
                ByteBufCodecs.fromCodecWithRegistries(CODEC);
    }
}
