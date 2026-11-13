# Lightstar · 星光进化系统

> 本文件是当前分支的权威说明。旧版进阶系统的文档已随代码移除。

## 1. 核心模型

- **阶段 = 真附魔**：每个进化节点指向一个真实的 `minecraft:enchantment` 条目。
- **物品只携带当前阶段**：进化时把原附魔从物品上移除，写入目标阶段附魔。
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
  "max_level": 5
}
```

- `ordinal`：排序与高低比较（0 = 基础阶）。
- `display`：显示名翻译键。
- `color`：星点颜色，`0xRRGGBB` 的十进制整数。
- `max_level`：显示用默认上限；实际以阶段附魔自己的 `max_level` 为准。

### 3.2 阶段附魔 `minecraft:enchantment`

标准原版附魔 JSON：

```json
{
  "description": { "translate": "enchantment.ultraenchantment.starlight.advanced_sharpness" },
  "supported_items": "#minecraft:enchantable/sharp_weapon",
  "primary_items": "#minecraft:enchantable/sword",
  "weight": 10,
  "max_level": 5,
  "min_cost": { "base": 11, "per_level_above_first": 20 },
  "max_cost": { "base": 31, "per_level_above_first": 20 },
  "anvil_cost": 3,
  "slots": ["mainhand"],
  "effects": {
    "minecraft:damage": [
      {
        "effect": {
          "type": "minecraft:add",
          "value": { "type": "minecraft:linear", "base": 3.0, "per_level_above_first": 1.0 }
        }
      }
    ]
  }
}
```

### 3.3 进化树 `starlight_tree`

```json
{
  "display": "tree.ultraenchantment.sharpness",
  "nodes": [
    {
      "id": "ultraenchantment:root",
      "enchantment": "minecraft:sharpness",
      "tier": "ultraenchantment:native",
      "pos": { "x": 0.0, "y": 0.0 }
    },
    {
      "id": "ultraenchantment:advanced",
      "enchantment": "ultraenchantment:starlight/advanced_sharpness",
      "tier": "ultraenchantment:advanced",
      "pos": { "x": 0.0, "y": 1.0 }
    },
    {
      "id": "ultraenchantment:blood",
      "enchantment": "ultraenchantment:starlight/super_sharpness_blood",
      "tier": "ultraenchantment:super",
      "pos": { "x": -1.0, "y": 2.0 },
      "conflict_group": "ultraenchantment:sharpness_branch"
    }
  ],
  "edges": [
    {
      "from": "ultraenchantment:root",
      "to": "ultraenchantment:advanced",
      "required_level": 5,
      "chance": 1.0,
      "cost_levels": 3
    },
    {
      "from": "ultraenchantment:advanced",
      "to": "ultraenchantment:blood",
      "required_level": 3,
      "chance": 0.6,
      "cost_levels": 5,
      "branch": "branch.ultraenchantment.blood"
    }
  ]
}
```

- `nodes[].pos` 是星图坐标；省略时客户端退化为网格布局。
- `edges[].required_level`：**起点节点**需要达到的等级。
- `edges[].chance`：成功率 0..1。
- `edges[].cost_levels`：成功/失败都扣的经验等级。
- `edges[].branch`：分支显示名翻译键；同一父节点的多条边就是分支选择。

## 4. 运行时

| 类 | 职责 |
|---|---|
| `TreeIndex` | 数据包重载后建索引：树、阶级、附魔 → 树。 |
| `EvolutionService` | 当前节点、等级、进化/升级校验与物品落盘。 |
| `StarlightTableMenu` | 物品槽 + 催化剂槽；操作走 payload。 |
| `StarlightNetwork` | 客户端 → 服务端 `EvolutionActionPayload`。 |
| `StarlightTableScreen` | 星图渲染、选中、详情卡、按钮。 |

**服务端权威**：客户端只选星、发节点 id；所有前置、经验、概率、附魔适用范围
都在服务端重新校验。

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
- 前置等级、经验代价、成功率、分支名；
- “点亮此星”或“提升等级”按钮（条件不足时禁用）。

## 6. 书模型

附魔书使用原版 `minecraft:enchanted_book`，阶段附魔写在它的原版 `stored_enchantments` 里；
**不注册任何自定义书物品或自定义数据组件**。

客户端 `EnchantedBookModelMixin`（MixinExtras `@ModifyReturnValue`）在
`ItemRenderer.getModel` 返回处检查：如果书里存储的阶段附魔属于某个阶级，
就换成该阶级的封面模型：

- `advanced` → `ultraenchantment:item/enchanted_book`
- `super` → `ultraenchantment:item/enchanted_book_super`
- `ultra` → `ultraenchantment:item/enchanted_book_ultra`

没有阶段附魔的原版书保持原版外观。创造栏不再投放自定义书样本；
阶段书由数据包 / 战利品 / 命令通过 `stored_enchantments` 生成。

## 7. 当前限制

- 只有 `sharpness` 一条示例树。
- 星图是静态布局，无拖拽/缩放。
- 概率已实现，**保底与洗点未实现**。
- `conflict_group` 已进数据，但尚未强制执行（单树单节点天然互斥）。
- 没有图鉴界面；星图只在星光台里。
- 融合（多父节点）尚未实现。

## 8. 加一条谱系

1. 在 `data/<ns>/enchantment/` 写阶段附魔 JSON；
2. 在 `data/<ns>/ultraenchantment/starlight_tier/` 引用或新增阶级；
3. 在 `data/<ns>/ultraenchantment/starlight_tree/` 写树：根节点用原版附魔 id，
   其余节点用阶段附魔 id；
4. 在 `UELang` 加节点/分支/星座的翻译键；
5. `runData` + `runServer`，看日志里的
   `[starlight] indexed N trees / M tiers / K node enchantments`
   以及 “树引用了不存在的附魔” 告警。
