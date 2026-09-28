package com.lyingice.ultraenchantment.event;

import com.lyingice.ultraenchantment.content.BookSpecs;
import com.lyingice.ultraenchantment.content.StageDefinition;
import com.lyingice.ultraenchantment.logic.BookFactory;
import com.lyingice.ultraenchantment.logic.InscriptionLogic;
import com.lyingice.ultraenchantment.logic.StageLookup;
import com.lyingice.ultraenchantment.registry.UEComponents;
import com.lyingice.ultraenchantment.registry.UEItems;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;

/**
 * <b>铁砧取件链路</b>——只做一件事：交付剩菜书（规格 §5.3）。
 *
 * <h2>为什么需要第二输出</h2>
 *
 * <p>载体书是多条目的：贴装备时可能「一部分吃下去、一部分被拒绝」。
 * 按规格，被拒绝的条目要留成一本「剩菜书」还给玩家。可铁砧只有一个输出槽，
 * 而 {@code repairItemCountCost} 只能「少扣」不能「返还」（{@code AnvilMenu.onTake}
 * 的 0/正数语义是反的，见 P0-6）。
 *
 * <h2>为什么落在 AnvilRepairEvent</h2>
 *
 * <p>{@code AnvilMenu.onTake} 的顺序是：
 * <pre>
 *   82: CommonHooks.onAnvilRepair(player, output, left, right);   ← 本事件
 *   84: inputSlots.setItem(0, EMPTY);                            ← 之后才清空
 * </pre>
 * 也就是说事件触发时<b>两个输入槽与玩家都还在手上</b>，可以拿到「操作发生前的真实输入」。
 * 而我们<b>不改 output</b>（事件 javadoc 明确 inputs/output 不可编辑），
 * 只是用同一份输入把解析重跑一次——解析是纯函数，结果必然与 {@code AnvilUpdateEvent}
 * 阶段一致，于是「哪些条目没吃下去」可以精确复原。
 *
 * <p>本事件在服务端触发（{@code onTake} 只由服务端的容器点击路径调用），
 * 因此这里做背包/掉落这类副作用是安全的——与 {@link AnvilEvents} 的纯函数要求互不干扰。
 */
public final class AnvilTakeEvents {
    private AnvilTakeEvents() {}

    /** 单例监听器，供 game bus 注册。 */
    public static final AnvilTakeEvents INSTANCE = new AnvilTakeEvents();

    @SubscribeEvent
    public void onAnvilRepair(AnvilRepairEvent event) {
        ItemStack right = event.getRight();
        BookSpecs.Inscription spec = right.get(UEComponents.INSCRIPTION_SPEC.get());
        if (spec == null) {
            return;
        }

        ItemStack left = event.getLeft();
        if (left.isEmpty() || UEItems.isLoadedBook(left)) {
            return;   // 书 + 书合并走 applyBookMerge，没有剩菜
        }
        if (event.getOutput().isEmpty()) {
            return;   // 没有产出 = 这次不是我们的操作
        }

        // AnvilRepairEvent 继承 PlayerEvent，取玩家用 getEntity()（21.1 没有 getPlayer）。
        Player player = event.getEntity();
        HolderLookup.RegistryLookup<StageDefinition> stages = StageLookup.lookup();
        HolderLookup.RegistryLookup<Enchantment> enchants = CommonHooks.resolveLookup(Registries.ENCHANTMENT);

        InscriptionLogic.resolve(stages, enchants, left, spec, player.isCreative()).ifPresent(res -> {
            List<BookSpecs.Inscription.Entry> leftovers = res.leftovers();
            if (leftovers.isEmpty()) {
                return;   // 整本都被吃掉了
            }
            ItemStack book = BookFactory.inscription(new BookSpecs.Inscription(spec.tier(), leftovers));
            if (!player.getInventory().add(book)) {
                player.drop(book, false);   // 背包满则掉在脚下，绝不静默吞掉
            }
        });
    }
}
