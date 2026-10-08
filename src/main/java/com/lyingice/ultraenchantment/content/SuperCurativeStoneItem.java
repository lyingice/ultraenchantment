package com.lyingice.ultraenchantment.content;

/**
 * <b>超级祛咒石</b>——继承 {@link CurativeStoneItem}，只把档位钉成 {@link AscensionTier#SUPER}。
 *
 * <p>继承而不是「加个布尔字段」：三个形态在合成、JEI 列表、工具提示里都是<b>三种物品</b>，
 * 类层次直接表达这件事；将来某一档要加专属行为（比如究极石不消耗），有地方放。
 */
public class SuperCurativeStoneItem extends CurativeStoneItem {
    public SuperCurativeStoneItem(Properties properties) {
        super(AscensionTier.SUPER, properties);
    }
}
