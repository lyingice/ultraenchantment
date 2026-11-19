# Ultra Enchantment · Lightstar

一个 NeoForge 1.21.1 的**附魔进化**原型。

不是“给原版附魔加数值”，而是让一件附魔**变成另一种附魔**：

```
minecraft:sharpness
        │  锋利 V + 5 钻石
        ├──────────────────────────────┐
        ▼                              ▼
jlme:advanced_sharpness        fancyenchantments:advanced_sharpness
        │  3 级 + 2 下界合金锭          │  3 级 + 2 下界合金锭
        ▼                              ▼
jlme:supreme_sharpness         fancyenchantments:sacred_supreme_sharpness
```

## 核心特点

- **每个节点都是真附魔**：树上的节点不是复制出来的“阶段附魔”，而是原版或
  联动模组**已经存在**的附魔。物品上携带的就是那个附魔本身，因此源模组的
  数据效果、代码效果、反查 key / 序列化 / 读 tag 全部照常工作。
- **跨模组分支**：同一个原版根源可以有多条进化分支（例如锋利可以走 JLME 线
  或 Fancy 线），由数据包把不同模组的真实变体附魔接到同一棵树上。
- **数据包驱动**：阶级、进化图、分支、概率、代价全部是 JSON。
- **KubeJS 联动**：脚本里通过 `UltraEnchantment` 查询/进化/造阶段书；
  数据包 JSON 也可由 KubeJS 注入，见 [`docs/kubejs.md`](docs/kubejs.md)。
- **星光附魔台**：放入带附魔的物品，星图展开；节点是星，连线是进化路径，
  当前星脉冲、可选星闪烁、已走路径金色；右侧详情卡显示条件、代价、成功率。
  物品同时带多个可进阶附魔时，详情面板标题两侧的 `◀` / `▶` 可以切换谱系。
- **附魔进阶总览**：按键默认**不绑定**；在「选项 → 控制 → 按键绑定」里给
  「打开附魔进阶总览」设置按键后，可随时查看所有树的根→叶路线、阶级、
  前置等级、成功率、经验与材料，并支持按附魔名搜索。
- **概率与代价**：每条边有独立成功率；经验与材料在尝试时扣除，失败不退，
  且当前附魔等级随机下降 2~4 级。
- **书架联动**：经验代价随目标阶级抬高（高阶 6 / 超级 10 / 究极 18 级），
  按原版附魔台摆法在台面周围放书架（至多 15 个）线性减免，满书架减半
  （3 / 5 / 9 级）；阶级内升级也吃折扣。低阶便宜、高阶值得搭书架墙。
- **阶级内升级**：进化改变“物种”，升级只提高当前物种的等级——两条曲线正交。

## 内容包

本分支**不内置任何附魔树**（旧的 5 个示例阶段附魔已删除）。
`content_pack/` 把以下两个模组自带的**进阶变体附魔**接成进化树：

- **Just a lot more enchantments**（`jlme`）
- **Fancy Enchantments**（`fancyenchantments`）

共 **8 棵树 / 11 条分支**，使用 **17 个模组真实变体附魔**，没有复制或生成任何附魔：

| 原版根源 | 分支 |
|---|---|
| 锋利 | JLME `advanced → supreme`；Fancy `advanced → sacred supreme` |
| 节肢杀手 | JLME `advanced → supreme` |
| 亡灵杀手 | JLME `advanced → supreme` |
| 抢夺 | JLME `advanced`；Fancy `advanced → greed supreme` |
| 力量 | JLME `advanced` |
| 保护 | JLME `advanced`；Fancy `advanced → mountain supreme` |
| 火焰附加 | Fancy `advanced` |
| 火矢 | Fancy `advanced` |

阶级对应：原版根源 = native，`advanced_*` = advanced，`supreme_*` = super。
安装：把 `content_pack/` 放进 `world/datapacks/ultraenchantment_integration/`，
并安装对应模组。重新生成：

```bash
py tools/generate_integration_pack.py
```

## 当前状态

**当前版本 `1.0-beta-2`（Lightstar 分支）**。旧版进阶系统（阶段注册表、铁砧书流程、图书馆、
旧进阶台、旧版图鉴、祛咒石、村民交易、战利品注入、旧 API 与兼容层）已整体移除。

保留下来的只有三张书皮贴图与对应的封面模型。附魔书使用原版
`minecraft:enchanted_book`，阶级附魔写在原版 `stored_enchantments` 里；
客户端 Mixin 在 `ItemRenderer.getModel` 返回处根据书上附魔所属阶级
替换封面。**没有自定义书物品，也没有自定义数据组件。**

联动包里的变体附魔书由数据包 / 战利品 / 命令 / KubeJS 通过
`stored_enchantments` 生成；没有阶级附魔的原版书保持原版外观。
框架仍保留 `ultraenchantment:evolution` 组件，用于将来没有真实变体附魔的
内容，本联动包不使用。创造栏也不再投放自定义书样本。

## 构建

```bash
./gradlew build
```

产物：`build/libs/ue-starlight-1.0-beta-2.jar`

## 星光附魔台配方

```
C . C
. E .
C A C
```

- `C` = 哭泣黑曜石 ×4
- `E` = 附魔台
- `A` = 紫水晶碎片

物品品质为**史诗**。

## 试玩

先安装上述两个模组与联动数据包，然后：

```mcfunction
/give @s ultraenchantment:starlight_table
/give @s minecraft:diamond_sword
/enchant @s minecraft:sharpness 5
/xp add @s 50 levels
```

1. 放下星光附魔台，像原版附魔台一样在周围摆书架（满 15 个经验减半），右键打开；
2. 把锋利 V 的剑放进左侧物品槽，右侧材料槽放 5 个钻石；
3. 星图上出现两条分支：JLME 高阶锋利、Fancy 高阶锋利；
4. 点亮其中一颗高阶星，剑变成对应的真实变体附魔 I；
5. 把该变体升到 3 级，准备 2 个下界合金锭，点亮它后面的超级星
   （JLME 至高锋利 / Fancy 圣洁超级锋利）；
6. 另一条分支仍然保留，可以在分叉点改走另一条路线。

## 数据格式

详细 schema、运行时流程与扩展方式见 [`docs/starlight.md`](docs/starlight.md)。

## 路线图

- 星图视觉迭代：贴图、拖拽/缩放、粒子与音效
- 保底计数、洗点（退回分叉点）
- 冲突组强制执行 + 高阶槽位预算
- 进阶总览支持节点拖拽/缩放与更多筛选
- 融合进化（多父节点）
- 更多模组的内容包按同一生成器扩展

## 许可

MIT
