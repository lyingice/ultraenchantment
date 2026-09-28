# 进阶附魔 · 数据包制作指南

面向要自己动手做附魔进阶内容的人：模组作者、整合包作者、数据包作者。

读完你能做到：

- 给任意原版附魔写一条**由你掌控**的三阶曲线；
- 给某一阶**新增**一个原版没有的效果；
- 手写一份**不依赖 datagen** 的 JSON 数据包；
- 知道什么能做、什么做不到、哪里会静默出错。

> 想先用现成内容上手，直接看 [**效果总表**](enchantment-effects.md)——它由 datagen 生成，
> 列出全部谱系每一阶的**绝对公式**，也是本指南所有例子的来源。

---

## 1. 三个概念

| 概念 | 是什么 |
|---|---|
| **阶段条目** | 「某条谱系的某一阶」。住在数据包注册表 **ultraenchantment:enchantment** 里，**它不是附魔**，不进 minecraft:enchantment。 |
| **谱系（lineage）** | 一条原版附魔的成长线。字段 **root** 永远指向那个原版附魔，例如 **minecraft:sharpness**。 |
| **阶级（tier）** | 原生阶 / 高阶(advanced) / 超级(super) / 究极(ultra)。**原生阶没有阶段条目**，它只是「还没进阶」这个状态的代称。 |

一条谱系最多三个条目，用 **next** 串成链：advanced → super → ultra →（无，即终点）。

---

## 2. 铁律：效果按 tierLevel 结算，不是按存储等级

升阶时物品上那条原版附魔的等级是**不变的**（锋利 5 升阶后仍是 5，改它会波及附魔台、村民交易等一切原版机制）。
真正决定强度的是 **tierLevel ——「这个进阶形态自己的附魔等级」**，它从 1 起算，用升级书往上提。

**做数据包时你只需要记住**：阶段条目里的公式，自变量 n 就是 **tierLevel**。

举例：高阶锋利写成 **3 + 1×(n-1)**，玩家在「高阶锋利 1 级」时吃到的是 **3**，升到 3 级吃到 **5**。
和原版那个存储的 5 级没有关系。

---

## 3. 三种写法

给某一阶准备效果时，有三种写法。**它们不是三个模块，而是三个不同的落点**。

| 写法 | 落点 | 一句话 | 本模组自己的谱系用吗 |
|---|---|---|---|
| **覆盖** | 改**已有**组件的数值 | 这一阶该组件的**公式完全由我决定**，原版数字不参与 | ✅ 主力，全部谱系都用 |
| **追加** | **新增**一个组件 | 这一阶**多出**一个原版没有的效果 | ✅ 少量（击退、穿甲、缓慢这类） |
| **增量** | 改**已有**组件的数值 | 我写「原版 + 我的增量」，由代码算出最终式 | ❌ 不用，留给第三方 |

### 3.1 覆盖（主力）

**原理**：阶位条目的数值**自成一体**。生成的阶段条目里，该组件的值就是该阶级自己的公式。

原版锋利的伤害是 **1 + 0.5×(n-1)**。三阶各写各的：

| 阶级 | 公式 | 1级 | 5级（满级） |
|---|---|---|---|
| 原生（原版） | 1 + 0.5×(n-1) | 1 | 3 |
| 高阶 | **3 + 1×(n-1)** | 3 | 7 |
| 超级 | **6 + 1.5×(n-1)** | 6 | 12 |
| 究极 | **10 + 2.5×(n-1)** | 10 | 20 |

**边界**：

- 覆盖的是**数值**，**不是条件**。亡灵杀手的伤害依然只打亡灵、保护类依然只挡特定伤害来源——
  条件是「这个附魔是什么」的一部分，把它一起换掉等于换了个附魔。
- 覆盖只对「有条件的**数值**条目」成立。像 **crossbow_charge_time**（单值组件）也是覆盖语义：
  你给什么值，结算时就用什么值**替换**当前值——所以数值写小是**变弱**，不是「少给一点」。

### 3.2 追加

**原理**：往组件的列表**末尾再挂一条**，与原版那条并列，结算时两条都生效。

**用途只有一个：这一阶多了个原版没有的效果。**

```
究极锋利 = 伤害 10 + 2.5×(n-1)              ← 覆盖（数值）
          + 击退 0.25 + 0.15×(n-1)          ← 追加（原版锋利根本没有击退组件）
          + 降低护甲有效性 0.05 + 0.02×(n-1) ← 追加
```

**边界**：

- **不要**用追加去改一个原版已有组件的数值——那是覆盖的活。两条 add 会在结算时先后作用，
  数学上等价于把公式相加，但读者得自己算，而且表里会出现两条看不出主次的条目。
- 追加与覆盖**不冲突**：它们作用在**不同的组件**上。冲突只发生在你想改**同一个**组件的数值时——
  那就必须二选一（见 3.4）。
### 3.3 增量（可选，给第三方）

**原理**：我写「原版那条 + 我的增量」，由代码在生成时相加/相乘，最终仍合成**一条**覆盖式。

**它唯一的用途**：这个效果是**别人定义的**（原版、或另一个模组），
你不想（或没法）把它的数字抄进自己的数据里，希望原版改了你的内容也跟着走。

**代价（务必权衡）**：

- 数值会**随原版漂移**——原版一改，你的强度就变了，而且不会报错；
- 读者看你的源文件**看不到绝对数值**，得心算「原版 + 增量」；
- 原版那条一旦变成带条件的、或从一个变成多个，合并就会**放弃**，退回成追加——
  也就是说同一份配置在不同版本下可能走不同分支。

**结论**：本模组自己的 30 条谱系**一条都不用**增量，全部写绝对公式。
你自己做内容时，除非确实要跟随别人的数字，否则也建议写绝对公式。

### 3.4 边界一句话版

> **覆盖与增量抢同一个位置（改已有组件的数值），同一组件只能选一个；
> 追加在另一个位置（新增组件），与它们互不冲突。**
>
> 本模组的选择是：**一律覆盖 + 少量追加，不用增量。**

---

## 4. 数值曲线的规格

阶段条目的数值 = 原版数值的「**首级值 × baseMul、每级增量 × slopeMul**」：

| 阶级 | baseMul | slopeMul |
|---|---|---|
| 高阶 advanced | 3 | 2 |
| 超级 super | 6 | 3 |
| 究极 ultra | 10 | 5 |

锋利（原版 1 + 0.5×(n-1)）验算：

```
高阶 = 1×3  + 0.5×2×(n-1) = 3 + 1×(n-1)
超级 = 1×6  + 0.5×3×(n-1) = 6 + 1.5×(n-1)
究极 = 1×10 + 0.5×5×(n-1) = 10 + 2.5×(n-1)
```

**只作用于「加法 + 线性」的数值**。其余形状没有「首级值 + 每级增量」这种结构，原样保留，
成长性由谱系自己用追加补丁给（例如耐久是概率、经验修补是乘数、效率是等级平方）。

**改这四个数就是改全部谱系的强度**，属于规格级改动：必须同步效果总表（[enchantment-effects.md](enchantment-effects.md)）。

---

## 5. 手写数据包

不用 datagen 也能做。路径规则是**双命名空间**：

```
data/<条目命名空间>/<注册表命名空间>/<注册表路径段>/<条目路径>.json
```

本模组固定为：

```
data/ultraenchantment/ultraenchantment/enchantment/<阶级>/<附魔名>.json
```

例：**data/ultraenchantment/ultraenchantment/enchantment/advanced/sharpness.json**

### 5.1 一个完整的三阶例子

**高阶（3 + 1×(n-1)）**

```json
{
  "root": "minecraft:sharpness",
  "tier": "advanced",
  "next": "ultraenchantment:super/sharpness",
  "required_level": 5,
  "definition": {
    "weight": 10,
    "max_level": 5,
    "min_cost": { "base": 11, "per_level_above_first": 21 },
    "max_cost": { "base": 31, "per_level_above_first": 21 },
    "anvil_cost": 3,
    "supported_items": "#minecraft:enchantable/sharp_weapon",
    "primary_items": "#minecraft:enchantable/sword",
    "slots": ["mainhand"]
  },
  "effects": {
    "minecraft:damage": [
      { "effect": { "type": "minecraft:add",
                     "value": { "type": "minecraft:linear", "base": 3.0, "per_level_above_first": 1.0 } } }
    ]
  }
}
```

**究极（10 + 2.5×(n-1)，外加击退与穿甲）**

```json
{
  "root": "minecraft:sharpness",
  "tier": "ultra",
  "required_level": 3,
  "definition": {
    "weight": 10, "max_level": 5, "anvil_cost": 7,
    "min_cost": { "base": 46, "per_level_above_first": 56 },
    "max_cost": { "base": 66, "per_level_above_first": 56 },
    "supported_items": "#minecraft:enchantable/sharp_weapon",
    "slots": ["mainhand"]
  },
  "effects": {
    "minecraft:damage": [
      { "effect": { "type": "minecraft:add",
                     "value": { "type": "minecraft:linear", "base": 10.0, "per_level_above_first": 2.5 } } }
    ],
    "minecraft:knockback": [
      { "effect": { "type": "minecraft:add",
                     "value": { "type": "minecraft:linear", "base": 0.25, "per_level_above_first": 0.15 } } }
    ],
    "minecraft:armor_effectiveness": [
      { "effect": { "type": "minecraft:add",
                     "value": { "type": "minecraft:linear", "base": -0.05, "per_level_above_first": -0.02 } } }
    ]
  }
}
```

### 5.2 字段表

| 字段 | 必填 | 说明 |
|---|---|---|
| root | ✅ | 谱系根源，必须是真实存在的附魔 id。进阶时用来定位物品上那条原版附魔。 |
| tier | ✅ | advanced / super / ultra。（**没有 native**——原生阶不是条目。） |
| next | ❌ | 下一阶的条目 id；缺省 = 本条即谱系终点。**必须是真实存在的条目**，写错会让进阶卡死。 |
| required_level | ✅ | 进阶到本条所需的 tierLevel（≥1）。实际门槛取 min(它, 来源阶级上限)，所以不会死锁。 |
| definition | ✅ | 见下。 |
| effects | ✅ | 见第 6 节。可以是空对象，但那条附魔就只剩「改了个名字」。 |

**definition** 子字段：weight（1..1024，附魔台权重）、max_level（1..255，**等级上限**）、
min_cost / max_cost（附魔台花费曲线，各含 base 与 per_level_above_first）、anvil_cost（铁砧代价）、
supported_items（#标签，哪些物品能被附魔）、primary_items（可选，#标签）、
slots（mainhand / offhand / head / chest / legs / feet / any / armor …）。

> **建议 max_level 沿用根源附魔的上限**（耐久 3、保护 4、锋利 5），这样升级书的上限与数据一致。
> 上限只有 1 的附魔（无限、经验修补、火矢、多重射击、引雷）要注意：**1 级就是满级**。
---

## 6. 八种做法（种类丰富的来源）

下面每一段 effects 都可以直接抄进你自己的条目。

### ① 纯数值型 —— 锋利、力量、穿刺、忠诚、饵钓、海之眷顾

```json
"minecraft:damage": [
  { "effect": { "type": "minecraft:add",
                 "value": { "type": "minecraft:linear", "base": 10.0, "per_level_above_first": 2.5 } } }
]
```

### ② 条件数值型 —— 亡灵杀手、节肢杀手（只对特定生物）

关键是 **requirements** ——它跟着**条目**，不跟着数值，所以覆盖数值时它自动保留：

```json
"minecraft:damage": [
  { "requirements": { "condition": "minecraft:entity_properties", "entity": "this",
                       "predicate": { "type": "#minecraft:sensitive_to_smite" } },
    "effect": { "type": "minecraft:add",
                 "value": { "type": "minecraft:linear", "base": 25.0, "per_level_above_first": 12.5 } } }
]
```

### ③ 伤害来源限定减免 —— 保护 / 火焰保护 / 爆炸保护 / 弹射物保护 / 摔落缓冲

```json
"minecraft:damage_protection": [
  { "requirements": { "condition": "minecraft:damage_source_properties",
                       "predicate": { "tags": [ { "id": "minecraft:is_fire", "expected": true },
                                                  { "id": "minecraft:bypasses_invulnerability", "expected": false } ] } },
    "effect": { "type": "minecraft:add",
                 "value": { "type": "minecraft:linear", "base": 6.0, "per_level_above_first": 3.0 } } }
]
```

### ④ 属性型 —— 水下呼吸、深海探索者、迅捷潜行、火焰保护（燃烧时间）

```json
"minecraft:attributes": [
  { "attribute": "minecraft:generic.oxygen_bonus",
    "id": "ultraenchantment:ascension/respiration",
    "operation": "add_value",
    "amount": { "type": "minecraft:linear", "base": 6.0, "per_level_above_first": 3.0 } }
]
```

**边界**：id 在同一阶段内**必须唯一**，重复会在运行期让 AttributeInstance 抛异常。
operation 可取 add_value / add_multiplied_base / add_multiplied_total。

### ⑤ 概率型 —— 耐久（按二项分布免除耐久消耗）

```json
"minecraft:item_damage": [
  { "effect": { "type": "minecraft:remove_binomial",
                 "chance": { "type": "minecraft:fraction",
                             "numerator": { "type": "minecraft:linear", "base": 3.0, "per_level_above_first": 1.5 },
                             "denominator": { "type": "minecraft:linear", "base": 10.0, "per_level_above_first": 5.0 } } } }
]
```

### ⑥ 乘算型 —— 经验修补

```json
"minecraft:repair_with_xp": [
  { "effect": { "type": "minecraft:multiply", "factor": 6.0 } }
]
```

### ⑦ 实体效果型 —— 火焰附加、荆棘、引雷、风爆、弹射物出膛

**post_attack** 的条目多两个字段：**enchanted**（谁带的附魔）与 **affected**（作用于谁）。

```json
"minecraft:post_attack": [
  { "enchanted": "attacker", "affected": "victim",
    "requirements": { "condition": "minecraft:random_chance",
                      "chance": { "type": "minecraft:linear", "base": 0.45, "per_level_above_first": 0.45 } },
    "effect": { "type": "minecraft:all_of", "effects": [
        { "type": "minecraft:damage_entity", "damage_type": "minecraft:thorns",
          "min_damage": 1.0, "max_damage": 5.0 },
        { "type": "minecraft:damage_item", "amount": 2.0 } ] } }
]
```

**给目标上状态效果**（例如究极击退附带的缓慢）：

```json
"minecraft:post_attack": [
  { "enchanted": "attacker", "affected": "victim",
    "effect": { "type": "minecraft:apply_mob_effect",
                 "to_apply": "minecraft:slowness",
                 "min_duration": { "type": "minecraft:linear", "base": 5.0, "per_level_above_first": 5.0 },
                 "max_duration": { "type": "minecraft:linear", "base": 5.0, "per_level_above_first": 5.0 },
                 "min_amplifier": { "type": "minecraft:linear", "base": 0.0, "per_level_above_first": 1.0 },
                 "max_amplifier": { "type": "minecraft:linear", "base": 0.0, "per_level_above_first": 1.0 } } }
]
```

时长单位是**秒**；amplifier 0 = I 级。点火用 **minecraft:ignite**（时长单位是 tick）。

### ⑧ 单值型 —— 快速装填、激流

这两个组件**不是列表**，直接就是一个值效果。**它们是覆盖语义**：结算时用你给的值**替换**当前值。

```json
"minecraft:crossbow_charge_time": {
  "type": "minecraft:add",
  "value": { "type": "minecraft:clamped", "min": -0.9, "max": 0.0,
             "value": { "type": "minecraft:linear", "base": -0.75, "per_level_above_first": -0.75 } }
}
```

**边界**：弩的装填时间会经过 floor(f × 20)，负值会算出负 tick，所以**必须用 clamped 兜住上界**。

---

## 7. 自检清单

- [ ] root 是真实存在的附魔 id；
- [ ] next 指向真实存在的阶段条目（或留空），且**不要成环**；
- [ ] tier 与所在目录一致；
- [ ] max_level 在 1..255，required_level ≥ 1；
- [ ] max_level 与该谱系根源附魔的上限一致（否则升级书会写出无处定义的等级）；
- [ ] 同一阶段内 attributes 的 id 不重复；
- [ ] 装填时间一类会被 floor 的数值用 clamped 兜底；
- [ ] 条件是「附魔身份」的一部分，覆盖数值时**保留**了它；
- [ ] 改完跑一次 **runData**，看 [效果总表](enchantment-effects.md) 里该阶的公式是不是你要的。

## 8. 常见错误

| 症状 | 原因 |
|---|---|
| 进阶后附魔「没效果」 | effects 写成空对象，或写到了不存在的组件名上（未知组件会被忽略，不报错） |
| 进阶后附魔**消失** | 阶段条目里没有该效果——结算层会把原版那条置 0，条目的 effects 就是它的全部 |
| 高等级被砍回去 | 阶段条目没写进物品组件就没这个问题；若你手改了存储等级，原版铁砧会按 max_level 砍 |
| 升级书点了没反应 | 升级书目标值会夹在该谱系该阶级的 max_level 内；已达上限自然无效 |
| 属性不叠加 / 报错 | 同一阶段内两个 attributes 条目用了同一个 id |
| 数值比预期小 | 用了「增量」写法却记错了原版数值；或该组件是覆盖语义（写小 = 变弱） |

---

## 9. 载体书（铭刻型）与数据包的接口

载体书**不是数据包文件**——它是物品上的载荷组件（`ultraenchantment:inscription_spec`），
由铁砧产出与消耗。但它的每一条规则边界都由你写的数据包决定。这一章只讲「你写的那部分如何影响它」。

### 9.1 谁决定什么

| 数据包里的项 | 决定载体书的什么 |
|---|---|
| `max_level`（逐谱系 × 逐阶级） | 条目等级的**夹取上限**：贴装备、书 +1 合并、创造旁路写入，全都要先夹它 |
| `required_level`（逐阶级） | **转印门槛**：原版书的存储等级 ≥ `min(required_level, 该附魔自身上限)` |
| 阶段条目是否存在（某 root × 某 tier） | 创造栏是否产出该载体书；**转印时缺一阶 → 整本不转** |
| `anvil_cost` | 升阶成本、转印成本、装备同名合并的成本系数 |
| `root` | 条目能不能落到某件装备上（谱系不存在的条目会进剩菜） |

### 9.2 载荷长什么样

它只出现在**物品组件**里（创造栏物品、铁砧产物、剩菜书），不落盘成 json：

    {
      "tier": "advanced",
      "entries": [
        { "enchantment": "minecraft:sharpness", "level": 3 },
        { "enchantment": "minecraft:looting",   "level": 2 }
      ]
    }

`level` 是**曲线等级（tierLevel）**，不是存储等级；实际生效值一律夹在该谱系该阶级的 `max_level` 内。

### 9.3 三条你会立刻撞上的自检

- [ ] 把某条谱系的 `max_level` 写成 1 → **那条谱系的升级书与一切合并全部空转**（夹取上限就是 1）。
      引雷 / 火矢 / 无限 / 经验修补 / 多重射击原版上限就是 1，天然如此。这是有意行为，不是 bug。
- [ ] 把某阶级的 `required_level` 写成 1 → 一本最普通的原版附魔书就能转印成载体书。
      想保住「必须一本锋利 V」的手感，就保持 `required_level: 5`。
- [ ] 撤掉某谱系某阶级的阶段条目 → 创造栏不再产出那本书，转印也**整本不转**（数据包是事实源，视图只是视图）。

### 9.4 常见错误

| 症状 | 原因 |
|---|---|
| 载体书点上去没反应 | 生存模式要求「目标谱系已有记录且同阶级」；白装备请用进阶书，或用创造模式 |
| 条目没吃下去、书却回来了 | 那是**剩菜书**：被拒绝的条目会原样留在书上（有意行为） |
| 整本都贴不上、书原样留在铁砧 | 一条都没改变 → 不产出、不消耗（等价于原版无效操作） |
| 合并两本书等级没涨 | 已达该谱系该阶级的 `max_level`（或该谱系上限为 1）；也可能两本阶级不同 |
| 转印失败 | 门槛不够（存储等级 < required_level）、原版书是空的、或该谱系没铺对应阶级 |
