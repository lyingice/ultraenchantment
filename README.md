# Ultra Enchantment · Lightstar

一个 NeoForge 1.21.1 的**附魔进化**原型。

不是“给原版附魔加数值”，而是让一件附魔**变成另一种附魔**：

```
minecraft:sharpness
        │  第一次进化（原附魔被消耗）
        ▼
ultraenchantment:starlight/advanced_sharpness
        │
        ├── 血刃分支 ──> super_sharpness_blood ──> ultra_sharpness_blood
        └── 破甲分支 ──> super_sharpness_armor ──> ultra_sharpness_armor
```

## 核心特点

- **阶段是真附魔**：每一阶都是 `minecraft:enchantment` 注册表里的真实条目，
  物品上携带的就是进化后的附魔。没有合成 `Holder.Direct`，没有
  `GetEnchantmentLevelEvent` 注入，因此第三方模组反查 key / 序列化 / 读 tag 都不会崩。
- **数据包驱动**：阶级、进化图、分支、概率、代价全部是 JSON。
- **KubeJS 联动**：脚本里通过 `UltraEnchantment` 查询/进化/造阶段书；
  数据包 JSON 也可由 KubeJS 注入，见 [`docs/kubejs.md`](docs/kubejs.md)。
- **星光附魔台**：放入带附魔的物品，星图展开；节点是星，连线是进化路径，
  当前星脉冲、可选星闪烁、已走路径金色；右侧详情卡显示条件、代价、成功率。
- **概率与保底位**：每条边有独立成功率；失败只扣经验与催化剂，不毁物品。
- **阶级内升级**：进化改变“物种”，升级只提高当前物种的等级——两条曲线正交。

## 当前状态

**原型（0.2.x / Lightstar 分支）**。旧版进阶系统（阶段注册表、铁砧书流程、图书馆、
旧进阶台、图鉴、祛咒石、村民交易、战利品注入、旧 API 与兼容层）已整体移除。

保留下来的只有三张书皮贴图与对应的封面模型。附魔书使用原版
`minecraft:enchanted_book`，阶段附魔写在原版 `stored_enchantments` 里；
客户端 Mixin 在 `ItemRenderer.getModel` 返回处根据书上阶段附魔所属阶级
替换封面。**没有自定义书物品，也没有自定义数据组件。**

阶段书由数据包 / 战利品 / 命令通过 `stored_enchantments` 生成；
没有阶段附魔的原版书保持原版外观。创造栏也不再投放自定义书样本。

## 构建

```bash
./gradlew build
```

## 试玩

```mcfunction
/give @s ultraenchantment:starlight_table
/give @s minecraft:diamond_sword
/enchant @s minecraft:sharpness 5
/xp add @s 50 levels
```

1. 放下星光附魔台，右键打开；
2. 把锋利 V 的剑放进左侧物品槽；
3. 点星图上的高阶星，右侧出现“点亮此星”；
4. 成功后剑变成高阶锋利 I；点当前星可“提升等级”；
5. 升到 3 级后，血刃 / 破甲两条分支星会闪烁，二选一；
6. 超级分支升到 4 级后，可继续点亮究极星。

## 数据格式

详细 schema、运行时流程与扩展方式见 [`docs/starlight.md`](docs/starlight.md)。

## 路线图

- 星图视觉迭代：贴图、拖拽/缩放、粒子与音效
- 保底计数、洗点（退回分叉点）
- 冲突组强制执行 + 高阶槽位预算
- 图鉴复用同一份星图渲染器
- 融合进化（多父节点）
- 内容包独立发布

## 许可

MIT
