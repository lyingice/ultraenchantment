# 进阶附魔书的战利品掉落

本文说明：进阶附魔书怎么作为战利品产出、三阶难度如何平衡、以及怎么调整。

- [机制](#机制)
- [掉落曲线（实测）](#掉落曲线实测)
- [设计理由](#设计理由)
- [如何调整](#如何调整)
- [验证方法](#验证方法)

---

## 机制

### 为什么必须用 GlobalLootModifier 而不是静态战利品表

战利品表 JSON 是**静态**的：`set_components` 只能写**固定**组件值。
而我们要的是「随机一条谱系 + 随机等级」——静态 JSON 表达不了。

原版 `EnchantRandomlyFunction` 确实能随机挑附魔，但它写的是
`minecraft:enchantments`（原版附魔注册表），**填不了我们的自定义载荷组件**
（`ascension_spec` / `inscription_spec` / `upgrade_spec`）。

所以随机必须在代码里做 → 用 NeoForge 的 `GlobalLootModifier`
（`logic/loot/ULTBookLootModifier.java`）。

### 追加而非替换

`GlobalLootModifier` 拿到的 `generatedLoot` 是原版战利品表**已经算完**的结果，
我们只在末尾 `add`。因此：

- **不修改**任何原版战利品表文件 → 与其它模组零冲突
- 原版掉落一个不少

### 掉落池来自数据包

谱系池走 `ReloadEvents.roots()` + `hasTier(root, tier)`，与 `LineageTradePool` 同源。
**以后新增谱系会自动进池**，不需要回来改代码。
数据包没定义某一阶时，该阶**不产出**（与创造栏、村民交易一致）。

### 掉出的是「成品书」

三阶都用**铭刻型（载体书）**：它像原版附魔书一样直接承载「某阶级的某条进阶附魔」，
捡到就能贴装备——这才是战利品该有的语义。

（进阶书/升级书描述的是「操作」而非「形态」，作为掉落会让玩家拿到需要另凑材料的半成品。）

---

## 掉落曲线（实测）

> 数据来自 20000 次真实战利品表调用（服务端探针），非理论推算。

### 单次开箱各阶概率

| 来源 | 出书概率 | 高阶 | 超级 | 究极 |
|---|---|---|---|---|
| 普通箱子 | 15% | **11.05%** | **3.95%** | **0%（不产出）** |
| 稀有箱子 | 15% | 10.50% | 3.75% | 0.75% |
| BOSS | 15% | 7.78% | 5.56% | 1.67% |

### 折算成「平均多少次能拿到一本」

| 来源 | 高阶 | 超级 | 究极 |
|---|---|---|---|
| 普通箱子 | ~9 次 | ~25 次 | **永不** |
| 稀有箱子 | ~10 次 | ~27 次 | ~133 次 |
| BOSS | ~13 次 | ~18 次 | ~60 次 |

### 实测与期望的吻合度

| 表 | 高阶偏差 | 超级偏差 | 究极偏差 |
|---|---|---|---|
| 地牢 | +0.2% | −1.2% | 一致 |
| 村庄 | +2.2% | +2.6% | 一致 |
| 末地城 | +0.6% | −0.3% | −6.0% |
| 凋灵 | −5.1% | −4.2% | +1.7% |

均在 20000 次抽样的合理波动内。

### 对照实验

| 检查项 | 结果 |
|---|---|
| 未挂载的表（`spawn_bonus_chest`） | **0 本** → 条件精确命中，不误伤其它表 |
| 载荷异常数 | **0** → 造出的书组件完整 |
| 普通箱子的究极 | **0** → 「究极不当普通箱子产出」确实生效 |

---

## 设计理由

### 两层控制，而不是一层概率

只用「究极 1%」会让玩家感到**运气差**，而不是「我该去更危险的地方」。
所以：

1. **来源门槛**：普通箱子**不产出**究极
2. **阶级权重**：同一来源内 高阶 ≫ 超级 ≫ 究极

### 权重表

**基础权重**：高阶 70 / 超级 25 / 究极 5

**来源修正**：

| 来源 | 高阶 | 超级 | 究极 |
|---|---|---|---|
| 普通箱子 | ×1 | ×0.5 | **×0** |
| 稀有箱子 | ×1 | ×1 | ×1 |
| BOSS | ×1 | ×2 | ×3 |

> 普通箱子的究极权重是 **0**，所以「究极只在稀有来源出现」这条规则
> 不需要额外 `if`——掷权重时自然选不到。

### 为什么用整数权重而非浮点概率

- 无浮点累积误差
- 期望值可以**手算校验**（见上表推导），便于探针比对

### 挂载了哪些表

**普通箱子（10 张）**：地牢、废弃矿井、要塞（走廊/交叉口/图书馆）、
村庄（盔甲匠/工具匠/武器匠/教堂/制图师）

**稀有箱子（5 张）**：末地城、林地府邸、堡垒（宝藏/其它）、远古城市

**BOSS（2 张）**：末影龙、凋灵

---

## 如何调整

### 改权重

编辑 `logic/loot/LootTierWeights.java` 的常量：

```java
public static final int BASE_ADVANCED = 70;
public static final int BASE_SUPER = 25;
public static final int BASE_ULTRA = 5;
```

来源修正在同文件的 `weightOf(...)` 里（`COMMON` / `RARE` / `BOSS` 三个分支）。

> 常数写在一起而不是散在 `switch` 里，是为了让「调平衡」只需看这一处。

### 改产出概率

编辑 `datagen/UEGlobalLootModifiers.java`：

```java
private static final double COMMON_CHANCE = 0.15D;
private static final double RARE_CHANCE  = 0.15D;
private static final double BOSS_CHANCE  = 0.15D;
```

### 加/减挂载的表

编辑同文件的 `COMMON_TABLES` / `RARE_TABLES` / `BOSS_TABLES` 三个清单。
「哪张表算稀有」是**数据**，不是硬编码逻辑——调整掉落口味只改这里。

改完运行 `gradlew runData` 重新生成 JSON。

---

## 验证方法

### 确定性检查

```bash
gradlew runData      # 第一次
gradlew runData      # 第二次应为 written: 0, removed stale: 0
```

### 曲线实测（需要临时探针）

本次实现时用的探针做法（**用后即删**）：

1. 在 `ServerStartedEvent` 里取 `reloadableRegistries().getLootTable(key)`
2. 循环 20000 次调用 `table.getRandomItems(params, seed)`
3. 统计 `UEItems.isAdvancedBook` 命中的物品，按 `INSCRIPTION_SPEC.tier()` 分桶
4. 与 `LootTierWeights.probabilityOf(...) ` 的期望值比对
5. **务必包含一个未挂载的表作对照**（应产出 0 本）——否则无法证明条件真的精确命中

> ⚠️ 探针跑完必须删除。本项目在早期曾因探针挂在每帧触发的路径上，
> 把日志刷到 111 MB 并拖垮客户端。

---

## 相关文件

| 文件 | 职责 |
|---|---|
| `logic/loot/LootTierWeights.java` | 权重与来源修正（**调平衡看这里**） |
| `logic/loot/LootBookFactory.java` | 随机谱系 + 随机等级 + 造书 |
| `logic/loot/ULTBookLootModifier.java` | 战利品修改器（`doApply`） |
| `registry/UELootModifiers.java` | 序列化器注册 |
| `datagen/UEGlobalLootModifiers.java` | 生成挂载 JSON（**调概率/表清单看这里**） |
| `data/neoforge/loot_modifiers/global_loot_modifiers.json` | 生成物：索引 |
| `data/ultraenchantment/loot_modifiers/*.json` | 生成物：各修改器实例 |
