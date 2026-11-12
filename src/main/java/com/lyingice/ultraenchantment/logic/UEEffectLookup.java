package com.lyingice.ultraenchantment.logic;

import com.lyingice.ultraenchantment.content.StageDefinition;
import com.lyingice.ultraenchantment.content.UEEffect;
import com.lyingice.ultraenchantment.registry.UEComponents;
import com.lyingice.ultraenchantment.registry.UERegistries;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * <b>`ue:*` 扩展效果的解析层</b>——把「一件装备上的进阶阶段」翻译成「一组可执行的效果」。
 *
 * <h2>为什么需要单独的解析层</h2>
 *
 * <p>原版效果由原版执行器在固定节点驱动，注册即可用。扩展效果不同：每个事件监听器
 * （伤害、死亡、盾牌、跳跃、拾取）都要问同一句话——「这个实体身上有没有这种效果、
 * 等级是多少」。把这句话写进每个监听器，就会出现八份略有差异的实现，
 * 而且等级取值、空表早退、服务端判断这些细节会在某一份里漏掉。
 *
 * <p>所以集中在这里：监听器只负责「在正确的时机调用 + 组装上下文」，
 * 解析逻辑只有这一份。
 *
 * <h2>不缓存</h2>
 *
 * <p>与 {@link StageLookup} 同理：数据包注册表在 {@code /reload} 重建，
 * 任何静态缓存都会变成悬空引用。装备遍历是几十次 hash 查表，开销可忽略。
 */
public final class UEEffectLookup {
    private UEEffectLookup() {}

    /**
     * 一条解析出来的效果及其等级。
     *
     * @param root   谱系根源（原版附魔 id）——主要用于调试与去重
     * @param effect 效果本体
     * @param level  生效等级（{@code AscensionData.tierLevel}，至少 1）
     */
    public record Resolved(ResourceLocation root, UEEffect effect, int level) {}

    /** 某个实体的<b>全部装备槽</b>（手 + 护甲）上的扩展效果。 */
    public static List<Resolved> fromEquipment(LivingEntity entity) {
        return fromSlots(entity.getAllSlots());
    }

    /**
     * 某个实体<b>手持武器</b>上的扩展效果（主手优先，空手时看副手）。
     *
     * <p>攻击类效果（增伤、真实伤害、吸血、驱散……）取这一份：
     * 附魔挂在武器上，作用在「这次攻击造成的伤害」上，语义清晰。
     */
    public static List<Resolved> fromWeapon(LivingEntity entity) {
        ItemStack main = entity.getMainHandItem();
        if (!main.isEmpty()) {
            return fromStack(main);
        }
        ItemStack off = entity.getOffhandItem();
        return off.isEmpty() ? List.of() : fromStack(off);
    }

    /** 某个实体的<b>副手</b>（盾牌）上的扩展效果。 */
    public static List<Resolved> fromOffhand(LivingEntity entity) {
        ItemStack off = entity.getOffhandItem();
        return off.isEmpty() ? List.of() : fromStack(off);
    }

    /** 一组装备槽上的扩展效果。 */
    public static List<Resolved> fromSlots(Iterable<ItemStack> stacks) {
        List<Resolved> out = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                out.addAll(fromStack(stack));
            }
        }
        return out;
    }

    /** 单个物品上的扩展效果。 */
    public static List<Resolved> fromStack(ItemStack stack) {
        Map<ResourceLocation, ResourceLocation> recorded = UEComponents.ascensionOf(stack).stages();
        if (recorded.isEmpty()) {
            return List.of();
        }
        HolderLookup.RegistryLookup<StageDefinition> lookup = StageLookup.lookup();
        if (lookup == null) {
            return List.of();
        }

        List<Resolved> out = new ArrayList<>();
        recorded.forEach((root, stageId) -> {
            StageDefinition stage = lookup.get(ResourceKey.create(UERegistries.STAGE, stageId))
                    .map(holder -> holder.value())
                    .orElse(null);
            if (stage == null || stage.ueEffects().isEmpty()) {
                return;
            }
            int level = Math.max(1, UEComponents.ascensionOf(stack).tierLevelOf(root));
            for (UEEffect effect : stage.ueEffects()) {
                out.add(new Resolved(root, effect, level));
            }
        });
        return out;
    }

    /** 从一组已解析效果里找第一条指定类型的对象。 */
    public static <T extends UEEffect> Optional<T> find(List<Resolved> resolved, Class<T> type) {
        for (Resolved entry : resolved) {
            if (type.isInstance(entry.effect())) {
                return Optional.of(type.cast(entry.effect()));
            }
        }
        return Optional.empty();
    }

    /** 从一组已解析效果里找出所有指定类型的对象。 */
    public static <T extends UEEffect> List<Resolved> findAll(List<Resolved> resolved, Class<T> type) {
        List<Resolved> out = new ArrayList<>();
        for (Resolved entry : resolved) {
            if (type.isInstance(entry.effect())) {
                out.add(entry);
            }
        }
        return out;
    }

    /** 效果是否只在服务端结算（防双端重复执行）。 */
    public static boolean serverSide(LivingEntity entity) {
        return entity != null && !entity.level().isClientSide();
    }

    /** 是否是可被伤害/拾取等逻辑处理的「实体」——排除死亡、创作模式等。 */
    public static boolean active(LivingEntity entity) {
        return entity != null && entity.isAlive() && !entity.isRemoved();
    }

    /** 攻击者/受击者之间的有效双向判定（都存活且不同一实体）。 */
    public static boolean opposing(LivingEntity a, LivingEntity b) {
        return a != null && b != null && a != b && a.isAlive() && b.isAlive();
    }

    /** 玩家是否在线且处于可正常交互状态。 */
    public static boolean usablePlayer(Player player) {
        return player != null && player.isAlive() && !player.isSpectator();
    }
}
