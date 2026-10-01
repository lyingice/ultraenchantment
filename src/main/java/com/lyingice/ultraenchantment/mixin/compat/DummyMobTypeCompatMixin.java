package com.lyingice.ultraenchantment.mixin.compat;

import com.lyingice.ultraenchantment.logic.EnchantmentFactory;
import com.lyingice.ultraenchantment.logic.UELookups;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <b>试验假人（dummmmmmy）兼容补丁</b>——只在该模组存在时应用（见 {@code UECompatMixinPlugin}）。
 *
 * <h2>对方的问题</h2>
 *
 * <p>{@code DummyMobType.isVulnerableTo(Enchantment)} 第 58 行（1.21-2.1.2 实测字节码）：
 * <pre>
 *   ResourceKey&lt;Enchantment&gt; id = Utils.hackyGetRegistry(Registries.ENCHANTMENT)
 *           .getResourceKey(enchantment)   // Optional
 *           .get();                        // ← 没有注册表条目就抛 NoSuchElementException
 * </pre>
 * 它想让假人模拟「亡灵 / 节肢 / 水生」三类抗性，因此对每个附魔反查注册表 key——
 * 但**没有处理查不到的情况**（{@code .orElse(null)} 即可）。
 *
 * <h2>我们的触发面</h2>
 *
 * <p>本模组的阶级附魔是<b>运行时组装</b>的（{@code EnchantmentFactory} + {@code Holder.Direct}），
 * 不在 {@code minecraft:enchantment} 注册表里，必然查不到 key。
 * 于是：带<b>条件效果</b>的阶级附魔（如穿刺/引雷，效果挂 {@code entity_properties} 条件）
 * 在命中假人时走到它们的兜底分支 → 崩在服务端 tick 里 → 飞行中的三叉戟每 tick 重演 → 读档即崩。
 *
 * <h2>补丁语义</h2>
 *
 * <p>补丁要做的不是「一律返回 false」，而是<b>替对方把身份补上</b>：
 * 阶级附魔的权威身份就是它的<b>原版谱系根源</b>（阶级穿刺 ≡ {@code minecraft:impaling}），
 * 于是海龟壳假人（{@code AQUATIC}）照样吃穿刺加成、亡灵头颅假人照样吃亡灵杀手，
 * 而锋利这类无特攻的附魔依旧返回 false。判不出来的（别家合成附魔）返回 false，至少不崩。
 */
@Mixin(targets = "net.mehvahdjukaar.dummmmmmy.common.DummyMobType")
public abstract class DummyMobTypeCompatMixin {

    @Inject(method = "isVulnerableTo(Lnet/minecraft/world/item/enchantment/Enchantment;)Z",
            at = @At("HEAD"), cancellable = true)
    private void ultraenchantment$answerByRootEnchantment(
            Enchantment enchantment, CallbackInfoReturnable<Boolean> cir) {

        // 先把这个附魔还原成「它属于哪条原版谱系」：
        //   本模组的合成附魔没有注册表 key，但它的阶级就是原版某条谱系（如 阶级穿刺 = minecraft:impaling）；
        //   其它附魔走常规注册表反查。
        ResourceKey<Enchantment> key = keyOf(enchantment);
        if (key == null) {
            // 真的无从判断（别的模组的合成附魔）→ 判「无特攻」，至少不让对方 Optional.get() 崩。
            cir.setReturnValue(false);
            return;
        }

        // 然后照对方自己的三条规则回答。
        //
        // ⚠️ 这里必须回答「是/否」，不能一律 false：
        //   海龟壳假人 = AQUATIC，本来就是给「穿刺」打的靶子；
        //   一律 false 会让玩家的阶级穿刺吃不到加成（作者 2026-10 实测抓到）。
        //
        // 目标枚举常量名（UNDEAD / ARTHROPOD / AQUATIC）在运行期就是 this——
        // mixin 的实例方法会被并进目标枚举类，所以 this 是那个枚举实例。
        String self = ((Enum<?>) (Object) this).name();
        cir.setReturnValue(
                (Enchantments.SMITE.equals(key) && "UNDEAD".equals(self))
                        || (Enchantments.BANE_OF_ARTHROPODS.equals(key) && "ARTHROPOD".equals(self))
                        || (Enchantments.IMPALING.equals(key) && "AQUATIC".equals(self)));
    }

    /**
     * 附魔 → 它的原版谱系 key：合成附魔用 {@code rootOf}（语义身份），其余走注册表反查。
     *
     * @return 判断不出来时返回 {@code null}
     */
    @Nullable
    private static ResourceKey<Enchantment> keyOf(Enchantment enchantment) {
        ResourceLocation root = EnchantmentFactory.rootOf(enchantment);
        if (root != null) {
            return ResourceKey.create(Registries.ENCHANTMENT, root);
        }
        if (EnchantmentFactory.isSynthetic(enchantment)) {
            return null;   // 我们的组装对象但查不到根源（异常情况）
        }
        Registry<Enchantment> registry = UELookups.enchantmentRegistry();
        return registry == null ? null : registry.getResourceKey(enchantment).orElse(null);
    }
}
