package com.lyingice.ultraenchantment.content;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * <b>祛咒石（高阶）</b>——把已进阶的附魔从装备上摘下来的消耗品。
 *
 * <h2>为什么是「一个阶级一个物品」而不是「一个物品 + 数据组件」</h2>
 *
 * <p>早期做法是单物品 + {@code ultraenchantment:curative_tier} 组件，靠 {@code custom_model_data}
 * 换贴图。问题出在<b>合成</b>上：1.21.1 的原版原料<code>比不了组件</code>
 * （写了 {@code components} 会被静默忽略，见 AGENT.md §3 P1-50），
 * 于是「超级 = 高阶石 + 哭泣的黑曜石」「究极 = 超级石 + 金块」这两条配方
 * <b>没法只认上一级</b> —— 究极配方能拿高阶石直接跳级。
 *
 * <p>改成三个物品后：
 * <ul>
 *   <li>配方中心写物品 id 就天然只认上一级，不需要任何自定义原料；</li>
 *   <li>JEI 把三档当成三个物品分开列，不再需要子类型解释器；</li>
 *   <li>阶级从<b>物品本身</b>读，不再依赖组件（省一次组件读写与一次同步）。</li>
 * </ul>
 *
 * <p>⚠️ 16 点耐久使其成为「可损坏物品」，这正是它能被放进原版砂轮第二输入槽的原因
 * （槽位的 {@code mayPlace} 判定为 {@code isDamageableItem()}）。子类只固定档位，不改这个。
 */
public class CurativeStoneItem extends Item {

    private final AscensionTier tier;

    public CurativeStoneItem(AscensionTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    /** 本物品的档位（高阶 / 超级 / 究极之一，永远不是基础阶）。 */
    public AscensionTier tier() {
        return this.tier;
    }

    /** 物品 → 档位；不是祛咒石时返回 {@code null}（调用方自行兜底）。 */
    @Nullable
    public static AscensionTier tierOf(ItemStack stack) {
        return stack.getItem() instanceof CurativeStoneItem stone ? stone.tier() : null;
    }
}
