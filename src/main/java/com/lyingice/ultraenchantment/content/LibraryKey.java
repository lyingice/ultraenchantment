package com.lyingice.ultraenchantment.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * <b>附魔图书馆的存储键</b>——「谱系 × 阶级」二元组。
 *
 * <h2>为什么必须是二元组</h2>
 *
 * <p>同一条附魔（如 {@code minecraft:sharpness}）在我们的体系里有<b>四个形态</b>：
 * 原生、高阶、超级、究极。单靠附魔身份无法区分它们——这正是本功能与
 * 神化附魔图书馆、Enchanting Infuser 的根本差异：
 * <ul>
 *   <li>神化图书馆的键是 {@code Holder<Enchantment>}（只有附魔一维）</li>
 *   <li>Enchanting Infuser 的三个等级表也以 {@code Holder<Enchantment>} 为键</li>
 * </ul>
 *
 * <p>我们多出的那一维就是「阶级」，因此键必须是 {@code (root, tier)}。
 *
 * <h2>⚠️ 不要用 {@code Holder<Enchantment>} 当键</h2>
 *
 * <p>我们的进阶附魔是运行时由 {@code EnchantmentFactory} 合成的 {@code Holder.Direct}，
 * <b>没有注册表键</b>（{@code unwrapKey()} 恒为空）。一旦拿它去取 {@code getKey()}：
 * <ul>
 *   <li>神化图书馆 {@code EnchLibraryTile.saveEnchData} 会 NPE（{@code e.getKey().getKey().location()}）</li>
 *   <li>Enchanting Infuser {@code EnchantmentCostHelper.getScalingEnchantmentCosts} 会抛
 *       {@code NoSuchElementException}（{@code unwrapKey().orElseThrow()}）</li>
 * </ul>
 *
 * <p>所以本键只用 <b>可序列化的 (ResourceLocation, AscensionTier)</b>，
 * 与任何 Holder 无关。
 *
 * <h2>序列化形态</h2>
 *
 * <p>NBT 里用 {@code "minecraft:sharpness|advanced"} 这样的单字符串做复合标签的键——
 * 比嵌套复合标签紧凑，且一眼能读。{@code '|'} 不是合法 ResourceLocation 字符，
 * 因此解析时按<b>最后一个</b> {@code '|'} 切分是安全的。
 */
public record LibraryKey(ResourceLocation root, AscensionTier tier) {

    public static final Codec<LibraryKey> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("root").forGetter(LibraryKey::root),
            AscensionTier.CODEC.fieldOf("tier").forGetter(LibraryKey::tier)
    ).apply(instance, LibraryKey::new));

    /** 存储用单字符串：{@code <root>|<tier>}。 */
    public String storageKey() {
        return this.root + "|" + this.tier.id();
    }

    /**
     * 解析 {@link #storageKey()}。
     *
     * @return 解析失败（含未知阶级）时为空——调用方应当<b>静默丢弃</b>该条目，
     *         而不是抛异常。数据包移除某阶级后，旧存档里会留下这类死键。
     */
    @Nullable
    public static LibraryKey parseStorageKey(String raw) {
        int split = raw.lastIndexOf('|');
        if (split <= 0 || split >= raw.length() - 1) {
            return null;
        }
        ResourceLocation root = ResourceLocation.tryParse(raw.substring(0, split));
        AscensionTier tier = AscensionTier.byId(raw.substring(split + 1));
        if (root == null || tier == null) {
            return null;
        }
        return new LibraryKey(root, tier);
    }
}
