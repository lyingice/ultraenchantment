package com.lyingice.ultraenchantment.mixin.compat;

import com.lyingice.ultraenchantment.logic.AscensionBookIO;
import com.lyingice.ultraenchantment.logic.AscensionReconcile;
import com.river_quinn.enchantment_custom_table.world.inventory.EnchantingCustomMenu;
import java.util.List;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <b>附魔编辑台（Enchantment Custom Table）兼容</b>：让<b>我们的载体书（铭刻书）</b>
 * 能当它的进出货 —— 放得进去，也拿得出来。
 *
 * <h2>它的四个相关动作（反编译实证）</h2>
 *
 * <table>
 *   <tr><th>它的方法</th><th>原本行为</th><th>我们怎么办</th></tr>
 *   <tr><td>{@code exportAllEnchantments()}「拿下」</td>
 *       <td>把物品的附魔导出成 <b>{@code Items.ENCHANTED_BOOK}</b> 塞进背包、清空槽位</td>
 *       <td>能表达就导出<b>载体书</b>；表达不了就导「原版书 + 我们的组件」（至少不丢身份）</td></tr>
 *   <tr><td>{@code checkCanPlaceEnchantedBook(stack)}「放进去」的门槛</td>
 *       <td>只看原版附魔组件 ⇒ 我们的书在它眼里是空书，直接拒绝</td>
 *       <td>是我们的载体书就放行</td></tr>
 *   <tr><td>{@code getEnchantmentInstanceFromEnchantedBook(stack)}</td>
 *       <td>同上，读出空列表</td>
 *       <td>把载体书载荷翻译成「根附魔 + 书上等级」的实例列表</td></tr>
 *   <tr><td>{@code addEnchantment(book, index, flag)}</td>
 *       <td>把实例写进物品的 {@code minecraft:enchantments}</td>
 *       <td>写完之后，把载体书的条目落成物品上的<b>进阶记录</b></td></tr>
 * </table>
 *
 * <h2>两个内部字段</h2>
 *
 * <p>{@code itemHandler}（台子里的物品槽）与 {@code entity}（玩家）是它的<b>私有字段</b>，
 * 这里用 {@code @Shadow} 拿。用它们是不得已：{@code exportAllEnchantments()} 无参、
 * 没法从方法上下文拿到槽位与玩家。它们若改名，mixin 会在<b>加载期</b>直接报错
 * （不是静默失效），所以这条依赖是可见的。
 *
 * <h2>为什么还要挂 {@code clicked} 做对账</h2>
 *
 * <p>增删改都走容器点击（它重写了 {@code clicked}）。对账保证「在它这里改过的进阶附魔」
 * 与我们的记录一致（规则见 {@link AscensionReconcile}）。
 */
@Mixin(EnchantingCustomMenu.class)
public abstract class EnchantingCustomTableMixin {

    @Shadow
    @Final
    private ItemStackHandler itemHandler;

    @Shadow
    @Final
    private Player entity;

    /** 这一侧是不是客户端。⚠️ 它<b>不是</b>「有没有客户端代码」，而是「这一份菜单在哪个逻辑侧」。 */
    @Shadow
    @Final
    private net.minecraft.world.level.Level world;

    /** 台子的「书页缓存」：网格里逐条显示的附魔书，也是玩家点选的对象。 */
    @Shadow
    @Final
    private java.util.List<ItemStack> enchantmentsOnCurrentTool;

    /** 台子里正在被编辑的那个物品（槽 0）。 */
    @Unique
    private ItemStack ue$item() {
        return this.itemHandler.getStackInSlot(0);
    }

    /**
     * <b>让网格里的进阶条目显示成我们的载体书。</b>
     *
     * <p>它的 {@code genEnchantedBookCache()} 会把物品的每条附魔都做成一本
     * <b>原版附魔书</b>塞进网格。网格是玩家<b>实际点选</b>的东西：
     * 点原版书 → 带进物品的只是「根附魔 @ 原版等级」，进阶记录写不进去
     * ⇒ 表现就是「进阶书放进去、出来只有普通附魔」。
     *
     * <p>把有条目的那些替换成对应阶级的载体书之后，整条链路自洽：
     * 网格显示载体书 → 点它 → {@code getEnchantmentInstanceFromEnchantedBook}（已钩）
     * → {@code addEnchantment} → {@code applyTo}（已钩）写回进阶记录。
     *
     * <p>⚠️ 本方法在两侧都会跑，但<b>不碰注册表 Holder</b>（只读物品组件 + 造我们自己的物品），
     * 所以不存在 P0-17 那类跨侧问题。
     */
    @Inject(method = "genEnchantedBookCache", at = @At("RETURN"))
    private void ue$cacheAsInscriptionBooks(CallbackInfo ci) {
        ItemStack item = this.ue$item();
        if (item.isEmpty() || this.enchantmentsOnCurrentTool.isEmpty()) {
            return;
        }
        for (int i = 0; i < this.enchantmentsOnCurrentTool.size(); i++) {
            ItemStack entry = this.enchantmentsOnCurrentTool.get(i);
            ItemStack ours = AscensionBookIO.inscriptionFor(item, entry);
            if (!ours.isEmpty()) {
                this.enchantmentsOnCurrentTool.set(i, ours);
            }
        }
    }

    // ───────────────────────── 出来：拿下一本载体书 ─────────────────────────

    @Inject(method = "exportAllEnchantments", at = @At("HEAD"), cancellable = true)
    private void ue$exportAsInscriptionBook(CallbackInfo ci) {
        ItemStack item = this.ue$item();
        if (item.isEmpty()) {
            return;
        }
        ItemStack book = AscensionBookIO.exportFrom(item);
        if (book.isEmpty()) {
            book = AscensionBookIO.plainExport(item);
        }
        if (book.isEmpty()) {
            return;                              // 纯基础阶 ⇒ 交给它原样处理
        }
        this.entity.getInventory().placeItemBackInInventory(book);
        this.itemHandler.setStackInSlot(0, ItemStack.EMPTY);
        this.entity.level().playSound(null, this.entity.blockPosition(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        ci.cancel();
    }

    // ───────────────────────── 进去：让载体书能用 ─────────────────────────

    /** 门槛：我们的载体书也允许放进台子。 */
    @Inject(method = "checkCanPlaceEnchantedBook", at = @At("HEAD"), cancellable = true)
    private void ue$acceptInscriptionBook(ItemStack book, CallbackInfoReturnable<Boolean> cir) {
        if (AscensionBookIO.isInscriptionBook(book)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 读法：把载体书载荷翻译成原版附魔实例，它后续的「添加」流程就能用。
     *
     * <p>⚠️ <b>注册表必须按「这一侧」取</b>：它的 {@code translateEnchantment} 是
     * {@code registry.getResourceKey(enchantment).get()}，给它另一个注册表的 Holder 会直接
     * {@code NoSuchElementException}。而**容器点击在客户端也会跑一遍**（原版
     * {@code MultiPlayerGameMode.handleInventoryMouseClick} 的本地预测），
     * 所以「服务端 Holder」会在这里被客户端注册表反查 ⇒ 崩。
     * 这正是 {@code UELookups} 类文档警告过的场景（见 P0-16 / 规格 §17.4）。
     */
    @Inject(method = "getEnchantmentInstanceFromEnchantedBook", at = @At("HEAD"), cancellable = true)
    private void ue$readInscriptionBook(ItemStack book, CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
        if (AscensionBookIO.isInscriptionBook(book)) {
            cir.setReturnValue(AscensionBookIO.instancesOf(book, this.world.isClientSide()));
        }
    }

    /**
     * 写回：它按实例写完原版附魔之后，把进阶记录补上（只对「物品上确实已有」的条目生效）。
     *
     * <p>⚠️ <b>只在服务端做</b>：写组件要往栈里塞附魔 Holder，客户端侧那份栈如果塞了服务端 Holder，
     * 一发包就是 {@code Can't find id for ...} ⇒ <b>玩家掉线</b>（P0-16）。客户端那份由原版容器同步覆盖。
     */
    // ⚠️ 两个重载都显式写出描述符：只写方法名时，遇到重载会落到「哪一个」不确定。
    @Inject(method = "addEnchantment(Lnet/minecraft/world/item/ItemStack;I)V", at = @At("RETURN"))
    private void ue$applyInscriptionBookShort(ItemStack book, int index, CallbackInfo ci) {
        this.ue$applyBook(book);
    }

    @Inject(method = "addEnchantment(Lnet/minecraft/world/item/ItemStack;IZ)V", at = @At("RETURN"))
    private void ue$applyInscriptionBook(ItemStack book, int index, boolean flag, CallbackInfo ci) {
        this.ue$applyBook(book);
    }

    @Unique
    private void ue$applyBook(ItemStack book) {
        if (this.world.isClientSide()) {
            return;
        }
        if (AscensionBookIO.applyTo(this.ue$item(), book) > 0) {
            // 写回之后立刻重建网格（见 ue$reconcileAll 的说明）。
            EnchantingCustomMenu self = (EnchantingCustomMenu) (Object) this;
            self.genEnchantedBookCache();
            self.updateEnchantedBookSlots();
        }
    }

    // ───────────────────────── 顺手：对账 ─────────────────────────

    @Inject(method = "clicked", at = @At("RETURN"))
    private void ue$reconcileAfterClick(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        this.ue$reconcileAll();
    }

    @Inject(method = "quickMoveStack", at = @At("RETURN"))
    private void ue$reconcileAfterQuickMove(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        this.ue$reconcileAll();
    }

    @Inject(method = "removed", at = @At("RETURN"))
    private void ue$reconcileOnClose(Player player, CallbackInfo ci) {
        this.ue$reconcileAll();
    }

    /**
     * 遍历菜单里所有槽位，逐个对账；**顺手把槽位里放着的载体书落到物品上**。
     *
     * <p>两件事都要：对账负责「编辑台删/改过的条目，我们的记录跟着变」；
     * 落帐负责「槽位里有一本我们的载体书 ⇒ 物品上要有对应的进阶形态」——
     * 后者是「外面的进阶书放进来只得到普通附魔」的补丁（放书那一步不走 addEnchantment）。
     */
    @Unique
    private void ue$reconcileAll() {
        ItemStack item = this.ue$item();
        boolean changed = false;
        for (Slot slot : ((AbstractContainerMenu) (Object) this).slots) {
            ItemStack stack = slot.getItem();
            if (AscensionReconcile.reconcile(stack)) {
                changed = true;
            }
            if (!item.isEmpty() && AscensionBookIO.isInscriptionBook(stack)) {
                AscensionBookIO.applyTo(item, stack);
                changed = true;
            }
        }
        if (changed) {
            // ⚠️ 写回之后**必须**重建右侧网格：它的网格是「物品的附魔逐条做成一本书」缓存下来的，
            //    不重建的话玩家看到的是旧书（要拿走物品再放回才会更新）。两个方法都是它的公开方法，
            //    也是它自己在槽位变化时用的那一套（genEnchantedBookCache 内部会先 clear 列表，幂等）。
            EnchantingCustomMenu self = (EnchantingCustomMenu) (Object) this;
            self.genEnchantedBookCache();
            self.updateEnchantedBookSlots();
        }
    }
}
