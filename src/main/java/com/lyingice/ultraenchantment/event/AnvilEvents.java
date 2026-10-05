package com.lyingice.ultraenchantment.event;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.api.event.UltraEnchantLockChangeEvent;
import com.lyingice.ultraenchantment.api.event.UltraEnchantTierUpgradeEvent;
import com.lyingice.ultraenchantment.content.AscensionData;
import com.lyingice.ultraenchantment.content.AscensionTier;
import com.lyingice.ultraenchantment.content.BookSpecs;
import com.lyingice.ultraenchantment.content.BookSubject;
import com.lyingice.ultraenchantment.content.LineageTier;
import com.lyingice.ultraenchantment.content.StageDefinition;
import com.lyingice.ultraenchantment.logic.AscensionLogic;
import com.lyingice.ultraenchantment.logic.UEEnchantRegistry;
import com.lyingice.ultraenchantment.logic.UEEvents;
import com.lyingice.ultraenchantment.logic.UERoots;
import com.lyingice.ultraenchantment.logic.BookFactory;
import com.lyingice.ultraenchantment.logic.InscriptionLogic;
import com.lyingice.ultraenchantment.logic.ItemMergeLogic;
import com.lyingice.ultraenchantment.logic.StageLookup;
import com.lyingice.ultraenchantment.logic.UELookups;
import com.lyingice.ultraenchantment.registry.UEComponents;
import com.lyingice.ultraenchantment.registry.UEItems;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * <b>铁砧链路</b>——进阶体系在铁砧上的全部入口。
 *
 * <p>接管点在 {@link AnvilUpdateEvent}：只要 {@code setOutput} 一个非空栈，
 * {@code CommonHooks.onAnvilChange} 就会写入结果槽并 <b>{@code return false} 跳过全部原版逻辑</b>
 * （源码实证：{@code CommonHooks.onAnvilChange} 第 693-699 行）。
 * 因此这一环不需要 Mixin。
 *
 * <h2>五条入口（v2 规格，见 docs/book-system-spec.md）</h2>
 *
 * <ol>
 *   <li><b>书 + 书</b>——两本我们的书 → 合并（载体书并条目、升级书升目标等级）</li>
 *   <li><b>进阶书</b>——谱系推进一阶；左槽是原版附魔书时改为<b>转印</b>（产出载体书）</li>
 *   <li><b>载体书</b>（铭刻型）——贴装备：生存只能提同阶级的曲线等级，创造可给白装备直接上阶级</li>
 *   <li><b>升级书</b>——只提曲线等级</li>
 *   <li><b>同名装备合并</b>——两侧都有阶段记录的同名装备 → 曲线等级 +1（§6）</li>
 * </ol>
 *
 * <h2>三个必须遵守的约束（都是已登记的坑）</h2>
 *
 * <ol>
 *   <li><b>设置输出就必须设置成本</b>：事件构造时 {@code cost = baseCost = 0}，
 *       而 {@code AnvilMenu.mayPickup} 要求 {@code cost > 0}，否则输出看得见却拿不走（P0-5）。</li>
 *   <li><b>{@code materialCost} 的 0/正数语义是反的</b>：0 = 消耗整个右槽堆叠，
 *       1 = 只消耗 1 个（P0-6）。书是 {@code stacksTo(1)}，两者等价，仍显式写 1；
 *       而同名装备合并要的正是「吃掉右槽那一件」，因此那里显式写 0。</li>
 *   <li><b>必须在「右槽为空或非本模组书」时也接管</b>：原版会把超过 {@code max_level}
 *       的附魔等级砍回去（{@code if (j2 > enchantment.getMaxLevel()) j2 = ...}），
 *       这会废掉进阶附魔的高等级（P0-1）。</li>
 * </ol>
 *
 * <h2>两侧一致</h2>
 *
 * <p>{@code AnvilMenu.createResult} 在客户端也执行（P0-4），因此本处理器是纯函数：
 * 只读输入栈与事件 getter，只写事件 setter，不碰世界 / 背包 / 随机数。
 * 需要副作用的只剩「剩菜书交付」，它被单独放在 {@link AnvilTakeEvents}（服务端）。
 */
public final class AnvilEvents {
    private AnvilEvents() {}

    /** 单例监听器，供 game bus 注册。 */
    public static final AnvilEvents INSTANCE = new AnvilEvents();

    private static final int UPGRADE_COST = 3;

    /**
     * 铁砧入口。
     *
     * <p>⚠️ 本方法运行在**容器包处理链**里（{@code AnvilMenu.createResult} 由点击/同步触发）。
     * 一旦抛出未捕获异常，服务端线程会随之中断，客户端看到的是「失去世界连接」——
     * 一个数据错误被放大成掉线。因此这里整体兜底：**任何异常都降级为「本次不产出」**，
     * 并且打一条带异常的错误日志（两侧都会走同一条确定性路径，所以两边都降级、不会错位）。
     */
    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        try {
            handleAnvilUpdate(event);
        } catch (RuntimeException | LinkageError t) {
            event.setOutput(ItemStack.EMPTY);
            event.setCost(0);
            Ultraenchantment.LOGGER.error("Anvil handling failed (operation refused)", t);
        }
    }

    private void handleAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        if (left.isEmpty()) {
            return;
        }

        ItemStack right = event.getRight();
        boolean leftIsOurBook = isOurBook(left);
        boolean rightIsOurBook = isOurBook(right);
        boolean leftHasStage = !UEComponents.ascensionOf(left).stages().isEmpty();

        // ① 两本都是我们的书 → 书 + 书合并。
        //    必须在「右槽是我们的书」之前判：否则会被当成「拿书去作用于一本书」，两边都不生效。
        //    ⚠️ v2.0 漏了这一步：载体书 + 进阶书 落进 applyBookMerge，而合并只认
        //    「载体书 + 载体书 / 升级书 + 升级书」，于是规格里写明的「把铭刻书升到下一阶」
        //    根本没有实现（表现：点上去毫无反应）。
        if (leftIsOurBook && rightIsOurBook) {
            if (!applyBookAdvance(event, left, right)) {
                applyBookMerge(event, left, right);
            }
            return;
        }

        // ② 右槽是我们的书 → 三类书的使用入口。
        if (rightIsOurBook) {
            dispatchBySubject(event, left, right);
            return;
        }

        // ③ 两条都不沾：完全不介入，交给原版。
        if (!leftHasStage) {
            return;
        }

        // ④ 两侧都有阶段记录的同名装备 → 同名合并（曲线等级 +1）。
        if (trySameItemMerge(event, left, right)) {
            return;
        }

        // ⑤ 右槽不是我们的书，但左槽物品带阶段数据 —— 必须接管以防止原版砍等级（P0-1）。
        //
        // 注意：这里**不实现**原版的修复/合并数学，只做一件事——
        // 若右槽是可用于修复的材料，就按原版的可预期行为修耐久并把等级原样保留；
        // 其余情况直接原样输出（等于「带阶段数据的物品在铁砧上不发生任何变化」），
        // 宁可少做事，也不要在铁砧里重写一遍原版逻辑（那会引入难以验证的分支）。
        preventLevelClamp(event, left, right);
    }

    // ── 科目分派 ────────────────────────────────────────────────────────

    private void dispatchBySubject(AnvilUpdateEvent event, ItemStack left, ItemStack right) {
        HolderLookup.RegistryLookup<StageDefinition> stages = StageLookup.lookup();
        // ⚠️ 必须按「生成这一侧」取注册表：写进物品的 Holder 要能被这一侧的编码器查到 ID。
        // 用 CommonHooks.resolveLookup 会在单机的客户端线程上拿到服务端注册表 → 掉线（§17.4）。
        boolean clientSide = event.getPlayer() != null && event.getPlayer().level().isClientSide();
        HolderLookup.RegistryLookup<Enchantment> enchants = UELookups.enchantmentsForItemWrites(clientSide);
        if (stages == null || enchants == null) {
            return;
        }

        boolean creative = isCreative(event);

        BookSpecs.Ascension ascension = right.get(UEComponents.ASCENSION_SPEC.get());
        if (ascension != null) {
            // 左槽是原版附魔书 → 转印（原版附魔书 → 载体书），而不是给它盖一个隐形印章。
            if (left.is(Items.ENCHANTED_BOOK)) {
                applyTranscription(event, left, ascension, creative);
                return;
            }
            AscensionLogic.resolveAscension(stages, enchants, left, ascension, creative)
                    .ifPresent(a -> applyAscension(event, left, a));
            return;
        }

        BookSpecs.Inscription inscription = right.get(UEComponents.INSCRIPTION_SPEC.get());
        if (inscription != null) {
            applyInscription(event, left, inscription, enchants, stages, creative);
            return;
        }

        BookSpecs.Upgrade upgrade = right.get(UEComponents.UPGRADE_SPEC.get());
        if (upgrade != null) {
            applyUpgrade(event, left, upgrade, stages);
        }
    }

    /**
     * 载体书 + 进阶书 / 升级书 —— 把<b>书</b>当作宿主来推进（规格 §4「双宿主对称」）。
     *
     * <ul>
     *   <li>进阶书：整本书的阶级推进一阶，所有条目曲线等级归 1；</li>
     *   <li>升级书：所有条目的曲线等级提到目标值（逐条夹取）。</li>
     * </ul>
     *
     * <p>定向进阶书只认它铭刻的那条附魔：多条目书里混了别的附魔就**整本拒绝**
     * （书的阶级是书级单值，拆不开），请改用通用进阶书。
     *
     * @return {@code true} = 已接管并设置输出（调用方不要再走合并）
     */
    private boolean applyBookAdvance(AnvilUpdateEvent event, ItemStack left, ItemStack right) {
        BookSpecs.Inscription book = left.get(UEComponents.INSCRIPTION_SPEC.get());
        if (book == null) {
            return false;
        }
        HolderLookup.RegistryLookup<StageDefinition> stages = StageLookup.lookup();
        if (stages == null) {
            return false;
        }

        BookSpecs.Ascension ascension = right.get(UEComponents.ASCENSION_SPEC.get());
        if (ascension != null) {
            AscensionTier toTier = AscensionTier.of(ascension.toTier()).orElse(null);
            if (toTier == null) {
                return false;
            }
            if (ascension.isTargeted()) {
                ResourceLocation pinned = ascension.applicable().orElseThrow();
                for (BookSpecs.Inscription.Entry entry : book.entries()) {
                    if (!entry.enchantment().equals(pinned)) {
                        return false;
                    }
                }
            }
            Optional<BookSpecs.Inscription> advanced = InscriptionLogic.advanceTier(
                    stages, book, ascension.fromTier(), toTier, isCreative(event));
            if (advanced.isEmpty()) {
                return false;
            }
            int cost = 1;
            for (BookSpecs.Inscription.Entry entry : advanced.get().entries()) {
                StageDefinition stage = StageLookup
                        .stageOf(stages, entry.enchantment(), toTier.asLineageTier()).orElse(null);
                if (stage != null) {
                    cost = Math.max(cost, stage.definition().anvilCost());
                }
            }
            event.setOutput(BookFactory.inscription(advanced.get()));
            event.setCost(cost);
            event.setMaterialCost(1);
            return true;
        }

        BookSpecs.Upgrade upgrade = right.get(UEComponents.UPGRADE_SPEC.get());
        if (upgrade != null) {
            Optional<BookSpecs.Inscription> raised = InscriptionLogic.upgradeEntries(
                    stages, book, upgrade.tier(), upgrade.targetLevel());
            if (raised.isEmpty()) {
                return false;
            }
            event.setOutput(BookFactory.inscription(raised.get()));
            event.setCost(UPGRADE_COST);
            event.setMaterialCost(1);
            return true;
        }
        return false;
    }

    /** 创造模式旁路：调试时无视等级/阶级门槛（规格明确允许）。 */
    private static boolean isCreative(AnvilUpdateEvent event) {
        // AnvilUpdateEvent.getPlayer() 在 21.1 是现成的（非空）——见 AGENT.md P1-21。
        return event.getPlayer() != null && event.getPlayer().isCreative();
    }

    // ── ① 书 + 书 合并 ──────────────────────────────────────────────────

    /**
     * 两本我们的书在铁砧上合并（规格 §5.4 / §9-4）。
     *
     * <p>载体书：同阶级 → 条目并集（同附魔同级 → +1 夹取、不同级取 max）。
     * 升级书：同阶级 → 目标等级 +1（夹全局上限）；不同级取 max。
     * 其余组合不产出（交回原版，原版对我们的书只会得到空输出）。
     *
     * <p>成本与先修惩罚照原版「书 + 书」：先修惩罚取两侧较深者再 ×2+1，成本含两侧先修惩罚之和。
     */
    private void applyBookMerge(AnvilUpdateEvent event, ItemStack left, ItemStack right) {
        HolderLookup.RegistryLookup<StageDefinition> stages = StageLookup.lookup();
        HolderLookup.RegistryLookup<Enchantment> enchants = CommonHooks.resolveLookup(Registries.ENCHANTMENT);
        if (stages == null || enchants == null) {
            return;
        }

        BookSpecs.Inscription leftIn = left.get(UEComponents.INSCRIPTION_SPEC.get());
        BookSpecs.Inscription rightIn = right.get(UEComponents.INSCRIPTION_SPEC.get());
        if (leftIn != null && rightIn != null) {
            InscriptionLogic.mergeBooks(stages, enchants, leftIn, rightIn).ifPresent(merged -> {
                event.setOutput(BookFactory.inscription(merged.payload()));
                event.setCost(mergeCost(event, left, right, merged.cost()));
                event.setMaterialCost(1);
            });
            return;
        }

        BookSpecs.Upgrade leftUp = left.get(UEComponents.UPGRADE_SPEC.get());
        BookSpecs.Upgrade rightUp = right.get(UEComponents.UPGRADE_SPEC.get());
        if (leftUp != null && rightUp != null && leftUp.tier() == rightUp.tier()) {
            int current = Math.max(leftUp.targetLevel(), rightUp.targetLevel());
            // 升级书不绑定具体谱系（载荷里没有 root），所以这里只能按全局上限走。
            // 神化把附魔上限抬高后，全局上限跟着放宽（见 ReloadEvents.refresh 的注释）。
            int target = leftUp.targetLevel() == rightUp.targetLevel()
                    ? Math.min(current + 1, ReloadEvents.globalMaxLevel())
                    : current;
            if (target <= current) {
                return;   // 已到全局上限：不产出，避免白吃一本书
            }
            ItemStack out = BookFactory.create(BookSubject.UPGRADE, leftUp.tier().asViewTier());
            out.set(UEComponents.UPGRADE_SPEC.get(), new BookSpecs.Upgrade(leftUp.tier(), target));
            event.setOutput(out);
            event.setCost(mergeCost(event, left, right, UPGRADE_COST));
            event.setMaterialCost(1);
        }
    }

    /**
     * 书合并的最终成本 = 两侧先修惩罚之和 + 合并本身的成本，并把「加深后的先修惩罚」写进产物。
     *
     * <p>照原版：先修惩罚取两侧较深者，然后 {@code ×2+1}（原版 258-267）。
     */
    private static int mergeCost(AnvilUpdateEvent event, ItemStack left, ItemStack right, int operationCost) {
        int priorLeft = left.getOrDefault(DataComponents.REPAIR_COST, 0);
        int priorRight = right.getOrDefault(DataComponents.REPAIR_COST, 0);
        ItemStack out = event.getOutput();
        if (!out.isEmpty()) {
            out.set(DataComponents.REPAIR_COST,
                    AnvilMenu.calculateIncreasedRepairCost(Math.max(priorLeft, priorRight)));
        }
        return Math.max(1, priorLeft + priorRight + operationCost);
    }

    // ── ② 进化型 ────────────────────────────────────────────────────────

    /**
     * 进化型：把谱系推进一阶。
     *
     * <h2>两条独立的变化</h2>
     *
     * <ol>
     *   <li><b>阶段条目</b>更新为新阶级（{@code advanced/sharpness} → {@code super/sharpness}）</li>
     *   <li><b>进阶曲线等级重置为 1</b>——升阶是新的成长曲线，玩家得重新用升级书往上提</li>
     * </ol>
     *
     * <p><b>存储等级原样保留</b>（锋利 5 仍是 5）：它属于原版 {@code minecraft:enchantments}
     * 组件，改写会波及附魔台、村民交易等一切原版机制。效果强度按 **tierLevel** 结算
     * （见 {@code EnchantmentLevelEvents}），存储等级只留给原版机制读。
     * 详见 AGENT.md P1-20。
     */
    private void applyAscension(AnvilUpdateEvent event, ItemStack left, AscensionLogic.Ascension a) {
        ResourceLocation rootId = a.root().unwrapKey().map(ResourceKey::location).orElse(null);
        if (rootId == null) {
            return;
        }

        ItemStack out = left.copy();

        // 存储层：原版附魔身份不变，只更新「现在处于哪一阶」。
        // with(root, stage) 的 tierLevel 默认归 1——升阶即重算曲线。
        AscensionData before = UEComponents.ascensionOf(out);
        int oldTier = before.stageOf(rootId).map(UERoots::tierOfStage).orElse(0);
        int newTier = UERoots.tierOfStage(a.stageId());

        // 对外事件（可取消）：取消 = 整次操作作废，物品与材料都不动。
        if (!UEEvents.fireTierUpgrade(out, rootId, a.root(), oldTier, newTier,
                before.tierLevelOf(rootId), 1, UltraEnchantTierUpgradeEvent.Source.ANVIL_BOOK)) {
            return;
        }

        AscensionData data = before.with(rootId, a.stageId());
        UEComponents.setAscension(out, data);
        if (oldTier == 0) {
            UEEvents.fireLockChange(out, rootId, a.root(), true, UltraEnchantLockChangeEvent.Cause.API);
        }

        // 存储等级原样写回 —— 进阶改变的是效果定义，不是原版等级。
        ItemEnchantments.Mutable table = new ItemEnchantments.Mutable(
                EnchantmentHelper.getEnchantmentsForCrafting(out));
        table.set(a.root(), a.level());
        EnchantmentHelper.setEnchantments(out, table.toImmutable());

        // ⚠️ cost 必须 > 0（P0-5）
        event.setOutput(out);
        event.setCost(a.cost());
        event.setMaterialCost(1);
    }

    /**
     * 转印：原版附魔书 + 「基础→X」进阶书 → <b>载体书</b>（规格 §5.5）。
     *
     * <h2>为什么必须取代「直接给原版书盖阶段记录」</h2>
     *
     * <p>那个状态是死数据：物品 id 仍是 {@code minecraft:enchanted_book}，提示框看不见，
     * 贴到装备上时走的是原版书分支——它只搬运 {@code STORED_ENCHANTMENTS}，
     * <b>不会搬运任何自定义组件</b>，于是进阶记录随书一起蒸发。更糟的是它还会漏进砂轮
     * （{@code ProtectionLogic.hasProtected} 只看 {@code stages()} 非空）。
     *
     * <p>转印把「进阶形态」落到真正的载体上：物品变成我们的书、载荷带全部条目，
     * 从此可以合并、可以流通、可以贴装备。
     *
     * <p>只剩「基础→高阶」这一档能转印——原生阶没有对应档位，
     * 而原版附魔书的状态就是原生阶（正好由 {@code from_tier == native} 表达）。
     */
    private void applyTranscription(AnvilUpdateEvent event, ItemStack vanillaBook,
                                    BookSpecs.Ascension spec, boolean creative) {
        if (spec.fromTier() != LineageTier.NATIVE) {
            return;
        }
        AscensionTier toTier = AscensionTier.of(spec.toTier()).orElse(null);
        if (toTier == null) {
            return;
        }
        HolderLookup.RegistryLookup<StageDefinition> stages = StageLookup.lookup();
        HolderLookup.RegistryLookup<Enchantment> enchants = CommonHooks.resolveLookup(Registries.ENCHANTMENT);
        InscriptionLogic.transcribe(stages, enchants, vanillaBook, toTier, creative).ifPresent(t -> {
            event.setOutput(BookFactory.inscription(t.payload()));
            event.setCost(t.cost());
            event.setMaterialCost(1);
        });
    }

    // ── ③ 铭刻型 = 载体书 ───────────────────────────────────────────────

    /**
     * 载体书贴装备（规格 §5.1 / §5.2）。
     *
     * <p>生存：目标谱系必须<b>已有记录且同阶级</b>，且条目等级高于当前曲线等级——
     * 等价于「带附魔限定与多条目的升级书」，不能凭空给装备上一个阶级。
     * 创造：放开「必须已有记录」，白装备可以直接拿到该阶级（含究极，允许跳阶）。
     *
     * <p>被拒绝的条目<b>不消耗</b>，由 {@link AnvilTakeEvents} 在取件时还原成剩菜书；
     * 一条都没吃下去则整次操作无产出（书不消耗）。
     */
    private void applyInscription(AnvilUpdateEvent event, ItemStack left,
                                  BookSpecs.Inscription spec,
                                  HolderLookup.RegistryLookup<Enchantment> enchants,
                                  HolderLookup.RegistryLookup<StageDefinition> stages,
                                  boolean creative) {
        InscriptionLogic.resolve(stages, enchants, left, spec, creative).ifPresent(res -> {
            event.setOutput(res.output());
            event.setCost(res.cost());
            event.setMaterialCost(1);
        });
    }

    // ── ④ 升级型 ────────────────────────────────────────────────────────

    /**
     * 升级型：不改种类与阶级，仅把<b>进阶曲线等级</b>提到目标值。
     *
     * <h2>为什么判定基准是 tierLevel 而不是存储等级</h2>
     *
     * <p>规格要求「升阶后归 1 级，再用升级书往上提」。而升阶时存储等级是不变的
     * （锋利 5 升阶后存储仍是 5），若拿存储等级去比：
     * <pre>
     *   存储 5，3 级升级书 → 5 &gt;= 3 → 判定为「已达目标」→ 升级无效
     * </pre>
     * 3 级升级书就永远用不出去了。因此这里读 {@code AscensionData.tierLevel}：
     * <pre>
     *   刚升阶  tierLevel=1，3 级升级书 → 1 &lt; 3 → 提到 3  ✅
     *   已到 3   tierLevel=3，3 级升级书 → 3 &gt;= 3 → 无效（不浪费书）✅
     * </pre>
     *
     * <p><b>存储等级不动、tierLevel 提升</b>：前者是原版机制的账（铁砧合并、附魔台），
     * 后者才是这个进阶形态的附魔等级——效果强度按它结算（见 {@code EnchantmentLevelEvents}），
     * 所以升级书是**实打实地变强**，不只是改数字。
     *
     * <p>⚠️ 目标值要夹在该谱系该阶级的 {@code max_level} 里：升级书不区分谱系，
     * 创造栏按全局最大值铺到 5 级，不夹的话「5 级升级书」会把上限 3 的耐久顶到 5 级。
     */
    private void applyUpgrade(AnvilUpdateEvent event, ItemStack left,
                              BookSpecs.Upgrade spec,
                              HolderLookup.RegistryLookup<StageDefinition> stages) {
        ItemStack out = left.copy();
        AscensionData data = UEComponents.ascensionOf(out);
        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(out);

        for (var entry : current.entrySet()) {
            Holder<Enchantment> ench = entry.getKey();
            if (entry.getIntValue() <= 0) {
                continue;
            }

            ResourceLocation rootId = ench.unwrapKey().map(ResourceKey::location).orElse(null);
            if (rootId == null) {
                continue;
            }

            // 阶级匹配：升级型书只对同阶级的**已进阶**附魔有效。
            // 谱系当前状态（LineageTier，可能是原生阶）对上书的档位（AscensionTier，必非原生阶）：
            // 原生阶没有对应档位 → of() 返回空 → 自然不匹配。
            var currentTier = AscensionTier.of(AscensionLogic.currentTierOf(stages, out, rootId));
            if (currentTier.isEmpty() || currentTier.get() != spec.tier()) {
                continue;
            }

            // 判定基准 = 进阶曲线等级（它就是进阶形态自己的附魔等级，见 EnchantmentLevelEvents）。
            int tierLevel = data.tierLevelOf(rootId);
            if (tierLevel <= 0) {
                continue;
            }

            // ⚠️ 上限是**逐谱系**的，且升级书不是——创造栏只按全局最大值铺 1..N 级。
            // 不夹这一下，「5 级升级书」会把上限 3 的耐久顶到 5 级，
            // 而阶段条目只按自己的 max_level 写了曲线，等于让数据被外推到没有定义的位置。
            // 夹住之后，已达上限时这本书自然无效（tierLevel >= target 分支），不浪费书。
            int cap = StageLookup.maxLevelOf(stages, currentTier.get().asLineageTier(), rootId, spec.targetLevel());

            // 神化联动（B1）：它把原版附魔上限抬高后，进阶曲线等级也允许提到数据包 max_level 之上。
            // 数值曲线不变——超出部分按数据包里的 Linear 自然外推。
            // 神化缺席或读取失败时 vanillaCapOf 原样返回 fallback，行为与从前一字不差。
            cap = Math.max(cap, com.lyingice.ultraenchantment.compat.apotheosis.ApothCaps
                    .vanillaCapOf(ench, cap));

            int target = Math.min(spec.targetLevel(), cap);
            if (tierLevel >= target) {
                continue;
            }

            // 对外事件（可取消）：取消 = 本次提级不生效，也不消耗书。
            int tier = currentTier.get().ordinal() + 1;   // ADVANCED=1 / SUPER=2 / ULTRA=3
            if (!UEEvents.fireTierUpgrade(out, rootId, ench, tier, tier, tierLevel, target,
                    UltraEnchantTierUpgradeEvent.Source.UPGRADE_BOOK)) {
                continue;
            }

            // 只更新进阶曲线等级，存储等级与阶段条目都不动。
            UEComponents.setAscension(out, data.withTierLevel(rootId, target));

            event.setOutput(out);
            // IMC 可覆盖升级花费（base + per_tier × (目标等级 - 1)），未注册则沿用默认值。
            event.setCost(UEEnchantRegistry.upgradeCost(rootId, UPGRADE_COST, target));
            event.setMaterialCost(1);
            return;
        }
    }

    // ── ⑤ 同名装备合并 ─────────────────────────────────────────────────

    /**
     * 同名装备合并（规格 §6）——接管判据与数学都在 {@link ItemMergeLogic}，这里只设输出。
     *
     * <p>⚠️ {@code setMaterialCost(0)} 是刻意的：0 表示「消耗整个右槽堆叠」，
     * 而原版同名合并本来就是整件吃掉——与书那边的 1 语义相反（P0-6）。
     *
     * @return {@code true} 表示已接管，调用方不要再走防砍分支
     */
    private boolean trySameItemMerge(AnvilUpdateEvent event, ItemStack left, ItemStack right) {
        var merge = ItemMergeLogic.merge(StageLookup.lookup(), left, right, event.getName());
        if (merge.isEmpty()) {
            return false;
        }
        event.setOutput(merge.get().output());
        event.setCost(merge.get().cost());
        event.setMaterialCost(0);
        return true;
    }

    // ── 防砍等级 ────────────────────────────────────────────────────────

    /**
     * 阻止原版把超过 {@code max_level} 的附魔等级砍回去（P0-1）。
     *
     * <p>只在**右槽是可用于修复的材料**时给出结果（修耐久 + 等级原样保留）；
     * 其余情况不设置输出，交回原版处理。
     *
     * <p>为什么不在此重写原版的合并逻辑：那会引入大量难以验证的分支，
     * 而本分支的职责只是「让进阶等级不被砍」。宁可少做事。
     * （同名装备合并是刻意开的一个例外，见 {@link ItemMergeLogic} 的类注释。）
     */
    private void preventLevelClamp(AnvilUpdateEvent event, ItemStack left, ItemStack right) {
        if (right.isEmpty() || !left.isDamageableItem()) {
            return;
        }
        if (!left.getItem().isValidRepairItem(left, right)) {
            return;
        }

        int damage = left.getDamageValue();
        if (damage <= 0) {
            return;
        }

        int perUnit = Math.max(1, left.getMaxDamage() / 4);
        int units = Math.min(right.getCount(), (damage + perUnit - 1) / perUnit);
        if (units <= 0) {
            return;
        }

        ItemStack out = left.copy();
        out.setDamageValue(Math.max(damage - perUnit * units, 0));

        event.setOutput(out);
        event.setCost(Math.max(1, units));   // ⚠️ 必须 > 0
        event.setMaterialCost(units);
    }

    /**
     * 判断右槽是不是本模组的书。
     *
     * <p>走 {@link UEItems#isLoadedBook} 而不是自己逐个查组件——
     * 单例化之后「是不是我们的书」只有一处定义，新增科目时不会漏改。
     */
    private static boolean isOurBook(ItemStack stack) {
        return UEItems.isLoadedBook(stack);
    }
}
