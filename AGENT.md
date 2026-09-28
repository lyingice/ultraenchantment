# AGENT.md · AI 协作与工程约定

> 面向在本仓库工作的 AI 代理（以及人类协作者）。
> 目标：**让每一次改动都可验证，让每一个坑只踩一次，让被否掉的方案不要再被重新推导。**
>
> 配套文档：[`docs/book-system-spec.md`](docs/book-system-spec.md)（体系规格与实现契约）、
> [`docs/guide-data.md`](docs/guide-data.md)（数据包教程）、[`README.md`](README.md)（面向玩家的介绍）。
> 基线：NeoForge **21.1.249** 源码实证 · 核验日期 **2026-09**

---

## 0. 铁律

1. **API 签名一律查证据，禁止凭记忆写。**
   优先级：① `build/moddev/artifacts/neoforge-21.1.249-sources.jar`（反编译 + patch 后的真实源码）
   → ② `<skill>/references/api/*.md` → ③ 官方文档 → ④ 网络搜索。
   前两者能回答的**不要**去搜网。
2. **零 Mixin 优先，但不是零 Mixin 教条。** 穷尽事件与数据包之后再上 Mixin。本方案唯一合法的 Mixin 是 `RepairItemRecipe`（见 P0-10）。
3. **构建一律走 `mc_gradle`**，不要直接 shell 调 `gradlew`。
4. **新工程一律走 `mc_scaffold`**，不要手写 Gradle 脚手架。
5. **改完必须构建通过**才算完成。编译失败不是「快好了」，是没做完。
6. **改动前先看 §2「被否掉的方案」**，不要重新推导已经排除的路线。

---

## 1. 工程事实（钉选，不要漂移）

| 项 | 钉选值 | 现状 |
|---|---|---|
| MC | 1.21.1 | ✅ |
| NeoForge | **21.1.249** | ✅ 已对齐（§2.5） |
| moddev 插件 | **2.0.144** | ✅ 已对齐 |
| Gradle wrapper | **9.2.1** | ✅ 已对齐（发行版走华为镜像） |
| Java toolchain | 21 | ✅ |
| mod id | `ultraenchantment` | ✅ |
| 包名 | `com.lyingice.ultraenchantment` | ✅ |
| 数据包注册表键 | **`ultraenchantment:enchantment`** | 新增 |
| 数据包目录 | **`data/ultraenchantment/ultraenchantment/{advanced,super,ultra}/`** | 新增 |

### 1.1 工程形态：脱离 MCreator（已决策）

现存目录是 MCreator 2026.0 工作区。**已决定转为纯手写 NeoForge 工程。**

理由（硬约束，不是偏好）：

- MCreator 每次重新生成会**清掉 `// Start of user code block X` 之外的一切**。本模组要写 8 个事件监听器、1 个数据包注册表、5 个数据组件、1 个 Mixin —— 全都会被覆盖。
- MCreator 管着 `assets/ultraenchantment/lang/en_us.json` 与 `.mcreator` 里的 `language_map`，手工加的翻译键会被重写。
- MCreator 生成的 `gradle.properties` 写死了代理且版本落后于钉选值。

**迁移动作**：用 `mc_scaffold`（platform=neoforge, minecraftVersion=1.21.1）生成到**新目录**（`targetDir` 必须不存在或为空），再迁移：

```
src/main/resources/assets/ultraenchantment/textures/item/*.png        ← 保留
src/main/resources/assets/ultraenchantment/textures/item/*.png.mcmeta ← 保留（动画）
```

**不要**带走 `build/`、`.gradle/`、`.mcreator/`、`run/`。

**资产**：书与三档祛咒石的贴图均已就位（`src/main/resources/assets/ultraenchantment/textures/item/`）。

### 1.2 代理配置

现有 `gradle.properties` 含：

```properties
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=10090
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=10090
```

**重建工程时必须确认这个代理是否仍然有效。** 代理已下线而配置还在，依赖下载会全部失败并报连接错误——最容易被误判为「网络问题」而不是「配置问题」。

---

## 2. 被否掉的方案（不要再推导一遍）

这些路线在架构讨论中都试过并被排除。**重新提出它们之前，先读这里的排除理由。**

| # | 被否方案 | 排除理由 |
|---|---|---|
| 1 | **三附魔分立**：`sharpness` → `advanced_sharpness` → … 四个独立附魔，进阶时替换 | 物品身份改变，第三方模组看到不认识的附魔；需要在 6 个标签里封堵获取途径；附魔注册表被污染 |
| 2 | **注册"隐形载体附魔"**，进阶时真正写进物品 | 仍污染 `enchantment` 注册表；创造栏会自动为它生成附魔书 |
| 3 | **阶级 = 等级区间**（原生 1-5 / 高阶 6-10 / 超级 11-15 / 究极 16-20） | **与神化（Apotheosis）等抬高上限的模组正面冲突**——`sharpness: 12` 在那边是合法的更强锋利，被误判成超级阶 |
| 4 | **继承 `Enchantment` 做子类** | `Enchantment` 是 **record**，Java 里隐式 final，不可继承；且 `RegistryDataLoader` 写死 `Enchantment.DIRECT_CODEC`，子类塞不进注册表 |
| 5 | **整体覆写原版 `sharpness.json`** | 一个附魔只能有一份定义，做不到「按阶段不同」；且与所有整合包正面冲突 |
| 6 | **Mixin 注入 `Enchantment.modifyDamage` 等方法做屏蔽** | 不必要——`GetEnchantmentLevelEvent` 已在正确的层面提供了屏蔽+注入能力；Mixin 还要逐方法注入（damage / knockback / protection / tick…） |
| 7 | **独立 `tier/` 注册表** | 阶级只是阶段条目自己身上的标签；谱系深度由注册条目数天然决定，不需要全局 tier 概念 |
| 8 | **跳阶的定向进阶书** | 与「推进到下一阶级」和品质匹配规则冲突；已确认定向书恒定 +1 阶 |
| 9 | **为高级附魔封堵 6 个获取标签 + `exclusive_set` 记账** | 本方案不注册附魔，这套活儿**整个消失** |

---

## 2.5 工程重建实录（已完成，勿重做）

工程已从 MCreator 工作区转为纯手写 NeoForge 工程，**已构建通过**。

| 项 | 落地值 |
|---|---|
| NeoForge | 21.1.249（moddev 2.0.144） |
| Gradle wrapper | 9.2.1，`distributionUrl` 指向**华为镜像** |
| MCreator 旧文件 | 已移至 `_mcreator_backup/`（可随时查阅或删除） |
| 贴图 | 原位保留在 `src/main/resources/assets/ultraenchantment/textures/item/` |
| 语言文件 | 由 datagen 产出（`src/generated/resources`），**不再手写** |

### 迁移时的三个 `Copy-Item -Recurse` 嵌套陷阱 ⚠️

用 `Copy-Item -Recurse` 覆盖已存在目录时，PowerShell 会**嵌套**而不是合并。本次连踩三次：

| 现象 | 后果 |
|---|---|
| `src/main/java/java/com/...` | 类重复编译错误 |
| `gradle/gradle/wrapper/` | 生效的是旧的华为镜像 9.6.1，不是钉选的 9.2.1 |
| `src/main/resources/META-INF/META-INF/neoforge.mods.toml` | **静默失效**：moddev 把类目录当 mod 文件扫描时报 `not a valid mod file`，因为正确路径下没有 toml |

**规则**：往已存在的目录里复制内容，一律用 `Copy-Item <src>\* <dst>\` 或先删后拷，**不要**对整个目录 `-Recurse`。第三个陷阱最阴——它不报路径错误，只报一句与路径无关的 mod 加载失败。

### 代理配置：已确认失效，未继承

原 MCreator 工程写死 `127.0.0.1:10090`。**实测连接被拒**（代理已下线），而直连 `maven.neoforged.net` 与 `services.gradle.org` 均正常。新 `gradle.properties` **不含任何代理配置**。

> Gradle 发行版仍走华为镜像下载：`services.gradle.org` 会重定向到 `downloads.gradle.org`，后者在本机连不上（wrapper 的 10s 超时直接失败）。镜像有完整的 9.2.1。

---

## 2.6 datagen 配置（踩过 6 次才通，务必照抄）

`build.gradle` 的 `data` run 必须**同时**具备三样东西，缺任何一个都会静默失败：

```groovy
neoForge {
    // ① mods {} 必须先于 runs {} 求值
    mods { "${mod_id}" { sourceSet sourceSets.main } }
    runs {
        data {
            data()
            // ② --mod：DatagenModLoader.begin(mods,...) 的集合来源
            programArgument '--mod'
            programArgument "${mod_id}"
            // ③ --output：默认落点是 run/generated，需指回资源源集
            programArgument '--output'
            programArgument file('src/generated/resources').absolutePath
            loadedMods.set([mods.getByName("${mod_id}")])
        }
    }
}
```

### 静默失败的识别特征

```
Initializing Data Gatherer for mods []        ← mods 为空 = 缺 ②
All providers took: 0 ms                      ← 没有任何 provider 跑
（src/generated/resources 不产出任何文件）
```

**机理**（源码实证）：`DatagenModLoader.begin` → `DataGeneratorConfig` 持有 mods 集合 → `makeGenerator(..., shouldExecute = getMods().contains(mc.getModId()))` → `DataGenerator` 在 `shouldExecute=false` 时**丢弃所有 `addProvider`**，不报错、不警告。

### 不要在这些方向上浪费时间（已排除）

| 尝试 | 结果 |
|---|---|
| `event.includeClient()` 过滤 | 1.21.1 恒为 `false`（无 `clientData` 路径），会把 provider 全挡掉 |
| `data()` 换成 `clientData()` / `serverData()` | `prepareDataRun` 只认名为 `data`、type 为 `data` 的 run |
| 依赖 `loadedMods` 自动注入 | 实测 `loadedMods` 赋值成功但 `environment` 为空，未转成 VM 参数；**真正的通路是 `--mod`** |
| `insertAfter` 定位创造栏 | 附魔书区块是标签最后一块，`accept` 追加即可 |
| 用 `RegistrySetBuilder` 写「引用了原版数据包注册表条目」的阶段条目 | 读的 provider 与写的 provider 不是同一份，序列化必然失败——见 P0-12 |

> ⚠️ `DatapackBuiltinEntriesProvider` 依然可以用，但**只适用于不引用外部数据包注册表 holder 的条目**。
> 本模组的阶段条目从原版附魔整段搬效果表，天然引用外部 holder，所以走 `UEStagePack`（P0-12）。

---

## 3. 已知的坑（按严重度）

每条都由实际源码行号支撑。**改相关代码前先读这一节。**

### P0 —— 会导致功能直接不可用或数据损坏

#### P0-1 · 原版铁砧会把超限等级砍回 `max_level`

```java
// AnvilMenu.createResult
if (j2 > enchantment.getMaxLevel()) j2 = enchantment.getMaxLevel();
```

**当时的问题**：物品上的 `sharpness` 可以超过原版 `max_level`（原版 5），两把带「受保护锋利」的剑在铁砧合并时会被原版一刀砍回 5。

**现状（v2.0 起已不是致命项，留档防止旧结论被重新推导）**：

- 效果强度由 `tierLevel` 结算（P1-20）——原版砍的是**存储等级**，砍了也不掉强度；
- 存储等级本来就不会超限：载体书（铭刻型）写入的就是该阶 `max_level`，而它等于原版上限；
- 两侧都有阶段记录的同名装备合并已由 `ItemMergeLogic` 接管，走不到原版那条路。

**仍然要守的规矩**：接管逻辑必须覆盖「右槽不是我们的书」的情形——右槽为原版附魔书 / 修复材料 / 空槽时都要有确定行为（见 P1-13）。

#### P0-2 · `Holder.Direct` 绝不能落回物品组件

注入用的 `Enchantment` 是运行时组装的，包在 `Holder.Direct` 里。它**只在查询期存在**。

一旦写回 `DataComponents.ENCHANTMENTS`，存档/网络同步会走 `ByteBufCodecs.holderRegistry(Registries.ENCHANTMENT)`，对 `Holder.Direct` **直接炸**（无注册表 ID）。

**铁律**：组件里的 `ENCHANTMENTS` 永远只存真附魔；组装出来的合成附魔只活在查询期。

#### P0-3 · 砂轮会把受保护附魔一起剥掉

```java
// GrindstoneMenu.computeResult —— 只填了槽 0 时
return EnchantmentHelper.hasAnyEnchantments(itemstack) ? this.removeNonCursesFrom(itemstack.copy()) : EMPTY;

// removeNonCursesFrom —— 剥除所有「非诅咒」附魔，不区分保护状态
EnchantmentHelper.updateEnchantments(stack, m -> m.removeIf(e -> !e.is(EnchantmentTags.CURSE)));
```

**处理**：只要槽 0 或槽 1 的物品带**受保护**附魔，就**无条件接管** `GrindstoneEvent.OnPlaceItem`，自行计算输出。必须覆盖三条原版分支：
① 单物品祛魔　② 双物品合并修复　③ 双物品合并同堆叠。

#### P0-4 · 铁砧与砂轮的结果计算在客户端也会跑

```java
// ItemCombinerMenu.slotsChanged —— 无 isClientSide 守卫
public void slotsChanged(Container c) { super.slotsChanged(c); if (c == this.inputSlots) this.createResult(); }
// GrindstoneMenu.slotsChanged —— 同样无守卫
```

两个菜单在客户端都存在实例，因此 `AnvilUpdateEvent` 与 `GrindstoneEvent.OnPlaceItem` **两侧都会触发**。

**处理**（三条同时满足）：

1. 处理器必须是**纯函数**：只读输入栈 + 事件 getter，只写事件 setter。**不得**访问世界、背包、随机数、静态可变状态。
2. 处理器读到的一切数据必须**已同步到客户端**（见 P0-7）。
3. **不要**用 `level.isClientSide()` 提前 return —— 客户端那份若不算，本地结果槽会先被算空再被服务端包覆盖，产生闪烁与竞态。

#### P0-5 · 铁砧成本默认 0，输出拿不走

```java
// CommonHooks.onAnvilChange
AnvilUpdateEvent e = new AnvilUpdateEvent(left, right, name, baseCost, player);  // baseCost == 0
container.setMaximumCost(e.getCost());          // ← 不 setCost 就是 0

// AnvilMenu.mayPickup
return (player.hasInfiniteMaterials() || player.experienceLevel >= this.cost.get()) && this.cost.get() > 0;
```

`cost == 0` → `mayPickup` 恒 false → **输出槽看得见、拿不走**，且不报任何错。

**处理**：只要 `setOutput`，就必须同时 `setCost(≥1)`。

#### P0-6 · `materialCost` 的 0 / 正数语义是反的

```java
container.repairItemCountCost = e.getMaterialCost();
// AnvilMenu.onTake
if (this.repairItemCountCost > 0) { itemStack.shrink(this.repairItemCountCost); }
else { this.inputSlots.setItem(1, ItemStack.EMPTY); }   // 整个堆叠消失
```

- `setMaterialCost(0)` → 消耗**整个**右槽堆叠
- `setMaterialCost(1)` → 只消耗 1 个

**处理**：书的 `max_stack_size` 若 > 1，必须显式 `setMaterialCost(1)`。即使是 1，也建议显式写出来。

> ⚠️ 反过来的用法同样成立：**同名装备合并**要的就是「整件吃掉右槽」，因此那里显式写
> `setMaterialCost(0)`（`AnvilEvents.trySameItemMerge`）。语义反直觉，两处都留了注释。

#### P0-7 · 自定义数据包注册表若不同步，客户端读不到

```java
public <T> void dataPackRegistry(ResourceKey<Registry<T>> key, Codec<T> codec)                          // 不同步
public <T> void dataPackRegistry(ResourceKey<Registry<T>> key, Codec<T> codec, @Nullable Codec<T> networkCodec)  // 同步
```

不同步时客户端**完全没有这个注册表**。结合 P0-4，客户端侧的事件处理器会 NPE 或算出错误结果。

**处理**：`ultraenchantment:enchantment` 必须用三参数版本并传非 null 的 `networkCodec`。

#### P0-8 · 数据包注册表的目录路径规则（`minecraft` 是特例）

路径公式（源码实证）：

```java
// Registries.elementsDirPath(registryKey)
//   → CommonHooks.prefixNamespace(registryKey.location())

public static String prefixNamespace(ResourceLocation registryKey) {
    return registryKey.getNamespace().equals("minecraft")
        ? registryKey.getPath()                                    // ← minecraft 是特例：丢弃命名空间
        : registryKey.getNamespace() + "/" + registryKey.getPath(); // ← 其它模组：命名空间 + 路径，必然 ≥ 两层
}
```

最终落点：

```
data / <条目命名空间> / prefixNamespace(注册表键) / <条目路径>.json
```

**原版对照**（`minecraft` 命名空间被丢掉，所以只有一层）：

| 注册表键 | `prefixNamespace` 结果 | 磁盘路径 |
|---|---|---|
| `minecraft:enchantment` | `enchantment` | `data/minecraft/enchantment/sharpness.json` |
| `minecraft:damage_type` | `damage_type` | `data/minecraft/damage_type/…` |
| `minecraft:worldgen/biome` | `worldgen/biome` | `data/minecraft/worldgen/biome/…` |

**本模组实况**——注册表键 `ultraenchantment:enchantment`，条目 `ultraenchantment:advanced/sharpness`：

```
data/ultraenchantment/ultraenchantment/enchantment/advanced/sharpness.json
                     └── 模组数据包前缀 ──┘ └ 与原版同名 ┘ └ 阶段 ┘
```

**注册表路径段取 `enchantment`（与原版同名）是刻意的**，目的是让中间层与原版视觉对齐。

> ⚠️ **非 minecraft 命名空间的注册表，中间层必然 ≥ 两层**——`ns + "/" + path` 铁定如此。
> 所以「精确复制原版那种单层形态」对模组注册表**在数学上不可能**；
> 能做的是让路径段与原版同名（当前方案），或改注册表路径为 `stage` 等更有语义的名字。
>
> **踩过的坑**：曾把该规则误述为「双命名空间」，并在文档里推广为通用规则。
> 实际它只是「非 minecraft 才会拼命名空间」的一个推论，原版并不符合。
> **教训：规则要从源码推，不能从「我生成的文件长这样」反推。**

#### P0-8b · `dataPackRegistry` 的第三参数是 `Codec`，不是 `StreamCodec`

```java
public <T> void dataPackRegistry(ResourceKey<Registry<T>> key, Codec<T> codec, @Nullable Codec<T> networkCodec)
```

传 `StreamCodec` 会以「找不到合适的方法」编译失败。传 `null` 则不同步，客户端读不到阶级数据（P0-7）。

#### P0-9 · `GetEnchantmentLevelEvent` 是热路径，必须早退

它在伤害结算、装备遍历、每次附魔等级查询时都会触发。**处理器第一行必须是组件判空 return。**

```java
AscensionData data = stack.get(UEComponents.ASCENSION);
if (data == null) return;        // ← 少了这行，全服每次伤害都白跑一遍逻辑
```

#### P0-10 · 合成修复抹掉附魔，而且**没有官方事件**

```java
// RepairItemRecipe.assemble
ItemStack itemstack2 = new ItemStack(itemstack.getItem());   // ← 全新物品
EnchantmentHelper.updateEnchantments(itemstack2, m ->
    lookup.listElements().filter(h -> h.is(EnchantmentTags.CURSE)).forEach(...));  // ← 只继承诅咒
```

玩家把两把带进阶附魔的剑丢进工作台修复 → **附魔直接蒸发**。

已核实：NeoForge 的事件表里**没有**任何合成结果修改事件（只有 `AnvilUpdateEvent` / `AnvilRepairEvent` / `GrindstoneEvent`）。

**处理**：**唯一合法的 Mixin**。
目标 `RepairItemRecipe.assemble`，`@Inject(method = "assemble", at = @At("RETURN"), cancellable = true)`，从两个输入里把**受保护**附魔（连同 ascension 记录）合并到结果上。

选择「合并保留」而不是「禁止修复」——玩家还能修装备，只是进阶附魔不会丢。

#### P0-11 · 砂轮经验按**输入槽**发放，不看输出 → 无限刷经验 ⚠️ 恶性

原版 `GrindstoneMenu.getExperienceFromItem` 完全不看输出结果：

```java
private int getExperienceFromItem(ItemStack stack) {
    ItemEnchantments ench = EnchantmentHelper.getEnchantmentsForCrafting(stack);  // ← 输入槽
    for (Entry<Holder<Enchantment>> entry : ench.entrySet()) {
        if (!holder.is(EnchantmentTags.CURSE)) total += holder.value().getMinCost(level);
    }
    return total;
}
```

而本模组的**保护性祛魔会把受保护附魔原样留在输出里**。于是：

```
放进去 → 取出拿全额经验 → 受保护附魔还在 → 放回去 → 再拿一次 → ∞
```

受保护附魔永不消失，经验无限。实测：`锋利5(受保护) + 耐久3` → 原版算 **xp = 33**，
而实际只该为被移除的耐久3 发 **xp = 3**。

**处理**：在 `OnPlaceItem` 里自己设 xp，按**实际被移除的附魔**重算。

事件 javadoc 给了明确的口子：

> Vanilla XP calculation logic will be used **unless** all of: xp ≥ 0、未取消、output 非空。

```java
event.setOutput(out);
if (!out.isEmpty()) {
    int total = ProtectionLogic.removedEnchantmentCost(result.removedRoots(), registry);
    event.setXp(ProtectionLogic.experienceFromCost(total));
}
```

配套改动：`protectiveScrub` / `applyCurative` 从「返回 ItemStack」改为返回
**`RemovalResult(stack, removedRoots)`** —— 必须把「移除了哪些附魔」这个信息
带出纯函数，否则事件层无从计算。

**⚠️ 不要用原版的随机公式。** 原版是 `half + random.nextInt(half)`，
但砂轮结果计算在**客户端也会跑**（P0-4），用随机会让两侧抽到不同值、
客户端预测与实际发放不符。改用确定性下界 `ceil(total / 2)`。

> **通用教训**：接管一个事件时，**必须连带接管它的所有副作用**。
> 我们接管了「输出什么」，却漏了「给多少经验」——而后者才是可被反复榨取的那一面。
> 凡是「产出与消耗不对称」的机制（输出留下、经验照发）都是刷取温床。

---

#### P0-12 · 引用了**外部 provider** holder 的数据包注册表条目，不能用 `DatapackBuiltinEntriesProvider` 写出

阶段条目的 `effects` 是「原版附魔效果表原样搬用 + 阶级加成」，于是它引用的 `Holder`
（如荆棘的 `minecraft:thorns` 伤害类型）来自**读它的那份 provider**。这一步有三条死路：

| 走法 | 结果 |
|---|---|
| bootstrap 里 `ctx.lookup(Registries.ENCHANTMENT)` | 拿到**没有绑定值的占位 holder**，`value()` 抛 `IllegalStateException`。机理：`RegistrySetBuilder.BuildState.create` 只把 `registryaccess.registries()`（内置注册表）与**本次 builder 声明过的注册表键**放进 lookup map，其余走 `getOrDefault` 回落 `UniversalLookup`，那里 `getOrCreate` 出来的是未绑定 holder |
| 先 `getLookupProvider().join()` 读出内容，再 `ctx.register` 进 builder | 序列化用的是 `DatapackBuiltinEntriesProvider` 交给 `RegistriesDatapackGenerator` 的 **patch provider**，其 holder owner 是它自己新建的 `UniversalOwner`；外部 holder 落不进去，`RegistryFileCodec#encode` 的 `holder.canSerializeIn(owner)` 为 false → `Element ... is not valid in current registry set` |
| 换成 `PatchedRegistries.full()` | 同样不行：`Cloner.clone` 是「**用源 provider 编码**、目标 provider 解码」（源码即 `directCodec.encodeStart(源ops, value)` → `parse(目标ops, ...)`），源 provider 仍是 patch provider，在克隆阶段以同样的理由失败 |

**处理**：写出这一步自己来，并且**用与读取时同一份 provider** 建 `RegistryOps`
（`provider.createSerializationContext(JsonOps.INSTANCE)`），owner 才天然一致。
落点是 `UEStagePack`（`DataProvider.saveStable` + 手工算路径，
公式仍照 P0-8 的 `elementsDirPath`）。

代价两条，都可接受：

- 失去 NeoForge `ICondition` 支持（本模组不需要条件化条目）
- 失去 builder 的重复注册 / 悬空引用检查 → 由 `UEStages.generate` 的自检补上
  （`root` 用 `getOrThrow` 保证真实存在，`next` 用生成集交叉校验）

> **判定信号：「读的 provider」与「写的 provider」必须是同一份。**
> 凡把数据从一份 `HolderLookup` 搬到另一份的场合，都要问「这个 holder 的 owner
> 在新 provider 里还算数吗」。`Holder.canSerializeIn` 就是那个判据——
> 它不会因为「key 看起来一样」而通过。
>
> 附带事实：`RegistriesDatapackGenerator` 本身就是按
> `DataPackRegistriesHooks.getDataPackRegistriesWithDimensions()` 遍历全部数据包注册表、
> 再按命名空间过滤的，所以自带写出器不会漏路径，也不会误写别的注册表。

---

#### P0-13 · 数据包重载事件里**不能**用 `CommonHooks.resolveLookup`——它静默返回 null

`TagsUpdatedEvent` 在 `ReloadableServerResources.updateRegistryTags` 里触发，
而那一刻 **`MinecraftServer` 实例还没被赋给 `ServerLifecycleHooks.currentServer`**。

```java
// CommonHooks.resolveLookup 的第一句
MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
if (server != null) { return server.registryAccess().lookup(key).orElse(null); }
else if (FMLEnvironment.dist.isClient()) { return ClientHooks.resolveLookup(key); }
return null;   // ← 专用服务器上就走这里
```

于是 `ReloadEvents.refresh()` 拿到的 lookup 恒为 `null`，按「注册表未就绪就保持旧值」
的约定**静默跳过刷新**，谱系矩阵永远是空的。后果（**专用服务器 runServer 实测**）：

| 现象 | 实测值（当时） |
|---|---|
| `matrix.size` | `0`（应为 30） |
| 创造栏搜索标签我们投出的条目 | `18`（只有 3 通用进阶 + 15 升级书；修好后 387） |
| 主标签 | `18`，其中定向进阶 / 铭刻 **一条都没有** |

而且**不报错、不警告**——看起来就像「这个功能还没做」，而不是「它坏了」。
更阴的是主标签样本：取样函数在 `hasTier` 全 false 后回退到遍历空列表，
返回 `null` 再 `continue`，整批样本消失。

**处理**：用事件自带的注册表——`TagsUpdatedEvent.getRegistryAccess()`。
它端上来的正是「刚绑定完标签的那份 `RegistryAccess`」，时间点天然对齐，
且 `SERVER_DATA_LOAD`（服务端）与 `CLIENT_PACKET_RECEIVED`（客户端）两条路径都能用。

> **判定信号：「注册表还没就绪」与「我取不到注册表」是两回事。**
> 前者该保持旧值，后者是 bug——但代码里它们长得一模一样，都是 `lookup == null`。
> **任何「取不到就静默跳过」的分支，都必须有一条途径能在正常流程里被验证到「取到了」**，
> 否则它既是兜底也是掩体。

> ⚠️ 本条**收窄了 P1-10 的适用范围**：那句「要用 `CommonHooks.resolveLookup`」
> 对运行期事件成立，对数据包重载事件不成立。

> **适用范围：本条只在专用服务器上发作。**
> 单人 / 集成服务器上 `ServerLifecycleHooks.getCurrentServer()` 早已有值
> （集成服务器先起、客户端后收标签包），旧写法照常工作——
> 所以这个 bug **在单人开发时完全看不出来**，只有 `runServer` 才暴露。
> 多人客户端更微妙：那时 `currentServer` 为 null，会落到
> `ClientHooks.resolveLookup`，而它依赖 `Minecraft.getInstance().level`——
> 标签包在配置阶段就到了，那时 level 可能还没有。用 `event.getRegistryAccess()`
> 三种环境一次都对。

---

### P1 —— 会导致行为不符合规格

#### P1-1 · 等级上限被 255 双重卡死

```java
ExtraCodecs.intRange(1, 255).fieldOf("max_level")                    // 数据包加载期
if (i < 0 || i > 255) throw new IllegalArgumentException(...);        // ItemEnchantments 构造期
```

`max_level` 写 256 会在数据包加载期**直接抛异常**；`Mutable.set` 会静默 `Math.min(level, 255)` 截断。设计数值时不要越过 255。

#### P1-2 · 等级 > 10 的罗马数字会显示成原始翻译键

```java
// Enchantment.getFullname
mutablecomponent.append(CommonComponents.SPACE).append(Component.translatable("enchantment.level." + level));
```

原版只提供 `enchantment.level.1` ~ `.10`。等级 11 起原样显示键名。
**处理**：若任一阶段上限 > 10，补全 `enchantment.level.<n>`。该键**全局生效**，会同时影响其它附魔（对原版无害）。

#### P1-3 · 子目录名会进入 ID，改目录名 = 破坏性变更

```
data/ultraenchantment/ultraenchantment/advanced/sharpness.json
   → 条目 id ultraenchantment:advanced/sharpness
```

之后想改 `advanced/` 这个名字，等于改掉全部条目 ID，**存档里所有已有的进阶附魔物品直接失效**。

**处理**：`advanced` / `super` / `ultra` 这三个目录名冻结。

#### P1-4 · 自定义书不能复用附魔组件

```java
// EnchantmentHelper.getComponentType
return stack.is(Items.ENCHANTED_BOOK) ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS;
```

原版铁砧判断「右槽是不是书」用的是 `itemstack2.has(DataComponents.STORED_ENCHANTMENTS)`。自定义书物品不是 `ENCHANTED_BOOK`，会落进 `ENCHANTMENTS`，语义错位，且原版书分支永远不会对它生效。

**处理**：自定义书必须携带**自己的强类型载荷组件**，并且**必须**接管 `AnvilUpdateEvent`。

#### P1-7 · 编译期签名速查（全是臆测就会踩的）

NeoForge 21.1.249 实测，**写之前先照抄**：

| API | 正确签名 | 错法后果 |
|---|---|---|
| `ByteBufCodecs.fromCodec` | 返回 `StreamCodec<ByteBuf, T>` | 塞进要 `RegistryFriendlyByteBuf` 的字段 → 类型不兼容 |
| `ByteBufCodecs.fromCodecWithRegistries` | 返回 `StreamCodec<RegistryFriendlyByteBuf, T>` | **组件与注册表字段一律用这个** |
| `DataPackRegistryEvent.dataPackRegistry` 第 3 参 | `Codec<T>` | 传 `StreamCodec` → 「找不到合适的方法」 |
| `BootstrapContext` | `net.minecraft.data.worldgen.BootstrapContext` | 不是 `RegistrySetBuilder.BootstrapContext` |
| `BootstrapContext.register` | `register(ResourceKey<T>, T)` | 传 `ResourceLocation` → 类型不兼容 |
| `RegistryCodecs` | `net.minecraft.core.RegistryCodecs` | 不是 `net.minecraft.core.registry.*` |
| `LevelBasedValue.Linear` | `Linear(float base, float perLevelAboveFirst)` | — |
| `ConditionalEffect` | `ConditionalEffect(T effect, Optional<LootItemCondition> requirements)` | — |
| `AddValue` | `AddValue(LevelBasedValue value)` | — |
| `DataComponentType.builder()` | `.persistent(Codec).networkSynchronized(StreamCodec).build()` | — |

#### P1-8 · PowerShell `Set-Content -Encoding UTF8` 会写 BOM

Java 编译器对 BOM 的反应是 `非法字符: '\ufeff'` + 后续整片「需要 class、interface、enum 或 record」——
错误信息完全指不到真正原因。

**规则**：改 Java 源文件一律用 .NET：
```powershell
[System.IO.File]::WriteAllText($p, $c, (New-Object System.Text.UTF8Encoding $false))
```
用 `write`/`edit` 工具则无此问题。

#### P1-9 · `GetEnchantmentLevelEvent` 的两种触发形态内容不同

`EventHooks` 的实现（源码实证）：

```java
// 全量枚举（效果结算走这条）
var mutableEnchantments = new ItemEnchantments.Mutable(enchantments);   // ← 含物品上全部附魔
var event = new GetEnchantmentLevelEvent(stack, mutableEnchantments, null, lookup);

// 单点查询
var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY); // ← 新建空表
enchantments.set(ench, level);                                            // ← 只塞了目标附魔
var event = new GetEnchantmentLevelEvent(stack, enchantments, ench, ...);
```

**不能假设单点查询时表里有别的附魔。** 本模组的处理：单点查询直接 return（保留原等级），
只有全量枚举才做屏蔽 + 注入。

**已验证**：事件里改的 `mutableEnchantments` 就是 `toImmutable()` 返回的那个，
即游戏实际结算所用的表——屏蔽与注入会真实生效（runServer 自检实测）。

#### P1-10 · 事件里拿不到自定义注册表，要用 `CommonHooks.resolveLookup`

`GetEnchantmentLevelEvent` 只提供 `RegistryLookup<Enchantment>`（仅能查附魔）。
阶段注册表必须另取，正确方式是 NeoForge 自己的取法：

```java
HolderLookup.RegistryLookup<StageDefinition> stages = CommonHooks.resolveLookup(UERegistries.STAGE);
```

它与 `EnchantmentHelper.runIterationOnItem` 用的是同一条路径（服务端优先、客户端兜底），
保证两侧行为一致。**不要**用 `ServerLifecycleHooks.getCurrentServer()` 手搓——客户端侧会拿到 null。

> ⚠️ **适用范围仅限「运行期事件」**（伤害结算、附魔查询、tooltip……那时服务器已经在跑）。
> 在**数据包重载事件**（`TagsUpdatedEvent`）里它恒为 `null`，会静默跳过——
> 见 **P0-13**。那边要用事件自带的 `getRegistryAccess()`。

#### P1-11 · 注入的 `Holder.Direct` 必须缓存为同一实例

注入用的 `Holder.Direct` 是 `ItemEnchantments.Mutable` 内部 `Object2IntOpenHashMap` 的 key，
依赖 `equals/hashCode`。每次结算都造新实例的话，一旦 `Enchantment` / `DataComponentMap`
的 `equals` 语义有偏差，表里就可能出现重复键或查不到键。

**处理**：`EnchantmentFactory` 用 `ConcurrentHashMap<ResourceLocation, Holder<Enchantment>>` 缓存，
同一阶段永远返回同一对象（按引用即可命中）。缓存对象是我们自己 new 的，不依赖注册表生命周期，
但阶段定义会随 `/reload` 变，故在 `TagsUpdatedEvent`（game bus）里 `invalidate()` 清空。

#### P1-12 · 三个"阶级"类型必须分清（曾三次混淆）

| 类型 | 语义 | 含原生阶？ | 用在哪 |
|---|---|---|---|
| `UETier` | **物品形态**阶级 | 否 | 书/祛咒石的外观（`custom_model_data` 1/2/3） |
| `AscensionTier` | **进阶档位** | 否 | 升级书的 `tier`、祛咒石的 `curative_tier` |
| `LineageTier` | **谱系当前状态** | **是** | `ascension` 组件反查；**进阶书的 `from_tier`/`to_tier`** |

**关键区分**：

- **进阶书**的 `from_tier` 用 `LineageTier`——它可以填 `NATIVE`，表示「这本书用于尚未进阶的原始附魔」（即「基础→高阶」品质）。
- **升级书**的 `tier` 用 `AscensionTier`——只作用于**已进阶**附魔，原生阶没有阶级可升，因此该字段出现 `native` 是无意义的。

**踩坑记录**：曾把书的 `from_tier` 一并改成 `AscensionTier`（过度修正），
导致「基础→高阶」书无法表达；又曾把书字段语义擅自解释成「源档位的前一档」，
造成判定行为不符预期。**类型选错会污染数据契约，改动前先确认语义。**

#### P1-13 · 铁砧接管必须覆盖「右槽不是我们的书」的情形

原版 `AnvilMenu.createResult` 有这行：

```java
if (j2 > enchantment.getMaxLevel()) j2 = enchantment.getMaxLevel();
```

带阶段数据的物品若**没放书**（右槽为空 / 是普通材料），原版会把超限等级砍回 `max_level`
——究极附魔直接废掉（P0-1）。

**处理**：`AnvilEvents` 判定「左槽有阶段数据」就介入，无论右槽是什么。
当前实现只在「右槽是可用修复材料」时给出结果（修耐久 + 等级原样保留），
其余情况交回原版——**不在此重写原版的合并数学**，那会引入大量难以验证的分支。

> **v2 起有一处刻意的例外**：「同名装备合并」（`ItemMergeLogic`）**必须**接管并自己算——
> 原版只处理存储等级，而升阶后存储等级恒为上限，原版算出的 `5+1` 会被 `getMaxLevel()` 夹回 5，
> 玩家付全额费用却只换到合并耐久，`tierLevel` 一动不动。
> 接管范围收得很窄（**同一物品 + 可损坏 + 两侧都有阶段记录**），耐久公式与附魔合并循环
> 逐行照抄 1.21.1 源码，末尾追加阶段记录合并。
> **连带义务**：接管后名称框（重命名）必须一并照抄（原版 230-240 行），
> 否则「边合并边改名」比原版少一个效果——那是**回归**，不是取舍。

#### P0-14 · 铁砧只有一个输出槽，第二输出只能靠 `AnvilRepairEvent` ⚠️

载体书是**多条目**的：贴装备时可能「一部分吃下去、一部分被拒绝」，被拒绝的条目要留成
「剩菜书」还给玩家。但 `AnvilMenu` 只有一个输出槽，`repairItemCountCost` 只能「少扣」不能「返还」（P0-6）。

```java
// AnvilMenu.onTake
float breakChance = CommonHooks.onAnvilRepair(player, output, inputSlots.getItem(0), inputSlots.getItem(1));  // 82 行
this.inputSlots.setItem(0, ItemStack.EMPTY);   // 84 行 —— 事件之后才清空
```

**处理**：在 `AnvilRepairEvent`（**服务端**触发，槽位清空之前）里，用同一份输入**重跑一次纯解析**，
拿到剩菜条目 → 重建一本载体书 → 进玩家背包（满则 `player.drop`）。三条铁律：

1. **不要改 output**——事件 javadoc 明确 inputs/output 不可编辑，我们只读；
2. **不要试图把剩菜写回右槽**——事件在清空之前触发，写进去会被随后的清空覆盖；
   靠 `server.execute` 延后又依赖菜单与槽位在那时仍然存活，已否决；
3. **解析必须是纯函数**（P0-4 的另一面）——「计算输出时」与「取件重算时」结果必然一致，
   剩菜的判定才不会漂。副作用全部收在 `AnvilTakeEvents` 一个类里。

#### P1-14 · Mixin 在 NeoForge 21.x 的配置方式（三处缺一不可）

NeoForge 不用 `@MixinConfig` 注解，也不用 `-Dmixin.config`。配置链路（源码实证）：

```
neoforge.mods.toml 的 [[mixins]] config="<文件名>"
   → ModFileParser.getMixinConfigs() 解析
   → DeferredMixinConfigRegistration.addMixinConfig()
   → Mixins.addConfiguration()
```

两处缺一不可：

1. **`src/main/resources/<modid>.mixins.json`** —— 配置文件本体
   （`package` / `compatibilityLevel` / `mixins` 列表 / `injectors.defaultRequire`）
2. **`neoforge.mods.toml` 里加**：
   ```toml
   [[mixins]]
   config="ultraenchantment.mixins.json"
   ```
   `config` 字段**必需**，缺失会以
   `InvalidModFileException: Missing "config" in [[mixins]] entry` 拒载。

**不需要**手动加 `mixin` 依赖：`sponge-mixin` 由 moddev 自动放进编译与运行时 classpath。
**不需要** refmap：NeoForge 21.x 用 mojmap，Mixin 直接按官方名映射。

> 排查技巧：Mixin 生效时日志会出现 `Compatibility level set to JAVA_21`；
> 注入点不匹配会打印 `Mixin apply failed`。
> **「编译通过」完全不能证明 Mixin 生效**——配置路径写错时它静默不加载。

#### P1-15 · 合成修复会抹掉所有非诅咒附魔（唯一的 Mixin 落点）

```java
// RepairItemRecipe.assemble
ItemStack itemstack2 = new ItemStack(itemstack.getItem());   // ← 全新栈
EnchantmentHelper.updateEnchantments(itemstack2, m ->
    lookup.listElements().filter(h -> h.is(EnchantmentTags.CURSE)).forEach(...));  // ← 只留诅咒
```

玩家把两把带进阶附魔的剑丢进工作台修复 → **附魔蒸发**。
NeoForge 在合成结果上**没有任何事件**，这是全项目唯一必须用 Mixin 的地方。

**处理**：`@Inject(method = "assemble", at = @At("RETURN"))`，
从输入里把**受保护**的附魔（连同 `ascension` 记录）补回结果。
选「合并保留」而非「禁止修复」——装备能修，进阶附魔也不丢。

注入面：单方法、单注入点、不改流程。

#### P1-16 · 同一实例不能注册到两条总线（NeoForge 会硬失败）

`BuildCreativeModeTabContentsEvent` 是 **mod bus** 事件，`ItemTooltipEvent` 是 **game bus** 事件。
把含这两类方法的**同一个实例**同时 `register` 到两条总线，会在加载期直接崩溃：

```
IllegalArgumentException: IModBusEvent events are not allowed on the common NeoForge bus!
Use a mod bus instead.
net.neoforged.fml.ModLoadingException: Loading errors encountered
```

**这其实是好设计**——挂错总线不是静默失效，而是响亮的失败。
**处理**：一个监听器类只管一条总线；需要两条时拆成两个类。

#### P1-17 · 服务端专用服务器没有客户端语言数据

在 `runServer` 里调 `stack.getTooltipLines(...)`，`Component.getString()` 返回的是
**翻译键本身或内置英文名**，而不是玩家看到的本地化文本。

**后果**：写自检判据时若按中文/本地化文本匹配，会得到假阴性。
用**翻译键**或**内置英文名**（如 `Sharpness`）匹配才可靠。

> 这也是我误判「tooltip 没生效」的原因——判据用了 `contains("锋利")`，
> 而服务端产出的是 `Super Sharpness V`。**判据写错比功能写错更隐蔽。**

#### P1-18 · 定位 tooltip 行要用「同一份 Component」，不要硬编码文本

改写附魔行时，需要先找到原版渲染出的那一行。正确做法是用**完全相同的构造方式**再生成一份：

```java
Component vanillaLine = Enchantment.getFullname(root, level);   // 与原版 tooltip 同源
lines.remove(indexOf(lines, vanillaLine.getString()));
```

这样 `getString()` 在客户端与服务端都一致，不依赖具体语言。
**不要**硬编码 `"Sharpness V"` 之类的中/英文文本——换个语言就失效。

#### P1-19 · 同 locale 不能开两个 `LanguageProvider`

```java
event.addProvider(new UELang(output, "en_us", false));
event.addProvider(new UEStageNames(output, "en_us", false));   // ← 崩
```

```
java.lang.IllegalStateException: Duplicate provider: Languages: en_us for mod: ultraenchantment
    at net.minecraft.data.DataGenerator.addProvider(DataGenerator.java:92)
```

**原因**：`DataGenerator` 按 **provider 名** 去重，而 `LanguageProvider` 的名字就是
`Languages: <locale> for mod: <modid>`——与提供器的*类*无关。两个不同类只要
locale 与 modid 相同，名字就一样，直接抛异常。

**处理**：把阶梯名并进 `UELang`，做成一个私有方法由同一个提供器输出。
`zh_cn` / `en_us` 各一个提供器，各自内含全部键。

> 反直觉之处：直觉上「一个提供器管静态键、一个管动态键」更整洁，
> 但 `DataGenerator` 的命名机制不允许。**同名即冲突，这是硬约束不是警告。**

#### P1-20 · 升级曲线等级（tierLevel）必须与存储等级分开——<b>而且它才是效果等级</b>

> ⚠️ **本条曾在「谁是效果等级」上写反过，已更正。** 原文说「存储等级管效果强度，
> tierLevel 管对外呈现」，那是错的：照那样写，「高阶锋利 1 级」会按存储的 5 级结算，
> 显示与实强完全脱节，而且升级书（把 tierLevel 从 1 提到 N）**不会带来任何强度变化**——
> 变成纯装饰。详见下面的「更正」。

规格：`锋利 5` 升阶后显示为 `超级锋利 1`，再用 3 级升级书可提到 3 级。

若拿**存储等级**做升级书判定：

```
存储 5，3 级升级书 → 5 >= 3 → 判定「已达目标」→ 升级永远无效
```

**根因**：升阶时存储等级**不变**（它属于原版 `minecraft:enchantments` 组件，
改了会波及附魔台、村民交易等一切原版机制），于是「归 1 后的进度」无处安放。

**处理**：`AscensionData` 为每条谱系额外记 `tierLevel`——
**进阶曲线上的等级**，与存储等级完全解耦：

| 事件 | 存储等级 | tierLevel | 显示 |
|---|---|---|---|
| 锋利 5 升阶 | 5（不变） | 1 | 超级锋利 1 |
| 用 3 级升级书 | 5（不变） | 3 | 超级锋利 3 |
| 再升一阶 | 5（不变） | 1（重置） | 究极锋利 1 |

**要点**：

- **`tierLevel` 就是「这个进阶形态自己的附魔等级」，效果强度按它结算。**
  `EnchantmentLevelEvents` 注入阶段条目时用的就是它，不是存储等级
  （存储等级为 0 的边界情况才回落到存储等级兜底）。
  数据包里每一阶的效果曲线都是按这条曲线写的，用别的等级结算等于让数据白写。
- **存储等级只留给原版机制读**（铁砧合并、附魔台、村民交易）——它升阶后不变。
  这也是为什么不能改它：改了就波及一切原版机制。
- 升级书的判定基准是 `tierLevel`，不是存储等级；目标值还要夹在该谱系该阶级的
  `max_level` 里（升级书不区分谱系，创造栏按全局最大值铺到 5 级，
  不夹的话「5 级升级书」会把上限 3 的耐久顶到 5 级，让曲线被外推到没定义的位置）。
- 任何搬运 `AscensionData` 的地方（如合成修复 Mixin 的合并）都必须**连 `tierLevel` 一起搬**，
  否则 `with(root, stage)` 会把它重置为 1，玩家用合成修复白丢等级

> **判定信号：一个「等级」字段有三种可能身份——存储/呈现/结算。**
> 只要三者不是同一个数，就必须在代码里写明「谁结算」，并在文档里写死；
> 含糊过去的结果是一个能编译、能跑、看起来对，但**玩家感觉不到**的系统。
> 这次的症状是「升级书没用」——而它本该是最容易被发现的那种问题。

#### P1-21 · 创造栏是 mod bus 事件，**读不到数据包注册表**

`BuildCreativeModeTabContentsEvent` 在物品注册期触发（mod bus），而阶段条目住在
**数据包注册表** `ultraenchantment:enchantment` 里。二者的生命周期不同步——
创造栏构建时数据包未必就绪，现场 `StageLookup.lookup()` 可能返回 `null` 或空表。

**处理**：把「数据包派生、创造栏要用」的值在数据包加载后算一次、缓存起来。

```java
// ReloadEvents：TagsUpdatedEvent 里刷新
private static volatile int globalMaxLevel = DEFAULT_MAX_LEVEL;

@SubscribeEvent
public void onTagsUpdated(TagsUpdatedEvent event) {
    EnchantmentFactory.invalidate();
    refreshGlobalMaxLevel();      // 扫全部阶段条目取 max_level 最大值
}
```

创造栏读 `ReloadEvents.globalMaxLevel()`，不查表。兜底值保证数据包从未加载时
仍能显示条目，而不是一片空白。

> 判定信号：**事件在 mod bus 还是 game bus，决定了它能碰什么**。
> mod bus = 注册期，只有注册表；game bus = 运行期，注册表与数据包都有。

#### P1-22 · `AnvilUpdateEvent.getPlayer()` 是现成的（21.1）

1.21.1 的 `AnvilUpdateEvent` 直接持有 `Player`，无 `@Nullable`：

```java
private final Player player;
public Player getPlayer() { return this.player; }
```

因此「创造模式绕过门槛」不需要 Mixin 去捞 `AnvilMenu` 的 owner，直接用
`event.getPlayer().isCreative()` 即可。

**但注意**：`AscensionLogic` 是两侧复用的纯函数，**不要**让它接收 `Player`。
正确做法是在 `AnvilEvents`（事件层）判定后，把 `boolean bypassGate` 作为参数传进去。

#### P1-23 · `BuildCreativeModeTabContentsEvent.accept()` 有重复校验

```java
private void assertNewEntryDoesNotAlreadyExists(Set<ItemStack> set, ItemStack newEntry) {
    if (set.contains(newEntry)) throw new IllegalArgumentException(
        "Itemstack " + newEntry + " already exists in the tab's list");
}
```

`ItemStack.equals` 比较**组件**。因此批量展开（如升级书 3 档 × 5 级 = 15 条）时，
必须保证每条条目的组件组合唯一——否则直接抛异常。

本例中升级书带 `upgrade_spec{tier,target_level}` + `custom_model_data`，
非同一条目组件必然不同，安全。**但若两个变种只在材质上不同、组件相同，就会抛。**

> 展开前先自检去重（用 `HashSet<ItemStack>` 跑一遍），比等到崩了再查便宜。

#### P1-24 · 附魔字段的颜色规则来自 `Enchantment.getFullname`

源码实证（`Enchantment.getFullname`）：

```java
if (holder.is(EnchantmentTags.CURSE)) mergeStyles(desc, Style.EMPTY.withColor(RED));
else                                 mergeStyles(desc, Style.EMPTY.withColor(GRAY));
```

**普通附魔 = `GRAY`**。所以「模组 tooltip 用附魔字段同款颜色」就是 `ChatFormatting.GRAY`。

注意本模组的阶段附魔是 `Holder.Direct` 且无注册表标签，`is(TagKey)` 恒为 false（P1-5），
因此**必然是 GRAY**，不会是 RED。

#### P1-25 · 别从「原版附魔 id」反推阶级——路径里根本没有阶级信息

铭刻书的载荷最初只记 `enchantment` + `level`，阶级靠工具方法从 id 反推：

```java
// 想从 "advanced/sharpness" 里切出 "advanced"
int slash = id.getPath().indexOf('/');
```

但铭刻书铭刻的是**原版附魔 id**（`minecraft:sharpness`），**没有斜杠**，
于是全部走兜底分支 → 三档铭刻书在 tooltip 里全显示成「高阶」。

**根因**：阶级是**书自身的属性**，与它铭刻哪个附魔无关，必须显式声明。
把 `tier` 加进 `BookSpecs.Inscription` 后，tooltip 与铁砧校验都直接读它。

> 判定信号：**当一个值无法从上下文唯一确定时，就必须存下来。**
> 反推只在「信息确实蕴含在 id 里」时成立——`ultraenchantment:super/sharpness`
> 这种模组自己的阶段 id 可以反推（路径首段就是阶级），原版 id 不行。

#### P1-26 · 原版附魔书在创造栏用**两种 visibility**，不是 `PARENT_AND_SEARCH_TABS`

源码实证（`CreativeModeTabs`）：

```java
generateEnchantmentBookTypesOnlyMaxLevel(tab, lookup, PARENT_TAB_ONLY);  // 主标签只放满级
generateEnchantmentBookTypesAllLevels(tab,    lookup, SEARCH_TAB_ONLY);  // 搜索标签放每一级
```

**两个函数、两种 visibility。** 若图省事用 `PARENT_AND_SEARCH_TABS` 同时投全等级，
主标签会被几十上百条条目塞爆。

照搬这个分层的做法：

```java
// 主标签：该格的满级（每谱系 × 每阶级各一本）
acceptParent(event, book(tier, maxLevel));
// 搜索标签：完整规格枚举
for (int lv = 1; lv <= maxLevel; lv++) acceptSearch(event, book(tier, lv));
```

#### P1-27 · tooltip 的「省略数字」规则 —— 前提变过两次，以 P1-32 为准

载荷 `level` 的语义变过（v1.7 起 = 曲线等级 `tierLevel`，可以是 1），
所以这条规则曾被删掉、又在 v2.0 按原版恢复。**当前实现见 P1-32**，不要照本条旧结论改代码。

#### P1-30 · 定向进阶书少显示一阶——**错的是规格，不是实现**

进化型书描述的是谱系链上的**一条边** `from_tier → to_tier`。通用进阶书一直是对的
（h3/h5 都用 `scope.*` 阶级范围）。定向进阶书却把 h3 一律写成**原版附魔名**
（`enchantName(applicable)`），于是三档显示成：

| 品质 | 实际（错） | 应为 |
|---|---|---|
| 基础→高阶 | 锋利 → 高阶锋利 | 锋利 → 高阶锋利 ✅（原生阶恰好就是原版名） |
| 高阶→超级 | **锋利** → 超级锋利 ❌ | **高阶锋利** → 超级锋利 |
| 超级→究极 | **锋利** → 究极锋利 ❌ | **超级锋利** → 究极锋利 |

后两档**中间整整少了一阶**，读起来像是把原生锋利直接推到究极。

**根因是规格写错了**：`README.md` 的「进化型 · 定向进阶」一节原本写「h3: 具体附魔名」——
「具体」被读成「原版附魔」，而它其实应该是 **`from_tier` 那一阶的具体名称**。
实现一字不差地照做了错误规格，所以两边一起错。

**处理**（规格与实现都改）：

- `BookTooltipEvents.targetedTooltip`：h3 改用 `from_tier` 的阶段名；
  `from_tier == NATIVE` 时退回原版附魔名（原生阶没有阶段条目）。
- `README.md` 该节重写，并补了三档示例表。
- 顺带把「父子」这条**口头规格**落进代码（`AscensionLogic.findTargetStageId`）：
  已有记录时目标只能取当前阶段的 `next()` 且其阶级必须等于书本的 `to_tier`；
  原生阶时目标必须是谱系链的**表头**（没有任何阶段的 `next` 指向它）。
  于是「原生→究极」这类跳阶书被拒（对应 §2 被否方案 #8）。

> **判定信号：要显示「上一阶」时，先看数据模型里那一阶有没有自己的名字。**
> `from_tier` 本来就是载荷里的一等字段，实现却绕开它去取附魔名——
> 「能反推出一个值」不等于「该值语义正确」（同一个原版附魔名，三档各不相同）。
>
> **更值得记的是：错的是规格，不是实现。** 「代码符合文档」从来不是正确性的证据——
> 两边要**各自独立地对得上数据模型**。这次如果只看「实现是否照 README 做了」，
> 会得出「没问题」的结论。

---

#### P1-29 · 主标签样本的「覆盖」要按**谱系总数**算，不能按阶级数算

创造栏主标签只放样本（搜索标签放全规格），样本的代表性由取样算法决定。
这里前后踩了两个**方向相反**的坑：

| 版本 | 取样规则 | 结果 |
|---|---|---|
| 最初 | 三个阶级都取锋利 | 主标签全是「锋利系列」，玩家以为模组只支持锋利 |
| 第二版 | 每个阶级换一条（高阶=锋利、超级=保护、究极=耐久） | 主标签只出现 **3 条**谱系——30 条里 27 条根本不存在 |
| v1.3~v2.0 | 每条谱系只露面一次，阶级沿谱系顺序**轮转** | 30/30 覆盖，但排布是「高阶X / 超级Y / 究极Z」——**同一附魔的三个阶级被拆散**，实测被当成「阶级串了行」报上来 |
| 现在（v2.1） | **谱系 × 阶级各一本**：谱系外层（字典序）、阶级内层（枚举序） | 90 条，同一附魔的三个阶级相邻且递增（高阶X / 超级X / 究极X → 高阶Y …） |

第二版看起来比第一版好，其实只修了「阶级维度」，**没有修「谱系维度」**——
「每个阶级都有样本」与「谱系被覆盖到了」是两件事。
而且第二版的问题在**只有 1 条谱系的数据包里也测不出来**，铺到 30 条才暴露。

**判定方法**：数「主标签里一共出现了几条不同的谱系」，与数据包谱系总数比。
不要数「每个阶级有没有样本」。

**v2.1 追加的教训：覆盖够了不等于读得懂。** 轮转虽然把 30 条谱系都露了面，
代价却是**把同一条谱系的三个阶级拆散**——玩家看到的是一串「高阶X / 超级Y / 究极Z」，
第一反应是「数据乱了」而不是「样本在轮转」。修法是让顺序本身成为契约：
**谱系外层、阶级内层**（`CreativeTabEvents.mainTabInscriptions()`，已抽成可自检的纯函数，
因为这种顺序没有任何界面能自动断言）。覆盖不但没退化，还从「每条谱系 1 次」变成「3 次」。

---

#### P1-28 · 阶段条目的等级上限是**逐谱系**的，不能扫全阶级取最大值

规格定为「等级上限沿用根源附魔自己的 `max_level`」——耐久 3、保护 4、锋利 5，
同一条谱系的三阶共用它。于是**同一阶级的不同谱系，上限并不相同**。

早期 `StageLookup` 提供的是 `maxLevelOfTier(lookup, tier, fallback)`（扫全阶级取最大）。
在「所有阶级上限都写 5」的年代看不出问题；上限改成逐谱系之后，
铭刻书会给一件保护（上限 4）的物品写入 **5 级**——越过它自己的上限，
同一条附魔在不同来源下能到的等级不一致。

**处理**：换成按谱系查的 `StageLookup.maxLevelOf(lookup, tier, root, fallback)`，
比较 `StageDefinition.root()`。调用点：升级书、载体书贴装备（`InscriptionLogic`）、
同名装备合并（`ItemMergeLogic`）、tooltip 与创造栏展开。

> 判定信号：**当「同一个 key 在不同条目上可以有不同值时，按 key 取聚合值就是错的」。**
> 「该阶级的等级上限」听起来像一个阶级级属性，实际是谱系级属性——名字骗了人。

#### P1-33 · 创造栏内容必须从数据包**动态枚举**，不能硬编码

最初写的是：

```java
private static final ResourceLocation SHARPNESS = ResourceLocation.withDefaultNamespace("sharpness");
acceptParent(event, inscriptionBook(tier, SHARPNESS, maxLevel));   // ← 只产出锋利
```

数据包里铺了 30 条谱系，创造栏却只生成锋利那一本铭刻书。
玩家看到「只有锋利」，而铁砧那边其实认全部谱系（它现场查注册表）——
于是出现**「能用但拿不到」的割裂**：整合包作者加了谱系，游戏里却找不到对应的书。

**处理**：在数据包加载后扫一遍阶段注册表，缓存成矩阵（`ReloadEvents.matrix()`），
创造栏只读缓存：

```java
for (ResourceLocation root : ReloadEvents.roots()) {
    for (AscensionTier tier : AscensionTier.values()) {
        if (!ReloadEvents.hasTier(root, tier)) continue;          // 数据包没铺就不产出
        int max = ReloadEvents.maxLevelOf(root, tier, fallback);  // 逐谱系上限
        ...
    }
}
```

> **原则：数据包是事实源，创造栏只是它的视图。**
> 凡是「数据包定义的内容」，UI 侧一律枚举而非硬编码。
> 硬编码的失败方式是**静默的**——不报错，只是少显示。

**⚠️ 缓存粒度要对齐消费方**：最初缓存的是「谱系 → 有哪些阶级」，
但创造栏展开铭刻书需要的是「谱系 × 阶级 → 该格上限」（因为上限逐谱系，见 P1-28）。
用 `globalMaxLevel` 展开会给保护书（上限 4）产出 5 级的条目——永远拿不到。
改成 `Map<root, Map<tier, maxLevel>>` 后才对齐。

#### P1-34 · 主标签收「每格的满级」，搜索标签收全规格

不分层的话，「谱系 × 阶级 × 等级」矩阵会把主标签塞爆。照原版附魔书的分层（P1-26）：
**只有「等级」这一维算隐藏维度** → 主标签只收各格的满级，搜索标签收全部等级。

| 科目 | 主标签 | 搜索标签 |
|---|---|---|
| 进化书 · 通用 | 3 品质 | 3 品质 |
| 进化书 · 定向 | 90（谱系 × 阶级，全量） | 90 |
| 铭刻书 | 90（谱系 × 阶级，各格满级） | 273 + 3 条多条目样本 |
| 升级书 | 15（全等级） | 15 |
| 祛咒石 | 3 档 | 3 档 |
| **合计** | **201** | **387** |

> 分层不是「给主标签取样」，而是「隐藏维度只上搜索标签」——主标签里
> **每条谱系、每个阶级都在**，顺序也不串（见 P1-29 的 v2.1 补记）。

#### P1-31 · 取样必须体现维度差异（已被 v2.1 取代）

主标签曾经靠「取样」压缩条目，前后试过三种规则：三档全用锋利（看起来像只支持锋利）、
每档换一条谱系（覆盖不全）、阶级轮转（把同一附魔的三个阶级拆散，见 P1-29 的补记）。

**v2.1 起不再取样**：每条谱系 × 每个阶级各一本，两个维度天然完整。
留档只为一句——**任何取样都要能让人看出取值空间的形状**。

#### P1-32 · 等级省略规则的前提：`level != 1 || getMaxLevel() != 1`

原版 `Enchantment.getFullname` 的精确条件（源码实证）：

```java
if (level != 1 || enchantment.getMaxLevel() != 1) { 显示罗马数字 }
```

**两个条件必须同时为假才省略**，即 `level == 1 && cap == 1`。
所以：`level=1, cap=1` → 省略；`level=1, cap=3` → **显示「I」**。

本项目曾以「铭刻书恒显示等级」为由删掉这条规则（P1-27），现按原版恢复。
**判断依据改用该阶级阶段条目的 `max_level`**（逐谱系，见 P1-28），
而不是原版附魔自身的 `getMaxLevel()`——与铁砧写入时的**夹取上限**同源，才算自洽。
（显示的数字是载荷里的 `level`，即曲线等级 `tierLevel`；上限只用来决定「省不省这个数字」。）

实测数据包中有 **15/90 个格子 `cap == 1`**（如无限、经验修补），
这些格子的 1 级铭刻书会正确省略数字。

---

#### P1-5 · `Holder.Direct` 的 `is(TagKey)` 恒为 `false`

```java
// Holder.Direct
public boolean is(TagKey<T> t) { return false; }   // 不抛异常，但永远 false
public Optional<ResourceKey<T>> unwrapKey() { return Optional.empty(); }
```

- 好处：不抛异常，能安全穿过效果管线
- 代价：`Enchantment.getFullname` 不会把合成附魔染成诅咒色；`getRegisteredName()` 返回 `[unregistered]`

**处理**：接受。但别把 `Holder.Direct` 喂给任何需要注册表 ID 的 codec（见 P0-2）。

#### P1-6 · 别把 `Holder<Enchantment>` / 阶段定义缓存进静态字段

数据包注册表在 `/reload` 时会重建，旧 `Holder` 全部失效。

**处理**：所有查找走 `RegistryAccess` / `HolderLookup.Provider` 现场解析。合成 `Enchantment` 对象可以缓存（它是我们自己 new 的，不依赖注册表生命周期），但缓存要挂**数据包重载事件**上清空。

---

### P2 —— 会浪费大量时间的坑

#### P2-1 · 首次构建的 NeoForm 反编译可达一小时

`neoforge:createMinecraftArtifacts` 要反编译整个 Minecraft。日志长时间无输出**不代表卡死**。给 `mc_gradle` 设足够超时（建议 ≥ 3600000 ms）。

#### P2-2 · `processResources` 的 Groovy `expand` 会吃掉资源里的 `${...}`

只对 `neoforge.mods.toml` 做 `expand`。**其它资源文件（含 JSON 注释、语言文件）里出现「美元号 + 花括号」片段会让构建以模板解析失败告终。**

#### P2-3 · 1.21.1 的 `neoforge.mods.toml` 必须显式声明 javafml

```toml
modLoader="javafml"
loaderVersion="[1,)"
```

缺失 → 客户端拒载，报 `Missing ModLoader`。javafml 默认化从 21.11 才开始，**不要**把 1.21.11 的 toml 形态套到 1.21.1。

#### P2-4 · modid 三处必须一致

`gradle.properties` 的 `mod_id`、`@Mod(...)` 注解、`neoforge.mods.toml` 的 `modId`。

#### P2-5 · `DataPackRegistryEvent.NewRegistry` 是 **mod bus** 事件

它实现 `IModBusEvent`。写到 `NeoForge.EVENT_BUS`（game bus）上**永远不会触发，且不报错**。

#### P2-6 · `BuildCreativeModeTabContentsEvent` 也是 **mod bus**，且有两个插入陷阱

1. 它实现 `IModBusEvent`（同 P2-5）。
2. `insertAfter(anchor, new, vis)` 是**紧挨着锚点插入**，不是追加列表。连续 `insertAfter(anchor, A)` 再 `insertAfter(anchor, B)`，结果是 `anchor, B, A` —— **顺序会倒**。必须链式传递锚点。
3. `insertAfter` 的锚点**不存在会抛异常**。

**本项目的实际做法**：附魔书区块是原材料标签的**最后一块**，所以直接 `event.accept(...)` 追加到末尾即可，落点天然正确，不需要任何锚点。

#### P2-7 · `runServer` 首次失败先看 eula

`run/eula.txt` 要改成 `eula=true`；局域网调试可把 `run/server.properties` 的 `online-mode` 设 false。

---

## 4. 权威事实来源速查

| 需要什么 | 去哪找 |
|---|---|
| Minecraft / NeoForge 真实类签名 | `build/moddev/artifacts/neoforge-21.1.249-sources.jar` |
| Minecraft 原版数据文件（附魔 JSON、标签、语言、配方） | `build/moddev/artifacts/neoforge-21.1.249-client-extra-aka-minecraft-resources.jar` |
| NeoForge 事件总览 | 上者内 `net/neoforged/neoforge/event/**` |
| 事件触发的真实语义（可取消性、返回值含义） | `net/neoforged/neoforge/common/CommonHooks.java`、`net/neoforged/neoforge/event/EventHooks.java` |
| moddev / 构建 / JDK 配对 | skill `minecraft-java-build` |
| NeoForge 工程形态与坑 | skill `minecraft-neoforge-mod` |
| Mixin（仅备用，本项目只用于 `RepairItemRecipe`） | skill `minecraft-fabric-mod` → `references/api/mixins.md` |

抽取脚本：

```powershell
$jar = 'build/moddev/artifacts/neoforge-21.1.249-sources.jar'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [System.IO.Compression.ZipFile]::OpenRead($jar)
$z.Entries | ForEach-Object { $_.FullName } | Where-Object { $_ -match 'world/inventory/.*\.java$' }
$e = $z.Entries | Where-Object { $_.FullName -eq 'net/minecraft/world/inventory/GrindstoneMenu.java' }
[System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, "$env:TEMP\GrindstoneMenu.java", $true)
$z.Dispose()
```

---

## 5. 代码组织约定

```
com.lyingice.ultraenchantment
├── Ultraenchantment.java           @Mod 入口：mod bus（注册/注册表/datagen/创造栏）+ game bus（结算/重载/铁砧/砂轮/tooltip）
├── content/
│   ├── UETier.java                 物品形态阶级（1/2/3，custom_model_data 驱动）
│   ├── LineageTier.java            谱系状态阶级（NATIVE/ADVANCED/SUPER/ULTRA）★ 与 UETier 不同
│   ├── AscensionTier.java          书的档位（必非原生阶）
│   ├── TieredItem.java             物品基类（物品名恒定，不分级）
│   ├── StageDefinition.java        阶段条目 record + codec（注册表元素）
│   ├── AscensionData.java          阶段标识载荷（Map<根源, {阶段 id, tierLevel}>）
│   ├── BookSubject.java            书的科目（ascension / inscription / upgrade）
│   └── BookSpecs.java              三种书载荷（Ascension / Inscription{多条目} / Upgrade）
├── registry/
│   ├── UEItems.java                2 个物品（进阶附魔书 / 祛咒石）
│   ├── UEComponents.java           5 个数据组件 + 物品读写便捷方法（ascensionOf / setAscension）
│   ├── UERegistries.java           注册表键与 id 常量（enchantment 路径段）
│   └── UEDataPackRegistries.java   DataPackRegistryEvent 监听（mod bus，单例注册）
├── logic/
│   ├── EnchantmentFactory.java     ★ 运行时组装 Enchantment + Holder.Direct（唯一入口 + 缓存 + invalidate）
│   ├── StageLookup.java            阶段条目查找（不缓存，走 CommonHooks.resolveLookup）
│   ├── AscensionLogic.java         进阶书判定（纯函数）
│   ├── InscriptionLogic.java       ★ 载体书：贴装备 / 转印 / 书合并 / 剩菜判定（纯函数）
│   ├── ItemMergeLogic.java         同名装备合并（照抄原版数学 + tierLevel +1 + 重命名）
│   ├── BookFactory.java            书的唯一构造入口（物品 + 载荷 + 材质谓词）
│   └── ProtectionLogic.java        受保护附魔与祛咒判定
├── event/
│   ├── EnchantmentLevelEvents.java ★ 结算层：屏蔽原版附魔 + 注入阶段定义
│   ├── AnvilEvents.java            ★ 铁砧五条入口（书+书 / 转印 / 贴装备 / 升级书 / 同名合并）
│   ├── AnvilTakeEvents.java        取件：交付剩菜书（唯一第二输出，P0-14）
│   ├── GrindstoneEvents.java       砂轮：受保护附魔守护 + 祛咒石
│   ├── ReloadEvents.java           TagsUpdatedEvent → 清组装缓存 + 谱系矩阵（P0-13）
│   ├── TooltipEvents.java          物品上的进阶附魔行（显示 tierLevel）
│   ├── BookTooltipEvents.java      三类书各自的 tooltip
│   └── CreativeTabEvents.java      创造栏投放（数据包驱动，顺序见 P1-29）
├── mixin/
│   └── RepairItemRecipeMixin.java  ← 全项目唯一的 Mixin
└── datagen/
    ├── UEDataGen.java              GatherDataEvent 入口（写出阶段条目用 UEStagePack，见 P0-12）
    ├── UEModels.java               物品模型（谓词 + 双层）
    ├── UELang.java                 中英语言文件（静态键 + 阶梯名同处一个提供器，P1-19）
    ├── LineageTable.java           ★ 谱系单一事实源：30 条常用谱系 + 三阶加成阶梯（表驱动）
    ├── UEStages.java               阶段条目生成（definition 取自原版 / effects = 原版 + 阶级加成）
    ├── UEStagePack.java            数据包写出器（必须与读取共用同一份 provider，P0-12）
    ├── UEEffectDoc.java            ★ 效果总表（markdown，落仓库 docs/，不进 jar）
    └── UEStageEffects.java         阶位加成补丁 API（声明式 Append / Replace，自带人话描述）
```

### 自检的写法（重要经验）

阶段 3 与阶段 4 都用「**临时自检类 + runServer**」验证，验证完立即删除。
这是唯一能证明运行期行为正确的方法——**编译通过 + 启动无异常证明不了逻辑正确**。

写法要点：

1. 监听 `ServerStartedEvent`（game bus），此时注册表与数据包均已就绪
2. 直接构造 `ItemStack` 与事件对象，手动 `NeoForge.EVENT_BUS.post(...)`
3. 把关键数据打进日志，跑 `runServer` 抓取
4. 用 `Start-Job` + `Wait-Job -Timeout` 限时，避免服务器常驻

**不要**用 datapack + `tick` 函数验证：服务器没有玩家，`@a` 选不中任何人，什么都测不出来。

**约定**：

- `logic/` 下全部是**纯函数**，不接 `Level` / `Player` 之外的东西，便于两侧复用（见 P0-4）。
- **所有** `new Enchantment(...)` + `Holder.direct(...)` 只允许出现在 `EnchantmentFactory` —— 把 P0-2 的风险收敛到一个可审计的文件。
- 阶段 JSON **由 datagen 产出**，不手写。30 条谱系 × 3 阶 = 90 个文件，手写必然出错。
- **不要手抄原版附魔的效果**。`UEStages` 直接读原版 `minecraft:enchantment` 的
  `definition` 与 `effects` 整段搬用，只叠加阶级加成。手抄 `soul_speed` / `frost_walker`
  那种效果树必错，且随版本漂移。
- **数值一律用「覆盖」：该阶级自己一套公式，原版数字完全不参与。**
  实现是 `UEStageEffects.scaleCore(vanilla, baseMul, slopeMul)`——把原版里所有
  「加法 + 线性」的数值按阶级倍率整体换成该阶公式（倍率规格见 [docs/guide-data.md](docs/guide-data.md) §4，
逐阶绝对数值见 [docs/enchantment-effects.md](docs/enchantment-effects.md)）。
  条件是附魔身份的一部分，**照原版保留**（亡灵杀手依然只打亡灵）。
- **「追加」只用于「这一阶多了个原版没有的效果」**（给锋利加击退、给究极击退加缓慢）。
  不要用追加去改已有组件的数值——那是覆盖的活。两者作用对象不同，**不冲突**。
- **「增量」（`mergeIntoVanilla`）是留给第三方的可选写法，本模组自己的谱系一条都不用**：
  它让「原版 + 我的增量」自动合成一条覆盖式，代价是数值随原版漂移、源文件里看不到绝对数值。
  三种写法的边界与原理见 `docs/guide-data.md`。
- **多数谱系的高阶只改数值、不新增效果**，用 `none()` 占位（效果总表会跳过它）。
- **添一条谱系 = 在 `LineageTable` 加一行**：根源 id + 中英名 + 三个补丁（没有就写 `none()`）。
  `definition`、物品标签、槽位全自动从原版取，不需要任何额外维护。
- **阶位加成是累积的**：某阶拿到的补丁 = 阶梯从第一项到它自己**依次叠加**，
  写表时只写「比上一阶多出来的那一项」。因此属性修饰符 id 必须在整条阶梯里互不相同
  （低阶用过的 id 在高阶阶段里仍然存在），`assertUniqueAttributeIds` 会在 datagen 时挡下来。

---

## 6. 自检清单（提交前过一遍）

- [ ] `mc_gradle task=build` 通过
- [ ] `runData` 产出的阶段 JSON 全部解析成功，`max_level` 全在 `1..255`
- [ ] 数据包注册表传了 `networkCodec`
- [ ] 阶段 JSON 落在 `data/ultraenchantment/ultraenchantment/{advanced,super,ultra}/`（双命名空间，P0-8）
- [ ] 每条链的 `root` 都指向真实存在的原版附魔；`next` 指向真实存在的阶段条目（或留空）
- [ ] `EnchantmentFactory` 是唯一构造合成 `Enchantment` 的地方，且没有任何路径把它写回物品组件（P0-2）
- [ ] `GetEnchantmentLevelEvent` 处理器第一行是组件判空 return（P0-9）
- [ ] 铁砧接管覆盖了「无书但带受保护附魔」（P0-1）
- [ ] 所有 `setOutput` 分支都配了 `setCost(≥1)`（P0-5）
- [ ] 砂轮接管覆盖单物品 / 双物品合并 / 同堆叠合并三条分支（P0-3）
- [ ] 事件处理器里没有 `isClientSide` 提前 return，没有世界/背包/随机访问（P0-4）
- [ ] `RepairItemRecipe` 的 Mixin 生效，且只影响受保护附魔
- [ ] 没有静态字段缓存 `Holder<Enchantment>` 或阶段定义（P1-6）
- [ ] 每个阶段的 `definition` 整段取自其根源附魔（supported_items / primary_items / slots 与原版一致），只有 `max_level` 与花费按阶级抬升
- [ ] 每条谱系的三个增量补丁齐全（`LineageTable` 静态块断言长度）
- [ ] 同一阶段内没有重复的属性修饰符 id（阶梯累积下低阶 id 在高阶里仍在；`assertUniqueAttributeIds`）
- [ ] 每条 `next` 都指向真实存在的阶段条目，终阶留空（`UEStages.generate` 自检）
- [ ] 阶段 JSON 是用**读取原版附魔的同一份 provider** 序列化出来的（P0-12）
- [ ] 若任一阶段上限 > 10，已补 `enchantment.level.<n>` 翻译键（P1-2）
- [ ] 创造的投放分了两层（主标签「每格满级」/ 搜索标签全量等级），且铭刻书主标签的
      顺序是**谱系外层 × 阶级内层**（`CreativeTabEvents.mainTabInscriptions()`，P1-29）
- [ ] 铁砧五条入口的输出**成本都 > 0**（P0-5），`materialCost` 语义对：书 = 1、同名装备合并 = 0（P0-6）
- [ ] 载体书贴装备时，被拒绝的条目由 `AnvilTakeEvents` 还原成剩菜书（P0-14）
- [ ] 原版附魔书不可能再带上 `ascension` 组件——它只会被**转印**成载体书
- [ ] `runData` 之后 `docs/enchantment-effects.md` 已刷新，且**没有被打进 jar**
      （它在仓库 `docs/` 下，不在 `src/generated/resources` 里）
- [ ] 主标签**覆盖到每一条谱系 × 每一个阶级**——数「出现过的谱系数」与谱系总数比，
      不要数「每个阶级有没有样本」（那个判据只有 1 条谱系时也能通过，见 P1-29）
- [ ] **数据包派生的缓存在重载后确实非空**（`ReloadEvents.matrix()` / `globalMaxLevel()`）——
      必须用 `TagsUpdatedEvent.getRegistryAccess()` 刷新，用 `CommonHooks.resolveLookup` 会静默跳过（P0-13）
- [ ] 定向进阶书 tooltip 的「可应用于附魔 / 进阶为」分别写 **`from_tier` / `to_tier` 的阶段名**
      （原生阶那一端退回原版附魔名），两端都是**单一键**、不是拼接（P1-30）
- [ ] 定向进阶书的 `to_tier` 与 `from_tier` **父子相连**——跳阶书必须不生效（`AscensionLogic`）
- [ ] 定向进阶书**两个标签都全量投放**（它只有「谱系 × 阶级」两维，没有需要靠搜索的隐藏规格）

---

## 7. 待办与开放项

| # | 项 | 状态 |
|---|---|---|
| 1 | 各阶级的进阶门槛 `required_level` | 已给出初值（`UEStages.TIERS`：5/4/3，实际生效值再取 `min(它, 上限)`），**仍待作者微调**。`max_level` **一律沿用根源附魔原版值**，不在可调项里 |
| 2 | 三种祛咒石的贴图 | ✅ 已完成 |
| 3 | 各谱系的深度（哪些写到超级就停、哪些到究极） | 已定为统一三阶（高阶→超级→究极）；要单独截断只需把该谱系阶梯的尾部补丁去掉 / `next` 留空 |
| 4 | 书的获取途径（合成 / 战利品 / 交易） | **设计空白**——四种物品目前只能从创造栏获得，见 [规格 §12.1 R1](docs/book-system-spec.md) |
| 5 | 代理 `127.0.0.1:10090` 是否仍有效 | 已确认失效，未继承（§2.5） |
| 6 | 各谱系的阶位加成数值 | 已给出一套初值（`LineageTable` 内就地可调），**待实际手感调整** |
| 7 | `fortune` / `silk_touch` / 两个诅咒的进阶 | **刻意不生成阶段条目**（理由见 `LineageTable` 类文档）。前两者的行为由单点查询驱动，阶段条目对它们不生效；要做需改结算/掉落层代码，不是数据包 |

---

## 8. 变更日志

| 日期 | 变更 |
|---|---|
| 2026-09 | **创造栏铭刻书排布修正（v2.1）**。两处「看起来像数据乱了」的问题：① 主标签用**阶级轮转**取样（v1.3 起），同一条谱系的三个阶级被拆散成「高阶X / 超级Y / 究极Z」，玩家第一反应是阶级串了行——改为**谱系外层 × 阶级内层**（90 条，同一附魔的高阶/超级/究极相邻递增），覆盖从「每条谱系 1 次」变成 3 次；② v2.0 新增的**多条目「已合并」样本**放错了标签页：它与单条书共用同一材质，摆在主标签里就像「某条附魔的书莫名多了一条别的附魔」（排序后前两条谱系恰好是爆炸保护与引雷，于是三条样本全是「爆炸保护 + 引雷」）——移到搜索标签。顺手把计划表抽成 `CreativeTabEvents.mainTabInscriptions()` 纯函数并自检（8/8） |
| 2026-09 | **载体书体系（v2.0）**。把「进阶形态」做成有实物载体的东西，一次解掉三个结构性问题：① **原版附魔书被进阶后**不再是「隐形印章」（物品 id 不变、提示框看不见、贴装备时随书蒸发、还会漏进砂轮）——改为**转印**：原版附魔书 + 「基础→高阶」进阶书 → 我们的**载体书**，物品 id 真的变了，条目从曲线 1 起，门槛复用原生阶的 `min(required, 原版上限)`（锋利要一本锋利 V）；② **同名合并升 1 级**在书层面与装备层面同时落地（README §2/§7 早就写了规则，一直没实现）：书同级 → `level+1`（逐谱系夹取）、不同级取 max；装备同级 → `tierLevel+1`；③ 铭刻型载荷由单条改为 **`{tier, entries[]}` 多条目**，`level` 语义 = tierLevel（存储等级升阶后恒为上限，拿它 +1 是空转）。生存与创造分叉：生存只能提**同阶级**的曲线等级，创造可给白装备直上究极（仍受 `supportsEnchantment` 与防降级约束）。新增 **P0-14**（剩菜书 = 唯一第二输出，只能走 `AnvilRepairEvent`），并放开 P1-13 一处例外（装备同名合并必须重写原版合并数学，连带必须照抄重命名，否则是回归）。规格 `docs/book-system-spec.md`，44 项运行时自检全通过 |
| 2026-09 | **文档重写（v1.0）**。架构从「三附魔分立」翻转为「原地升级 + 独立注册表 + 运行时组装」，与首版完全不相容，旧内容全部作废。记录 P0×10 / P1×6 / P2×7 共 23 个坑（当时值，现已有增补），以及 9 条被否方案 |
| 2026-09 | **改为纯覆盖模型 + 数据包指南（v1.8）**。① 确认「覆盖」= **每阶一条自己的完整公式，原版数字完全不参与**（锋利 3+1 / 6+1.5 / 10+2.5），实现为 `scaleCore`：把原版所有「加法+线性」数值按阶级倍率（首级值 ×3/×6/×10、每级增量 ×2/×3/×5）整体替换，**条件照原版保留**。② 生成管线里删掉自动合并；`mergeIntoVanilla` 保留为第三方可选写法（本模组 30 条谱系一条不用）。③ 新增 `none()` 空补丁——多数谱系的高阶只改数值、不新增效果。④ 同步删掉 19 条谱系里与覆盖重复的「加成」补丁。⑤ 新增 docs/guide-data.md：三种写法的边界与原理、阶级倍率规格、八类效果的完整 JSON 做法、自检与常见错误 |
| 2026-09 | **tierLevel 成为效果等级（v1.7）**。确认并落地：`tierLevel` 就是进阶形态自己的附魔等级，`EnchantmentLevelEvents` 注入阶段条目时改用它（存储等级只留给原版机制）。此前按存储等级结算，导致「高阶锋利 1 级」按 5 级算、升级书纯装饰。连带修正三处**写反了的**文档（README §4 / `AscensionData` / `AnvilEvents`，以及 P1-20 本身）。升级书目标值改为夹在该谱系该阶级的 `max_level` 内 |
| 2026-09 | **合并式覆盖 + 击退究极效果（v1.6）**。① 新增 `UEStageEffects.mergeIntoVanilla`：格式相同的加成自动合并进原式改成覆盖（锋利伤害 → `2 + 1×(等级-1)`，经验修补 → `×3/×4.5/×9`，属性同理）；带条件的与多条分流的整组放弃合并。② `Replace` 泛化为 `Replace<T>`，列表组件也能覆盖。③ 合并踩到一次 unchecked 强转的坑：按组件强转前必须先通配读取确认元素类型，否则 `post_attack` 的 `TargetedConditionalEffect` 会在 datagen 中途抛 `ClassCastException`——已记入类文档。④ 击退究极阶改为「命中后使目标中缓慢（5 秒起 +5/级，强度 I 起 +1/级）」 |
| 2026-09 | **效果总表（v1.5）**。新增 `UEEffectDoc`：`runData` 顺带导出 `docs/enchantment-effects.md`（30 谱系 × 3 阶），列 = 谱系/阶级/上限/门槛/**效果摘要**/阶位加成/**满级合计**；摘要里**粗体**就是进阶多给的那部分，加成标「追加」或「覆盖」。配套把 `UEStageEffects.Patch` 从不透明 `UnaryOperator` 改成声明式 `Append`/`Replace` 并自带人话描述——表不再靠「与原版逐条比对」反推（加成恰好与原版同式时会失效），`UEStages.GeneratedStage` 同时带出原版那份与补丁列表。表是生成物，改数值仍走 `LineageTable` |
| 2026-09 | **定向进阶书语义与显示修正（v1.4）**。① **规格错了**：README 把定向书的 h3 写成「具体附魔名」，实现照做，于是「高阶→超级」显示成「锋利 → 超级锋利」，**中间少一阶**；规格与实现同改（h3 = `from_tier` 的阶段名）。② 把「父子」约束落进代码：目标只能是当前阶段的 `next` 且阶级须等于 `to_tier`，原生阶只能进**表头**——跳阶书一律不生效。③ 定向书改为**两个标签全量投放**（90 条），不再走样本轮转（那样每条谱系只露一个随机阶级）。④ 主标签 24 → 138、搜索标签 381 不变。新增 **P1-30** |
| 2026-09 | **主标签样本改为全谱系覆盖（v1.3）**。原「每个阶级换一条谱系」只让 3 条谱系露面（30 条里 27 条缺席），改为**每条谱系都露面一次、阶级沿谱系顺序轮转**：主标签 24 → **78** 条，谱系覆盖 30/30，两个科目各 10/10/10 阶级分布。新增 **P1-29**（覆盖要按谱系总数算） |
| 2026-09 | **修复创造栏整块缺失（v1.2）**。`ReloadEvents` 在 `TagsUpdatedEvent` 里用 `CommonHooks.resolveLookup` 取注册表，而那一刻服务器对象尚未存在 → 恒为 null → 谱系矩阵永远为空：搜索标签只剩 18 条（应为 381）、主标签 6 条谱系样本**全灭**，且不报错。改用 `event.getRegistryAccess()`，新增 **P0-13**，并收窄 P1-10 的适用范围。定向进阶书末行改为直接引用阶段名键（中文不再显示成「高阶 锋利」，且可被整合包覆盖） |
| 2026-09 | **数据包铺开（v1.1）**。谱系表从 1 条（锋利）扩到 **30 条玩家常用**附魔（武器/护甲/通用工具/弓弩/三叉戟/钓鱼）；效果模型改为「**原版 effects 整段搬用 + 阶级累积加成**」，`definition` 整段取自原版附魔（不再手工维护物品标签、槽位与等级上限）；写出改走 `UEStagePack`，新增 **P0-12**（外部 holder 与 provider owner）；等级上限定为逐谱系后新增 **P1-28**（按谱系查上限）。90 个阶段条目 + 90 组阶梯名全部生成并自检通过 |

---

*本文件是活的。每踩到一个新坑，追加到 §3，并标注严重度与源码行号证据。*
