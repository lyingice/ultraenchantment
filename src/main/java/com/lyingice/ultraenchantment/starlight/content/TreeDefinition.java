package com.lyingice.ultraenchantment.starlight.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * 一棵进化树——数据包注册表 {@code ultraenchantment:starlight_tree} 的一条。
 *
 * <p>树是<b>图</b>，不是链：一个节点可以有多个子节点（分支），
 * 将来的融合节点也可以有多个父节点。
 *
 * <p>树里包含「根源节点」——它的 {@code enchantment} 就是原版附魔本身。
 * 因此物品第一次进化时，从根源节点出发，把原版附魔替换成第一个阶段附魔。
 *
 * @param display 星座显示名翻译键
 * @param nodes   全部节点（含根源）
 * @param edges   全部进化边
 */
public record TreeDefinition(String display, List<TreeNode> nodes, List<TreeEdge> edges) {

    public static final Codec<TreeDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("display").forGetter(TreeDefinition::display),
            TreeNode.CODEC.listOf().fieldOf("nodes").forGetter(TreeDefinition::nodes),
            TreeEdge.CODEC.listOf().optionalFieldOf("edges", List.of()).forGetter(TreeDefinition::edges)
    ).apply(instance, TreeDefinition::new));

    public TreeDefinition {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    /** 按 id 找节点。 */
    public Optional<TreeNode> node(ResourceLocation id) {
        for (TreeNode node : this.nodes) {
            if (node.id().equals(id)) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    /** 从某个节点出发的全部边。 */
    public List<TreeEdge> edgesFrom(ResourceLocation from) {
        return this.edges.stream().filter(edge -> edge.from().equals(from)).toList();
    }

    /** 从 A 到 B 的边；没有则空。 */
    public Optional<TreeEdge> edge(ResourceLocation from, ResourceLocation to) {
        for (TreeEdge edge : this.edges) {
            if (edge.from().equals(from) && edge.to().equals(to)) {
                return Optional.of(edge);
            }
        }
        return Optional.empty();
    }

    /** 某个附魔在这棵树里对应的节点——物品「现在是哪颗星」靠它反查。 */
    public Optional<TreeNode> nodeOfEnchantment(ResourceLocation enchantment) {
        for (TreeNode node : this.nodes) {
            if (node.enchantment().equals(enchantment)) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }
}
