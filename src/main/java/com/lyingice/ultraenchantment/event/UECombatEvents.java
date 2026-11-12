package com.lyingice.ultraenchantment.event;

import static com.lyingice.ultraenchantment.logic.UEEffectExecution.KINETIC_TYPE;

import com.lyingice.ultraenchantment.UEConfig;
import com.lyingice.ultraenchantment.content.UEEffect;
import com.lyingice.ultraenchantment.logic.UEEffectExecution;
import com.lyingice.ultraenchantment.logic.UEEffectGuard;
import com.lyingice.ultraenchantment.logic.UEEffectLookup;
import com.lyingice.ultraenchantment.registry.UEMobEffects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent;

/**
 * <b>`ue:*` 战斗效果</b>——伤害管线上的全部扩展效果。
 *
 * <h2>为什么分三个事件</h2>
 *
 * <p>NeoForge 把一次伤害拆成了有序的阶段（见 {@code DamageContainer} 文档），
 * 每个效果必须挂在它语义对应的阶段，否则会算错：
 *
 * <table>
 *   <tr><th>阶段</th><th>此时伤害值是什么</th><th>挂在这里的效果</th></tr>
 *   <tr><td>{@link LivingIncomingDamageEvent}</td>
 *       <td>原始值，护甲/保护<b>尚未</b>介入</td>
 *       <td>闪避（取消）、无敌帧穿透、攻击方增伤（百分比 / 周围数量）</td></tr>
 *   <tr><td>{@link LivingDamageEvent.Pre}</td>
 *       <td>护甲 / 保护 / 抗性<b>已全部结算</b>，尚未扣血</td>
 *       <td>双重保护（独立乘区）、自伤加深</td></tr>
 *   <tr><td>{@link LivingDamageEvent.Post}</td>
 *       <td>生命值<b>已经</b>扣除完毕</td>
 *       <td>吸血、真实伤害追加、驱散、反伤、受击状态效果、换位</td></tr>
 * </table>
 *
 * <h2>为什么用 Incoming 事件算「受击方」的效果</h2>
 *
 * <p>{@code LivingIncomingDamageEvent} 会覆盖「无敌帧判定之前」的时机；
 * 而本模组的 {@code bypass_iframes} 需求正是「命中时按概率跳过无敌帧」。
 * 在 Incoming 里把 {@code invulnerableTime} 清零，后续的原版无敌帧判定就不会再拦这一击。
 */
public final class UECombatEvents {
    private UECombatEvents() {}

    public static final UECombatEvents INSTANCE = new UECombatEvents();

    /**
     * 一次横扫的上下文：主目标、被横扫的候选者、目标总数。
     *
     * <p>{@code SweepAttackEvent} 在 {@code Player.attack} 里、横扫循环<b>之前</b>触发，
     * 而横扫伤害是在循环里逐个 {@code hurt} 的。这里把「这一刀会扫到谁」提前算出来，
     * 随后在 {@link LivingIncomingDamageEvent} 里按目标数补上伤害倍率。
     */
    private record SweepContext(int tick, Entity target, Set<LivingEntity> pending, int totalTargets) {}

    /** 当前线程的横扫上下文。只在服务端写入；{@link WeakHashMap} 避免强持有玩家。 */
    private static final Map<Player, SweepContext> SWEEP_CONTEXTS = new WeakHashMap<>();

    // ── 阶段 ①：伤害介入前（原始值） ────────────────────────────────────

    /**
     * 闪避、无敌帧穿透、攻击方增伤。
     *
     * <p>攻击方判定统一走 {@link LivingEntity#getKillCredit()} 拿不到实体时的兜底链：
     * {@code getEntity()}（真实来源）→ {@code getDirectEntity()}（弹射物等）。
     */
    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (UEEffectExecution.disabled()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (!UEEffectExecution.serverSide(victim)) {
            return;
        }
        // 动能伤害是我们自己打出的二次伤害：绝不能再次进入本套效果链（否则会自我增殖）。
        if (event.getSource().is(KINETIC_TYPE)) {
            return;
        }

        LivingEntity attacker = resolveAttacker(event.getSource());

        // 火焰附加的标记：在护甲结算之前放大 / 追加火焰伤害。
        applyBurnMarkers(event);

        // ── 受击方的防御类效果 ──
        List<UEEffectLookup.Resolved> victimEquip = UEEffectLookup.fromEquipment(victim);

        // 闪避：完全免疫本次伤害。
        for (UEEffectLookup.Resolved entry : victimEquip) {
            if (entry.effect() instanceof UEEffect.DamageDodge dodge
                    && victim.getRandom().nextFloat() < UEEffectExecution.clamp01(dodge.chance().at(entry.level()))) {
                event.setCanceled(true);
                return;
            }
        }

        // 无敌帧穿透：把目标的受击间隔清零，让原版的「无敌帧拦截」不再生效。
        if (attacker != null) {
            for (UEEffectLookup.Resolved entry : UEEffectLookup.fromWeapon(attacker)) {
                if (entry.effect() instanceof UEEffect.BypassIframes bypass
                        && attacker.getRandom().nextFloat()
                                < UEEffectExecution.clamp01(bypass.chance().at(entry.level()))) {
                    victim.invulnerableTime = 0;
                    victim.hurtTime = 0;
                    break;
                }
            }
        }

        // ── 攻击方的进攻类效果（改原始伤害值） ──
        float amount = event.getAmount();
        if (attacker != null && amount > 0.0f) {
            for (UEEffectLookup.Resolved entry : UEEffectLookup.fromWeapon(attacker)) {
                amount = applyOffensive(entry, amount, attacker);
            }
            // 横扫增伤：只作用于「被横扫到」的目标，主目标不吃这一份。
            amount = applySweepBonus(attacker, victim, amount);
            // 自伤加深：受击方装备上「增加自身所受伤害」的效果。
            for (UEEffectLookup.Resolved entry : victimEquip) {
                if (entry.effect() instanceof UEEffect.SelfVulnerability vulnerable) {
                    float mul = UEEffectExecution.clamp01(vulnerable.multiplier().at(entry.level()));
                    amount *= (1.0f + mul);
                }
            }
            event.setAmount(amount);
        }
    }

    /**
     * 火焰易伤 / 灼烧之殇：只在火焰伤害上生效。
     *
     * <p>易伤翻倍；灼烧之殇额外追加受击者剩余生命值的 1%。两者可以同时存在
     * （不同来源的标记），按「先翻倍、后追加」结算。
     */
    private void applyBurnMarkers(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypeTags.IS_FIRE)) {
            return;
        }
        float amount = event.getAmount();
        if (amount <= 0.0f) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim.hasEffect(UEMobEffects.FIRE_VULNERABILITY)) {
            amount *= 2.0f;
        }
        if (victim.hasEffect(UEMobEffects.BURN_AGONY)) {
            amount += victim.getHealth() * 0.01f;
        }
        event.setAmount(amount);
    }

    /** 攻击方的进攻类效果：百分比增伤、周围数量增伤、烈焰之力。 */
    private float applyOffensive(UEEffectLookup.Resolved entry, float amount, LivingEntity attacker) {
        if (entry.effect() instanceof UEEffect.PercentDamage percent) {
            float ratio = UEEffectExecution.clamp01(percent.ratio().at(entry.level()));
            return amount * (1.0f + ratio);
        }
        if (entry.effect() instanceof UEEffect.InfernoMight inferno && isInferno(attacker)) {
            float multiplier = Math.max(0.0f, inferno.multiplier().at(entry.level()));
            return amount * (1.0f + multiplier);
        }
        if (entry.effect() instanceof UEEffect.ScalingDamage scaling) {
            int nearby = countNearbyEntities(attacker, scaling.radius());
            double multiplier = scaling.multiplierFor(entry.level(), nearby);
            return (float) (amount * Math.max(0.0, multiplier));
        }
        return amount;
    }

    /** 攻击者周围符合条件的实体数量（排除自己、排除队友、排除死亡）。 */
    private int countNearbyEntities(LivingEntity attacker, double radius) {
        AABB box = attacker.getBoundingBox().inflate(radius);
        return attacker.level()
                .getEntitiesOfClass(LivingEntity.class, box,
                        other -> other != attacker && other.isAlive() && !other.isAlliedTo(attacker))
                .size();
    }

    /** 烈焰之力成立的场景：下界维度 / 正在着火 / 在岩浆里。 */
    private boolean isInferno(LivingEntity attacker) {
        return attacker.level().dimension() == Level.NETHER
                || attacker.isOnFire()
                || attacker.isInLava();
    }

    // ── 横扫增伤 ────────────────────────────────────────────────────────

    /**
     * 在 {@code Player.attack} 的横扫循环之前算出「这一刀会扫到哪些目标」。
     *
     * <p>判定条件与 {@code Player.attack} 源码逐条一致：
     * 武器横扫碰撞箱、排除自己与主目标、排除队友、排除盔甲架标记、
     * 距离小于实体交互距离的平方。算出的列表随后用于
     * {@link #applySweepBonus}，主目标本身不在列表里。
     */
    @SubscribeEvent
    public void onSweepAttack(SweepAttackEvent event) {
        if (UEEffectExecution.disabled()) {
            return;
        }
        Player player = event.getEntity();
        if (player.level().isClientSide() || !event.isSweeping()) {
            return;
        }
        Entity target = event.getTarget();
        if (!(target instanceof LivingEntity)) {
            SWEEP_CONTEXTS.remove(player);
            return;
        }

        Set<LivingEntity> victims = Collections.newSetFromMap(new IdentityHashMap<>());
        AABB box = player.getWeaponItem().getSweepHitBox(player, target);
        double reachSq = Mth.square(player.entityInteractionRange());
        for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class, box,
                other -> other != player && other != target && other.isAlive()
                        && !player.isAlliedTo(other)
                        && (!(other instanceof ArmorStand stand) || !stand.isMarker())
                        && player.distanceToSqr(other) < reachSq)) {
            victims.add(candidate);
        }

        // 第一次进入时就把上下文写死：后续目标逐个消费，直到列表清空。
        // 目标数为 0 时移除旧上下文，避免上一刀的目标误吃这一刀的倍率。
        if (victims.isEmpty()) {
            SWEEP_CONTEXTS.remove(player);
            return;
        }
        // totalTargets 含主目标：规格里的「命中 2 个以上目标」是总目标数，
        // 而 pending 只放被横扫的次要目标（主目标不参与横扫增伤）。
        SWEEP_CONTEXTS.put(player,
                new SweepContext(player.tickCount, target, victims, victims.size() + 1));
    }

    /**
     * 给「被横扫到的目标」补上横扫增伤。
     *
     * <p>只有当目标确实出现在 {@link SweepContext#pending()} 里、且事件发生在同一 tick
     * 才生效；命中一个就移除一个，避免同一目标在本次横扫里被重复放大。
     */
    private float applySweepBonus(LivingEntity attacker, LivingEntity victim, float amount) {
        if (!(attacker instanceof Player player)) {
            return amount;
        }
        SweepContext context = SWEEP_CONTEXTS.get(player);
        if (context == null || context.tick() != player.tickCount || !context.pending().remove(victim)) {
            return amount;
        }
        if (context.pending().isEmpty()) {
            SWEEP_CONTEXTS.remove(player);
        }

        for (UEEffectLookup.Resolved entry : UEEffectLookup.fromWeapon(player)) {
            if (entry.effect() instanceof UEEffect.SweepDamage sweep) {
                double multiplier = sweep.multiplierFor(entry.level(), context.totalTargets());
                return (float) (amount * Math.max(0.0, multiplier));
            }
        }
        return amount;
    }

    // ── 阶段 ②：护甲/保护已结算（尚未扣血） ─────────────────────────────

    /**
     * 双重保护：在原版护甲与保护全部完成之后，再乘一层独立减伤。
     *
     * <p>每件带该效果的装备各自提供一层，<b>按乘法叠加</b>，单件被
     * {@link UEConfig#MAX_DOUBLE_PROTECTION_PER_ITEM} 压住上限。
     * 与 EPF / 保护上限无关——这正是「独立乘区」的含义。
     */
    @SubscribeEvent
    public void onDamagePre(LivingDamageEvent.Pre event) {
        if (UEEffectExecution.disabled()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (!UEEffectExecution.serverSide(victim)) {
            return;
        }
        if (event.getSource().is(KINETIC_TYPE)) {
            return;
        }

        float remaining = event.getNewDamage();
        boolean touched = false;
        for (UEEffectLookup.Resolved entry : UEEffectLookup.fromEquipment(victim)) {
            if (entry.effect() instanceof UEEffect.DoubleProtection protection) {
                float reduction = UEEffectExecution.clampProtection(protection.reduction().at(entry.level()));
                remaining *= (1.0f - reduction);
                touched = true;
            }
        }
        if (touched) {
            event.setNewDamage(Math.max(0.0f, remaining));
        }
    }

    // ── 阶段 ③：生命值已扣除 ────────────────────────────────────────────

    /**
     * 吸血、真实伤害追加、驱散、反伤、受击状态效果。
     *
     * <p>这里读 {@code getNewDamage()}（本次实际扣除的生命值），因此吸血与反伤
     * 用的是<b>真实损失量</b>而不是预减伤数字——这正是把它们放在 Post 的理由。
     */
    @SubscribeEvent
    public void onDamagePost(LivingDamageEvent.Post event) {
        if (UEEffectExecution.disabled()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (!UEEffectExecution.serverSide(victim)) {
            return;
        }

        DamageSource source = event.getSource();
        if (source.is(KINETIC_TYPE)) {
            return;
        }
        LivingEntity attacker = resolveAttacker(source);
        float dealt = event.getNewDamage();

        // 整段用重入保护包住：真实伤害与反伤都会再次进入伤害管线。
        UEEffectGuard.run(victim, () -> {
            List<UEEffectLookup.Resolved> weapon = attacker == null ? List.of() : UEEffectLookup.fromWeapon(attacker);

            // ① 攻击者武器上的「命中后」效果。
            for (UEEffectLookup.Resolved entry : weapon) {
                onAttackEffect(entry, attacker, victim, dealt);
            }

            // ② 受击方装备上的「被命中后」效果。
            for (UEEffectLookup.Resolved entry : UEEffectLookup.fromEquipment(victim)) {
                onHurtEffect(entry, victim, attacker, dealt, source);
            }
        });
    }

    /** 攻击者武器上的命中效果。 */
    private void onAttackEffect(UEEffectLookup.Resolved entry, LivingEntity attacker,
                                LivingEntity victim, float dealt) {
        UEEffect effect = entry.effect();
        int level = entry.level();

        // 吸血：按目标已损失生命的比例回复自身。
        if (effect instanceof UEEffect.Hemophagia hemophagia) {
            float ratio = UEEffectExecution.clamp01(hemophagia.ratio().at(level));
            float missing = Math.max(0.0f, victim.getMaxHealth() - victim.getHealth());
            float heal = missing * ratio;
            if (heal > 0.0f) {
                attacker.heal(heal);
            }
            return;
        }

        // 真实伤害：把本次伤害的一部分转为无视一切的真实伤害。
        if (effect instanceof UEEffect.RealDamage real) {
            float chance = UEEffectExecution.clamp01(real.chance().at(level));
            if (attacker.getRandom().nextFloat() >= chance) {
                return;
            }
            float ratio = UEEffectExecution.clampRealRatio(
                    real.ratio().at(level) + real.bonus().at(level));
            float cap = UEEffectExecution.clampRealRatio(real.cap().at(level));
            float trueDamage = Math.min(dealt * ratio, dealt * cap);
            UEEffectExecution.applyTrueDamage(victim, trueDamage);
            return;
        }

        // 动能伤害：按本次实际伤害的比例追加一次独立类型的伤害（受护甲影响，非真实伤害）。
        if (effect instanceof UEEffect.KineticDamage kinetic) {
            float ratio = Math.max(0.0f, kinetic.ratio().at(level));
            if (ratio > 0.0f) {
                UEEffectExecution.applyKineticDamage(attacker, victim, dealt * ratio);
            }
            return;
        }

        // 驱散增益。
        if (effect instanceof UEEffect.StripBuffs strip) {
            UEEffectExecution.stripBuffs(victim, strip.count().atInt(level));
            return;
        }

        // 换位（命中方向）。
        if (effect instanceof UEEffect.SwapPosition swap && swap.activation().onHit()
                && attacker.getRandom().nextFloat() < UEEffectExecution.clamp01(swap.chance().at(level))) {
            UEEffectExecution.swap(attacker, victim);
        }
    }

    private void onHurtEffect(UEEffectLookup.Resolved entry, LivingEntity victim,
                              LivingEntity attacker, float dealt, DamageSource source) {
        UEEffect effect = entry.effect();
        int level = entry.level();

        // 反伤：把本次伤害完全返还攻击者（带重入保护，避免两个反伤装互弹）。
        if (effect instanceof UEEffect.DamageReflect reflect) {
            if (attacker == null || attacker == victim) {
                return;
            }
            float chance = UEEffectExecution.clamp01(reflect.chance().at(level));
            if (victim.getRandom().nextFloat() >= chance) {
                return;
            }
            UEEffectGuard.run(attacker, () -> attacker.hurt(
                    victim.damageSources().thorns(victim), dealt));
            return;
        }

        // 受击状态效果（对自身或攻击者）。
        if (effect instanceof UEEffect.BeAttacked attacked) {
            UEEffectExecution.applyEffects(attacked.effects(), level, victim, attacker);
            return;
        }

        // 换位（受击方向）。
        if (effect instanceof UEEffect.SwapPosition swap && swap.activation().onHurt()
                && attacker != null && attacker != victim
                && victim.getRandom().nextFloat() < UEEffectExecution.clamp01(swap.chance().at(level))) {
            UEEffectExecution.swap(victim, attacker);
        }
    }

    // ── 死亡 ───────────────────────────────────────────────────────────

    /**
     * 濒死免疫：血量归零时取消本次死亡。
     *
     * <p>取消死亡后把血量设为 1（{@code clamp} 到至少 1，确保脱离「死亡判定」），
     * 再按配置清除状态效果、施加自定义效果。用重入保护确保一次伤害链只触发一次。
     */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (UEEffectExecution.disabled()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (!UEEffectExecution.serverSide(victim)) {
            return;
        }

        for (UEEffectLookup.Resolved entry : UEEffectLookup.fromEquipment(victim)) {
            if (!(entry.effect() instanceof UEEffect.SkipDeath skip)) {
                continue;
            }
            if (!UEEffectGuard.enter(victim)) {
                return; // 本次链上已触发过——不再重复救。
            }
            try {
                event.setCanceled(true);
                victim.setHealth(1.0f);
                if (skip.clearEffects()) {
                    UEEffectExecution.clearEffects(victim);
                }
                LivingEntity attacker = resolveAttacker(event.getSource());
                UEEffectExecution.applyEffects(skip.effects(), entry.level(), victim, attacker);
            } finally {
                UEEffectGuard.exit(victim);
            }
            return;
        }
    }

    /**
     * 战利品复制（究极抢夺）：击杀产生的每一份掉落都额外复制一份。
     *
     * <p>{@link LivingDropsEvent} 是死亡掉落<b>最终集合</b>，因此装备掉落、抢夺加成、
     * 其它模组注入的战利品都会被一起复制。每份 {@link ItemEntity} 复制成一模一样的新实体，
     * 原实体的数量/组件/名称全部保留。
     *
     * <p>只在服务端执行；同一次死亡即使武器上有多条该效果也只复制一次。
     */
    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (UEEffectExecution.disabled()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (!UEEffectExecution.serverSide(victim)) {
            return;
        }
        LivingEntity attacker = resolveAttacker(event.getSource());
        if (attacker == null) {
            return;
        }
        boolean duplicates = false;
        for (UEEffectLookup.Resolved entry : UEEffectLookup.fromWeapon(attacker)) {
            if (entry.effect() instanceof UEEffect.LootDuplication) {
                duplicates = true;
                break;
            }
        }
        if (!duplicates || event.getDrops().isEmpty()) {
            return;
        }

        List<ItemEntity> copies = new ArrayList<>(event.getDrops().size());
        for (ItemEntity drop : event.getDrops()) {
            ItemEntity copy = new ItemEntity(victim.level(), drop.getX(), drop.getY(), drop.getZ(),
                    drop.getItem().copy());
            copy.setDeltaMovement(drop.getDeltaMovement());
            copy.setPickUpDelay(10);
            copies.add(copy);
        }
        event.getDrops().addAll(copies);
    }

    // ── 攻击者解析 ─────────────────────────────────────────────────────

    /**
     * 从伤害来源解析「攻击者」。
     *
     * <p>优先 {@code getEntity()}（真正的责任方，例如射箭的玩家），
     * 退化到 {@code getDirectEntity()}（弹射物本体）。二者都不是 {@link LivingEntity} 时返回 {@code null}。
     */
    static LivingEntity resolveAttacker(DamageSource source) {
        Entity entity = source.getEntity();
        if (entity instanceof LivingEntity living) {
            return living;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    /** 攻击者是不是玩家（部分效果的专属判定预留）。 */
    static boolean isPlayer(LivingEntity entity) {
        return entity instanceof Player;
    }
}
