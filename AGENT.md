# AGENT.md · AI 协作与工程约定

> 面向在本仓库工作的 AI 代理（以及人类协作者）。
> 目标：**让每一次改动都可验证，让每一个坑只踩一次，让被否掉的方案不要再被重新推导。**
>
> 配套文档：[`docs/book-system-spec.md`](docs/book-system-spec.md)（体系规格与实现契约）、
> [`docs/guide-data.md`](docs/guide-data.md)（数据包教程）、[`README.md`](README.md)（面向玩家的介绍）。
>
> **图书馆与进阶台的现行模型：[`docs/library-energy-model.md`](docs/library-energy-model.md)** ——
> 每阶级一份**等级单位**池（同阶级内提级用）＋ 一份**书库存**（跨阶用进阶书）。
> v5 起取代了 v4 的「8 类抽象能量」；文中保留 v4/v5 的决策记录与被否选项，读的时候注意版本。
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
| 3 | **阶级 = 等级区间**（基础 1-5 / 高阶 6-10 / 超级 11-15 / 究极 16-20） | **与神化（Apotheosis）等抬高上限的模组正面冲突**——`sharpness: 12` 在那边是合法的更强锋利，被误判成超级阶 |
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

| 类型 | 语义 | 含基础阶？ | 用在哪 |
|---|---|---|---|
| `UETier` | **物品形态**阶级 | 否 | 书/祛咒石的外观（`custom_model_data` 1/2/3） |
| `AscensionTier` | **进阶档位** | 否 | 升级书的 `tier`、祛咒石的 `curative_tier` |
| `LineageTier` | **谱系当前状态** | **是** | `ascension` 组件反查；**进阶书的 `from_tier`/`to_tier`** |

**关键区分**：

- **进阶书**的 `from_tier` 用 `LineageTier`——它可以填 `NATIVE`，表示「这本书用于尚未进阶的原始附魔」（即「基础→高阶」品质）。
- **升级书**的 `tier` 用 `AscensionTier`——只作用于**已进阶**附魔，基础阶没有阶级可升，因此该字段出现 `native` 是无意义的。

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

#### P0-15 · 铁砧跑在**容器包处理链**里：未捕获异常 = 玩家掉线 ⚠️

`AnvilMenu.createResult`（铁砧）与 `AnvilMenu.onTake`（取件）都不是普通的方法调用——
它们由**容器点击包**的处理链触发。在这里抛出未捕获异常，掀掉的是服务端线程，
客户端看到的是「**失去世界连接**」：一个数据错误被放大成掉线，而且日志里只有一条网络异常，看不出真凶。

**处理**（两道，缺一不可）：

1. **两个入口都整体兜底**——`AnvilEvents.onAnvilUpdate` 与 `AnvilTakeEvents.onAnvilRepair`
   各自把实现体抽成私有方法，外层 `catch (RuntimeException | LinkageError)`：
   降级为「本次不产出 / 这张剩菜书不交付」+ 一条带异常的错误日志。
   两侧走的是同一条确定性路径，所以两边一起降级，不会出现单边错位。
2. **写入的值必须落在可序列化范围内**——典型是附魔等级：`ItemEnchantments` 的等级有取值域，
   数据包把 `max_level` 写得过大时，越界等级会在**网络编码**阶段炸（同样是掉线，
   不是一条可读的报错）。所以写存储等级前一律
   `min(该阶上限, 该附魔原版上限, 255)`。

> 判定信号：**任何跑在「包处理链 / tick 链」里的代码，都不允许把异常抛出去。**
> 这类代码出错的表现不是「功能没生效」，而是「玩家掉线 / 服务器崩」，成本高一个数量级。

#### P0-16 · 运行期代码引用 datagen / 客户端类 = 服务端掉线 ⚠️

**真实事故（v2.2）**：运行期的 `logic/BookFactory`（铁砧产物造书）调用了
`datagen/UEModels.predicateOf(...)`——而 `UEModels extends ItemModelProvider`，
那是**客户端**的模型生成器 API。开发环境里客户端/服务端类都在同一个 classpath 上，
所以怎么测都正常；**只跑服务端的发行版**里加载它就会抛 `NoClassDefFoundError`。
而这个调用发生在铁砧结果槽的写入路径上——即**包处理链**里，于是玩家直接掉线（P0-15）。

**规矩（依赖方向只有一个）**：`content / logic / event / registry` 这些运行期包
**不得** import `datagen.*`，也不得 import `net.minecraft.client.*` 与
`net.neoforged.neoforge.client.*`。共享的纯逻辑要下沉到运行期，**datagen 反过来调它**。

**怎么发现**：`grep "ultraenchantment\.datagen|neoforge\.client|net\.minecraft\.client"`，
排除 `datagen/` 自身，剩下的每一处都要有理由。
（`Ultraenchantment → UEDataGen` 是合法的：只是注册 `GatherDataEvent` 监听，
provider 在事件里才实例化，专用服务器上永不触发。）

> 判定信号：**「dev 里测不出来」+「只在服务端发行版炸」+「一放上去就断线」**——
> 这三个特征凑齐，先怀疑类加载与依赖方向，而不是数据。

#### P0-17 · 写进物品组件的 Holder 必须来自**生成它的那一侧**注册表 ⚠️

**真实事故（v2.4）**：单机（集成服务器）+ 创造模式，把物品与高阶附魔书放进铁砧 → **立刻掉线**。
客户端日志里的决定性证据：

```
Internal Exception: io.netty.handler.codec.EncoderException:
    Failed to encode packet 'serverbound/minecraft:container_click'
Caused by: java.lang.IllegalArgumentException:
    Can't find id for 'Reference{ResourceKey[minecraft:enchantment / minecraft:smite]=Enchantment 亡灵杀手}'
    in map net.minecraft.core.Registry$1@...
    at net.minecraft.core.IdMap.getIdOrThrow
    at net.minecraft.network.codec.ByteBufCodecs$25.encode     // holderRegistry
    at net.minecraft.core.component.DataComponentPatch.encodeComponent
```

**机制**：`ByteBufCodecs.registry(...)` 编码 Holder 用的是 `IdMap.getIdOrThrow(holder)`——**按值查 ID**。
而服务端与客户端的 `Enchantment` 是**两套不同实例**（record 里嵌着各自那一侧的 `HolderSet` / 效果组件），
跨侧**不相等** → 查不到 ID → 编码失败 → 该包是**客户端发往服务端**的点击包，于是玩家掉线。

**为什么会跨侧**：`CommonHooks.resolveLookup` 的第一优先级是
`ServerLifecycleHooks.getCurrentServer()`。**单机里那台集成服务器一直在**，
于是**客户端线程**上它也返回**服务端**注册表；而我们（创造旁路给白装备直上阶级时）
把这个服务端 Holder 写进了客户端本地生成的铁砧结果栈。客户端一发包 → 上面的异常。
（纯多人客户端没有集成服务器，回落客户端注册表，所以**只有单机复现**；
专用服务器两侧同表，也复现不出来——这是它躲过所有服务端自检的原因。）

**处理**：写入物品组件时，注册表必须按「**谁生成这个栈**」来选——
`UELookups.enchantmentsForItemWrites(clientSide)`：
逻辑客户端（`level.isClientSide()`）用 `ClientHooks.resolveLookup`，否则用 `CommonHooks.resolveLookup`。
⚠️ 集成服务器上**两侧线程同时在跑**，所以判据是 `level.isClientSide()` 而**不是** `FMLEnvironment.dist`。
客户端分支要包在独立嵌套类里，避免专用服务器加载客户端专属类（P0-16）。

**配套**：只写「有注册表 ID」的 Holder——写入前检查 `holder.unwrapKey().isPresent()`，
把运行时组装的合成 Holder（P0-2）挡在门外。

> 判定信号：**「单机炸、专用服务器不炸、且只在我们新建物品组件时炸」**——
> 先怀疑「写进去的 Holder 属于另一侧的注册表」，而不是数据本身。

#### P1-37 · 「展示能力」不等于「往里塞样本」：创造栏只放单条目书

**真实事故（v2.7 → v2.8）**：为了「让玩家发现载体书可以刻多条」，我往搜索标签投了 3 条多条目样本
（当时是「爆炸保护 + 引雷」）。作者看到的第一反应是**数据乱了**——多条目书与单条目书
共用同一套材质（外观只有「科目 × 阶级」两个维度），在列表里就是「某条附魔的书莫名多了
一条别的附魔」，没有任何界面能说明这是刻意的样本。

我第一次的修法是「换成能贴到同一件物品上的组合」（爆炸保护 + 深海探索者）——**方向错了**：
问题不是「组合挑得不好」，而是**创造栏本来就不该有多条目书**。最初定夺的投放规格只有
「谱系 × 阶级 × 等级」这条单条目轴；「一本刻多条」是**玩家在铁砧上合并两本**才该出现的结果，
不该由创造栏凭空发出来。

**现在的规矩**：创造栏（两个标签）**只放单条目载体书**；多条目书只能由玩家合并产生。
校验反向做：`UEJeiPlugin.checkNoMultiEntryBooks` 遍历材料表，**发现任何多条目书就报 ERROR**
（约定值 0，实测 0 本）。

> 教训：**要展示某个能力，就用「玩家真能走到的路子」去展示，不要为了展示而造一个界面上
> 无法自证的数据样本。** 界面上无法自证的东西，玩家只能理解为 bug。

#### P1-38 · 运行时合成的附魔**没有注册表 key**，第三方反查会崩 ⚠️

**真实事故（v2.10，作者在非编译环境遇到）**：装了**试验假人**（`dummmmmmy` 1.21-2.1.2）后：
「用带进阶附魔的物品打假人没有伤害；用三叉戟投掷则立刻崩，之后一进世界就崩，只有删掉实体才恢复」。

崩溃报告给出真凶：`DummyMobType.isVulnerableTo` 第 58 行（**字节码实测**）：

```java
ResourceKey<Enchantment> id = Utils.hackyGetRegistry(Registries.ENCHANTMENT)
        .getResourceKey(enchantment)   // Optional
        .get();                        // ← 查不到就抛 NoSuchElementException
```

它要让假人模拟「亡灵 / 节肢 / 水生」三类抗性，于是对**每个**附魔反查注册表 key，
却没处理「查不到」的情况。而本模组的阶级附魔是**运行时组装**的
（`EnchantmentFactory` + `Holder.Direct`）——不在 `minecraft:enchantment` 里，
`getResourceKey` 必然为空 → 崩。

**为什么表现成两个症状**（同根）：
- `ThrownTrident.onHitEntity` → `EnchantmentHelper.modifyDamage` → 遍历附魔 → 它的 mixin
  对**条件效果**（穿刺/引雷那类带 `entity_properties` 的）走兜底分支 → 调 `isVulnerableTo` → 崩；
  飞行中的三叉戟实体留在世界里，**每 tick 重演** → 读档即崩（不是存档损坏）；
- 近战同样会崩在伤害计算里（实测连**无条件**的锋利阶段也触发）→ 伤害没结算 →
  玩家看到的就是「打不动」。

**处理**：兼容补丁 `mixin/compat/DummyMobTypeCompatMixin`——在 `isVulnerableTo` 头部拦下
「反查不到注册表 key」的附魔，直接返回 `false`（语义正确：不是亡灵/节肢/水生特攻）。
判定两条：我们自己的合成附魔走**引用身份表**（`EnchantmentFactory.isSynthetic(Enchantment)`，O(1)），
其它来源走通用反查（`UELookups.enchantmentRegistry()`）。
**门控**：单独一份 `ultraenchantment.compat.mixins.json` + `UECompatMixinPlugin`，
只有装了 `dummmmmmy` 才应用（`required: false`：对方重构时只记日志、不让玩家崩）。

**实测（同一份代码，只切换补丁开关）**：

| 场景 | 补丁关闭 | 补丁开启 |
|---|---|---|
| `isVulnerableTo(合成附魔)` | `NoSuchElementException` | 返回 `false` ✅ |
| 进阶穿刺三叉戟对假人 `modifyDamage` | 崩 | 正常（2.0）✅ |
| 进阶锋利剑对假人 | 崩 | **3.0 → 5.0**（进阶伤害确实生效）✅ |

**伤害数值本身也逐点对过账**（补丁开启、假人在场，`EnchantmentHelper.modifyDamage` 基础伤害 2.0）：

| 阶级 | 1 级 | 2 级 | 实测斜率 | 设计值 |
|---|---|---|---|---|
| 原版锋利（对照） | 3.0 | 3.5 | 0.5 | base 1.0 / slope 0.5 |
| 高阶 | 5.0 | 6.0 | 1.0 | base 1.0×3.0 / slope 0.5×2.0 |
| 超级 | 8.0 | 9.5 | 1.5 | base 1.0×6.0 / slope 0.5×3.0 |
| 究极 | 12.0 | 14.5 | 2.5 | base 1.0×10.0 / slope 0.5×5.0 |

→ **8/8 全中**。结论：崩溃修掉之后，伤害数值严格按阶级曲线走；
崩溃期间那个「没伤害」**不是公式错了，而是计算中途抛异常、这一刀根本没结算**。

> **补记（v2.11）：兼容补丁不能只求「不崩」，必须把语义补回去。**
>
> 第一版补丁对「查不到注册表 key」一律返回 `false`——不崩了，但**语义丢了**。
> 作者按假人百科把海龟壳戴上，假人变成 `AQUATIC`（对方规则：`IMPALING → AQUATIC`），
> 结果**阶级穿刺照样吃不到加成**。实测（同一份代码，只切换补丁实现）：
>
> | 判定 / 伤害 | 一律 false（v2.10） | 按根源回答（v2.11） | 设计值 |
> |---|---|---|---|
> | 阶级穿刺 × 水生假人 | `false` ❌ | `true` ✅ | 应为 true |
> | 阶级亡灵杀手 × 亡灵假人 | `false` ❌ | `true` ✅ | 应为 true |
> | 阶级穿刺 × 亡灵假人 | `false` | `false` ✅ | 应为 false |
> | 阶级锋利 × 水生假人 | `false` | `false` ✅ | 应为 false |
> | 海龟壳假人挨**阶级穿刺 I** | **2.0**（零加成） | **9.5** ✅ | 2.0 + 2.5×3.0 = 9.5 |
> | 对照：原版穿刺 V | 14.5 | 14.5 | 2.0 + (2.5 + 2.5×4) = 14.5 |
>
> **做法**：合成附魔的权威身份是它的**原版谱系根源**——`EnchantmentFactory` 现在维护
> 「组装对象 → `stage.root()`」的身份表（`rootOf(Enchantment)`，引用判等 O(1)），
> 补丁把根源当 key 交回给对方，再按对方自己的三条规则回答
> （smite→UNDEAD / bane_of_arthropods→ARTHROPOD / impaling→AQUATIC）。
>
> **教训：兼容补丁的验收标准不是「不崩」，而是「行为与对方原意一致」。**
> 一个只把异常吞掉的补丁，会把 bug 从「崩服」降级成「悄悄算错」——后者更难发现。
> 判定信号：**对方模组里有「按附魔身份分支」的逻辑时，替它补身份，而不是替它做决定。**

**这条坑上又踩了两个小坑（都已修，记下来省下次的时间）**：
1. **`shouldApplyMixin` 里不能碰 `ModList.get()`**——那一刻 ModList 还没建好，返回 `null` → NPE
   → Mixin 抛 `InvalidMixinException` → **整份配置作废**（补丁静默失效，且因为 `required:false` 连崩都不崩）。
   改用加载期 `FMLLoader.getLoadingModList()`，再退回「类在不在」探测；**并且绝不让异常逃出插件**。
   > 判定信号：日志里**没有**你插件的那行日志、却也没崩 —— 大概率是插件早退了。
2. **开发环境里用对方模组的 jar 时，文件名必须是 ASCII**。拷 `[试验假人] dummmmmmy-….jar` 进
   `run/mods` 会**加载不到**（依赖模组却报「dummmmmmy is not installed」），重命名成
   `dummmmmmy-1.21-2.1.2-neoforge.jar` 即可。

**根治方向（未做）**：把 59 个阶级附魔改成**真正的数据包附魔**
（`data/ultraenchantment/enchantment/<阶>/<附魔>.json`，原版格式），运行时直接取注册表 holder，
不再 `Holder.direct`。这样所有模组的反查都成立，同时消灭 P0-2 / P0-17 那一类隐患。
代价：datagen + 结算层改造，且要防止它们出现在附魔台/村民交易里。

#### P1-39 · 原版 tooltip 的属性段**不含附魔加伤**，第三方提示框只读存储附魔

**现象（作者 2026-10）**：装了「传说提示框」后，两面下界合金剑的攻击伤害都显示 **11**——
普通锋利满级和阶级究极锋利满级一模一样。

**查证（实测三种剑）**：

| 物品 | 属性段（ATTACK_DAMAGE 修正） | 真实附魔加伤 | tooltip 行 |
|---|---|---|---|
| 裸下界合金剑 | 7.0 | 0.0 | `7 Attack Damage` |
| 原版锋利 V | 7.0 | 3.0 | `7 Attack Damage` |
| 阶级究极锋利 V | 7.0 | **20.0** | `7 Attack Damage` |

两个独立原因叠加：
1. **原版的属性段根本不算附魔加伤**——属性段只统计 `ATTRIBUTES` 组件
   （源码实证：`EnchantmentHelper.forEachModifier` 只读 `EnchantmentEffectComponents.ATTRIBUTES`），
   而锋利的加伤走的是 `DAMAGE` 效果（攻击时才结算）。所以三把剑的属性行都是同一个 7。
2. **第三方提示框按「存储附魔」套原版公式**：我们的铁律是升阶**不动存储等级**
   （锋利 5 仍是 5，见 P0-1），所以它们算出来永远是原版的 +3（8+3=11），
   而阶级加成只存在于结算层（查询期注入）——**它们读不到**。

**处理（v2.12 曾实现 → v2.13 按作者要求撤销）**：一度在进阶附魔行下面插了一行
`攻击伤害 +N`（蓝色，属性修正配色）把真实增量写出来（究极锋利 V → +20，与实测一致）。
作者看过后要求**保持 tooltip 原样**，已撤销：`TooltipEvents` 不再插行，
tooltip 回到「阶级附魔行 + 原版属性段」的形态（实测：究极锋利 V 仍只显示 `7 Attack Damage`）。

> ⚠️ 本条留档**只为解释「数字为什么对不上」**，不要因此再往 tooltip 里插行。
> 想改显示口径（例如让属性段把阶级加伤算进去），先和作者确认——那是产品决定，不是 bug。

> 教训：**显示层要为自己的数字负责**。第三方模组按旧口径（存储等级）算，我们无法改它；
> 但玩家看到的总和必须是真相，所以我们把真实增量显式写出来。
> 判定信号：**「我方数值走效果组件、别人走属性/存储」时，tooltip 一定对不上**——
> 别指望它们读到结算层。

#### P1-41 · Prism 彩虹行 & 传说提示框数值适配（**软联动**的两种范式）

**需求（作者 2026-10）**：不强制依赖，装了才生效——Prism 让**究极阶**附魔行变成渐变彩虹；
同时适配传说提示框那两个 mixin，让它们的附魔加伤计算用上我们的数据。

### 一、Prism：究极阶渐变彩虹（已实测 ✅）

| 环节 | 做法 |
|---|---|
| 取色 | `DynamicColor.fromRGB(...)`（色相用原版 `Mth.hsvToRgb` 算，避开对方 HSV 单位歧义） |
| 渐变 | 24 档色带，第 i 档的色环整体旋转 i 格 → 整行逐字渐变，且随时间流动 |
| 动画 | `DynamicColor` 的相位在**实例里**，tooltip 每帧重建 → **实例必须长期缓存**（每帧 new 会重置动画钟） |
| 隔离 | `compat/tooltip/PrismRainbow` 只在「逻辑客户端 + Prism 已装」时被类加载（P1-40 铁律） |

实测：究极行颜色档位数 **6**，40 tick 后再采样**颜色已变**（`[LT] ... 动画 = PASS`）。

### 二、传说提示框：把它算错的绿色攻击伤害行补正（已实测 ✅）

**它在做什么**：修原版 MC-271840（附魔加伤不出现在攻击伤害行）。两个 mixin
（`AttributeUtilMixin` → `@Redirect(method="applyTextFor")`、
`ItemStackMixin` → `@Redirect(method="addModifierTooltip")`）
都把「玩家基础攻击力」换成「基础 + 遍历附魔 DAMAGE 效果」。
**它读的是 `DataComponents.ENCHANTMENTS`（原始组件）**，而我们的阶级附魔只在结算层 →
它永远算出原版数字（实测：普通锋利 V 与阶级究极锋利 V 都是 **11**）。

**先试的路（失败，记下来别再试）**：在它注入的方法上再注入 `@ModifyExpressionValue`，换掉它读的附魔表。
- priority 500 与 1500 都报 `failed injection check, (0/1) succeeded. Scanned 0 target(s)`
  ——我们应用时它注入的指令还没进方法体；**跨模组 mixin 的应用顺序不可依赖**。
- 另一个坑：把 `require` 从 0 改成 1 做诊断时，**即使配置写了 `required: false` 也直接把客户端崩掉**。
  所以正式代码一律留 `require = 0`，靠日志与自检发现问题。

**最终做法（不依赖对方内部）**：在我们自己的 `ItemTooltipEvent` 里补正成品行：
`Δ = 结算表附魔加伤 − 原始表附魔加伤`（两边同一套求和方式，经 `LegendaryTooltipsCompat.settledEnchantments` 取结算表），
只对进阶物品、只在装了对方且是逻辑客户端时执行；普通物品 Δ=0，**一个数字都不动**。

> **踩坑**：那条行是 `Component.translatable("attribute.modifier.equals.0", 数值, 属性名)`，
> 数值是**参数**不是 sibling——改 `getSiblings()` 完全改不动（实测 28 改不出来）。
> 必须用 `Component.visit` 展开带样式的片段后**逐段重建**，既改数字又保住各段颜色。

**实测（客户端，同一份代码）**：

| 物品 | 修正前 | 修正后 | 期望 |
|---|---|---|---|
| 原版锋利 V（对照） | 11 | **11** ✅ | 11（不动） |
| 阶级究极锋利 V | 11 | **28** ✅ | 8 + 20 |
| 阶级超级锋利 II | 11 | **15.5** ✅ | 8 + 7.5（证明用 tierLevel=2，不是存储等级 5） |


### 三、有条件的效果**一律不计入**（v2.16 修正）

第一版把「所有 DAMAGE 效果」都算进去了——**错的**：像亡灵杀手（只对亡灵）、穿刺（只对水生）这类，
加伤取决于打谁，tooltip 上写死一个数字就是骗人。

反过来看对方的做法（反编译 AttributeUtilMixin 第 173/176 行）：它自己就调了
ConditionalEffect.requirements() → Optional.isEmpty() 来跳过条件效果。
所以「两边同口径」也必须跳过——否则差值会凭空多一块。**修正后两边都只算无条件部分。**

实测（客户端）：

| 物品 | 实测 | 期望 |
|---|---|---|
| 原版锋利 V（对照） | 11.0 ✅ | 11 |
| 阶级究极锋利 V（无条件） | 28.0 ✅ | 8 + 20 |
| 阶级究极亡灵杀手 V（**有条件**） | **8.0** ✅ | 8 + 0（不承诺数字） |

### 四、无前置时不会崩（实测 ✅）

「软联动」的硬要求：玩家没装 Prism / 传说提示框 / 冰山时，绝不能因为我们的代码崩。三道保障：

1. **依赖声明可选**：neoforge.mods.toml 里都是 type="optional"（写 required 会让漏装者开不了游戏）；
2. **类加载隔离**：引用对方类的 PrismRainbow（还引用客户端类）只在「逻辑客户端 + Prism 已装」时被加载；
   LegendaryTooltipsFixup / LegendaryTooltipsCompat 引用 Minecraft（客户端专属），
   只在「逻辑客户端 + 传说提示框已装」时被调用——专用服务端上永远不会加载它们；
3. **判据只用 modid**：ModList.isLoaded（不触发类加载），且 shouldApplyMixin 绝不抛异常。

实测（专用服务端，**摘掉全部前置**）：

```
[GUARD] present(LT)=false prismPresent=false usePrismGradient=false useLegendaryTooltipsFix=false
[GUARD] PASS 未装前置时全部门控关闭，模组正常启动
```

#### P1-40 · 可选依赖（提示框栈）：**声明为 optional + 类加载隔离**

**需求（作者 2026-10）**：为「传说提示框（Legendary Tooltips）＋冰山（Iceberg）＋Prism」做
编译/运行期依赖，声明为**可选**，并且**打包后的 jar 在对方未加载时不能崩**。

### 怎么接的

| 环节 | 做法 | 为什么 |
|---|---|---|
| 拿 jar | 三个模组都**不在公共 maven**（CurseForge/Modrinth 专有）→ 放 `libs/` 本地 jar | 没有可声明的仓库坐标 |
| 编译期 | `compileOnly fileTree('libs', ...)` | 只借 API；**绝不进我们的 jar** |
| 开发运行期 | `runtimeOnly fileTree('libs', ...)` | 让开发客户端真能加载它们做联调 |
| 发布声明 | `neoforge.mods.toml` 里 `type="optional"` + `ordering="AFTER"` + `side="BOTH"` | 写成 `required` 会让**没装的玩家直接开不了游戏** |
| 代码接入 | `compat/tooltip/TooltipStackCompat` 门控 + `ModList.isLoaded` | 见下 |

联调版本：`legendarytooltips 1.5.5` · `iceberg 1.3.2` · `prism 1.0.11`（1.21.1 / NeoForge）。

### 铁律：引用对方类的代码只能在「确认装了」之后被加载

`TooltipStackCompat.present()` 用 `ModList.get().isLoaded("legendarytooltips")`——
**只查模组清单，不触发类加载**，也不会因为对方改名而抛异常。
任何 `import` 对方类的代码必须写在**只在 `present()` 为真时才被调用**的类里：

```java
if (TooltipStackCompat.present()) {
    TooltipStackHooks.something();   // 这个类里才允许出现对方 import
}
```

否则 JVM 在链接/校验时抛 `NoClassDefFoundError`——服务端、没装的玩家那里都会炸，
而且报错跟我们的功能毫无关系（同类分层原则见 P0-16）。

### 实测（同一份代码，只切换有没有装对方）

| 场景 | 结果 |
|---|---|
| 开发客户端 + 三个模组在运行期 | 三者均加载（`Iceberg 1.3.2` / `Legendary Tooltips 1.5.5` / `Prism 1.0.11`），门控日志 `[TooltipStack] 检测到传说提示框` ✅ |
| 开发服务器 + **摘掉运行期依赖**（模拟玩家没装） | `TooltipStackCompat.present() = false`，模组照常启动，无 `NoClassDefFoundError` ✅ |
| 产物 jar | 不含 `com/anthonyhilyard/...` 任何类 ✅ |

> `libs/` 是**开发专用**：不要把第三方 jar 提交进仓库或发布物；
> 换版本只需替换 `libs/` 下同前缀的 jar（`fileTree` 按前缀匹配），并同步 `build.gradle` 注释里的版本号。

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
| 基础→高阶 | 锋利 → 高阶锋利 | 锋利 → 高阶锋利 ✅（基础阶恰好就是原版名） |
| 高阶→超级 | **锋利** → 超级锋利 ❌ | **高阶锋利** → 超级锋利 |
| 超级→究极 | **锋利** → 究极锋利 ❌ | **超级锋利** → 究极锋利 |

后两档**中间整整少了一阶**，读起来像是把基础锋利直接推到究极。

**根因是规格写错了**：`README.md` 的「进化型 · 定向进阶」一节原本写「h3: 具体附魔名」——
「具体」被读成「原版附魔」，而它其实应该是 **`from_tier` 那一阶的具体名称**。
实现一字不差地照做了错误规格，所以两边一起错。

**处理**（规格与实现都改）：

- `BookTooltipEvents.targetedTooltip`：h3 改用 `from_tier` 的阶段名；
  `from_tier == NATIVE` 时退回原版附魔名（基础阶没有阶段条目）。
- `README.md` 该节重写，并补了三档示例表。
- 顺带把「父子」这条**口头规格**落进代码（`AscensionLogic.findTargetStageId`）：
  已有记录时目标只能取当前阶段的 `next()` 且其阶级必须等于书本的 `to_tier`；
  基础阶时目标必须是谱系链的**表头**（没有任何阶段的 `next` 指向它）。
  于是「基础→究极」这类跳阶书被拒（对应 §2 被否方案 #8）。

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
| 进化书 · 定向 | 93 → **59**（v3：谱系 × 阶级，全量） | 59 |
| 铭刻书 | 93 → **59**（谱系 × 阶级，各格满级） | 288 → **181** |
| 升级书 | 15（全等级） | 15 |
| 祛咒石 | 3 档 | 3 档 |
| **合计（v2.9）** | **207** | **402** |
| **合计（v3.0 实测）** | **180** | **258** |

> 分层不是「给主标签取样」，而是「隐藏维度只上搜索标签」——主标签里
> **每条谱系、每个阶级都在**，顺序也不串（见 P1-29 的 v2.1 补记）。

#### P1-35 · 分派顺序：左槽是我们的书时，**不能先判合并**

v2.0 的分派是「两本都是我们的书 → 走合并」，而合并只认
「载体书 + 载体书」与「升级书 + 升级书」。于是规格里写明的
**「拿进阶书 / 升级书去推一本书」被整条吞掉**——点上去毫无反应，既不报错也不消耗。

**处理**：两本都是我们的书时，先试 `applyBookAdvance`（载体书 + 进阶书 → 阶级 +1、
载体书 + 升级书 → 条目等级提升），**返回 false 才**落到 `applyBookMerge`。

> 判定信号：**「A 或 B」的分支里，先写的那个条件太宽就会把后一个吃掉。**
> 判据越具体越先判；或者写成「先试专用规则、不适用再退回通用规则」。

#### P1-36 · JEI 判断「同一种材料」用的是 subtype——**默认完全不认数据组件** ⚠️

**实测（v2.5：同一份代码、同一个存档，只切换「注册 / 不注册」）**：

| 场景 | JEI 材料表里的条数 |
|---|---|
| 不注册（JEI 默认） | 进阶附魔书 **1** 条、祛咒石 **1** 条 |
| 注册 subtype 之后 | 进阶附魔书 **384** 条（进化 93 / 载体 276 / 升级 15）、祛咒石 **3** 条 |
| 注册 subtype（v2.7 纳入穿刺后） | 进阶附魔书 **402** 条（进化 96 / 载体 291 / 升级 15）、祛咒石 **3** 条 |
| 注册 subtype（**v3.0 效果总表落地后**） | 进阶附魔书 **258** 条（进化 **62** / 载体 **181** / 升级 15 / 空白 0）、祛咒石 **3** 条 |

本模组的书与祛咒石都是**单例物品**：几百个变体共用同一个 `Item`，只靠**数据组件**区分。
JEI 默认把「同一个 Item + 相同 subtype」的材料压成一条 → 整张材料表里只剩一本书。
原版附魔书能一本本列出来，是因为 JEI 在它的原版插件里**内建注册了同类解释器**；
我们的物品没人替它注册。

**处理**：`compat/jei/UEJeiPlugin`（`@JeiPlugin`）在 `registerItemSubtypes` 里
给两个物品各注册一个 `ISubtypeInterpreter<ItemStack>`；键取自**语义载荷**
（载荷组件走自己的 codec 转成 JSON），而不是整份组件——于是修复费、自定义名字、耐久
这类无关数据不会把同一变体分裂成多条。

**版本与依赖注意**：
- 用 **`ISubtypeInterpreter`**（`getSubtypeData` 返回任意可比较数据）；
  旧的 `IIngredientSubtypeInterpreter` / `NONE` 已标记过时待删，不要再写。
- 已在 **19.44.0.400（用户实例）** 与 **19.57.0.450（开发依赖）** 上确认接口一致。
- JEI 只走 `compileOnly` + `runtimeOnly`，**不进 jar**；`@JeiPlugin` 由 JEI 自己扫描，
  所以专用服务器永不加载本类（P0-16）。

> 判定信号：**「一个 Item + 一堆组件变体」的模组，JEI 那一格大概率只显示一个**。
> 日志里看到 `[JEI] 材料表：… 1 条`，就是 subtype 没注册。

> **补记（v2.7 / v2.9）：加上配方页。** `compat/jei/AnvilRecipeCategory` 把铁砧上的操作做成
> JEI 类别「附魔铁砧」，**从数据包动态生成**，配方上那行字分三类：
> **附魔进阶**（物品 + 进阶书）、**附魔合并**（书 + 书：合并 / 推阶 / 提升 / 转印）、
> **附魔升级**（物品 + 升级书）。
>
> 三条硬规矩：
> 1. **展示栈必须用** `UELookups.enchantmentsForItemWrites(true)`——它们是客户端造的、
>    JEI 作弊模式还能直接塞到玩家手上，用服务端 Holder 就是 P0-17 那个掉线（单机尤其）；
> 2. **中间等级靠槽位轮播**（`addItemStacks`），不要每等级铺一条——条目数会爆；
> 3. **结果调用铁砧那套纯函数算**（`InscriptionLogic.*`），不手写近似值。
>
> ⚠️ **静默拒绝要用数量去发现**：第一版「载体书 + 升级书」把目标阶级传成了目标阶，
> 而升级书只作用于**同阶级**的书 → 62 条配方被 `upgradeEntries` 静默返回空
> （总数 161 → 99）。界面上看不出少了什么，是**配方总数**一眼看穿的。
> 凡是「返回 Optional 的函数批量调用」的地方，都要数一数产出条数对不对。
>
> 运行时校验（可失败）：`[JEI] 配方展示栈编码校验：1092 个栈，失败 0 个（约定 0）`。

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

实测数据包中有 **15/93 个格子 `cap == 1`**（如无限、经验修补），
这些格子的 1 级铭刻书会正确省略数字。
（**v3 后重测：13/59** —— 三系保护超级、摔落缓冲、激流三阶、无限、火矢、多重射击三阶、经验修补。）

> **补记（v2.6）：这条规则最初只落在书 tooltip 上，物品那条路漏了。**
> `TooltipEvents.renderStagedLine` 当时**无条件**拼数字，于是那 5 条「原版上限就是 1」的
> 谱系（经验修补 / 引雷 / 火矢 / 无限 / 多重射击）在物品上被显示成「高阶经验修补 I」，
> 而原版与书都只写「经验修补」——15 个 `cap == 1` 的格子里，书对了、物品全错。
>
> 根因不是「忘了一处」，而是**一条规则写了两份**。现在判定收进
> `StageLookup.displayLevelCap(tier, root, vanillaMaxLevel)`，物品与书都调它。
> 自检两侧边界同时验：上限=1 必须**无**数字、上限>1 必须有数字、
> 上限=1 但等级=2 仍要显示——22/22 通过（**漏了或过头了都会红**）。

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

#### P1-42 · 「基础阶」没有专属语言键——界面按 tierOrdinal 拼名字会显示原始键

阶级专属名是 `enchantment.ultraenchantment.<tier>.<附魔>`，datagen **只为阶段条目生成**，
所以只有 `advanced` / `super` / `ultra` 三套，**没有 `native`** —— 基础阶本来就是原版附魔，
它的显示名就是原版附魔名。

写成 `row.tierOrdinal() >= 0 ? translatable(tier 名) : 原版名` 就错了：
	exttt{NATIVE.ordinal() == 0} 也满足 `>= 0`，于是基础阶那一行会去查
`enchantment.ultraenchantment.native.sharpness`，`getString()` 把**键本身**当显示名返回。
平时只有玩家手动点「基础」时才会看到；**加上「预选」之后每一行原版附魔都是选中态，整屏都是原始键**。

**处理**：只有 `shown != LineageTier.NATIVE` 时才用阶级专属名。判据用 `LineageTier` 而不是序号范围。

#### P1-43 · 「这条谱系现在是什么状态」只能有一份实现（预选与差价共用）

同一件物品上，一条谱系的「当前阶级 + 当前等级」有**两种存法**：

| 谱系状态 | 阶级从哪来 | 等级从哪来 |
|---|---|---|
| 已进阶 | `ascension` 组件的阶段 id → 阶段条目的 `tier` | 组件的 **tierLevel（曲线等级）** |
| 未进阶（基础） | 恒为 `NATIVE` | `minecraft:enchantments` 里的**存储等级** |

两个坑：
1. **已进阶的谱系不能用存储等级**。`AscensionLogic.writeAscension` 刻意把存储等级写成
   「该阶上限」（锋利升阶后存储仍是 5），拿它当基准 ⇒ 曲线等级 8 会被当成「从 0 到 8」来收钱。
2. **基础阶不是「没有记录 = 0 级」**。旧代码用 `currentTierOrdinal == tier.ordinal() ? tierLevel : 0`，
   基础阶的 `currentTierOrdinal` 恒为 −1 ⇒ 现有等级被当成 0 ⇒ 玩家一放物品进去就看见
   `f(5) = 16` 的**凭空差价**，一按灌注就白扣 16 点。

**处理**：`AscensionTableMenu.currentSelectionOf(root)` 是全模组唯一实现（返回
`(tierOrdinal, level)` 或 `null`），**预选**与**差价**都走它。凡是要「物品现在长什么样」的地方都复用它。

#### P1-44 · 预选等级不能向下夹取，灌注的上限也不能低于物品现有等级

物品上的 `tierLevel` 可能**高于当前算得出的上限**：装过神化（超限来自配置文件，关掉就没了）、
数据包把 `max_level` 调小、装了别人的 `level_lock`。

如果预选时把它夹到上限（比如 8 → 5），玩家一开界面那行就变成「5」，一按灌注就把那条附魔
**静默降级**，而且完全看不出为什么。所以：

- 预选照实填，`Math.max(1, tierLevel)`，**不取 min**；
- `apply()` 的等级上限取 `max(该阶级上限, 物品现有等级)`（`ceiling`），让「没有改动」真的判定为没有改动。

#### P1-45 · 一个概念只有一个中文名，且显示值只有一处定义

这一档（`LineageTier.NATIVE` / `tier = 0`）**只有一个中文名：基础**
（基础 → 高阶 → 超级 → 究极，读起来是一条阶梯）。

作者定过规矩：**「设计时才用的词」不许带进游戏当术语。** 本档曾经有过第二个说法
（只在设计与代码注释里用），已**全库统一为「基础」**——代码注释、设计文档、视频脚本一起改的，
不要再造回来。

**处理**：

- 显示值只有一处：`tier.ultraenchantment.native`（进阶台方块、图鉴表头、图书馆能量条与列表全读它）。
  改词只改 `UELang` 那一行 + `runData`。
- **代码标识符不改**：枚举仍叫 `NATIVE`、`CodexData.NATIVE_BIT` 与语言键名 `…native` 也照旧，
  事件对外仍报 `0=基础`（`docs/api.md` 已声明）。改标识符要动存档/网络/API，收益为零。
- 凡是**新增**玩家可见的阶级文案，先问一句「玩家看得见这个词吗？和他已经见过的一致吗？」

#### P1-46 · 「方块亮不亮」的判据必须与服务端的接受判据同源

作者报的现象：**进阶台里所有「基础」方块都是亮的，可点了没反应。**

根因是两个判据各写各的：

| 位置 | 当时的判据 |
|---|---|
| 界面（画方块） | `maxLevels[0] > 0` —— 基础档**恒亮**，根本不看图鉴 |
| 服务端（`selectTier`） | `isUnlocked(root, tier)` —— v4 起基础档也要「见过」 |

⇒ 画成「可选」，点了被拒，且**一点反馈都没有**。

**处理（两条，都要）**：

1. **位的取法只准一处定义**：`AscensionTableMenu.unlockBit(tier)`，
   与 `CodexData` 的掩码同构（进阶三档占 0/1/2 位，基础占 `NATIVE_BIT`）。
   界面读行数据里的掩码，服务端写行数据也用同一个函数。
2. **拒绝必须带原因**：`STATUS_TIER_LOCKED`（图鉴没解锁）与 `STATUS_TIER_MISSING`（数据包没铺）
   是两件事，文案分开。静默 `return` 是这类 bug 的放大器 —— 玩家分不清「我点错了」还是「功能坏了」。

**顺带**：候选列表的判据也踩过同一个坑 —— 见 P1-47。

#### P1-47 · 「预选」选上的谱系，必须在列表里看得见

`#in_enchanting_table` 里**没有**宝藏附魔（经验修补、灵魂疾行、迅捷潜行、冰霜行者…）。
候选列表若只按这个标签取，那么物品上带着经验修补时：预选把它**选中并锁定**，
列表里却**没有这一行** ⇒ 玩家得到一条看不见、也改不了的选择（`清空` 都清不掉它）。

**处理**：候选 = `#in_enchanting_table` 过滤结果 **∪ 物品上已有的全部谱系**。
后者的判据是「物品上有没有」，**不是**「能不能再附上去」—— 已经在物品上的附魔，
按定义就适用于这件物品，不必再问 `canEnchant`。

**教训**：凡是「系统替我做了什么」的功能（预选、自动解锁、自动折算），
都要回头问一句「玩家看得见它做了什么吗」。

#### P1-48 · 「界面显示得付得起」与「真的扣得动」必须同源

v5 把升阶的代价换成了「消耗一本进阶书」，于是同一个判定同时出现在三处：
**GUI 的「N 本 / 缺书」**、**灌注前的校验**、**实际扣减**。三处各写一份，
必然出现「显示有书、点了失败」或「显示缺书、其实能升」。

**处理（三条）**：

1. **策略与判据各只有一处**：`logic/KeyBooks.required`（要几本）与 `KeyBooks.hasEnough`（够不够）。
   GUI、校验、扣减全走它。
2. **扣减顺序必须与判据一致**：判据说「定向优先」，扣减就必须**先扫遍环内所有图书馆的定向书**，
   再扫通用书。写成「逐座图书馆用完再下一座」会让先遇到的那座用通用书顶掉后面的定向书 ——
   判据说够、扣减却拿走了更贵的那本。
3. **聚合值与明细不能混用**：图书馆界面只下发最多 N 条定向明细，
   **总数必须单独下发一个字段**。拿截断后的明细求和，在超过上限时格子里的 `×N` 就会少算 ——
   这是「用明细反推聚合值」的一个具体例子。**只要数据被截断过，聚合值就必须是独立的一份**，
   不能从明细里算。

同类教训见 P1-46（方块亮不亮）与 P1-47（预选看得见吗）——**凡是「系统替玩家算好的东西」，
都要问一句「玩家看到的那个数，是不是真的那个数」。**

#### P1-49 · 长得像按钮的东西必须能点

作者反馈「图鉴切换不了阶级按钮、无法互动」。查下来：**点击链路是通的** ——
上方那排列标题（`基础/高阶/超级/究极`）点了确实会换档。但**每行那四个阶级方块明显更像按钮**，
它们当时只是状态指示、点了没反应 —— 玩家自然会去点它们，然后判定「这功能是坏的」。

**处理**：方块与列标题**都**绑到同一个动作上，并且方块要有**悬停反馈**与**选中态**
（「当前显示的那一档」用选中态画出来），让「点了有反应」这件事<b>看得见</b>。
提示文案也从「点阶级名可切换」改成「点阶级名或方块可切换名称」。

**教训**：**可发现性不是文档问题，是形状问题。** 一个控件只要长得像按钮，
它就必须能点；否则正确的功能也会被判定为坏的 ——
而且这种「坏」在源码里完全看不出来（代码逻辑逐行都正确）。

**验证方式**：不要只看截图（截图证明不了点击）。探针里**真发** `screen.mouseClicked(x, y, 0)`，
再断言屏幕状态变了。这才叫「点过了」。

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

#### P1-50 · 1.21.1 的原料**比不了组件**，写了会被静默忽略

配方的 `key` 里给中心格写 `{"item": "ultraenchantment:curative_stone", "components": {...}}`，
**不会报错**，但那个 `components` 会被丢掉 ⇒ 中心格**吃任意阶级**的祛咒石。

实测（服务端探针，2026-10）：超级配方能吃**究极石**（把玩家做好的东西降级），
究极配方能吃**高阶石** ⇒ **直接跳级，省掉整整一圈哭泣的黑曜石**。

正解：用 NeoForge 自带的自定义原料（`DataComponentIngredient`）：

```json
"Y": { "type": "neoforge:components", "items": "ultraenchantment:curative_stone",
       "components": { "ultraenchantment:curative_tier": "advanced" } }
```

`items` + `components` 必填，`strict` 可选（默认非严格 = 只要求列出的组件匹配）。

**教训**：原料的匹配语义必须用探针**正反两面**钉住 —— 只断言「中心吃高阶石」是不够的，
还要断言「中心**不**吃究极石」。第一版只写了正面断言，跑出来一片绿。

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
│   ├── AscensionTier.java          书的档位（必非基础阶）
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
    ├── LineageTable.java           ★ 谱系单一事实源：26 条常用谱系 + 1~3 阶**绝对数值**（表驱动）
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
- 阶段 JSON **由 datagen 产出**，不手写。26 条谱系 × 1~3 阶 = 59 个文件（v3 起阶级数逐条决定），手写必然出错。
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
- [ ] **「等级数字省略规则」只有一份实现**（`StageLookup.displayLevelCap`），
      物品 tooltip 与书 tooltip 两侧都要验（P1-32 补记）
- [ ] **第三方兼容补丁必须门控**：只在目标模组存在时应用，且 `shouldApplyMixin` 不得抛异常（P1-38）
- [ ] **创造栏只放单条目载体书**：装了 JEI 的客户端里应看到 `[JEI] 创造栏多条目载体书 0 本`（多条目只由玩家在铁砧合并产生，见 P1-37）
- [ ] **装了 JEI 的客户端里 `[JEI]` 行的条数 = 变体数**（塌成 1 就是 subtype 没注册，P1-36）
- [ ] **写进物品组件的 Holder 来自「生成它的那一侧」注册表**（`UELookups.enchantmentsForItemWrites`）——
      单机掉线的头号成因（P0-17）；写完就不会再动它
- [ ] **运行期包（content / logic / event / registry）没有 import `datagen.*` 或客户端类**
      （`net.minecraft.client.*` / `neoforge.client.*`）——这是「服务端掉线」的经典成因（P0-16）
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
      （基础阶那一端退回原版附魔名），两端都是**单一键**、不是拼接（P1-30）
- [ ] 定向进阶书的 `to_tier` 与 `from_tier` **父子相连**——跳阶书必须不生效（`AscensionLogic`）
- [ ] 定向进阶书**两个标签都全量投放**（它只有「谱系 × 阶级」两维，没有需要靠搜索的隐藏规格）

---

## 7. 待办与开放项

| # | 项 | 状态 |
| # | 项 | 状态 |
|---|---|---|
| 1 | 各阶级的进阶门槛 `required_level` | 已给出初值（`UEStages.TIERS`：5/4/3，实际生效值再取 `min(它, 上限)`），**仍待作者微调**。`max_level` **一律沿用根源附魔原版值**，不在可调项里 |
| 2 | 各谱系的深度（哪些写到超级就停、哪些到究极） | 已定为统一三阶（高阶→超级→究极）；要单独截断只需把该谱系阶梯的尾部补丁去掉 / `next` 留空 |
| 3 | 书的获取途径（合成 / 战利品 / 交易） | **设计空白，且 v5 起它变成了瓶颈**——进阶书不再是「多一种攒点数的方式」，而是**升阶的唯一代价**（没有书就升不了阶）。四种物品目前只能从创造栏、村民交易与战利品表获得，见 [规格 §12.1 R1](docs/book-system-spec.md)。**祛咒石不在此列**——三条合成配方已于 2026-10 落地（见 §8） |
| 4 | 各谱系的阶位加成数值 | 已给出一套初值（`LineageTable` 内就地可调），**待实际手感调整** |
| 5 | `fortune` / `silk_touch` / 两个诅咒的进阶 | **刻意不生成阶段条目**（理由见 `LineageTable` 类文档）。前两者的行为由单点查询驱动，阶段条目对它们不生效；要做需改结算/掉落层代码，不是数据包 |
| 6 | **跨阶一次跳收几本进阶书** | **待作者拍板**。当前 = 1 本（严格按 v5 规格 §4.1）⇒ `基础→究极` 比逐级走便宜。三个候选（维持 / 跨几档收几本 / 禁止跳阶）与改动量见 [`docs/library-energy-model.md` §6.3](docs/library-energy-model.md)，代码里 `KeyBooks.required` 是唯一开关 |
| 7 | v4 旧存档的「阶级单位」 | **刻意丢弃**，不换算。`level|` 保留、`ascension|` 丢弃。若将来真有玩家存档需要迁移，再单独设计 |
| 8 | **两个配方文件：已定稿，此后不得再动** | ✅ ① `advanced_enchantment_library_from_apoth.json`（8 哭泣的黑曜石 + 神化魔咒图书馆 → 我们的进阶附魔图书馆，`mod_loaded: apothic_enchanting` 门控）；② `ascension_table.json`（8 经验瓶 + 高级灌注台 → 进阶台，`enchantinginfuser` 门控）。**作者明令二者都锁死，不准再有任何相关改动**（含「优化」「顺手统一」）。详见 §8 变更日志 |
| 9 | `textures/gui/ascension_table.png` 下沿**透明** | **待作者定夺，未擅自修改**。实测（Pillow）：y=0..167 满宽 340 不透明，**y=168..235 只有 x=78..259 不透明**，其余全 alpha=0，且没有收尾的暗边。⇒ 进阶台界面下半部**左右两侧能直接看到世界**。生成器 `docs/gen-gui-textures.py` 画的是满幅面板（`panel(img,0,0,340,236)`），所以现在这份是**手绘版**（该脚本有「已存在且内容不同就 SKIP」的防覆盖保险）。若本来就是想要「浮起来的物品栏底板」则无需处理 |
---

## 8. 变更日志

| 日期 | 变更 |
| 2026-10 | **三条祛咒石配方**（作者给的形状）。祛咒石是**一个物品 + `curative_tier` 组件**的三个形态，所以三条配方都是 `XXX/XYX/XXX`：① **高阶** = 8 × 附魔之瓶 围 **石头标签**（`c:stones`，已核实 NeoForge 确实有这个物品标签）；② **超级** = 8 × 哭泣的黑曜石 围 **高阶**祛咒石；③ **究极** = 8 × 金块 围 **超级**祛咒石。产出用 `result.components` 指定阶级。**⚠️ 本轮踩到并已记进 §3 的坑（P1-50）**：1.21.1 的原料**比不了组件** —— 中心格写 `{"item":..., "components": {...}}` **不报错但被静默忽略**，中心照样吃任意阶级 ⇒ 究极配方能拿高阶石**跳级**（省一圈哭泣黑曜石），超级配方能把究极石**降级**。改用 NeoForge 自带的自定义原料 `{"type":"neoforge:components","items":...,"components":{...}}`（`DataComponentIngredient`）后恢复正常。**注意这个坑是探针抓到的**：第一版只断言了「中心吃对的那个阶级」（全绿），补上「中心**不**吃其它阶级」的反例才暴露。**验证**：服务端探针 **17/17**（三条配方存在、产出阶级正确、材料数量 8/1、`c:stones` 覆盖石头与深板岩且不吃泥土、三条跨阶级反例）。**副产品**：为它临时写的自定义原料类已删除（NeoForge 自带的不必自己造）。
| 2026-10 | **宝典产出改为「我们的载体书」+ 文档整理**。**① 产出改写**：神化三类宝典原本产出**原版附魔书**（只带 `minecraft:enchantments`），进阶身份要么丢、要么只能以「原版书 + 我们的组件」这种四不像存在。现在分两条路：**幸存条目全是进阶的** ⇒ 产出换成**我们的载体书**（`advanced_enchanted_book` + 铭刻载荷；图书馆/铁砧/剩菜机制里的一等公民，零丢失），**跨多个进阶阶级**时条目最多的一组占产出、其余各阶级在取件时各补一本（复用既有的「剩菜书」机制）；**夹杂基础阶** ⇒ 产出保持原版附魔书（基础阶没有别的家），但把进阶身份写进 `ascension` 组件带走。全基础阶时**完全不改写**，神化行为零变化。**② 取件逻辑收成一条**：「武器上有、却不在产出里的进阶记录 ⇒ 各补一本铭刻书」——一条规则同时覆盖「被拆解宝典随机丢掉的那一半」（作者方案 B）与「跨阶级没装进产出的那部分」，**不用反推神化的随机数、不用跨事件存状态**（判定全部现场可读，沿用 `AnvilTakeEvents` 的「同一份输入重跑一遍」）。**⚠️ 顺带否掉一条看似省事的路**：「让载体书直接承载基础阶」——`BookSpecs.Inscription.tier` 是**书级单值**，改它要动 `mergeBooks`（合并要求同阶级，改后基线阶+高阶合并成什么？）与**组件 codec**（存档格式），代价远大于收益。**③ 已知缺口（未修，等作者定）**：混合情形产出原版附魔书，而 `LibraryMenu` 存入分支只看「是 `enchanted_book` + 有附魔」⇒ **不读 `ascension` 组件**、一律按基础阶入库，进阶身份会在存入时丢。修法二选一：存入分支认组件（小），或上面那条（大）。**验证**：服务端探针 **29/29** —— 全进阶→载体书、跨阶级补发、混合→原版书+组件（含「组件里没有基础阶那条」的反例）、全基础阶不改写、提取清武器、拆解返还。**④ 文档整理**（作者要求「保持工程向纯洁性」）：§7 删掉已完成条目（祛咒石贴图、代理确认）并重新编号、把配方锁死写成一条；头部陈旧的「v4 提案·尚未动代码」改成实情（v5 起已是书库存+等级单位）；README「配方与战利品尚未接入」改成实情；`docs/ascension-effects-v3.md` 的「阻塞 3 处」改成「均已落盘」并压缩。
| 2026-10 | **`ascension_table.json` 一并锁定**（作者追加明令）。至此**两个配方文件都被锁死**：`advanced_enchantment_library_from_apoth.json`（8 哭泣黑曜石 + 神化魔咒图书馆 → 我们的进阶附魔图书馆）与 `ascension_table.json`（8 经验瓶 + 高级灌注台 → 进阶台，`enchantinginfuser` 门控）。**此后不得再有任何相关改动**，理由见 §7 第 11 条。 |
|---|---|
| 2026-10 | **配方定稿并锁死（作者明令，写进 §7 第 11 条）**。作者原话：「附魔进阶台只有 `advanced_enchantment_library_from_apoth.json` 是正确的，不准再有任何相关改动了」。最终口径：**8 × 哭泣的黑曜石（`minecraft:crying_obsidian`）＋ 神化的「魔咒图书馆」（`apothic_enchanting:library`）→ 我们的「进阶附魔图书馆」（`ultraenchantment:advanced_enchantment_library`）**，文件 `advanced_enchantment_library_from_apoth.json`，带 `mod_loaded: apothic_enchanting` 门控。**我此前连续读错两次**：第一版把**产出**写成「进阶台」；第二版把作者那句「让你新加的配方**结果是**进阶附魔图书馆」理解成在说**材料**，又把 Y 换成了我们自己的图书馆。多出来的 `ascension_table_from_library.json` **已删除**。**未动** `ascension_table.json`（「经验瓶×8 + 高级灌注台」，早于本次改动、带 `enchantinginfuser` 门控）—— 按「不再有任何相关改动」保持原样；若作者的意思是连它一起删，需要明确指示。**此后任何人不得再改这一块配方。** |
| 2026-10 | **配方改对 + 神化宝典兼容（v7 计划第 5 步 + 修正）**。**① 配方（作者两次澄清后的最终口径）**：`XXX / XYX / XXX`，X = **哭泣的黑曜石**，Y = **神化的「魔咒图书馆」**（`apothic_enchanting:library`），**产出 = 我们的「进阶附魔图书馆」**。⚠️ 这一条我读错了两次：第一版把产出写成了「进阶台」，第二版又把 Y 换成了我们自己的图书馆 —— 作者的原话是「让你新加的配方**结果是**进阶附魔图书馆」，指的正是**产出**。新增 `advanced_enchantment_library_from_apoth.json`（带 `mod_loaded: apothic_enchanting` 门控，注册名已用对方 jar 的 `blockstates/library.json` 核实）。**同时保留** `ascension_table_from_library.json`：8 哭泣的黑曜石 + **我们自己的**进阶附魔图书馆 → 进阶台 —— 它正好是链条的下半段（神化图书馆 → 我们的图书馆 → 进阶台），也对应作者第一条「（魔咒）图书馆作为附魔进阶台的配方」的说法。旧的「经验瓶 + 灌注台」配方仍保留。**验证**：服务端探针 **8/8** 直接查 `RecipeManager` —— ① 配方存在、产出是 `ultraenchantment:advanced_enchantment_library`、材料里既有哭泣的黑曜石也有 `apothic_enchanting:library`；② 下半段存在、产出是进阶台；③ 没装灌注台时旧配方**确实不加载**（条件门控有效）。**② 神化三类宝典兼容（新）**：`compat/apotheosis/ApothTomes`（**只查模组清单**的入口）+ `ApothTomesImpl`（真正 import 神化类），用 **`EventPriority.LOW` 挂同一批铁砧事件做后置处理**，不碰他们的类结构。解决的三件事全是**静默吞资产**：(a) 三类宝典产出都是**原版附魔书**、只搬 `minecraft:enchantments` ⇒ 进阶记录不跟着走 —— 现在只把**幸存**附魔的进阶记录搬进产出书；(b) **提取宝典**的 `updateRepair` 会清空武器附魔再把武器还给你，但我们的组件留在武器上 ⇒ **白送一次进阶** —— 现在一并清掉；(c) **拆解宝典**丢掉一半附魔，被丢那半的进阶记录按作者选的**方案 B 返还成铭刻书**（`BookFactory.inscription`，与图书馆取出的完全同一种东西；不用定向书，因为定向书只能推**未进阶**的附魔，对不上「拿回原来那一阶」）；**高级拆解 / 提取全留 ⇒ 一条都不返还**（反例已断言）。另有 `mixin/compat/TomeItemMixin`：9 种装备宝典的 `use` 会新建一本原版书、把我们的组件丢掉，在返回处补上。**验证**：服务端探针 **20/20** —— 三种宝典走**真实 NeoForge 事件**（`AnvilUpdateEvent` / `AnvilRepairEvent`），断言产出书带上阶级与曲线等级、武器组件被清、拆掉的那半返还 1 本铭刻书、全留时零返还。**⚠️ 又是探针自己写错**（第六次）：我用 `super/protection` 造测试武器，但**数据包里 protection 只有 `advanced` 一阶** ⇒ `orElseThrow` 抛 `NoSuchElementException`。**这条顺带暴露一个数据包问题：protection 没有 super / ultra 阶段**（`glob` 实证只有 `advanced/protection.json`）—— 是漏铺还是有意为之，请作者看一眼。

| 2026-10 | **进阶台的悬浮书（原版同款）**。做法是**照抄原版附魔台那一套**，一行公式都没有自己发明：① 新增 `AscensionTableBlockEntity`，字段与 `bookAnimationTick` **逐字**取自 `EnchantingTableBlockEntity`（`time / flip / oFlip / flipT / flipA / open / oOpen / rot / oRot / tRot`）；② 新增 `AscensionTableRenderer`，照抄 `EnchantTableRenderer` —— 用原版 `BookModel`（`ModelLayers.BOOK`）+ **原版贴图** `minecraft:entity/enchanting_table_book`（走方块图集，**不新增任何美术**），浮沉 `0.1 + sin(t*0.1)*0.01`、`rotZ = 80°`、翻页 `frac(flip±0.25)*1.6-0.3` 全部照抄，连 `getRenderBoundingBox` 都抄（少它的话，方块被视锥剔除时书会跟着一起消失）；③ ticker **只在客户端**返回 —— 原版就是这个三元判断，悬浮书是纯客户端装饰，服务端零开销、不需要同步。**「进阶台故意没有方块实体」这条设计没有被推翻**：新 BE **依然不持有任何物品**，物品槽仍由菜单自持、关界面照样还给玩家；它存在的唯一理由是动画需要**跨帧累积**的状态（开合 / 翻页 / 转向都是逐帧插值的，没法从当前时间直接算出来）。**实测**：探针在玩家面前 2 格放台子，客户端日志 `time=140 open=1.0 rot=-1.5707963 flip=4.74977` —— `rot = -π/2` 正是 `atan2(dz, dx)` 对「玩家在 -Z 方向」算出的值，`open` 升到 1 也符合原版「半径 3 内有玩家就翻开」；截图可见台子上方**摊开的书页朝玩家**。**⚠️ 两个坑**：(a) 探针第一次把台子放在**正好 3.0 格**外，`open` 恒为 0（书合着）—— 原版判定是 `getNearestPlayer(..., 3.0, false)`，得放 2 格才在半径内；(b) 第一次截图是在 `ScreenEvent.Render.Post` 里抓的，那个事件**只在开着界面时触发**，拍出来整屏是界面背景的模糊版 —— **抓世界画面必须从 tick 里抓**（GUI 截图才用 Render.Post）。**⚠️ 旧存档**：给已存在的方块补方块实体不会自动发生，升级前就摆好的进阶台可能看不到书 —— 拆掉重放一次即可（其余功能不受影响）。

| 2026-10 | **附魔台「进阶」机制 + 神化适配（v7 计划第 1–4 步）**。**① 配方**：新增 `ascension_table_apoth.json` —— 8 哭泣的黑曜石环绕**神化的「魔咒图书馆」**（`apothic_enchanting:library`，注册名已用对方 jar 的 `blockstates/library.json` 核实），带 `mod_loaded: apothic_enchanting` 门控。**旧的「8 经验瓶 + 高级灌注台」配方我保留没删** —— 删配方是破坏性的，而且删掉之后没装神化的玩家就完全合不出进阶台；要删说一声。**② 概率模型**：新增 `logic/AscensionChance`（纯函数，两条路径共用）+ `UEConfig`（COMMON 配置）。无神化 = 每点附魔能力 × {0.1%, 0.2%, 0.4%}（按玩家点的第 1/2/3 行）；神化 = × {0.1%, 0.25%, 0.5%} ＋ 每点阿卡那 +0.02%（绝对）再 × 量子稳定 ×1.5；进阶**条数**按位阶分档（≤30→1；<45→1..2；<90→1..3；≥90→2..3，45 这个边界我明确归到「1..3」）；**等级**无神化保持原级、神化按量子化在 `[1, 位阶/3]` 内随机（作者没给范围，默认值在配置里，`levelDivisor` 可调）。**③ 应用层**：新增 `logic/TableAscension` —— 附魔前后各拍快照、diff 出「本次写上或抬高的」，只留**可进阶**的，掷概率后**每次只进一阶**，写回统一走已有的 `AscensionLogic.writeAscension`（进阶台/铁砧同一条路径，不另写一份）。**④ 两个注入点**：无神化走 `mixin/EnchantmentMenuMixin`（进主 `ultraenchantment.mixins.json`）；神化走 `mixin/compat/ApothEnchantmentMenuMixin`（进 compat 配置 + `UECompatMixinPlugin` 按 `apothic_enchanting` 门控）。**为什么不用 `PlayerEnchantItemEvent`**：它确实在附魔写入后触发，但**不带行号**，而概率按行分 —— 所以两边都注入 `clickMenuButton` 的那个 `int`，与神化路径对称。**⚠️ 关键坑**：神化的 `ApothEnchantmentMenu` 虽然 `extends EnchantmentMenu`，但它**重写了 `clickMenuButton`** ⇒ 打在父类方法上的注入**不会**跟着跑，必须单独注一次（否则「装了神化就失效」，而且不会报错）。**验证**：服务端探针 **32/32** —— 概率三档、阿卡那/量子稳定、封顶 1.0、**无神化不吃阿卡那**的反例、条数分档（含「位阶 120 见过 3」）、应用层（命中 1 条 + 高阶锋利 + **等级保持 3**）、连进两阶（高阶→超级）、神化随机等级落在 `[1,位阶/3]`，以及四条反例（不可进阶的附魔一条都不进、附魔能力 0 不触发、已是究极不再进、快照没变不算本次新增）；**mixin 应用由 `runServer` 正常启动证明**（注入点错会因 `defaultRequire: 1` 直接启动失败，不会静默）。**⚠️ 本轮又出了 5 条假 FAIL，还是「测试自己算错」**（第五条）：把 `minecraft:mending` 当成不可进阶的（它在数据包里）、神化用例 power 只给 60（概率 0.3，不是必中）—— 前者改成**现场扫一个不可进阶的附魔出来**，不写死。**未做（等确认）**：计划第 5 步「拆解/提取宝典兼容」取决于返还策略，以及第 2/3 条的「附魔宝典」到底是哪个物品。

| 2026-10 | **图书馆 v6：一行一条谱系 + 行内阶级/等级 + 取出不再吞书（本轮，作者三条反馈）**。**① BUG：切换条目会吞掉已产出的书。** 根因在 `LibraryMenu.extract`：它把「已经取到几级」这个状态**藏在输出槽那本书里**（`currentOutputLevel` 读输出槽），然后 `output.setItem(...)` **直接覆盖** —— 玩家点一下别的条目，那本书连同已经花掉的等级单位一起消失，界面上毫无提示。**修法不是加一句判断就完事，而是把那个隐藏状态删掉**：v6 改成「行内选好阶级与等级 → 一次性付款 → 得到一本」，每次取出都是独立的一笔买卖。另加两道保险：输出槽非空时**拒绝**新的取出（`STATUS_OUTPUT_BUSY`，红字「先把它拿走」）；关界面时 `clearContainer(player, output)` 本来就会把输出槽还给玩家（已确认，不用改）。**② 条目很多、中间空间没用起来。** 根因是**一行 = 谱系 × 阶级**，一条附魔解锁四档就占四行。改成**按谱系去重、一行一条**，中间那段空白放「**阶级 −/+**」「**等级 −/+**」和「**取出**」小按钮。阶级 +/− 只在**图鉴解锁 且 数据包真有这一阶**（`max_level > 0`）的档之间循环 —— 少任何一个条件都会切到「上限 0、永远取不出来」的死格；等级夹在 `[1, 该档上限]`。**余量检查**：行内右侧直接显示花费（`N 点`，不够标红）、取出按钮画成不可用态、服务端扣减前再查一次（界面 / 按钮态 / 扣减**三处同源**）。**③ 取出按钮 + tooltip。** 用 `widgets.png` 里现成的 `SQUARE`(14×14) + `ARROW_DOWN`，**不新增美术**；悬停显示「取什么 / 花多少 / 图书馆有多少 / 取完剩多少」，不够时红字「还差 N 点」。**顺带**：点一行不再直接取出（那正是误触的来源）。**取出的定价也简化了**：`花费 = f(等级)`，与原来「一级一级点、只付边际差」的**总价完全相同**（`f(1) + (f(2)−f(1)) + … = f(n)`），但不再需要「降级退还」这种只因为隐藏状态才存在的路径。服务端探针 **29/29**：去重成一行、默认最高档、阶级−切档后余额跟着变、等级上下限夹取、取出扣 4 点并产出高阶 3 级书、**第二次取出被拒且书还在、一分没扣、切档也不动它**、拿走后再取出正常、余量不够时状态 3 且零扣减零产出、连点 40 次等级不越界。**⚠️ 本轮未完成的验证**：客户端截图探针在 `gradlew --stop` 之后重启客户端时**卡住没起来**（等了 3 分半、零输出、零截图）。因此**图书馆新界面的外观尚未截图验证** —— 布局常量、四个调节钮与取出钮的位置、tooltip 换行都只过了编译与服务端数据校验。**下一轮第一件事：先 `runClient -PueGuiProbe` 把图书馆拍下来量一遍**（重点：240px 行宽里塞进 5 组控件之后的间距与右边界）。见下一条的补充。

| 2026-10 | **图书馆 v6 微调 + 一次「没查清就上报」的异常（作者两条反馈）**。**① 去掉「已取出」提示**：取出成功时书就在输出槽里，玩家看得见，不需要一行字；只有*被拒*才需要解释。`status.ok` 键一并删除（不留死键）。**② 「取出」按钮的箭头图标上移 3px**：`GuiRender.arrow()` 内部还会再 `+4`，所以传 `rowY + EXTRACT_DY`（原来是 `+3`）才是垂直居中。**③ 截图验证（补上一轮欠的）**：客户端这次正常起来了，两张图都拍到 —— **行内布局成立**：`究极锋利 ｜ − 究极 + ｜ − 3 + ｜ 4 点 ｜ [取出]`、`保护 ｜ − 超级 + ｜ − 5 + ｜ 16 点(红) ｜ [取出(灰)]`，表头三列（附魔/阶级/等级）都在，取出按钮在买不起时画成不可用态。**⚠️ 但发现一个我没查清的异常**：图书馆界面里 tooltip 只画出**底色方块**、**看不到文字**，而**进阶台的两处 tooltip 用同一个 `graphics.renderComponentTooltip` 画得好好的**。证据：暗块尺寸 133×35 逻辑像素，正好等于我那条最长文案的宽度；但它出现在 `(170, 82)`，而探针给的悬停坐标是 `(314, 95)`（取出按钮中心），且 `renderStockTooltip` 的命中判定我读过 —— 它只认书库存那两行格子，不会画在这里。两种可能我**没能在一轮内区分开**：(a) tooltip 其实画对了、只是 `Screenshot.grab` 抓帧抓在文字批次 flush 之前（进阶台那两张就没这个问题，所以存疑）；(b) 另有一处用**真实鼠标坐标**画 tooltip 的地方在空内容上也画了底色。**下一轮用一个实验就能分开**：在探针里把悬停设成「取出按钮」的同时，把真实鼠标坐标也强制到同一个点；若暗块跟着跑到 (314,95) 就是 (b)，若仍在 (170,82) 就是 (a)。**在查清之前，不要对外宣称图书馆 tooltip 可用。**

| 2026-10 | **进阶台花费列：改名 + 重排 + 悬停明细（本轮，作者三条反馈）**。**① 「钥匙」这个词不符合语境** —— 那是设计内部打的比方；玩家要的是**进阶书**这个实物。界面上这一列改叫「**书耗**」，和右边的「级耗」成一对。**只改显示值**（`UELang` 一行 + `runData`）；代码标识符 `keyBook` / `KeyBooks` / `F_KEY_BOOK` 与设计文档里的「钥匙」**一律没动**（上一轮的教训：作者要的就是改本地化，不要顺手全库改名）。**② 两列跑到面板外面了** —— 这是**我上一轮自己改出来的回归**：当时为了让「缺 3 本」不顶到加号按钮，把两列各右移 8（290/318 → 298/330），**只量了「离加号按钮的间隙」、没量右边界**，于是级耗整列右沿 = `left + 330 = 383`，正好压在面板边框上（面板就是 340 宽）。**最要命的约束**：面板背景是 **1:1 整图**（槽位/列表底都烘焙在 PNG 里，见 `docs/gui-textures.md`），**宽度改不了** —— 而那些贴图是**作者手绘的**，`docs/gen-gui-textures.py` 开头就写着防覆盖保险（2026-10-07 重跑把美术冲掉过一次）。**所以在 340 里重排**：把「缺 N 本」（32px）取消，缺的时候也写「**N 本**」但用**红字**（和右边级耗付不起标红是同一套视觉语言；缺多少、还剩多少交给悬停明细），左组整体左移 8（名字 88→84、方块 96→92、等级 −/+ 212/228/244 → 208/224/240），两列落到 **279 / 319**。逐像素实测：面板 `43..383`、列表底 `53..373`，级耗右沿 = `left+319 = 372` ⇒ **距面板边框 11 逻辑像素**（此前是 0）；书耗最坏情况「3 本」19px 起于 313，**距加号按钮 6 逻辑像素**。**③ 花费列悬停明细（新）**：鼠标停在「书耗」或「级耗」格上给出 —— **具体消耗什么**（`需要 3 本 · 究极进阶书`）、**环内图书馆的总储备**（`环内图书馆共 2 本（定向 0 · 通用 2）`）、**这一笔之后还剩多少**（`还差 1 本` / `这一笔之后剩余 128 点`）。为此 `ContainerData` 每行加三个字段（定向 / 通用 / 等级单位储备），`DATA_STRIDE` 11→14，全部由服务端算好下发 —— **客户端依旧不碰方块实体**（v5 §12.4）。服务端探针 **24/24**（含跨阶缺书、灌注后储备刷新、同阶级提级书耗为 0、扣减后各桶数值）。**⚠️ 两条本轮的坑**：(a) 中途有 **3 条假 FAIL**，根因是探针没把该阶级在**图鉴里解锁**，`selectTier` 直接拒绝 ⇒ 读到的还是上一次的选择 —— **第四次「测试自己算错」**（前三次见 v4 实现⑩/⑬、v5 复跑）；(b) 第一版 tooltip 截图里**只有底色、没有文字** —— 因为我在 `ScreenEvent.Render.Post` 里反射调用渲染，drawing 状态与真实路径不同；改用**临时调试钩子**走 `render()` 内部才拍到正确画面（探针与钩子已全部删除）。结论写进铁律：**tooltip 类的东西必须在真实渲染路径上验，不能在事件回调里"补画一下"就认定它对。** **探针与条件块全部删除，grep 零残留；`runData` 首跑 `written: 2`、二跑 `written: 0`。**

| 2026-10 | **图鉴解锁语义 + 图鉴交互 + 物品缓存（本轮，作者四条反馈）**。**① 高阶级自动解锁其下级所有阶级**：`CodexData.with(root, tier)` 改成**向下闭包**（新增 `CodexData.maskUpTo`）——解锁究极 ⇒ 基础 / 高阶 / 超级 / 究极全亮。理由：拿到一本「究极锋利」当然意味着你知道「高阶锋利」是什么；只点亮一格会出现荒谬情形——**物品带着究极锋利，却选不了它的高阶档**。基础阶也在内（见过这条谱系的任何一档，就没道理还说「没见过它本身」）。**② 图鉴切换阶级点不动** —— 根因是**一屏里最像按钮的东西不能点**：四个阶级方块画在每一行上、明显是按钮的样子，但当时只有上方那排**列标题**能点。现在方块也能点，「当前显示的那一档」的方块用选中态画出来（解锁的才亮），列标题照旧高亮，提示改成「点阶级名或方块可切换名称」。**⚠️ 验证方式不是看截图，而是真发鼠标事件**：探针直接 `screen.mouseClicked(x, y, 0)`，断言点列标题 → `shownTier=高阶`、点行内方块 → `shownTier=究极`，**两条都 PASS**（说明点击链路本来就是通的，作者点的是方块）。**③ 放入已有附魔的物品时不点亮对应阶级** —— 根因是**「判定与显示不一致」的第三次变体**：预选**确实**把物品的阶级填进了选择表（`F_TIER` 有值），但界面画方块看的是**图鉴解锁位**；玩家没「见过」这条谱系的书 ⇒ 方块画成暗的，看起来就是「没点上」。**④ 新功能：物品缓存**（`AscensionTableMenu.itemUnlocks`，`rebuildSelections` 里重算）。放进进阶台的物品，**它自己带着的每条谱系**，其所在阶级**及所有下级阶级**一律视为可选（与图鉴取**并集**、**不写回图鉴**）。这样「我手里这把剑带着高阶锋利，可我就是改不了它」不会再发生；换一件物品即重算。**反例都验过**：物品上**没有**的谱系掩码仍为 0、点它仍被 `STATUS_TIER_LOCKED` 拒；物品缓存**不会**写进图鉴（`CodexData.isEmpty()` 仍为 true）；解锁高阶**不**解锁超级/究极；别的谱系不受影响；图鉴全空时也能把物品上已有的条目从高阶 3 级降到 1 级并成功灌注（状态码 OK）。**服务端探针 32/32**。**⚠️ 又双叒踩 runData**：改完 `codex.hint` 文案直接开客户端，截图里提示还是旧文案——**第四次踩同一个坑**（v4 实现⑤、v4 实现⑩、v5 复跑各一次）。**加语言键 = 必须 runData**，这条不写进铁律就永远会再犯。**探针与条件块全部删除，grep 零残留；`runData` 首跑 `written: 2`、二跑 `written: 0`。**

| 2026-10 | **图书馆能量模型 v5：阶级书库存化（本轮）**。作者拍板：**把「阶级单位点数」换成「阶级书库存计数」**，并**关闭跨阶级兑换**。**为什么**：点数把「一把钥匙」拆成了「五枚硬币」——玩家会算「我还差 3 点」，而进阶书的直觉是「我有这本就能跨过去」；4:1 向上兑换又把低阶书的充裕直接变成高阶书的供给，稀缺性被抹平；更根本的是 v4 后期为了「一步到位不比一步步走便宜」加了一条**手写的守恒律**（`Σ 跳过每档价 + Σ 中间阶级满级价 + 目标价`）——**一条需要手写才能维持的守恒律，通常说明模型选错了：钥匙天然守恒，被拆成硬币的东西才需要定价规则去缝**。**触发点是一条硬证据**：同一件事当时有**两套经济** —— 铁砧「用掉那本书」，进阶台「花阶级单位点数、不需要书」⇒ 只要存在一条不用书也能升阶的路，**书就不再是钥匙，而是一张打折券**。**做了什么**：① 删 `EnergyFamily` / `EnergyKey` 两个类（只剩一个家族时键退化成阶级本身，留着只会让人以为还有第二个家族），`EnchantmentLibraryBlockEntity` 换成 `int[4]` 等级单位 + 通用书 `int[4]` + 定向书 `Map<阶级, Map<谱系,int>>`（**8 个桶 → 4 + 2 张表**）；② 存入：铭刻 / 升级书仍折算等级单位（`f(L)`、升级书 ×4），**进阶书改成记一本库存**（定向 / 通用分开记），`toTier == NATIVE` 的进阶书 **WARN + 整本拒绝**；③ 升阶改成**消耗一本进阶书**——判定集中在 `logic/KeyBooks`（`required` 是唯一策略点、`hasEnough` 是唯一判据），**优先消耗定向书、其次通用书**；跨多座图书馆时「先扫遍环内定向、再扫遍通用」，**不是**逐座用完（否则先遇到的那座会用通用书顶掉后面的定向书）；④ 删掉 `ascensionPrice` / `tierCoefficient` / `GENERIC_ASCENSION_BASE` / `EXCHANGE_RATE` / `exchangeYield` 与 4:1 兑换，以及 v4 的「完整路径定价」和「中间阶级满级价」；⑤ 图书馆界面：能量条 2 行 → 1 行（等级单位）+ 新增「进阶书」行（格子 `×N`，**悬停看通用 / 定向明细**，明细与总数分开下发——明细是截断的，拿它求和会少算）；⑥ 进阶台花费列：「阶耗」→「**钥匙**」，显示「N 本 / 缺书」（红字），新增 `STATUS_NO_KEY_BOOK = 10`（8/9 已被占用；状态码是发给客户端的**协议值**，不为了对齐文档去改旧值）；⑦ 库物品 tooltip 改读 v5 格式。**旧存档**：`energy` 里的 `level|<tier>` 照旧读进来，`ascension|<tier>` **直接丢弃**（点数是「元」、书是「本」，无损换算不存在；开发期无真实存档，已在 §7 标注）。**验证**：服务端探针 **47/47**，逐条覆盖规格 §10 的 11 条，含反例 —— 定向锋利书**升不了**保护、两种书都没有时状态码 10 且**物品不被写、库存不变**、`toTier=native` 整本留在输入槽、NBT 往返逐一还原、v4 旧存档 `level|super=123` 保留而 `ascension|ultra=999` 丢弃、等级单位存取与 v4 逐分一致（取出 +1 级只付边际差）。客户端截图 2 张 + Pillow 逐像素：「1 本」右端 **682** /「+124」左端 **694** ⇒ 间隙 12px 不重叠；「缺书」右端 681 /「4」左端 730；图书馆面板 **516×468 物理 = 258×234 逻辑**，等级单位行 4/12/4/0、进阶书行 —/×5/×1/×0 与注入值逐一吻合。**⚠️ 本轮又踩一次「测试自己算错」**：NBT 用例里把前面用例的**增量**当成绝对期望，出了 1 条假 FAIL —— 改成「前后快照对比」后 47/47（这是本项目第三次同类失误）。**⚠️ 遗留缺口（规格未拍板）**：当前「跨阶一次跳只收一本目标阶级的书」⇒ `基础→究极` 只花 1 本，而逐级走要 3 本，**跳阶更便宜**；v5 取消了那套定价公式之后，没有任何东西在阻止它。`KeyBooks.required` 是唯一开关，改成 `Math.max(0, to - from)` 即「跨几档收几本」，GUI 与扣减已按数量处理、不用动别处；已写进 `docs/library-energy-model.md` §6.3 与代码注释。**⑧ 作者拍板（同日追加）：跨阶跳跃采用「方案 B —— 跨几档收几本」**（`KeyBooks.required = Math.max(0, to - from)`）：基础→究极收 **3 本**，**与逐级走等价**（各 1 本 × 3 = 3 本），跳阶不再便宜；只有 1 本究极书时跳阶会**失败**（需要 3 本），玩家要么攒够一次跳、要么逐级走 —— **两条路等价，玩家自己选**。否决了「维持一本」（把已知的定价倒挂留在系统里）与「禁止跳阶」（那是改玩法不是修定价，且与 `next` 链允许跳过的语义冲突）。UI 与扣减本来就按数量处理，只改了策略那一行；顺带把「缺书」改成「**缺 N 本**」（跨阶要 3 本时，只写「缺书」玩家分不清还差几本）。**⚠️ v5 升级后，升级前就躺在背包里的图书馆物品 tooltip 会显示为空**（方块本体在加载时已迁移，只有「物品形态」没做 v4 回退）—— 这是**开发期一次性代价，不是 bug**，不做回退；将来有人报「tooltip 空了」，先看这一条。**⑨ 复跑时又抓到两处只有截图能看见的问题**：(a) 新增 `book_short` 语言键后**忘了跑 `runData`**，界面上直接显示原始键 `container.ultraenchantment.ascension_table.book_short` —— **同一个坑第三次踩**（前两次见 v4 实现⑤、v4 实现⑩）；**加语言键 = 必须 runData，没有例外**。(b) 「缺 3 本」在 `COST_TIER_RIGHT = 290` 时刚好顶到等级加号按钮上（实测间隙 **0**），两列各右移 8（298 / 330）后最坏情况仍有 **17 物理像素**间隙、两列之间 20。⇒ **「差点就放得下」这种判断必须靠测像素，不能靠估字宽。** **探针与 `-PueGuiProbe` 条件块全部删除，grep 零残留；`runData` 首跑 `written: 2`、二跑 `written: 0`。**

| 2026-10 | **进阶台 / 图鉴体验修正（v4 实现⑭，本轮）**。作者四条反馈逐条落地。**① 术语：玩家看到的那一档叫「基础」**（作者：「设计时用的词不应该作为游戏中的术语」——那个只在设计内部使用的说法，已于后续轮次**全库清除**）。改的是**显示值**（`tier.ultraenchantment.native`：**基础 / Basic**）；进阶台方块、图鉴表头、图书馆能量条与列表、tooltip 全都读这一个键，所以只有一处要改。**代码里的枚举仍叫 `LineageTier.NATIVE`**（内部标识，不进界面）。**② 修 bug：进阶台「所有基础方块都是亮的，可就是点不动」**。根因是**两个判据不一致**：界面写死了「基础档恒亮」（只判 `maxLevels[0] > 0`，根本不看图鉴），而服务端 `selectTier` 照样查图鉴（v4 起基础档也要「见过」才解锁）⇒ 画成可选、点了被拒。修法：`unlockMask` 把**基础位也发下去**（与 `CodexData` 同一套位：进阶三档占 0/1/2，基础占 `NATIVE_BIT`），并抽出**全模组唯一的 `AscensionTableMenu.unlockBit(tier)`**，界面与服务端都走它。**③ 点暗色方块必须说明原因**：新增状态码 `STATUS_TIER_LOCKED(8)`「该阶级未解锁：把它对应的书存进图书馆」、`STATUS_TIER_MISSING(9)`「这条附魔没有这一档」—— 以前两种情况是同一个静默 `return`，玩家只会以为界面坏了。**④ 「重置」按钮接进界面**：`BTN_CLEAR` 早就实现了（恢复到物品当前状态，锁定项清不掉），但**界面上一直没有入口**，是死代码。现在在灌注按钮右侧放一个 30×14 中按钮；顺手把中按钮命中框从 36 改成与精灵同宽 30（原来右边 6px 点得到却看不见按钮）。**⑤ 图鉴可切换阶级看本地化名**：四个列标题变成可点，选中档高亮（`TEXT_OK`），名称列显示该阶级的专属名（`enchantment.ultraenchantment.super.sharpness` = 「超级锋利」）；**这条谱系没有该档（数据包没铺）或语言文件缺键时退回原版附魔名并压暗，绝不显示原始键**（先 `Language.getInstance().has(key)` 再翻）。标题行加了「点阶级名可切换显示」提示。**⑥ 补掉「预选」的一个隐形副作用**：候选列表原本只收 `#in_enchanting_table`，而**宝藏附魔（经验修补、灵魂疾行、迅捷潜行）不在那个标签里** ⇒ 物品上有它们时预选会把它们选上并锁定，列表里却没有这一行，玩家得到一条**看不见也改不了**的选择。现在**物品上已有的谱系无条件进列表**（判据是「物品上有没有」，不是「能不能再附上去」）。**验证**：服务端探针 **42/42**（含反例：经验修补确实不在那个标签里、物品没有它时列表里就没有它、有它时必须可见且预选为 0 级差 0；smite 未解锁时基础位为 0、点它是状态 8 且不被选中；耐久点超级是状态 9）；客户端截图 3 张 + Pillow 逐像素测量：**方块底色 row3 基础 = (110,110,110)「锁」、其余 = (198,198,198)「可选」/ (111,168,111)「选中」**（正是那个 bug 的反面）、新状态文案最右端 **638 < 重置按钮左边界 686**（不压按钮）、图鉴列标题选中档 = `TEXT_OK(16,95,16)` 其余 = `(111,111,111)`、名称列文字宽 68 / 103px（4~6 个中文字，不是原始键）。**⚠️ 本轮又踩两次「测试写错」**：(a) 服务端探针为找 smite 滚动过页面后**拿旧行号断言**，出了两条假 FAIL；(b) 客户端探针**相位编号写错**（`Render.Post` 里置 2、tick 里等 3），界面卡在第一张截图。两次都是测试的错，代码没问题 —— 已写进 P1。**探针与 `-PueGuiProbe` 条件块全部删除，grep 零残留；`runData` 首跑 `written: 2`（术语 + 新键）、二跑 `written: 0`。**

| 2026-10 | **进阶台「预选 + 锁定」+「完整路径定价」（v4 实现⑬，本轮）**。**① 任务 A 预选**：`slotsChanged` 不再只 `selections.clear()`，改为 `rebuildSelections()` —— 按**物品实际状态**把已有附魔装进选择表：已进阶的取 `ascension` 组件的 **tierLevel**（不是存储等级！`writeAscension` 刻意把存储写成「该阶上限」，锋利升阶后仍是 5），未进阶的取 `minecraft:enchantments` 的存储等级；同时把谱系记进 `lockedRoots`（点同一档**不取消**、`清空` 也清不掉；切到**别的**阶级仍然允许，那正是升阶）。**关键重构**：新增 `currentSelectionOf()`，把「预选」与「差价」的基准统一——旧代码用 `currentTierOrdinal == tier.ordinal() ? tierLevel : 0`，基础阶的 `currentTierOrdinal` 恒为 −1 ⇒ 现有等级被当成 0 ⇒ **一放物品进去就显示 f(5)=16 的凭空差价，一按灌注就白扣**。预选等级**不向下夹取**（物品 tierLevel 可能高于当前 effectiveCap：装过神化的存档/数据包调小），`apply()` 的上限取 `max(该阶级上限, 现有等级)`，避免「开界面+灌注」把附魔静默降级。`apply()` 另加「**已经处于目标状态的条目不参与**」——否则每次灌注都会对物品上每条附魔发一次事件，第三方监听器一取消就整次作废。**② 任务 B 完整路径定价（作者选方案 B）**：`tierCostOf` 从「只收目标那一档」改成 `Σ` 当前+1→目标的每档 `ascensionPrice`；`levelCostOf` 加上 `Σ` 中间阶级的满级价。**中间阶级的满级取数据包 `max_level`，不取 `effectiveCap`**（后者含神化超限与 `level_lock`，是「玩家能调多高」；这里要的是「该阶级基础段有多长」，与 §15 升阶条件同口径；超限来自配置文件默认关闭，拿它定价会随配置漂移）。**「当前阶级未走满就跳」不补收走满的钱**——补收会让**相邻**进阶也涨价，而本轮要修的是跳阶；改动点只有一处（`t=from+1` → `t=from` 并补 `- f(当前等级)`），已写进代码注释与设计文档。**③ 顺带修掉两个只有截图才看得见的界面 bug**：基础阶选中时会去查**不存在**的键 `enchantment.ultraenchantment.native.<x>`，整行显示原始键（**预选之后每行原版附魔都会这样**）⇒ 基础阶退回原版附魔名；负的级耗（降级返还）落进「免费」那一支，看起来像免费降级、实际悄悄吞掉投入 ⇒ 改成绿色 `+N`。**验证**：① 服务端探针（FakePlayer + 真实菜单/方块，55 项断言，含反例）**55/55** —— 预选「高阶 8 级」显示 8 且不被夹到 5、点同一档不取消、**非锁定行点同一档确实能取消**（证明锁定是选择性的）、基础 5 级差价 0（旧代码 16）、基础→究极 35/48、高阶→究极 30/32、**35 = 5+30 且 48 = 16+32（一步到位 == 一步步走）**、反例余额不足 status=5 且物品不被写、正例扣光库存并写入 `ultra/sharpness`、降级 8→3 退还 `f(8)-f(3)=124` 进图书馆、原版附魔书可存并产出等级单位·基础 4 且解锁「见过」（上轮三件未实测改动一并复验）。② 客户端探针截图 + Pillow 逐像素测量：面板 676×468 物理 = 338×234 逻辑、四行选中带 y=[146..317]、花费两列「35」右端 683 /「+124」左端 694 ⇒ **间隙 11px 不重叠**。③ 语言键核查：59 个阶段条目在 zh_cn/en_us 里 **59/59** 齐全，且**不存在 `native` 键**（正是 ③ 那个 bug 的成因）。④ 探针源码与客户端探针用的 Gradle 条件块**已全部删除**（仓库里 grep 探针类名零命中，只剩本条变更记录的文字描述）；`runData` `written: 0, removed stale: 0`。**⑤ 截图顺带发现一处未改动的美术问题**：`ascension_table.png` 在 y=168 以下、x=78..259 之外**全透明**，界面下半部左右两侧能看到世界且无收尾边框 —— 该贴图是手绘版（生成器画的是满幅面板），**未擅自修改**，已记入 §7 待办第 8 条。 |

| 2026-10 | **端到端实测通过（v4 实现⑫）**。作者要求「自己试试能不能用」。服务端探针走完整链路（`FakePlayerFactory` + 真实菜单 + 真实方块）：**存入进阶书 → 产出阶级单位 10 / 等级单位 1 → 图鉴解锁锋利·超级 → 存入铭刻书(3级) → 等级单位 5 → 进阶台选中超级档 → 应用 → 物品拿到 `ascension` 组件**。**实测 8/8**：`isDepositable(进阶书) = true`（这是之前那个致命 bug 的回归测试）、阶级单位 10、等级单位 1→5、图鉴 `{sharpness=10}`（bit1=超级 + bit3=基础）、选中 tierOrdinal=2、**再点一次 = 撤销**、应用后组件存在、状态码 1(OK)、扣款正确（阶级 10→0、等级 5→4）。**⚠️ 探针自身踩了两个坑（都不是代码问题）**：① 列表 12 条但一次只显示 `ROWS=4` 行，**必须翻页找**；② **按钮编码用的是 `LineageTier.ordinal()`（基础0/高阶1/超级2/究极3），不是 `AscensionTier.ordinal()`（高阶0/超级1/究极2）** —— 用错会选中相邻的档，表现为「选了却被拒绝」。这两个坑值得记下：**测试写错比代码写错更常见**。 |
| 2026-10 | **图书馆库存随方块搬运 + 物品 tooltip（v4 实现⑪）**。作者要求「给图书馆做反序列化，掉落也包含其中的功能，作为物品在手中也能通过 tooltip 观察」。**查明**：掉落/放回**本来就实现了**（`EnchantmentLibraryBlock.getDrops` → `BlockEntity.saveToItem`、`setPlacedBy` → `loadWithComponents`、`getCloneItemStack`），缺的只是**可读的 tooltip**。新增 `BookTooltipEvents.onLibraryTooltip`：读 `minecraft:block_entity_data` 里的 `energy` 表，按**家族 → 阶级**固定顺序列出非空桶（避免行序跳动），空图书馆不显示。`TAG_ENERGY` 改为 `public` 供 tooltip 读取。**实测 6/6**：掉落物带 `block_entity_data`、放回后三个桶逐一还原（10/12/8）、未存的桶仍为 0、NBT 键 `ascension|super` 存在。**「见过」定义按作者决定保持 A**（存入含该谱系的书即算见过）。 |
| 2026-10 | **实机测试揪出一个致命 bug + 两项作者反馈（v4 实现⑩）**。作者挑战「你自己试试图书馆给灌注台供能」。**服务端探针实测**：`BOOKSHELF_OFFSETS` 共 **32** 个、相对 y 分布 `{0=16, 1=16}`，**32/32 偏移都能供能**（图书馆摆进去就能被找到），`(2,0,0)` 在偏移表里 ⇒ **书架环本身没问题**。**⇒ 真正的 bug 在入口**：`LibraryMenu.isDepositable` **只认 `INSCRIPTION_SPEC`**，而 `tryDeposit` 已经写好了三种书的折算公式 —— 结果**升级书与进阶书根本放不进输入槽**。后果致命：**阶级单位只能由进阶书产出** ⇒ 图书馆永远供不出阶级单位 ⇒ **进阶台的「升阶」操作永远做不了**。这是「功能写了但入口没开」的典型，**只有实机走一遍才会暴露**。已放开准入，并在两处加了「必须成对修改」的注释。**作者反馈①**：图鉴**基础阶也要「见过」才解锁**（v3 是恒解锁，太便宜）。`CodexData` 新增 `NATIVE_BIT = 1<<3`（AscensionTier 只占 0/1/2 位）与 `withNative(root)`；`isUnlocked(root, NATIVE)` 不再恒真；存入任何含该谱系的书即记为「见过」。**作者反馈②**：进阶台**无法取消选择** —— 已实现「再点同一档 = 撤销该行选择」。**教训**：准入判定与折算逻辑是**一对**，改一个必须改另一个；**书架环这类「摆好了却不工作」的问题，只能实机验证**。 |
| 2026-10 | **修复更名引发的三处连带故障 + 书架环对齐原版（v4 实现⑨）**。作者报告「图书馆 GUI 变成马赛克、本地化也没改」。**根因一（马赛克）**：更名脚本改了 `GuiSprites` 里的**贴图路径字符串**，但 `textures/gui/` 下的文件没跟着改名 ⇒ 路径断了 ⇒ 缺失贴图。已把 GUI 背景纹理一并更名（**内容不变，作者美术保住**）。**根因二（本地化）**：只改了**键**没改**值**，显示名仍是「附魔图书馆」。已改「进阶附魔图书馆 / Advanced Enchantment Library」。**根因三（我的失误）**：**PowerShell 的 `-replace` 默认不区分大小写**，把 Java 标识符 `ENCHANTMENT_LIBRARY` 也改成了小写。更糟的是，用「引号包住占位符」的办法保护字符串时，`"...".png"` 这种**带后缀**的字面量没被匹配到，反而把字符串内容改成了大写。⇒ 教训：**批量替换必须用 `-creplace`（区分大小写）**，且保护字面量的正则要覆盖「后缀」情形。已全部修正（13 个文件的标识符 + 4 个文件的字符串）。**书架环对齐原版**：`libraries()` 原本只查 `BOOKSHELF_OFFSETS` 位置上有无图书馆，**漏了原版第二条判定**——「中间那格必须是空气」（`EnchantingTableBlock.isValidBookShelf`）。少了它会隔着墙供能。已补。**多对一供能本来就已实现**：环内每座图书馆独立求和（`pooledEnergy`）、按顺序摊扣（`deduct`），摆得越多供能越足。**验证**：构建通过；`runData` `written: 0`；生成的 `zh_cn` 已是「进阶附魔图书馆」。 |
| 2026-10 | **四项改动（v4 实现⑧）**。**① 铭刻书超限 → 阶级渐变字体**：`BookTooltipEvents` 的每条目行，当 `entry.level()` 超过数据包 `max_level` 时套用**与物品 tooltip 同一份**的 `overCapColors` + `PrismRainbow.applyFlow`（把 `TooltipEvents.overCapColors` 从 `private` 改为包内可见，**避免两处各写一份配色**）。**② 数据包新键 `level_lock`（等级锁 / 超限上限）**：`StageDefinition` 新增 `Optional<Integer> levelLock` + codec `level_lock`；`LineageTable.StageSpec` 加第四个分量并新增四参 `stage(...)` 工厂；`StageLookup` 新增 `levelLockOf` 与 **`effectiveCap` = min(神化上限, 等级锁)**；接到 `AnvilEvents`（升级路径）与 `AscensionTableMenu.maxLevelOf`（等级选择上限）。缺省 = 不锁，**神化缺席时行为与从前一字不差**。**③ 进阶台碰撞箱 = 附魔台同款**：`Block.box(0,0,0,16,12,16)`，只覆写 `getShape`（与原版 `EnchantingTableBlock` 逐字一致）。**④ 图书馆更名 `advanced_enchantment_library` + cube 模型**：方块/方块实体注册名、blockstate/model/item JSON、纹理文件名、pickaxe 标签、lang 键全部同步（20 个文件替换，零残留）；模型从自定义 12 高几何改为 `minecraft:block/cube_bottom_top`。**验证**：构建通过；`runData` 第二次 `written: 0`（确定性）；注册名与资源名核对一致。 |
| 2026-10 | **GUI 纹理拆分：背景 + 精灵图（v4 实现⑦）**。作者要求「把 GUI 相关纹理拆分成背景和精灵图，我想根据自己的美术重绘，包括按钮」。**产出**：`textures/gui/` 下 **3 张背景**（`ascension_table` 340×236 / `enchantment_library` 260×236 / `codex` 260×204）+ **1 张共用精灵表** `widgets` 256×256（宽/中/小方/微按钮各三态、阶级方块四态、箭头四态）；坐标表 `client/GuiSprites.java`（**改图不用改代码**）；重绘指南 `docs/gui-textures.md`；占位图生成器 `tools/gen-gui-textures.py`。`GuiRender` 从「`fill` 程序画」全部改成「`blit` 贴图」，槽位凹陷/列表底/分隔线**烘焙进背景**（原版惯例）。**⚠️ 截图抓到 1 个真 bug**：`GuiGraphics.blit(tex,x,y,u,v,w,h)` 简版重载**内部假定贴图 256×256**，而我们的背景宽 260 / 340 —— 超过 256 的部分 **UV 回绕**，画面表现为「**面板被重复平铺、中间一条竖缝**」。必须用带贴图尺寸的重载 `blit(tex,x,y,u,v,w,h,w,h)`。**这个 bug 源码里完全看不出来。****⚠️ 另一个易踩点**：槽位位置烘焙在背景里 ⇒ 移动槽位必须**同时**改背景 PNG 与 `*Menu` 的槽位坐标。已写进文档。 |
| 2026-10 | **图书馆界面重做 + 三个截图抓到的 bug（v4 实现⑥）**。图书馆的职责在 v4 变成两个，界面必须都表达：**① 存了什么**（7 个能量桶）**② 能取出什么**（图鉴解锁的列表）。新布局：标题行（滚动 + 图鉴按钮）+ 槽位 + **能量条**（2 家族 × 4 阶级，基础阶的阶级单位画成 `—`）+ 分隔线 + 列表（附魔/阶级两列）+ 玩家背包。**截图抓到 3 个真 bug**：① **`LibraryMenu.ROWS` 还是 8 而面板只放得下 3 行** ⇒ 列表直接画到面板外；② **玩家背包槽位与屏幕坐标不一致**（菜单 150/206 vs 屏幕 164/218）；③ **滚动按钮压在列表「阶级」表头上**。**另修 2 个**：图鉴显示**注册名**（`berserkers_fury`）而非显示名 ⇒ 改用 `description()`；**忘了跑 `runData`** ⇒ 界面上出现 `energy.ultraenchantment.ascension` 这种原始键。**通用进阶书定价定案**：从常数 5 改为「**该阶级全部谱系里最大的 `required_level`**」（通用书能替代任意一条，价值应对标最好的那条）。**⚠️ 又一次「忘跑 datagen」**：新增语言键后必须 `runData`，否则界面显示原始键 —— 这已是第二次同类失误（上次是进阶台的列标题）。复验通过：图鉴显示中文显示名（狂战士之怒 / 大地恩惠 / 链锯 …），图书馆能量条与列表均正确。 |
| 2026-10 | **图书馆能量模型 · 步骤 5：三个界面（v4 实现⑤）**。**① 进阶台**：灌注按钮从 y=140 **上移到 y=38**（作者要求「给物品栏留更多空间」），面板 320→**340** 宽以容纳**两笔花费分列**（阶耗 / 级耗），列表 4 行 × 22px，物品栏 4 行全在面板内。**② 图鉴界面（新）** `CodexScreen` —— 只读 `Screen`（不是容器菜单，没有槽位、没有服务端判定）；数据直接读**已同步的玩家附件**，不需要任何网络往返；每行 = 一条附魔 + 四个阶级方块（基础恒亮）；8 行滚动。**③ 入口按钮**：进阶台与图书馆界面右上角各一个「图鉴」按钮，点击 `setScreen(new CodexScreen())`。**实测（客户端截图 + Python 逐像素测量）**：面板 bbox **676×468 物理 = 338×234 逻辑**（设定 340×236 ✓）；槽位行 4 段 `(332,363) (368,399) (404,435) (440,471)` ⇒ **物品栏 3 行 + 热键栏全在面板内**。**⚠️ 又一次「肉眼读像素不可靠」**：截图里我数成 3 行、以为热键栏被裁，测量证明是 4 行。这已是第二次，写进教训。**截图抓到 1 个真 bug**：图鉴显示的是**注册名**（`berserkers_fury`）而不是附魔**显示名** —— 已改用 `description()`。**⚠️ 该修复已编译通过但未再截图复验**（本轮上下文接近上限）。 |
| 2026-10 | **图书馆能量模型 · 步骤 2–4 完成（v4 实现②③④）**。**存储层重写**：新增 `EnergyFamily`（阶级/等级两个家族）、`EnergyKey`（家族×阶级，**不含谱系**）、`EnergyMath`（全部公式，文档的可执行版本）；`EnchantmentLibraryBlockEntity` 从「90 个 (谱系,阶级) 池 + maxLevels」改成「**7 个能量桶**」。**旧存档直接丢弃**（作者决定：开发期）。**LibraryMenu**：三种书都能存（铭刻/升级/进阶，按 §5 公式折算，通用书 ×4）；列表来源从「图书馆有哪些键」换成「**图鉴解锁了什么**」；**补回神化那条被抄漏的 `+1` 机制**（`EnchLibraryContainer:137`）——左键把输出槽那本书 +1 级、只付边际差，Shift+左键取 1 级；v3 的「见过的最高级」天花板**删除**（职责与图鉴重复）。**AscensionTableMenu**：消费改按 `(家族, 阶级)` 池化；**花费拆成两笔**（升阶扣阶级单位 `required_level × 系数`、提级扣等级单位 `f(L)` 边际差）；**NATIVE 不再免费**；`DATA_STRIDE` 10→12 以携带两笔花费。删除死代码 `LibraryKey`。**实测 11/11**：f(L) 曲线、阶级系数 0/1/2/4、锋利升阶价 5/10/20、兑换 4→1（余数留原桶）、究极不可再兑、`(阶级,基础)` 非法桶被拒、余额不足不扣、NBT 往返一致、存档键解析（含拒绝死键）。**⚠️ 两条「失败」又是测试自己的算术错**（6−4=2，我写成 1），不是代码 bug。**⚠️ 发现一个设计缺口**：通用进阶书没有 root，而升阶价依赖**逐谱系**的 `required_level` ⇒ 暂用常数 5（`EnergyMath.GENERIC_ASCENSION_BASE`），已在代码与文档标注为待定。 |
| 2026-10 | **图书馆能量模型 · 步骤 1 完成：图鉴同步（v4 实现①）**。给 `CodexData` 加 `STREAM_CODEC`、给附件 `.sync(...)`，为「图鉴界面」扫清障碍（v3 时刻意不同步，因为当时没有浏览界面）。**实测 7/7**：往返无损（掩码一致、缓冲无残留）、2 条谱系仅 **44 字节**、附件注册为 `ultraenchantment:codex`。**踩到的坑**：`ByteBufCodecs.map(HashMap::new, ...)` 会让编译器把 `M` 推成 `HashMap`，于是 `.map(CodexData::new, CodexData::unlocked)` 的 from 函数要求「返回 HashMap」而 `unlocked()` 返回 `Map` ⇒ **编译不过**。必须写显式类型见证 `ByteBufCodecs.<ByteBuf, ResourceLocation, Integer, Map<ResourceLocation,Integer>>map(...)`。另：`AttachmentType` **没有 `getKey()`**，反查注册名要走 `NeoForgeRegistries.ATTACHMENT_TYPES.getKey(type)`。 |
| 2026-10 | **图书馆能量模型：补上「升阶条件与超限折算」（v4 提案续）**。作者追问「超限等级要不要作为进阶条件」，并给出两个候选：A「满级后降级换阶级」vs B「中途选择低阶深耕还是升阶」。**查出关键事实**：神化的超限**来自配置文件**而非数据包 —— `ApothEnchEvents:301-323` 用 `ApothicAttributes.getConfigFile(\"enchantments\")` 逐附魔读取，查询时 `EnchantmentInfo.fallback(ench)` 回落到**原版上限**。⇒ **不改配置就没有超限** ⇒ **超限不能作为升阶的必要条件**，否则未配置的整合包里进阶永久卡死（硬约束）。**又发现 A 和 B 不是并列选项**：「升阶归 1」本来就是现状（`docs/book-system-spec.md:444`），B 是 A 的**后果**。**最终采纳 B + 2:1 折算**：升阶条件 = **该阶级的数据包上限满**；超限等级按 **2:1** 折算成新阶级的起始等级（`起始 = 1 + floor(超限等级数 / 2)`）。这样「深耕不浪费、条件清晰、选择真实」。数值验证（锋利 `3+1L`/`6+1.5L`/`10+2.5L`）：高阶 L5 直接升 → 超级 L1 = 7.5，**比高阶 L5 的 8 还低**（升阶瞬间净亏）；高阶 L10 再升 → 超级 L3 = 10.5。**升阶永远是短期亏、长期赚**。详见 [docs/library-energy-model.md](docs/library-energy-model.md) §15。 |
| 2026-10 | **图书馆「能量模型」设计定稿（v4 提案，尚未实现）**。作者认为 v3 的图书馆「功能在外面这个体系下也是不完全的」，并提出：在图书馆方块内存**抽象能量单位**，作为**进阶台的燃料**。经一轮逐项追问（grill）定稿，**完整规格见 [docs/library-energy-model.md](docs/library-energy-model.md)**。核心：**8 类能量 = 2 家族 × 4 阶级，不分谱系**；铭刻书/升级书 → 等级单位（按 `2^(level-1)`，通用书 ×4），进阶书 → 阶级单位（= `required_level` × 阶级系数 ×1/×2/×4，通用书 ×4）；同类内 **4:1 向上兑换**（有损耗）；**图鉴决定能取出哪些 `(谱系, 阶级)`，能量决定能取多高等级**；进阶台**升阶花阶级单位、提级花等级单位**。**顺带补掉三个旧洞**：NATIVE 阶花费为 0、图书馆存了没出口、90 个谱系池。**关键发现**：① 数据包里 `required_level` **从未被显式指定**（三参 `stage(...)` 用量为 0），全部走默认 = 原版满级 ⇒ 三阶同价，故需乘阶级系数；② v3 的取出逻辑**抄漏了神化的核心机制** —— `EnchLibraryContainer:137` 的普通点击是 `curLvl + 1`（在输出槽的书上一级一级加），v3 只会取「能付的最高级」或「1 级」，中间等级取不到；③ 进阶台**早就连上图书馆了**（书架环找附近图书馆、多座池化、严格「校验→扣→写」），只需改键（去谱系）与把花费拆成两笔。**新需求**：图鉴要能**打开**，由图书馆/进阶台提供按钮进入；但 `UEAttachments.CODEX` 目前**刻意没 `.sync()`**，需先解决同步。 |
| 2026-10 | **删除交易附魔规则，成本回到普通书（v3.10，作者权衡）**。作者决定：附魔进阶师的交易形状照原版图书管理员 —— **一本普通书 + 稀有货币 → 一本进阶附魔书**，不再附加任何附魔要求。删除 `TradeRequirement`、`MerchantOfferMixin` 与 `trade_requirement` 组件。**理由**：那条规则只在成交瞬间生效、成本槽里又完全显示不出来，玩家看到的是「放本书进去却做不成」；门槛现在完全交给货币（回响碎片 / 下界之星），可见、可预期。**保留下来的引擎事实**：`ItemCost` 的 codec 只编解码 `(id, count, components)`，**`itemStack` 不参与序列化**（反序列化走三参构造重建）；且 `ItemCost.test` 只认 `pay1.is(成本物品)` —— 所以「成本物品类型」与「要求交另一种物品」**不可能同时成立**。**验证**：交一本普通书 + 回响碎片即成交；泥土 / 石头 / 附魔书均被拒；大师级交下界之星成交、交普通书被拒。7/7 通过。 |
| 2026-10 | **GUI 重做 + 截图验证法（v3.9）**。作者评价 v1 界面「不是给人看的」，属实。**v1 的四个问题**：16px 行高里塞四个 9px 小三角（点不准）、阶级只有两个三角（看不出总共几档）、整块面板纯色 `fill` 没有明暗边（可点与不可点长得一样）、没有列标题。**v2 做法**：面板 320×236 / 行高 22px；**阶级改成四个并排方块**（亮=可选 / 暗=未解锁 / 绿=选中），点哪档选哪档（新增 `BTN_TIER_SET`，不再循环）；等级用实体 −/+ 按钮；加列标题行（附魔/阶级/等级/花费）；抽 `GuiRender` 统一「凸起=可点、凹陷=槽位」的原版立体约定。**🔑 关键新方法：客户端截图验证。** 界面外观此前只能靠人眼看，现在可以自己看：客户端探针用 `ClientTickEvent` 推进时序 → `setScreen` → `ScreenEvent.Render.Post` 里 `Screenshot.grab` → `read_image` 审阅。需要一个有 player/level 的客户端环境，做法是把服务端世界复制成 `run/saves/<名>`，再用 `-PueGuiProbe` 触发 `--quickPlaySingleplayer`（该条件块测完已删）。**这个手段立刻抓到三个源码里看不出来的 bug**：① **面板 258 > 逻辑视口 240**（GUI scale 2 + 480p）⇒ `topPos` 为负、标题与列标题被顶部裁掉；② **`ROWS` 还是 6 而面板已缩到 236** ⇒ 列表底画到 180、灌注按钮在 140，**第 5 行直接压在按钮上**；③ **槽位完全没有底**——原版的槽位凹陷底是画在 GUI 贴图里的，我们既然用代码画面板、不 blit 贴图，就必须自己给每个 `Slot` 画底，否则玩家看不到往哪放物品。**教训**：界面代码的坐标错误在源码里**不可见**（都是常量相减），只有渲染出来才知道。有截图能力之前不要声称界面「做完了」。**顺带纠正一处自己的误判**：截图肉眼读数把 4 行槽位看成 3 行、以为热键栏被裁；改用 Python 逐行统计底色像素后确认是 4 行且都在面板内（y≈332/368/404/440，面板底 473）。**肉眼读像素不可靠，要测量。**截图留档：`docs/gui/ascension-table.png`、`docs/gui/enchantment-library.png`。 |
| 2026-10 | **修复「进阶附魔师无法正确交易」（v3.8）**。三个叠加的 bug，服务端探针逐个定位：**① mixin 写成 `cancellable = false` 却调 `setReturnValue`**—— Mixin 的 `setReturnValue` 在不可取消时会**直接抛 `CancellationException`**，于是「拒绝成交」变成「抛异常」，症状就是交易做不成、而且**只在需要拒绝时才炸**；**② 成本物品类型与规则冲突**（详见 v3.10）；**③ 规则存在 `ItemCost.itemStack` 上**，而 `ItemCost` 的 codec 只编解码 `(id, count, components)`，组件在存档往返/网络同步后必然丢。经验值同时从 `{0,1,5,10,15,30}` 改为 `{0,4,18,25,32,40}`（原版阈值 10/60/80/100 ⇒ 3~4 本一级）。**⚠️ 其中两条「失败」是测试自己的错**：blast_protection 上限是 4（写死 5）、大师级 costA 是下界之星（拿书去付）。 |
| 2026-10 | **附魔进阶台（v3.7，阶段 4 完成）**。新增 `AscensionTableMenu` + `AscensionTableScreen`，并在 `AscensionLogic` 加可复用的 `writeAscension`、`EnchantmentLibraryBlockEntity.spend`、`UltraEnchantTierUpgradeEvent.Source.ASCENSION_TABLE`。**① 差异化 = 多一个「阶级」轴**：候选来源与 Enchanting Infuser 同源（注册表 + `#in_enchanting_table` 标签 + 物品可用性），但每行多一组阶级三角（基础/高阶/超级/究极），再在该阶级内调等级。**刻意不排除物品已有的附魔**——排除掉就没法给已有附魔<b>升阶</b>，而那正是这个方块存在的理由。**② 代价不是经验而是图书馆库存**：「附近」用原版附魔台同一套书架环 `EnchantingTableBlock.BOOKSHELF_OFFSETS`；多座图书馆在环内时<b>池化</b>（校验看总量，扣减按顺序摊）。**③ ⚠️ 严格「全量校验 → 扣库存 → 写物品」**：先写物品再扣库存会留下白拿窗口——中间任何异常或取消都让玩家免费拿到附魔。事件（可取消）在扣减<b>之前</b>发。**④ 存储等级规则与 `InscriptionLogic` 同源**（`AscensionLogic.writeAscension`）：取该阶 `max_level`，夹进该附魔自身上限与 255，且至少 1。结算层要求存储等级 > 0 才会注入阶段；越界等级会在<b>网络编码</b>阶段抛异常，表现是<b>玩家掉线</b>而非可读报错。改一处必须改两处。**⑤ 阶级循环只走「已解锁且数据包铺了」的档位**：锁着的直接被跳过，玩家连点也切不到没解锁的阶级。**⑥ 基础阶不消耗库存**（设计决策，见下方待定项）。**验证（服务端探针 + FakePlayer + 真实方块实体，用后即删）**：无物品无候选、放剑后 12 条候选、**未解锁时循环只停在基础**、解锁后能切到高阶、反例·无库存（status=5 且物品附魔数=0）、反例·无图书馆（status=7）、等级循环（3 级/4 点/够付）、**灌注**（status=1，阶段=`advanced/sharpness`，tierLevel=3，存储等级=5，库存 4→0）、基础阶不扣库存。9/9 通过。**⚠️ 两条断言失败的是测试不是代码**：(a) `levelToPoints(L)=2^(L-1)`，我按「传 4 就是 4 点」写，实际 4 级=8 点，于是「余库存 4」是对的；(b) 上一次 apply 会清空选择，从「未选」出发按「向下循环」落到的是<b>最后</b>一个可用档而非基础——想选基础要用「向上循环」。 |
| 2026-10 | **图鉴（v3.6，阶段 3 完成）**。新增 `CodexData`（内容记录）与 `UEAttachments`（玩家数据附件）。**① 存玩家身上**：规格原话是「<b>玩家</b>把一本铭刻书放进图书馆，就<b>永久</b>解锁这条附魔的这个阶级」——天然 per-player。存方块会「换个图书馆就重新开始」，存存档全局则没有个人进度。附件加 `serialize`（随玩家 NBT 存盘）+ `copyOnDeath()`（死了不掉，否则不叫「永久」）。**② 数据结构用位掩码不用集合**：每条形如 `谱系根源 → 3 bit 掩码`（bit 依次对应 `AscensionTier.ordinal()`）。一条谱系最多 3 个阶级，掩码把「一条谱系」压成一个 int——实测编码结果就是 `{unlocked:{"minecraft:sharpness":1,"minecraft:unbreaking":2}}`，30 条谱系总共 30 个条目。**③ 基础阶恒为已解锁**：`LineageTier.NATIVE` 不进掩码——原版附魔人人可见，把它纳入解锁状态只会让「新玩家能不能用原版附魔」变成荒谬问题。**④ 解锁与存入是两件事**：存入改图书馆库存（方块），解锁改玩家图鉴（玩家），同一本书同时影响两者。`CodexData.with` 在已解锁时<b>返回自身实例</b>，调用方据此跳过写盘（否则每次存入都会触发一次无意义的玩家数据写入）。**⑤ 刻意不做 `sync`**：图鉴的读取方是服务端（进阶台「这个阶级能不能选」由服务端判定），客户端只需知道<b>当前可见那几行</b>的掩码，由菜单 `ContainerData` 逐行下发比整份图鉴全量同步更省；且目前没有独立的图鉴浏览界面。**验证（服务端探针 + FakePlayer，用后即删）**：附件注册、反例（从未存入的 Bob 图鉴为空）、存入锋利·高阶只解锁高阶（超级/究极仍 false）、基础阶恒解锁（含从未见过的 fortune）、幂等（重复解锁返回同一实例）、第二条谱系、**玩家隔离**（Alice 的解锁不跑到 Bob 身上）、Codec 往返一致、反例（非法输入正确抛异常）。9/9 通过。**踩坑**：`BuiltInRegistries.ATTACHMENT_TYPE` <b>不存在</b>——附件注册表在 `NeoForgeRegistries.ATTACHMENT_TYPES`（源码核实：`Keys.ATTACHMENT_TYPES = key("attachment_types")`）。 |
| 2026-10 | **附魔图书馆的容器与界面（v3.5，阶段 2 完成）**。新增 `UEMenus`（菜单类型）、`LibraryMenu`、`LibraryScreen`、`UEClientSetup`（客户端注册）。**① 菜单同步只用 `ContainerData`，不加网络包**：每行 4 个 int =「附魔注册表**数字 id** / 阶级序号 / 点数 / 已达等级」，表头是「总数 / 滚动偏移」；按钮点击走原版 `clickMenuButton`（`handleInventoryButtonClick`）。附魔用数字 id 而非 id 字符串，因为 ContainerData 只能传 int，而注册表数字 id 恰好两侧一致。**② ⚠️ 容器监听器必须用「匿名子类重写 `setChanged`」这个原版惯用法，不能只 `new SimpleContainer(n)`**：`AbstractContainerMenu` 并不把自己注册成容器监听器（源码核实：`addListener` 在原版菜单里根本没有被调用），原版 `CartographyTableMenu` / `StonecutterMenu` / `EnchantmentMenu` 全是「匿名子类里调 `Menu.this.slotsChanged(this)`」。漏了这一步，玩家把书放进槽位后**什么都不会发生且不报任何错**——这是最难查的一类 bug。**③ 取出的目标等级由点数反推**（`affordableLevel`），再对「见过的最高级」取 min。若直接取见过的最高级，点数不够时只能**拒绝**——玩家攒一堆低级书却什么都取不出来；由点数反推后，点数只够 2 级就给 2 级。**「合并等级」不需要任何特殊代码，它是 `2^(L-1)` 点数换算的自然结果**（两本 2 级 = 4 点 = 一本 3 级）。**④ 存入是原子的**：先 `canAccept` 全量校验，任一条目已满则整本书留在槽里——逐条吸收会静默吞掉玩家半本书的价值。**验证（服务端探针 + `FakePlayerFactory`，用后即删）**：菜单类型注册、存入 L3→4 点且槽位清空、ContainerData 行数据、取出 L3 且扣光、上限原子性（灌满后书留在槽且点数不变）、**合并**（花光后两本 L2→4 点→取出 L3）、反例（点数只够 1 级给 L1 而非报错）、阶级隔离（两行两池）、滚动边界。8/8 通过。**⚠️ 探针必须自清场**：世界在两次 `runServer` 之间是持久化的，上一轮留下的方块实体还在原地；对同一位置 `setBlock` 相同方块状态是 no-op、方块实体不会重建，于是旧数据污染断言（实测踩到：`maxLevel` 读到上一轮的 5 而不是 3）。修法是先 `removeBlock` 再 `setBlock`。另外我第一版的合并测试本身算错了账（前一条测试残留 16 点），**失败的是测试不是代码**——已用全新图书馆隔离。 |
| 2026-10 | **新增两个方块：附魔图书馆 + 附魔进阶台（v3.4，阶段 1 完成）**。这是本模组**第一次引入方块 / 方块实体基础设施**——此前 `registry` 包只有物品。**① 方块永远注册，不做条件注册**：进阶台的配方以 Enchanting Infuser 的「进阶高级附魔台」为材料，但方块本身<b>不能</b>条件注册——玩家卸载对方模组后方块 id 消失 ⇒ 已放置方块变未知方块 ⇒ 存档受损。门控分层：方块/物品永远注册；**配方**用静态 JSON 的 `neoforge:mod_loaded` 条件；**创造栏**（`CreativeTabEvents.functionalBlocks`）装了对方模组才投放进阶台（图书馆不依赖任何外部模组，总是投放）。**② 配方写成静态 JSON 而非 datagen**：中心材料来自我们**不依赖**的外部模组，datagen 期它不在注册表里，`Ingredient` 根本构造不出来——这不是「手写生成物」，是外部引用。**③ 纹理**：复制原版附魔台三张（top/side/bottom），每个方块各一套（6 张），便于作者日后独立改图。**④ 方块模型是静态 JSON**：原版附魔台是 16×12×16 的自定义几何体，NeoForge 的模型构建器表达不了 elements，因此方块状态/模型/物品模型都走静态资源（无漂移风险：几何体固定）。**⑤ 数据契约**：新增 `LibraryKey(root, tier)` 二元键——同一条附魔有四个形态，单靠附魔身份无法区分，这是与神化图书馆 / Enchanting Infuser 的根本差异。**⚠️ 绝不能用 `Holder<Enchantment>` 当键**：我们的合成 holder 无注册表键，神化图书馆会 NPE、Enchanting Infuser 会抛 `NoSuchElementException`（两处均已源码定位）。点数换算 `2^(L-1)`，位移夹在 30 以内防溢出；上限逐阶段取自数据包 `StageDefinition.definition().maxLevel()`，**不写死**。**验证（服务端探针，用后即删）**：注册 5 项全通过；阶级隔离（高阶 L3=4 点 / 超级 L2=2 点，互不串池）；**强对照**——动态挑 `advanced/unbreaking`(max=3) 与 `advanced/punch`(max=2) 两个上限不同的阶段，封顶分别跟随各自数据包值；反例：点数不足拒绝且余数不变、未见过该等级拒绝、超数据包上限拒绝；NBT 往返（`saveToItem` → `loadWithComponents`）一致；`LibraryKey` 垃圾输入与未知阶级均返回空。**配方条件的反证实验**：把条件 modid 临时改成 `minecraft`（必然为真）后，日志出现 `Parsing error ... Unknown registry key ... enchantinginfuser:advanced_enchanting_infuser`——这证明①配方文件确实被读取 ②条件才是拦住它的原因 ③装了对方模组后配方会正常加载。改回后无任何解析错误。datagen 连跑两次 `written: 0`。 |
| 2026-10 | **附魔进阶师交易成本换成稀缺资源（v3.3）**。作者指出绿宝石<b>可以靠村民刷</b> ⇒ 构不成门槛。**验证方法**：列出原版 `VillagerTrades` 涉及的**全部 206 种物品**做比对——「村民能买卖的就能刷」。结论：绿宝石**在表内**（可刷）；**钻石也在表内**（所以换钻石是无效方案，一度是候选）；而**回响碎片**（远古城市，不可再生）与**下界之星**（凋灵，不可农场）**都不在表内**。**改动**：1-4 级（铭刻/定向书）的 `costB` 绿宝石 → **回响碎片 ×2..6**（随机，含端点）；5 级（通用进阶书/升级书）的 `costA` 绿宝石 → **下界之星 ×1..3**（随机，含端点）。数量在 `getOffer` 里摇，与原先绿宝石定价同一时机 ⇒「同一次交易生命周期内成本固定」的性质不变。**删除 `UETradePrices`**（四个调用点全被替换，成死代码）——它的类文档记录了原版定价公式，该知识已在删除时经核对确认只服务于绿宝石路径，不再需要。**实测（各 2000 次）**：回响碎片分布 {2=387,3=432,4=395,5=392,6=394}、下界之星 {1=676,2=691,3=633}，覆盖完整区间且无偏斜；2000/2000 全部成功造出；maxUses 未被破坏（12/12/1/1）；成交判定仍工作（交对的书 `accepts=true`、交空书 `false`）——证明 `trade_requirement` 组件仍在 costA 上，1-4 级的附魔门槛未受影响。**绿宝石已完全消失**。 |
| 2026-10 | **战利品表掉落进阶附魔书（v3.2）**。新增全局战利品修改器，三阶分级掉落，实测验证通过。**① 为什么必须用 GlobalLootModifier 而不是静态 JSON**：书需要「随机谱系 + 随机等级」，而战利品表 JSON 是静态的（`set_components` 只能写固定值）；原版 `EnchantRandomlyFunction` 虽能随机挑，但它写的是 `minecraft:enchantments`（原版注册表），**填不了我们的自定义载荷组件**。故随机必须在代码里做。新增 `logic/loot/`：`LootTierWeights`（权重+来源修正）、`LootBookFactory`（随机谱系/等级/造书）、`ULTBookLootModifier`（继承 NeoForge `LootModifier`）、`registry/UELootModifiers`（序列化器）、`datagen/UEGlobalLootModifiers`（生成挂载）。生成物路径是 `data/neoforge/loot_modifiers/`——**是 neoforge 不是 forge**（源码实证）。**② 两层控制难度**（不是单层概率）：来源门槛（普通箱子**不产出**究极）+ 阶级权重（高阶70/超级25/究极5）。来源修正：普通 x1/x0.5/**x0**、稀有 x1/x1/x1、BOSS x1/x2/x3。普通箱子的究极权重为 0，「究极只在稀有来源出」无需额外 if，掷权重时自然选不到。**③ 实测（20000 次/表真实调用战利品表）**：普通箱子 高阶11.07%/超级3.90%/究极**0**、稀有 10.57%/3.74%/0.71%、BOSS 7.39%/5.32%/1.70%，与期望吻合（偏差多在 ±5% 内）。**对照实验**：未挂载的表 `spawn_bonus_chest` 产出 **0** 本 ⇒ 条件精确命中、不误伤其它表；载荷异常 **0** 本 ⇒ 书组件完整。**④ 只追加不替换**：`GlobalLootModifier` 拿到的是原版算完的结果，我们只 `add`，不改任何原版战利品表文件 ⇒ 与其它模组零冲突。失败一律静默跳过（战利品路径抛异常会毁掉整箱）。**⑤ 文档** `docs/loot-tables.md`（曲线表+设计理由+调整方法+验证方法）。⚠️ 我自己的一处失误：规划时把「出书概率」与「出某阶概率」混为一谈，写出的期望表每行加起来超过了 1（0.15+0.04=0.19）。实现时用整数权重手算校验才发现并修正。**教训：概率表必须自己加起来验一遍**。 |
| 2026-10 | **村民职业收尾：语言键格式错误 + 交易成本槽无说明（v2.26）**。① **语言键写错格式**：我按直觉写成 `entity.ultraenchantment.villager.advanced_enchanter`，但 `Villager.getTypeName()`（源码 744-749）拼的是`getType().getDescriptionId() + '.' + (非minecraft命名空间 ? ns+'.' : '') + path`。`getDescriptionId()` 来自【实体类型】（村民是 minecraft，故自带 `entity.minecraft.villager` 段），**职业自己的命名空间插在 `villager` 之后** ⇒ 正确键是 `entity.minecraft.villager.ultraenchantment.advanced_enchanter`。原版 `entity.minecraft.villager.armorer` 里 minecraft 只出现一次，是因为实体类型与职业同命名空间；**不同命名空间时两段都要在**。② **交易成本槽是一本空的原版附魔书**（`costA = minecraft:enchanted_book`，规则写在 `trade_requirement` 组件上），玩家只看到「附魔书」三个字，完全不知道要交哪条附魔、几级 ⇒ 新增 `BookTooltipEvents.tradeCostTooltip` 分支，对**带 trade_requirement 组件的原版附魔书**显示「需要交出的附魔：/ 锋利 V（未进阶）」；普通附魔书一个字节不动。「未进阶」必须写明——`TradeRequirement.accepts` 会拒绝已进阶物品（升阶后存储等级不变，只看等级区分不出，故查 ascension 组件）。③ 过程中先用探针【直接调用四个交易 listing 拆组件】取得权威事实，证明载荷/组件完全正常，推翻了「组件未同步」的错误猜测；也确认 `minecraft:enchantments` 空组件是引擎默认行为、不是 bug。**教训：用户报「什么都没显示」时，先确认他悬停的是哪个槽位**——本次是成本槽，不是出售槽。 |
| 2026-10 | **村民职业不认领工作站——缺 POI 标签（v2.25）**。症状：附魔台放着，村民不认领、职业不生效。根因（源码实证）：村民找工作站点时**先按标签筛候选**——`Villager.java` 的 AcquirePoi 过滤器用`VillagerProfession.ALL_ACQUIRABLE_JOBS`，其定义是 `p -> p.is(PoiTypeTags.ACQUIRABLE_JOB_SITE)`；而原版 `acquirable_job_site.json` 只列 13 个原版职业站点，**没有我们的 POI** ⇒ 站点进不了候选名单，放什么方块都不认领。**修法**：新增 `src/main/resources/data/minecraft/tags/point_of_interest_type/acquirable_job_site.json`，只列 `ultraenchantment:advanced_enchanter`。**合并语义已查证**：`TagFile.CODEC` 的`optionalFieldOf("replace", false)` ⇒ 默认 false = **追加**，原版 13 项不受影响（若是替换语义，村庄会直接瘫痪——这个默认值必须查证而不是假定）。⚠️ 原有的 `data/c/tags/block/villager_job_sites.json` 帮不上忙：那是 `c` 命名空间的**方块**标签，而游戏查的是 `minecraft` 命名空间的 **POI 类型**标签，属不同注册表、不能互替（无用文件已删）。另：**换方块（如雕纹书架）解决不了**——问题在标签缺失而非方块；附魔台保持原样（原版无 POI，不与原版职业抢站点）。同轮还补了职业名语言键 `entity.ultraenchantment.villager.advanced_enchanter`（走 datagen），缺它交易界面会直接显示未翻译的键名；以及纹理名必须**等于职业注册名**（`VillagerProfessionLayer.getResourceLocation` 用职业注册名拼路径，原版没有「指定纹理」的接口）。 |
| 2026-10 | **神化兼容收尾：视觉确认通过（v2.24）**。作者实机确认「感觉没问题了」，本轮不再加探针。最终形态：① 装神化时**删两种行、插我们自己的行**——原版名行（`锋利 0 (V - V)`，神化用原版 holder 渲染的过时残留）与阶级名行（`究极锋利 IX (0 + IX)`，神化借我们的结算表渲染的**我们自己的数据**）都删，换成我们自己渲染的干净行，判据一律按**名字本体**匹配、不硬编码神化后缀格式（P1-18）。② 颜色：装神化时**不用彩虹**、究极未超限保留原色 GOLD；**超限**（`tierLevel > 数据包 max_level`）时按阶级走深浅双色流体渐变——高阶蓝⇄深蓝、超级淡紫⇄深紫、究极橙⇄黄。③ 彩虹渐变只保留给「没装神化」的究极阶玩家。**经验教训（两条，都值得记住）**：（a）**「我们没渲染」不等于「那行不是我们的」**——神化遍历的第三张表 `realLevels` 就是我们的结算表，合成 holder 的 description 就是阶级名键。我曾误判它是「别人的行」而不敢碰，白绕了两轮；判据应该是**数据来源**而非**渲染者**。（b）**闭合环的长度 ≠ 档数**：去-回色环元素数是 `DUAL_STEPS - 2`，按档数取模直接 `IndexOutOfBounds` 整屏崩（已修，改用 `ring.size()`）。另：tooltip 崩溃**只在客户端悬停时暴露**，`runServer` 永远测不到（`ItemStack.addToTooltip` 不在专用服务端执行）——本轮两次崩溃都是靠客户端实机才发现的 |
| 2026-10 | **超限阶级配色 + 一处崩溃（v2.23）**。① 作者规格：装神化时**不用彩虹**（究极保留原色 GOLD）；**超过数据包定义等级**时按阶级走「浅 ⇄ 深」双色**流体渐变**——高阶 蓝 #5555FF ⇄ 深蓝 #0000AA、超级 淡紫 #FF55FF ⇄ 深紫 #AA00AA、究极 橙 #FFAA00 ⇄ 黄 #FFFF55。新增 PrismRainbow.applyFlow(行, 浅, 深)（色带去-回闭合环 + 每档平移一格 = 流动感）；StageLookup.isAboveDataPackCap 重新加回作判据。② **实测崩溃并修复**：IndexOutOfBoundsException: Index 22 out of bounds for length 22 → 整屏ReportedException: Rendering screen（悬停创造栏物品即崩）。根因是**去-回闭合环的元素数是 DUAL_STEPS - 2**（24 档时为 22），而平移取模写成了 % DUAL_STEPS；改为 % ring.size()。**教训：闭合环的长度 ≠ 档数，凡按「档数」索引环的地方都要用 ring.size()**；主色环 palette() 因环恰好构造成 STEPS 个元素而幸免，属同类隐患。③ 同轮把「神化渲染的两种行都删、改插我们自己那行」（原版名行 + 阶级名行）——因为**阶级行本来就是我们的数据**：神化遍历的第三张表 realLevels 就是我们的结算表，合成 holder 的 description 就是阶级名键；此前误当它是「别人的行」而不敢碰 |
| 2026-10 | **对外 API 落地（v3.1）**。新增公开包 `api`：`UltraEnchantmentApi`（查询 5 项 + 修改 4 项 + 阶段 id/IMC 读取）、`api.event.UltraEnchantTierUpgradeEvent`（可取消，带 source：铁砧书/升级书/合并/API）、`api.event.UltraEnchantLockChangeEvent`（不可取消：解锁由祛咒石触发且已扣耐久，允许取消会出现「石头没了附魔还在」）、`api.UltraEnchantImc`（三通道，载荷为 String 以免对方编译依赖）。**关键设计**：内部名 `tierLevel`（曲线等级）与对外 `tier`（阶级）同名不同义 → 公开层做映射并补 `getCurveLevel`/`setCurveLevel`，内部字段**不改名**（已进存档与网络格式）。写入只在服务端主线程生效（客户端/跨线程 → false + WARN）；`setTierLevel` 越界**不夹取**、`setCurveLevel` 夹取并 WARN；所有写入路径（铁砧升阶/提级、同名合并、祛咒石、API）统一经 `logic/UEEvents` 发事件，取消 = 整次作废、零副作用；IMC 与数据包**分开存**（时序无关）查询时合并。顺手消掉重复实现：dummy 兼容补丁的「附魔→谱系根源」判定改用共享 `logic/UERoots`。自检 **43/43**，文档 `docs/api.md` |
| 2026-10 | **神化兼容三项落地（v2.20）**。① **C＝星标 + 三段渐变**：超限时给进阶行加与神化同款星标（沿用它的语言键 `text.apothic_enchanting.star_prefix`，并有「键不存在时 `getString()` 会返回键名本身」的兜底）；装了 Prism 且超限时走 `PrismRainbow.applyDual(行, 阶级色, 神化超限色 0x00B3FF)`——**三种渐变对应三阶**（蓝/紫/金 → 神化超限色），双色色带按 (起点,终点) 缓存（`DynamicColor` 相位在实例里，每帧 new 会重置动画钟）。② **B1 联动**：新增 `StageLookup.isAboveDataPackCap`（判据＝`tierLevel > 数据包 max_level`）；`AnvilEvents.applyUpgrade` 的上限与 `ReloadEvents.globalMaxLevel()` 都并入神化上限。**语义边界**：只放宽「可到达的等级」，数据包仍是数值曲线权威，超出部分按 `Linear` 自然外推。神化上限在 `onTagsUpdated` 里用 `event.getRegistryAccess()` 解析（`refresh` 只拿得到阶段注册表，解析不了附魔——同 P0-13 的教训）。③ **API 修正**：`Enchantment.getDescriptionId()` 在 1.21.1 不存在，`Enchantment` 是 record，裸名取 `description()` 组件（源码第 61 行，`getFullname` 正是拿它 copy 后拼等级）。④ **依赖补漏**：神化把 `gateways` 声明为 **optional**，但它的 datagen 类 import 了 `dev.shadowsoffire.gateways.gate.Reward` → 缺了它 `runData` 直接 `NoClassDefFoundError`（**神化自己的问题**）。补 `maven.modrinth:gateways-to-eternity:1.21.1-5.1.0`（Apotheosis 要求 `[5.1.0,)`）后 `runData` 通过，复跑 `written: 0, removed stale: 0`。⑤ 类加载隔离已核：`dev.shadowsoffire` 只出现在 `ApothCapsImpl` 一处，且只经 `ApothCaps.present()` 门控后调用 |
| 2026-10 | **神化兼容开工：tooltip 缺陷已修 + 两处机制查清（v2.19）**。① **推翻上一轮自己的方案 C**（"提高 mixin 优先级抢在神化前 cancel"）：AGENT.md 第 917 行早有实测结论——**跨模组 mixin 应用顺序不可依赖**（`priority 500/1500 都 Scanned 0 target(s)`）。本轮补到决定性证据：`ItemStack.getTooltipLines` 第 813 行 `EventHooks.onItemTooltip` 是**最后**才发的，附魔行那时已躺在 list 里 → **事件层足够用，不需要 mixin**。② **查清神化改写附魔行的两处机制**（都是源码实证）：`ItemStackMixin`(500) 在 `addToTooltip(ENCHANTMENTS)` HEAD cancel 并自渲染成**等级差形态**；`EnchantmentMixin`(1500) 直接改写 `Enchantment.getFullname`——超过神化上限时返回 **`"🌟 " + 原名`**（lang 键 `text.apothic_enchanting.star_prefix`，实测探针里亲眼见到 `🌟 Mending II`）。③ **修掉匹配失效**：原实现按 `getFullname(root,level)` **精确文本**定位行，装了神化就匹配不上（实测 `nbtLevel=5/realLevel=0` → 神化渲染 `Sharpness 0 (- 5)`）→ 双行且其中一行等级是 0。改为按**名字本体**（`Enchantment` 是 record，第 61 行的 `description` 组件即裸名）匹配，比较前剥掉行首非字母数字（容忍 `🌟` 前缀）；判据全部来自附魔自身，不硬编码语言字符串。**11/11 形态用例通过**（含 `🌟 Sharpness 0 (- 5)` 这种叠加形态）。④ 新增 `compat/apotheosis/ApothCaps`（门控壳，只查 ModList）+ `ApothCapsImpl`（**全项目唯一** import 神化类型的类，P1-40 隔离），提供 B1 语义的 `vanillaCapOf(root, fallback)`——神化缺席/读取失败一律退回原值（`getEnchInfo` 在配置未加载时会抛 `UnsupportedOperationException`，必须兜住）。⑤ **探针教训**：`ItemStack.addToTooltip` 在**专用服务端不会被调用**（tooltip 是客户端行为），本轮为此白跑一次 runServer；tooltip 类改动**只能客户端验证** |
| 2026-10 | **神化 tooltip 冲突实测（v2.18，只调查未修）**。读神化源码（sources jar 从官方 maven 取）定位到冲突面：**Apothic Enchanting 有自己的 `ItemStackMixin`（`priority=500`），在 `ItemStack.addToTooltip(ENCHANTMENTS)` 的 `HEAD` 处 `ci.cancel()`，用它自己的 `TooltipUtil.applyEnchTooltip` 完全接管附魔行渲染**——这是**跨模组 mixin 接管**，不是事件，我们无法用事件优先级绕过。它的判定是 `nbtLevel = 物品上的 minecraft:enchantments` vs `realLevel = ItemStack.getAllEnchantments()`（**后者会走 GetEnchantmentLevelEvent，即我们的结算层**）。**实机实测（runServer）**：进阶物品 `nbt=sharpness:5`、`real=(空，锋利已被我们清零)` → `nbtLevel=5 / realLevel=0` → 判定「等级变了」→ 走 `appendModifiedEnchTooltip`，渲染出 **`Sharpness 0 (- 5)`**（等级数字是 0、还带一个负数差）。同时我们 `TooltipEvents` 按纯文本找的是 `Sharpness V` → **匹配不上、删不掉** → 结果是**双行且其中一行是错的**。次要风险：`ApothEnchEvents.clamp` 也监听 `GetEnchantmentLevelEvent` 并遍历整表调 `ApothicEnchanting.getEnchInfo`，我们注入的**合成 holder 不在它的 `ENCHANTMENT_INFO` 表里** → 落到 `EnchantmentInfo.fallback`（实测该路径 `ench.value().getMaxLevel()`、不调 `getKey()`，故**不崩**，但 `getMaxLevel()` 里那句 `ENCH_HARD_CAPS.getOrDefault(this.ench.getKey(),...)` 对合成 holder 取 key 的行为仍需实机确认）。修复方向与选择待作者定 |
| 2026-10 | **神化（Apotheosis）依赖接入（v2.17）**。为兼容做准备，按神化**官方文档**给出的坐标接入：group 固定 `dev.shadowsoffire`、artifact 首字母大写、版本形如 `<mcVersion>-<modVersion>`，仓库为 `https://maven.shadowsoffire.dev/releases`（**结尾的 `/releases` 不能少，少了整个仓库 404**）。五个 artifact 全部用 Gradle 实测可解析并在 `compileClasspath` 中列为直接依赖：Placebo 9.9.2 / ApothicAttributes 2.11.0 / ApothicSpawners 1.4.0 / ApothicEnchanting 1.6.2 / Apotheosis 8.9.0（均 1.21.1）。Modrinth Maven 一并接入，**7 条依赖全部实测解析成功**（5 条 shadowsoffire + `maven.modrinth:patchouli` + `maven.modrinth:curios`）。**踩点记录（含一次我自己的误判）**：① 神化的传递依赖 Curios / Patchouli 不在 Shadows' Maven，只在 Modrinth Maven；② **Modrinth Maven 的 group 是 `maven.modrinth`（不是 `com.modrinth`）**，artifact 用项目 **slug**，版本用 Modrinth 上的版本号（patchouli `1.21.1-92-neoforge`、curios `9.5.1+1.21.1`）——我一度按 `com.modrinth` 探测、连根路径都 404，据此误判「该仓库不可用」并在上一版记录了错误结论，实际是**路径写错**，用 `maven.modrinth` + `/maven/<group>/<artifact>/maven-metadata.xml` 立即 200；**教训：把「探测失败」当「不存在」之前，先确认自己请求的路径形状是对的**；③ `exclusiveContent` 限定到 `maven.modrinth` 组（该仓库对未知路径一律 404，不限定会拖慢解析）；④ 依赖一律 `compileOnly`（神化不是运行前提，`neoforge.mods.toml` 声明 optional），`runtimeOnly` 仅供开发环境加载；⑤ 硬验证方式是**真的 import 一次**——`Affix` / `ApothEnchantmentMenu` / `DeferredHelper` 三个类 `--rerun-tasks` 强制重编通过，证明解析到 jar 且编译期可引用 |
| 2026-10 | **有条件加伤的口径修正 + 无前置不崩的实测（v2.16）**。上一版把**有条件**的 DAMAGE 效果也计入了 tooltip 差值，导致阶级亡灵杀手这类会凭空多出数字；实测对方自己也用 ConditionalEffect.requirements() 跳过条件效果，故两边统一为**只算无条件**。实测：原版锋利 V 11 ✅ / 阶级究极锋利 V 28 ✅ / 阶级究极亡灵杀手 V **8**（不承诺数字）✅。另实测专用服务端在**摘掉全部前置**时正常启动、[GUARD] 四项门控全 false ✅ |
| 2026-10 | **Prism 彩虹行 + 传说提示框数值适配（v2.15）**。① 究极阶附魔行在装了 Prism 时变成逐字渐变彩虹（24 档、色环错相位、`DynamicColor` 实例缓存以保留动画），实测档位数 6 且 40 tick 后颜色变化 ✅。② 传说提示框两个 mixin（修 MC-271840）读的是**原始附魔组件**，导致阶级究极锋利 V 与普通锋利 V 都是 11；先试「在它注入的方法上再注入」——priority 500/1500 都 `Scanned 0 target(s)`，**跨模组 mixin 顺序不可依赖**（且 `require=1` 会让 `required:false` 的配置也崩客户端）；改为在自己的 `ItemTooltipEvent`里按 `Δ = 结算表加伤 − 原始表加伤` 补正成品行，用 `Component.visit`逐段重建（数值是 translatable 的**参数**、不是 sibling）。实测 11 / **28** / **15.5** 全中。新增 **P1-41** |
| 2026-10 | **提示框栈做成可选依赖（v2.14，为下一步联调铺路）**。为传说提示框 1.5.5 / Iceberg 1.3.2 / Prism 1.0.11 建立依赖：三者都不在公共 maven，故以 `libs/` 本地 jar 走 `compileOnly`（不进 jar）+ `runtimeOnly`（仅开发环境）；`neoforge.mods.toml` 声明 `type="optional"`（写 required 会让漏装者开不了游戏）。新增门控 `compat/tooltip/TooltipStackCompat`（`ModList.isLoaded` 判定 + 「装了才加载对方类」的接入层）。实测：客户端装了 → 三者加载且门控日志正常；摘掉运行期依赖 → `present()=false`、模组照常启动、无 `NoClassDefFoundError`。新增 **P1-40** |
| 2026-10 | **效果总表 v3 全量落地（v3.0）**。作者给定「进阶附魔效果总表（最终版）」并逐条定稿（荆棘概率 ×2/×3/×4、击退究极=受击方按秒、火焰保护超级连着火一起免、荆棘去掉全部追加、究极锋利攻伤按 60%）。数据包模型随之改造：**数值由「全局倍率」改为逐阶绝对值**（表里每行都是该阶总值）、**阶梯长度 1~3 阶可变**、**逐阶 cap 与门槛可覆盖**（门槛默认 = 原版上限 = 满级）。产物 **93 → 59** 个阶段、**31 → 26** 条谱系（移除深海探索者/迅捷潜行/忠诚/引雷/快速装填）。对账中抓到并修掉一类**静默翻倍**：老式 `value(...)` 在「原版那条带条件/已被改写」时合并不上改为追加，导致穿刺/力量/穿透/抢夺/击退/饵钓/海之眷顾/多重射击数值翻倍、爆炸保护超级残留缩放值——新增绝对覆盖原语 `absoluteValue`（有原版条目则沿用其条件只换数值，没有则新建）后全部消失。荆棘按实测结构改写原版 `post_attack`（`LootItemRandomChanceCondition(EnchantmentLevelProvider(Linear))` + 沿用原版 `Holder<DamageType>`）。数值唯一权威 = [docs/ascension-effects-v3.md](docs/ascension-effects-v3.md)。**JEI 实机重测**（客户端直接进存档跑新包）：铁砧配方 **170** 条（进阶 59 / 合并 65 / 升级 46，v2.9 为 286）、材料表 **258** 条（进化 62 / 载体 181 / 升级 15，v2.9 为 399）、创造栏主/搜索标签 **180 / 258**、多条目载体书 **0** 本、配方展示栈编码校验 **662 栈 0 失败** |
| 2026-10 | **撤销 tooltip 的加成行（v2.13，作者要求）**。v2.12 曾在进阶附魔行下插一行 `攻击伤害 +N`；作者要求保持 tooltip 原样，已完整回退：删掉 `TooltipEvents.renderDamageBonus` 与其调用、删掉 `tooltip.ultraenchantment.bonus.damage` 两个语言键（生成语言里已归零）。实测回退后：究极锋利 V 的 tooltip = `[Netherite Sword, Ultra Sharpness V, , When in Main Hand:,  7 Attack Damage,  -2.4 Attack Speed]` ✅。**P1-39 保留为「现象解释」**，并注明不要再插行 |
| 2026-10 | **tooltip 补上真实攻击伤害加成（v2.12）**。作者发现「传说提示框」下阶级究极锋利满级与普通锋利满级的攻击伤害都是 **11**。实测三把下界合金剑：属性段全是 `7 Attack Damage`，真实附魔加伤却是 0 / 3 / **20**——原版属性段只统计 `ATTRIBUTES` 组件（不含 `DAMAGE`），第三方提示框又只按**存储附魔**套原版公式。处理：`TooltipEvents.renderDamageBonus` 在进阶附魔行下自己写 `攻击伤害 +20`（蓝色、属性修正配色，只统计**无条件**加伤；穿刺那种条条件效果不给数字）。新增 **P1-39** |
| 2026-10 | **假人特攻语义补全（v2.11）**。v2.10 的补丁「查不到注册表 key 就返回 false」只解决了崩溃，却把语义丢了：作者给假人戴海龟壳（`AQUATIC`）后，阶级穿刺吃不到加成（实测伤害 2.0＝零加成，而原版穿刺 V 是 14.5）。改为**按谱系根源回答**：`EnchantmentFactory` 新增「组装对象 → `stage.root()`」身份表（`rootOf`），补丁把根源当 key 交回，再按对方三条规则（smite→UNDEAD / bane→ARTHROPOD / impaling→AQUATIC）回答。复验：四个判定全部正确，海龟壳假人挨阶级穿刺 I = **9.5**（= 2.0 + 2.5×3.0，与阶级表一致）。P1-38 补记「兼容补丁的验收标准是行为一致，不是不崩」 |
| 2026-10 | **试验假人（dummmmmmy）兼容补丁（v2.10）**。「进阶物品打假人没伤害 / 投三叉戟崩服且一进世界就崩」——真凶是对方 `DummyMobType.isVulnerableTo` 对每个附魔做 `getResourceKey(...).get()`，而我们的阶级附魔是运行时合成、**没有注册表 key**。新增独立配置 `ultraenchantment.compat.mixins.json` + `UECompatMixinPlugin` 门控的 `DummyMobTypeCompatMixin`（只在该模组存在时应用）。实测：补丁关闭时直接调用崩 `NoSuchElementException`、锋利的伤害这条路也崩；开启后 `isVulnerableTo(合成附魔)=false`、进阶穿刺三叉戟对假人 2.0、**进阶锋利 3.0→5.0**（伤害确实生效）。新增 **P1-38**（含两个连带坑：`shouldApplyMixin` 里 ModList 为 null 会让整份配置作废；dev 里对方 jar 的文件名必须 ASCII） |
| 2026-10 | **JEI 配方页按作者四条要求重做（v2.9）**。① 配方上那行字改为 **附魔进阶 / 附魔合并 / 附魔升级**（类别标题改「附魔铁砧」避重名）；② 补上**书与书的操作**（附魔合并 115 条：两本载体书合并、载体书+进阶书推阶、载体书+升级书提升、原版附魔书+进阶书转印、两本升级书合并，结果全走 `InscriptionLogic` 真函数）；③ **中间等级改轮播**（槽位吃列表，JEI 自己轮播），不再每等级铺一条；④ 附魔进阶左槽显示**刚好够门槛的等级**，并给「达到满级 / 达到 N 级（上限 M 级）」的悬停说明；⑤ 类别图标改为**三本书轮播**。实测 `附魔铁砧配方 286 条（进阶 93 / 合并 115 / 升级 78）`、`配方展示栈编码校验 1092 个栈失败 0 个`。踩坑见 P1-36 补记（静默拒绝用数量发现） |
| 2026-10 | **创造栏回归「只放单条目载体书」（v2.8）**。作者纠正：搜索标签里出现多条目载体书**本身就违约**——最初定夺的创造栏投放只有「谱系 × 阶级 × 等级」这一条单条目轴，「一本刻多条」应当只由玩家在铁砧上合并产生（规格 §5.4）。v2.7 里我把样本改成「可共用物品的组合」是**方向错了**：仍是多条目书，作者圈红的地方当然还在。本次把 3 条样本**整个删除**，校验反过来做成「创造栏出现多条目书就报错」。实测：材料表 402 → **399** 条（进化 96 / 载体 288 / 升级 15），`创造栏多条目载体书 0 本`。另记 **P1-37** |
| 2026-10 | **穿刺纳入 + 样本配对修正 + JEI 配方页（v2.7）**。① 按作者要求把 `impaling`（穿刺，三叉戟）纳入谱系表，**三档补丁全为 `none()`**——只走它自己的原版效果（含原版 `#minecraft:sensitive_to_impaling` 条件），阶级成长只由曲线缩放体现，不挂额外属性；谱系 30 → **31**，阶段文件 90 → **93**。② 修掉搜索标签的多条目样本「**爆炸保护 + 引雷**」——护甲与三叉戟永远贴不到同一件物品上，直接违背该方法自己写的「不做假数据，样本要能真的用出去」；改为按 `supported_items` 交集挑伙伴，样本变成「爆炸保护 + 深海探索者」（同一双靴子）。③ JEI 新增“附魔进阶（铁砧）”配方类别，数据包动态生成 279 条（31 谱系 × 3 阶级 × 进阶/铭刻/升级），铁砧作催化剂。④ 把「多条目书必须能贴到同一件物品上」做成**可失败的检查**（遍历 JEI 材料表逐本验，见 `checkMultiEntryBooks`），实测：多条目书 3 本、条目无法共用的 **0 本** |
| 2026-10 | **修掉「物品 tooltip 漏了等级数字省略」（v2.6）**。书那边按 P1-32 省略了，物品那边 `renderStagedLine` 却无条件拼数字——于是 5 条原版上限为 1 的谱系（经验修补 / 引雷 / 火矢 / 无限 / 多重射击）在物品上成了「高阶经验修补 I」。根因是**一条规则两份实现**：判定收进 `StageLookup.displayLevelCap`，物品与书共用；规格 §15 补上此前根本没有的「物品上的进阶附魔行」一节。自检两侧边界 22/22 通过 |
| 2026-09 | **接入 JEI 并修掉「几百本书塌成一本」（v2.5）**。装 JEI（`compileOnly` API + `runtimeOnly` 完整版，不进 jar）后实测：不注册 subtype 时 JEI 材料表里进阶附魔书只有 **1** 条、祛咒石 **1** 条；注册后 **384** 条（进化 93 / 载体 276 / 升级 15）、祛咒石 3 条。新增 `compat/jei/UEJeiPlugin`（`@JeiPlugin` + `ISubtypeInterpreter`，键取语义载荷的 JSON），并留下 `[JEI]` 计数日志当可失败的证据。新增 **P1-36** |
| 2026-09 | **修掉「单机创造模式放铁砧即掉线」（v2.4）**。用工作区里的客户端日志（`run/logs` 轮转归档）定位到真实异常：`Failed to encode packet 'serverbound/container_click'` ← `Can't find id for 'Reference[minecraft:enchantment / minecraft:smite]'`。真凶是**注册表侧别错配**：`CommonHooks.resolveLookup` 在单机的客户端线程上也返回**服务端**注册表，于是创造旁路把服务端 Holder 写进客户端栈，客户端发包时查不到 ID → 掉线。新增 `logic/UELookups.enchantmentsForItemWrites(clientSide)`（按生成侧选表，客户端分支独立成嵌套类以防 P0-16），并在写入前加 `unwrapKey().isPresent()` 守卫。新增 **P0-17** 并写进 §6 清单 |
| 2026-09 | **修掉「铁砧放入即掉线」（v2.3）**。真凶是**依赖方向**：运行期 `BookFactory` 调了 datagen 的 `UEModels.predicateOf`，而后者继承客户端模型生成器 `ItemModelProvider`——dev 环境两端同 classpath 测不出来，只跑服务端的发行版里在**包处理链**抛 `NoClassDefFoundError`，表现就是玩家立刻掉线。把编码算术下沉到运行期 `content/BookView`，datagen 反过来调它（模型 JSON **逐字未变**，已 diff 验证）。新增 **P0-16**（运行期不得引用 datagen/客户端类）并写进 §6 清单 |
| 2026-09 | **两条实机 bug 修复 + 三处加固（v2.2）**。① 「进阶书 / 升级书推不动载体书」——分派顺序问题：两本都是我们的书时先走合并，而合并不认「载体书 + 进阶书」，整条路被吞（**P1-35**）；补上 `applyBookAdvance`，双宿主对称终于闭环（定向进阶书对多条目书整本拒绝，因为书的阶级是书级单值）。② 「创造模式用高阶段书贴物品导致掉线」——按 **P0-15** 处理：铁砧入口与取件入口整体兜底（异常降级为「本次不产出」+ 错误日志），存储等级一律夹进 `min(该阶上限, 原版上限, 255)`。复现尝试：90 个阶段条目全谱系全阶级的产物做网络编码往返 + 结算 + tooltip（93 次编码全绿）、创造旁路直接解析 90 例（零异常）——数据与逻辑侧不可复现，故按「包处理链异常」堵死并留日志 |
| 2026-09 | **创造栏铭刻书排布修正（v2.1）**。两处「看起来像数据乱了」的问题：① 主标签用**阶级轮转**取样（v1.3 起），同一条谱系的三个阶级被拆散成「高阶X / 超级Y / 究极Z」，玩家第一反应是阶级串了行——改为**谱系外层 × 阶级内层**（90 条，同一附魔的高阶/超级/究极相邻递增），覆盖从「每条谱系 1 次」变成 3 次；② v2.0 新增的**多条目「已合并」样本**放错了标签页：它与单条书共用同一材质，摆在主标签里就像「某条附魔的书莫名多了一条别的附魔」（排序后前两条谱系恰好是爆炸保护与引雷，于是三条样本全是「爆炸保护 + 引雷」）——移到搜索标签。顺手把计划表抽成 `CreativeTabEvents.mainTabInscriptions()` 纯函数并自检（8/8） |
| 2026-09 | **载体书体系（v2.0）**。把「进阶形态」做成有实物载体的东西，一次解掉三个结构性问题：① **原版附魔书被进阶后**不再是「隐形印章」（物品 id 不变、提示框看不见、贴装备时随书蒸发、还会漏进砂轮）——改为**转印**：原版附魔书 + 「基础→高阶」进阶书 → 我们的**载体书**，物品 id 真的变了，条目从曲线 1 起，门槛复用基础阶的 `min(required, 原版上限)`（锋利要一本锋利 V）；② **同名合并升 1 级**在书层面与装备层面同时落地（README §2/§7 早就写了规则，一直没实现）：书同级 → `level+1`（逐谱系夹取）、不同级取 max；装备同级 → `tierLevel+1`；③ 铭刻型载荷由单条改为 **`{tier, entries[]}` 多条目**，`level` 语义 = tierLevel（存储等级升阶后恒为上限，拿它 +1 是空转）。生存与创造分叉：生存只能提**同阶级**的曲线等级，创造可给白装备直上究极（仍受 `supportsEnchantment` 与防降级约束）。新增 **P0-14**（剩菜书 = 唯一第二输出，只能走 `AnvilRepairEvent`），并放开 P1-13 一处例外（装备同名合并必须重写原版合并数学，连带必须照抄重命名，否则是回归）。规格 `docs/book-system-spec.md`，44 项运行时自检全通过 |
| 2026-09 | **文档重写（v1.0）**。架构从「三附魔分立」翻转为「原地升级 + 独立注册表 + 运行时组装」，与首版完全不相容，旧内容全部作废。记录 P0×10 / P1×6 / P2×7 共 23 个坑（当时值，现已有增补），以及 9 条被否方案 |
| 2026-09 | **改为纯覆盖模型 + 数据包指南（v1.8）**。① 确认「覆盖」= **每阶一条自己的完整公式，原版数字完全不参与**（锋利 3+1 / 6+1.5 / 10+2.5），实现为 `scaleCore`：把原版所有「加法+线性」数值按阶级倍率（首级值 ×3/×6/×10、每级增量 ×2/×3/×5）整体替换，**条件照原版保留**。② 生成管线里删掉自动合并；`mergeIntoVanilla` 保留为第三方可选写法（本模组 30 条谱系一条不用）。③ 新增 `none()` 空补丁——多数谱系的高阶只改数值、不新增效果。④ 同步删掉 19 条谱系里与覆盖重复的「加成」补丁。⑤ 新增 docs/guide-data.md：三种写法的边界与原理、阶级倍率规格、八类效果的完整 JSON 做法、自检与常见错误 |
| 2026-09 | **tierLevel 成为效果等级（v1.7）**。确认并落地：`tierLevel` 就是进阶形态自己的附魔等级，`EnchantmentLevelEvents` 注入阶段条目时改用它（存储等级只留给原版机制）。此前按存储等级结算，导致「高阶锋利 1 级」按 5 级算、升级书纯装饰。连带修正三处**写反了的**文档（README §4 / `AscensionData` / `AnvilEvents`，以及 P1-20 本身）。升级书目标值改为夹在该谱系该阶级的 `max_level` 内 |
| 2026-09 | **合并式覆盖 + 击退究极效果（v1.6）**。① 新增 `UEStageEffects.mergeIntoVanilla`：格式相同的加成自动合并进原式改成覆盖（锋利伤害 → `2 + 1×(等级-1)`，经验修补 → `×3/×4.5/×9`，属性同理）；带条件的与多条分流的整组放弃合并。② `Replace` 泛化为 `Replace<T>`，列表组件也能覆盖。③ 合并踩到一次 unchecked 强转的坑：按组件强转前必须先通配读取确认元素类型，否则 `post_attack` 的 `TargetedConditionalEffect` 会在 datagen 中途抛 `ClassCastException`——已记入类文档。④ 击退究极阶改为「命中后使目标中缓慢（5 秒起 +5/级，强度 I 起 +1/级）」 |
| 2026-09 | **效果总表（v1.5）**。新增 `UEEffectDoc`：`runData` 顺带导出 `docs/enchantment-effects.md`（30 谱系 × 3 阶），列 = 谱系/阶级/上限/门槛/**效果摘要**/阶位加成/**满级合计**；摘要里**粗体**就是进阶多给的那部分，加成标「追加」或「覆盖」。配套把 `UEStageEffects.Patch` 从不透明 `UnaryOperator` 改成声明式 `Append`/`Replace` 并自带人话描述——表不再靠「与原版逐条比对」反推（加成恰好与原版同式时会失效），`UEStages.GeneratedStage` 同时带出原版那份与补丁列表。表是生成物，改数值仍走 `LineageTable` |
| 2026-09 | **定向进阶书语义与显示修正（v1.4）**。① **规格错了**：README 把定向书的 h3 写成「具体附魔名」，实现照做，于是「高阶→超级」显示成「锋利 → 超级锋利」，**中间少一阶**；规格与实现同改（h3 = `from_tier` 的阶段名）。② 把「父子」约束落进代码：目标只能是当前阶段的 `next` 且阶级须等于 `to_tier`，基础阶只能进**表头**——跳阶书一律不生效。③ 定向书改为**两个标签全量投放**（90 条），不再走样本轮转（那样每条谱系只露一个随机阶级）。④ 主标签 24 → 138、搜索标签 381 不变。新增 **P1-30** |
| 2026-09 | **主标签样本改为全谱系覆盖（v1.3）**。原「每个阶级换一条谱系」只让 3 条谱系露面（30 条里 27 条缺席），改为**每条谱系都露面一次、阶级沿谱系顺序轮转**：主标签 24 → **78** 条，谱系覆盖 30/30，两个科目各 10/10/10 阶级分布。新增 **P1-29**（覆盖要按谱系总数算） |
| 2026-09 | **修复创造栏整块缺失（v1.2）**。`ReloadEvents` 在 `TagsUpdatedEvent` 里用 `CommonHooks.resolveLookup` 取注册表，而那一刻服务器对象尚未存在 → 恒为 null → 谱系矩阵永远为空：搜索标签只剩 18 条（应为 381）、主标签 6 条谱系样本**全灭**，且不报错。改用 `event.getRegistryAccess()`，新增 **P0-13**，并收窄 P1-10 的适用范围。定向进阶书末行改为直接引用阶段名键（中文不再显示成「高阶 锋利」，且可被整合包覆盖） |
| 2026-09 | **数据包铺开（v1.1）**。谱系表从 1 条（锋利）扩到 **30 条玩家常用**附魔（武器/护甲/通用工具/弓弩/三叉戟/钓鱼）；效果模型改为「**原版 effects 整段搬用 + 阶级累积加成**」，`definition` 整段取自原版附魔（不再手工维护物品标签、槽位与等级上限）；写出改走 `UEStagePack`，新增 **P0-12**（外部 holder 与 provider owner）；等级上限定为逐谱系后新增 **P1-28**（按谱系查上限）。90 个阶段条目 + 90 组阶梯名全部生成并自检通过 |

---

*本文件是活的。每踩到一个新坑，追加到 §3，并标注严重度与源码行号证据。*
