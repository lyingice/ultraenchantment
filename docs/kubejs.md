# KubeJS 联动

可选。安装 **KubeJS + Rhino** 后，KubeJS 会读取本模组 jar 里的
`kubejs.plugins.txt`，注册脚本绑定 `UltraEnchantment`。
没有 KubeJS 时不会加载任何插件类，也不影响模组本体。

## 查询

```js
UltraEnchantment.tree(stack)        // 'ultraenchantment:integration/sharpness'
UltraEnchantment.node(stack)        // 'ultraenchantment:integration/sharpness/jlme_advanced'
UltraEnchantment.tier(stack)        // 'ultraenchantment:advanced'
UltraEnchantment.level(stack)       // 3
UltraEnchantment.next(stack)        // ['ultraenchantment:integration/sharpness/jlme_supreme']
UltraEnchantment.history(stack)     // ['ultraenchantment:integration/sharpness/root', ...]
```

阶级查询（适合做书、UI、战利品判断）：

```js
// 树上每个节点都是真实附魔，直接查附魔 id
UltraEnchantment.tierOfEnchantment('jlme:supreme_sharpness')
// -> 'ultraenchantment:super'

UltraEnchantment.tierOfEnchantment('fancyenchantments:sacred_supreme_sharpness')
// -> 'ultraenchantment:super'

UltraEnchantment.tierColor('ultraenchantment:super')
// -> 0xB36BFF
```

## 脚本级进化

```js
UltraEnchantment.canEvolve(stack, 'ultraenchantment:integration/sharpness/jlme_supreme')
UltraEnchantment.evolve(stack, 'ultraenchantment:integration/sharpness/jlme_supreme')
```

`evolve` 是**确定性**的：不掷概率、不扣经验/材料，只校验
「当前节点 → 目标节点」与前置等级。适合 KubeJS 自定义仪式、剧情奖励。
正常的概率/失败惩罚仍走星光附魔台。

## 阶级附魔书

```js
// 书里存真实变体附魔，封面自动按阶级切换
event.player.give(UltraEnchantment.stageBook('jlme:supreme_sharpness', 1))
event.player.give(
    UltraEnchantment.stageBook('fancyenchantments:sacred_supreme_sharpness', 1))

// 原版根源书，方便玩家自己走分支
event.player.give(UltraEnchantment.stageBook('minecraft:sharpness', 5))
```

书使用原版 `minecraft:enchanted_book` + 原版 `stored_enchantments`；
封面由客户端 Mixin 按阶级自动切换，不需要任何自定义书物品。

## 用 KubeJS 添加数据

`ServerEvents.highPriorityData` 可以往两个注册表写 JSON：

| 内容 | 路径 |
|---|---|
| 阶级 | `mypack:ultraenchantment/starlight_tier/<id>` |
| 进化树 | `mypack:ultraenchantment/starlight_tree/<id>` |

节点引用的附魔必须**已经存在**（原版或其它模组提供），本模组不生成附魔。

```js
ServerEvents.highPriorityData(event => {
    event.addJson('mypack:ultraenchantment/starlight_tier/awakened', {
        ordinal: 2,
        display: 'tier.mypack.awakened',
        color: 0x66CCFF,
        max_level: 5,
        evolution_cost: { item: 'minecraft:netherite_ingot', count: 2 },
        evolution_xp: 10 // 基础经验；台面书架最高再减 50%
    })

    event.addJson('mypack:ultraenchantment/starlight_tree/sharpness_awakened', {
        display: 'enchantment.minecraft.sharpness',
        nodes: [
            {
                id: 'mypack:sharpness/root',
                enchantment: 'minecraft:sharpness',
                tier: 'ultraenchantment:native',
                pos: { x: 0.0, y: 0.0 }
            },
            {
                id: 'mypack:sharpness/awakened',
                enchantment: 'mypack:awakened_sharpness', // 必须已存在
                tier: 'mypack:awakened',
                pos: { x: 0.0, y: 1.0 }
            }
        ],
        edges: [{
            from: 'mypack:sharpness/root',
            to: 'mypack:sharpness/awakened',
            required_level: 5,
            chance: 0.5
            // cost_levels 可省略：阶级写了 evolution_xp 时以阶级为准
        }]
    })
})
```

## 当前边界

- 目前提供的是**绑定 + 数据入口**，还没有自定义 KubeJS 事件组；
  脚本可以监听 KubeJS/原版事件后调用 `UltraEnchantment.evolve(...)`。
- `evolve` 只做确定性物品变换；需要概率/失败惩罚请走星光台。
- 书的阶级来自书上存储的阶级附魔；框架也支持 `ultraenchantment:evolution`
  组件节点（用于没有真实变体附魔的内容），两者都没有的原版书保持原版外观。
