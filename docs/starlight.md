# Lightstar · 星光进化系统

> 本文件是当前分支的权威说明。旧版进阶系统的文档已随代码移除。

## 1. 核心模型

- **节点 = 已存在的真附魔**：每个进化节点指向一个真实的 `minecraft:enchantment` 条目。
  联动包直接使用原版/联动模组自带的变体附魔（如 `jlme:advanced_sharpness`），
  不复制、不生成任何阶段附魔。
- **物品只携带当前节点附魔**：进化时把原附魔从物品上移除，写入目标节点附魔。
  原附魔只留在 `ultraenchantment:evolution` 组件的 `history` 里。
- **等级只有一份真源**：物品 `minecraft:enchantments` 里阶段附魔的等级，就是该节点等级。
  `evolution` 组件不存等级。
- **没有注入**：不需要 `GetEnchantmentLevelEvent`，也不需要运行时合成 `Holder`。

## 2. 注册表与目录

| 注册表 | 用途 | 目录 |
|---|---|---|
| `minecraft:enchantment` | 阶段附魔（真实条目） | `data/<ns>/enchantment/<path>.json` |
| `ultraenchantment:starlight_tier` | 阶级定义 | `data/<ns>/ultraenchantment/starlight_tier/<id>.json` |
| `ultraenchantment:starlight_tree` | 进化图 | `data/<ns>/ultraenchantment/starlight_tree/<id>.json` |
| `ultraenchantment:evolution`（组件） | 物品履历 | 物品组件，不进数据包 |

## 3. JSON schema

### 3.1 阶级 `starlight_tier`

```json
{
  "ordinal": 2,
  "display": "tier.ultraenchantment.super",
  "color": 11758591,
  "max_level": 5,
  "evolution_cost": { "item": "minecraft:netherite_ingot", "count": 2 },
  "evolution_xp": 10
}
```

- `ordinal`：排序与高低比较（0 = 基础阶）。
- `display`：显示名翻译键。
- `color`：星点颜色，`0xRRGGBB` 的十进制整数。
- `max_level`：显示用默认上限；实际以阶段附魔自己的 `max_level` 为准。
- `evolution_cost`：可选。进化到该阶级时从材料槽扣除的物品与数量；
  尝试时扣除，失败不退。当前数据：高阶 5 钻石 / 超级 2 下界合金锭 /
  究极 1 下界合金块。
- `evolution_xp`：可选。进化到该阶级的**基础经验等级**，越高阶越贵
  （当前数据：高阶 6 / 超级 10 / 究极 18）。实际扣除再按台面周围的
  书架打折，见 §4.1。省略时回落到边的 `cost_levels`。

### 3.2 阶段附魔 `minecraft:enchantment`

标准原版附魔 JSON：

联动包不生成阶段附魔；节点直接引用源模组已有的变体附魔，例如
`jlme:advanced_sharpness`、`fancyenchantments:sacred_supreme_sharpness`。
这些文件由源模组自己提供，不需要也不应该在本模组重复定义。

### 3.3 进化树 `starlight_tree`

```json
{
  "display": "enchantment.minecraft.sharpness",
  "nodes": [
    {
      "id": "ultraenchantment:integration/sharpness/root",
      "enchantment": "minecraft:sharpness",
      "tier": "ultraenchantment:native",
      "pos": { "x": 0.0, "y": 0.0 }
    },
    {
      "id": "ultraenchantment:integration/sharpness/jlme_advanced",
      "enchantment": "jlme:advanced_sharpness",
      "tier": "ultraenchantment:advanced",
      "pos": { "x": -1.0, "y": 1.0 }
    },
    {
      "id": "ultraenchantment:integration/sharpness/jlme_supreme",
      "enchantment": "jlme:supreme_sharpness",
      "tier": "ultraenchantment:super",
      "pos": { "x": -1.0, "y": 2.0 }
    },
    {
      "id": "ultraenchantment:integration/sharpness/fancyenchantments_advanced",
      "enchantment": "fancyenchantments:advanced_sharpness",
      "tier": "ultraenchantment:advanced",
      "pos": { "x": 1.0, "y": 1.0 }
    },
    {
      "id": "ultraenchantment:integration/sharpness/fancyenchantments_supreme",
      "enchantment": "fancyenchantments:sacred_supreme_sharpness",
      "tier": "ultraenchantment:super",
      "pos": { "x": 1.0, "y": 2.0 }
    }
  ],
  "edges": [
    {
      "from": "ultraenchantment:integration/sharpness/root",
      "to": "ultraenchantment:integration/sharpness/jlme_advanced",
      "required_level": 5,
      "chance": 1.0
    },
    {
      "from": "ultraenchantment:integration/sharpness/jlme_advanced",
      "to": "ultraenchantment:integration/sharpness/jlme_supreme",
      "required_level": 3,
      "chance": 0.6,
      "failure_drop_min": 2,
      "failure_drop_max": 4
    },
    {
      "from": "ultraenchantment:integration/sharpness/root",
      "to": "ultraenchantment:integration/sharpness/fancyenchantments_advanced",
      "required_level": 5,
      "chance": 1.0
    },
    {
      "from": "ultraenchantment:integration/sharpness/fancyenchantments_advanced",
      "to": "ultraenchantment:integration/sharpness/fancyenchantments_supreme",
      "required_level": 3,
      "chance": 0.6
    }
  ]
}
```

- `nodes[].pos` 是星图坐标；省略时客户端退化为网格布局。
- `nodes[].enchantment` 必须是已存在的附魔 id（原版或联动模组）。
  同一父节点的多条出边就是多个分支，可以指向不同模组的变体。
- 框架也支持 `enchantment` 指向树内节点 id + `evolution` 组件记录阶级，
  用于将来没有真实变体附魔的内容；当前联动包全部使用真实附魔。
- `edges[].required_level`：**起点节点**需要达到的等级。
- `edges[].chance`：成功率 0..1。
- `edges[].cost_levels`：可选兜底。目标阶级没写 `evolution_xp` 时才用它当
  基础经验；同样吃书架折扣。新内容建议把经验写在阶级上，边只管前置与概率。
- `edges[].failure_drop_min/max`：失败时当前附魔等级下降的随机范围，默认 2~4，最低保留 1 级。
- `edges[].branch`：分支显示名翻译键；同一父节点的多条边就是分支选择。

## 4. 运行时

| 类 | 职责 |
|---|---|
| `TreeIndex` | 数据包重载后建索引：树、阶级、附魔 → 树。 |
| `EvolutionService` | 当前节点、等级、进化/升级校验与物品落盘。 |
| `StarlightTableMenu` | 物品槽 + 材料槽；操作走 payload。 |
| `StarlightNetwork` | 客户端 → 服务端 `EvolutionActionPayload`。 |
| `StarlightTableScreen` | 星图渲染、选中、详情卡、按钮。 |
| `AscensionIndexScreen` | 附魔进阶总览：全部树 / 路线查询，按键默认不绑定。 |

**服务端权威**：客户端只选星、发节点 id；所有前置、经验、概率、附魔适用范围
都在服务端重新校验。

### 4.1 书架联动

星光附魔台沿用原版附魔台的书架摆法：台面水平一格之外必须是空气（上下两格
都检查），两格处的 `minecraft:bookshelf` 才计入，至多 **15** 个。

- 折扣线性增长：`折扣 = 50% × min(书架, 15) / 15`，满书架经验**减半**；
- 进化经验 = 目标阶级 `evolution_xp` × (1 − 折扣)，至少 1 级；
- 阶级内升级同样吃折扣，且基础值随阶级抬高：`3 + 2×等级 + 2×阶级序号`；
- 书架数由服务端现扫、经 DataSlot 实时同步到界面，摆/拆立刻刷新。

平衡目标：低阶便宜、高阶贵但值得搭书架墙。默认数值下：

| 进化 | 无书架 | 满 15 书架 |
|---|---|---|
| 基础 → 高阶 | 6 级 | 3 级 |
| 高阶 → 超级 | 10 级 | 5 级 |
| 超级 → 究极 | 18 级 | 9 级 |

界面左下角常驻「书架 x/15」与当前折扣；详情卡的经验行显示折后值
（有书架时带 `(-xx%)`）。进阶总览展示的是**基础**经验，并在页脚注明
书架最高减免 50%。

## 5. 星图状态

| 状态 | 表现 |
|---|---|
| 当前节点 | 大号星点 + 金色光环 |
| 可选下一步 | 闪烁星点 + 青色流动边 |
| 已走过 | 金色实线 |
| 未解锁 | 暗色小星 |
| 选中 | 白色菱形描边 |

右侧详情卡：

- 节点名 / 阶级 / 当前等级；
- 前置等级、折后经验代价（带书架折扣百分比）、成功率、失败惩罚、材料；
- “点亮此星”或“提升等级”按钮（条件不足时禁用）。

### 附魔进阶总览

`AscensionIndexScreen` 是只读查询界面：

- 按键 `key.ultraenchantment.open_index` 默认 **未绑定**，玩家在
  「选项 → 控制 → 按键绑定 → 附魔进阶」里自行设置；
- 左侧列出当前数据包的全部树，支持按树名 / 根附魔 / 节点附魔名搜索；
- 右侧把每棵树的所有根→叶路线纵向展开，每个节点按阶级色显示，
  节点之间标注前置等级、成功率、基础经验与材料，页脚注明书架折扣；
- 悬停节点可看完整附魔名、阶级、等级上限与附魔 id；
- 不向服务端发包，纯客户端读取已同步的 `starlight_tree` /
  `minecraft:enchantment` 注册表。

## 6. 书模型

附魔书使用原版 `minecraft:enchanted_book`，阶段附魔写在它的原版 `stored_enchantments` 里；
**不注册任何自定义书物品或自定义数据组件**。

客户端 `EnchantedBookModelMixin`（MixinExtras `@ModifyReturnValue`）在
`ItemRenderer.getModel` 返回处检查：如果书里存储的阶段附魔属于某个阶级，
就换成该阶级的封面模型：

- `advanced` → `ultraenchantment:item/enchanted_book`
- `super` → `ultraenchantment:item/enchanted_book_super`
- `ultra` → `ultraenchantment:item/enchanted_book_ultra`

书里存的是真实变体附魔时，直接用 `stored_enchantments` 里的附魔查阶级。
框架也支持从 `ultraenchantment:evolution` 组件的节点推导阶级，
用于没有真实变体附魔的内容；当前联动包不使用。

没有阶级信息的原版书保持原版外观。创造栏不再投放自定义书样本；
阶级书由数据包 / 战利品 / 命令 / KubeJS 生成。

## 7. 当前限制

- 本模组不内置附魔树；`content_pack/` 把 JLME 与 Fancy Enchantments 的
  17 个真实进阶变体附魔接成 8 棵树（11 条分支）。
- 星图是静态布局，无拖拽/缩放。
- 概率已实现，**保底与洗点未实现**。
- `conflict_group` 已进数据，但尚未强制执行（单树单节点天然互斥）。
- 进阶总览暂不支持拖拽/缩放；星图仍只在星光台里。
- 融合（多父节点）尚未实现。

## 8. 加一条谱系

1. 在 `data/<ns>/ultraenchantment/starlight_tier/` 引用或新增阶级；
2. 在 `data/<ns>/ultraenchantment/starlight_tree/` 写树：根节点用原版附魔 id，
   其余节点用**已经存在**的变体附魔 id（原版 / 联动模组）；
3. 节点显示名直接复用附魔自己的 `description` 翻译键，无需改 `UELang`；
4. `runData` + `runServer`，看日志里的
   `[starlight] indexed N trees / M tiers / K node enchantments`
   以及 “树引用了不存在的附魔” 告警。

批量生成参考 `tools/generate_integration_pack.py`：显式列出
「原版根源 → 模组变体附魔」的家族表，校验每个 id 确实存在于对应模组 jar，
然后生成分支树；不生成附魔 JSON。
