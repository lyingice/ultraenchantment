# 对外 API（给模组作者）

UltraEnchantment 给其它模组留了一套**软依赖**用的公开面。入口只有一个类：

```java
com.lyingice.ultraenchantment.api.UltraEnchantmentApi
```

（源码在无对应中文时按英文读；本文所有示例都只用 JDK + 原版类型，**不需要** import 我们的内部包。）

---

## 0. ⚠️ 先分清两个「等级」

这是最容易用错的地方——物品上其实有**三个**数字：

| 概念 | API 里的名字 | 取值 | 例子 |
|---|---|---|---|
| **阶级**（进阶到哪一档） | `tier` | 0=基础 1=高阶 2=超级 3=究极 | 「究极锋利」的 tier = **3** |
| **该阶的曲线等级** | `curveLevel` | 1 .. 该阶上限 | 「究极锋利 V」的 curveLevel = **5** |
| 原版附魔等级 | （原版组件） | 原版上限 | 锋利 5 —— 它**只决定进阶门槛** |

**效果强度按 `curveLevel` 结算**，不是按原版等级。所以：

- `getTierLevel(stack, sharpness)` → 这条附魔进阶到哪一档；
- `getCurveLevel(stack, sharpness)` → 该档练到几级。

---

## 1. 查询（只读，双端可用）

```java
int  UltraEnchantmentApi.getTierLevel(ItemStack stack, Enchantment enchant);      // 0..3
int  UltraEnchantmentApi.getCurveLevel(ItemStack stack, Enchantment enchant);    // 0=未进阶
boolean UltraEnchantmentApi.isUltraEnchant(Enchantment enchant);
int  UltraEnchantmentApi.getMaxAllowedTier(Enchantment enchant);                  // 0=不支持
boolean UltraEnchantmentApi.isEnchantLocked(ItemStack stack, Enchantment enchant);
Optional<ResourceLocation> UltraEnchantmentApi.getStageId(ItemStack stack, Enchantment enchant);
Optional<String> UltraEnchantmentApi.getImcAttribute(Enchantment enchant, String key);
```

约定：

- 未进阶 / 不支持的附魔，等级类返回 **0**（不是 -1、不抛异常）；
- `getMaxAllowedTier` = **数据包里实际存在的最高阶**，并被 IMC 的 `max_tier` 收紧；
- `isUltraEnchant` 对「本版没有进阶的附魔」返回 `false`（例如财富、精准采集、两个诅咒，以及 v3 移除的深海探索者/迅捷潜行/忠诚/引雷/快速装填）；
- `isEnchantLocked` 等价于「tier ≥ 1」：已进阶 → 砂轮洗不掉、铁砧合并不会抹除。

## 2. 修改（**只在逻辑服务端主线程**）

```java
boolean setTierLevel(ItemStack stack, Enchantment enchant, int tier);
boolean setCurveLevel(ItemStack stack, Enchantment enchant, int curveLevel);
boolean unlockEnchant(ItemStack stack, Enchantment enchant);
boolean removeUltraEnchant(ItemStack stack, Enchantment enchant);
```

| 方法 | 行为 | 备注 |
|---|---|---|
| `setTierLevel(…, tier)` | 升到该阶级；**曲线等级归 1**（与铁砧进阶一致） | `tier == 0` 等价 `unlockEnchant`；tier 不变 → 返回 `false`（想改等级用 `setCurveLevel`） |
| `setCurveLevel(…, n)` | 改当前阶级的曲线等级 | 未进阶 → `false`；超上限**夹取**并记 WARN（与升级书一致） |
| `unlockEnchant` | 清进阶记录（回到基础阶，祛咒石做的事） | 本来就没记录 → `false` |
| `removeUltraEnchant` | 清记录**并摘掉附魔本身** | 两者都没有 → `false` |

返回值 `true` = **物品确实被改了**。以下情况一律 `false`：越界、未纳入体系、该阶没有阶段条目、客户端或非主线程、被事件取消。**不夹取**（`setTierLevel` 超上限直接拒绝，避免"静默改小"）。

> 写接口**就地修改**传入的 `ItemStack`（它本来就是值语义），不返回新栈。

## 3. 事件

事件挂在 NeoForge 的 **game bus**（`NeoForge.EVENT_BUS`）。

### UltraEnchantTierUpgradeEvent（可取消）

任何会让阶级或曲线等级变高的操作都会触发，`source()` 区分来源：
`ANVIL_BOOK` / `UPGRADE_BOOK` / `MERGE` / `API`。

```java
NeoForge.EVENT_BUS.addListener((UltraEnchantTierUpgradeEvent e) -> {
    if (e.rootId().equals(ResourceLocation.withDefaultNamespace("sharpness")) && e.newTier() == 3) {
        e.setCanceled(true);   // 整次操作作废：物品不变、材料不扣
    }
});
```

读得到的字段：`stack()`（**尚未修改**的原栈）、`rootId()`、`enchantment()`（拿不到注册表时为 `null`，用 `rootId()`）、`oldTier()` / `newTier()`、`oldCurveLevel()` / `newCurveLevel()`、`source()`。

⚠️ 取消是**整次作废**，不是"改小一点"。同名装备合并时，取消只让**那一条谱系**不参与本次合并。

### UltraEnchantLockChangeEvent（不可取消）

锁定状态翻转时触发（进阶 → 锁定 / 解除）。`cause()`：`CURSE_STONE` / `REMOVE` / `API`。字段：`stack()`（已变化）、`rootId()`、`enchantment()`、`locked()`、`cause()`。

**为什么不可取消**：解锁基本由玩家主动行为触发（祛咒石已扣耐久），允许取消会出现「石头没了、附魔还在」。要拦截请在**操作发生前**的事件里拦。

## 4. IMC 软注册（无需编译依赖）

```java
InterModComms.sendTo("ultraenchantment", "register_enchant", () -> "mymod:my_ench;max_tier=2");
InterModComms.sendTo("ultraenchantment", "tier_attributes", () -> "mymod:my_ench;damage_bonus=4");
InterModComms.sendTo("ultraenchantment", "upgrade_cost",   () -> "mymod:my_ench;base=2;per_tier=4");
```

载荷一律是 **String**：分号分段，首段是附魔 id，其余是 `key=value`。

| 通道 | 作用 | 键 |
|---|---|---|
| `register_enchant` | 声明纳入体系 | `max_tier`（1..3，**收紧**，不会超过数据包实际阶级数） |
| `tier_attributes` | 自定义键值，别的模组可读（`getImcAttribute`） | 任意 |
| `upgrade_cost` | 覆盖升级书花费：`base + per_tier × (目标等级 - 1)` | `base`、`per_tier` |

坏格式 / 未知键 / 非法数值一律 **WARN 并忽略**，不影响其它注册。

> **⚠️ 进阶效果仍然来自数据包。** IMC 只做注册与调参：每一阶的实际效果必须由数据包提供
> （`data/<你的命名空间>/ultraenchantment/enchantment/<阶级>/<附魔>.json`）。
> 只注册、不带数据包，这条附魔不会获得任何进阶效果。

## 5. 软依赖写法（推荐）

不要 `implementation` 依赖本模组，而是：

```groovy
// 编译期可见（不打进你的 jar）
compileOnly files("libs/ultraenchantment-0.1.0.jar")
```

运行时**先判模组在不在**再调用：

```java
if (ModList.get().isLoaded("ultraenchantment")) {
    int tier = UltraEnchantmentApi.getTierLevel(stack, enchant);
    // ...
}
```

并把它标成可选依赖（`neoforge.mods.toml` 里 `type="optional"`），这样不装也不会崩。

## 6. 稳定性承诺

- 公开面 = `com.lyingice.ultraenchantment.api` 包（门面类 + 两个事件 + IMC 常量）。**其余包不保证兼容**。
- 组件字段名与序列化格式属于内部实现，可能随版本调整——请只用 API 读写，不要自行解析组件。
- 行为不变更：查询永远无副作用；修改永远只在服务端主线程生效。

---

## 附：真源与回归

本文件的每条约定都有对应的运行期自检（查询/写入/越界拒绝/事件取消零副作用/IMC 容错，共 43 项断言）。
改 API 时请同步更新自检，并跑一次 `runServer` 确认全绿。
