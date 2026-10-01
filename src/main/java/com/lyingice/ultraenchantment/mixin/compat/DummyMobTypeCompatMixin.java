package com.lyingice.ultraenchantment.mixin.compat;

import com.lyingice.ultraenchantment.logic.EnchantmentFactory;
import com.lyingice.ultraenchantment.logic.UELookups;
import net.minecraft.core.Registry;
import net.minecraft.world.item.enchantment.Enchantment;
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
 * <p>在方法<b>头部</b>拦下「反查不到注册表 key」的附魔，直接返回 {@code false}
 * ——语义正确：它不是亡灵/节肢/水生特攻附魔，对假人无额外抗性判定。
 * 同时覆盖两种情况：我们自己的合成附魔（引用判定，O(1)），
 * 以及其它模组同样查不到的附魔（服务端可用时做一次通用反查）。
 */
@Mixin(targets = "net.mehvahdjukaar.dummmmmmy.common.DummyMobType")
public abstract class DummyMobTypeCompatMixin {

    @Inject(method = "isVulnerableTo(Lnet/minecraft/world/item/enchantment/Enchantment;)Z",
            at = @At("HEAD"), cancellable = true)
    private void ultraenchantment$guardUnregisteredEnchantment(
            Enchantment enchantment, CallbackInfoReturnable<Boolean> cir) {

        // ① 我们现场组装的阶级附魔：按引用识别，不碰任何注册表。
        if (EnchantmentFactory.isSynthetic(enchantment)) {
            cir.setReturnValue(false);
            return;
        }

        // ② 其它来源：服务端在时做一次通用反查，同样把「查不到」挡在这里。
        Registry<Enchantment> registry = UELookups.enchantmentRegistry();
        if (registry != null && registry.getResourceKey(enchantment).isEmpty()) {
            cir.setReturnValue(false);
        }
    }
}
