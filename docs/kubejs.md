# KubeJS 联动

可选。安装 **KubeJS + Rhino** 后，KubeJS 会读取本模组 jar 里的
`kubejs.plugins.txt`，注册脚本绑定 `UltraEnchantment`。
没有 KubeJS 时不会加载任何插件类，也不影响模组本体。

## 查询

```js
UltraEnchantment.tree(stack)        // 'ultraenchantment:sharpness'
UltraEnchantment.node(stack)        // 'ultraenchantment:blood'
UltraEnchantment.tier(stack)        // 'ultraenchantment:super'
UltraEnchantment.level(stack)       // 3
UltraEnchantment.next(stack)        // ['ultraenchantment:blood_ultra']
UltraEnchantment.history(stack)     // ['ultraenchantment:root', ...]
```

阶级查询（适合做书、UI、战利品判断）：

```js
UltraEnchantment.tierOfEnchantment('ultraenchantment:starlight/super_sharpness_blood')
// -> 'ultraenchantment:super'

UltraEnchantment.tierColor('ultraenchantment:super')
// -> 0xB36BFF
```

## 脚本级进化

```js
UltraEnchantment.canEvolve(stack, 'ultraenchantment:blood')
UltraEnchantment.evolve(stack, 'ultraenchantment:blood')
```

`evolve` 是**确定性**的：不掷概率、不扣经验/催化剂，只校验
「当前节点 → 目标节点」与前置等级。适合 KubeJS 自定义仪式、剧情奖励。
正常的概率/失败惩罚仍走星光附魔台。

## 阶段附魔书

```js
// 给玩家一本超级锋利·血刃 I 的原版附魔书
event.player.give(
    UltraEnchantment.stageBook('ultraenchantment:starlight/super_sharpness_blood', 1)
)
```

书使用原版 `minecraft:enchanted_book` + 原版 `stored_enchantments`；
封面由客户端 Mixin 按阶段阶踔自动切换，不需要任何自定义组件。

## 用 KubeJS 添加数据

`ServerEvents.highPriorityData` 可以往三个注册表写 JSON：

| 内容 | 路径 |
|---|---|
| 阶级 | `mypack:ultraenchantment/starlight_tier/<id>` |
| 进化树 | `mypack:ultraenchantment/starlight_tree/<id>` |
| 阶段附魔 | `mypack:enchantment/<path>`（`minecraft:enchantment` 注册表） |

```js
ServerEvents.highPriorityData(event => {
    event.addJson('mypack:ultraenchantment/starlight_tier/awakened', {
        ordinal: 4,
        display: 'tier.mypack.awakened',
        color: 0x66CCFF,
        max_level: 5
    })

    event.addJson('mypack:enchantment/starlight/awakened_sharpness', {
        description: { translate: 'enchantment.mypack.awakened_sharpness' },
        supported_items: '#minecraft:enchantable/sharp_weapon',
        weight: 10,
        max_level: 5,
        min_cost: { base: 1, per_level_above_first: 11 },
        max_cost: { base: 21, per_level_above_first: 11 },
        anvil_cost: 7,
        slots: ['mainhand'],
        effects: {
            'minecraft:damage': [{
                effect: {
                    type: 'minecraft:add',
                    value: { type: 'minecraft:linear', base: 20, per_level_above_first: 5 }
                }
            }]
        }
    })

    event.addJson('mypack:ultraenchantment/starlight_tree/sharpness_awakened', {
        display: 'tree.mypack.sharpness_awakened',
        nodes: [
            // 节点格式见 docs/starlight.md §3.3
        ],
        edges: []
    })
})
```

## 当前边界

- 目前提供的是**绑定 + 数据入口**，还没有自定义 KubeJS 事件组；
  脚本可以监听 KubeJS/原版事件后调用 `UltraEnchantment.evolve(...)`。
- `evolve` 只做确定性物品变换；需要概率/失败惩罚请走星光台。
- 书的阶级来自书上存储的阶段附魔所属的数据包树节点；
  没有阶段附魔的原版书保持原版外观。
