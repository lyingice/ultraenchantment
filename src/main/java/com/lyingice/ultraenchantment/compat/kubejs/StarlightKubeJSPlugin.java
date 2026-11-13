package com.lyingice.ultraenchantment.compat.kubejs;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

/**
 * KubeJS 插件入口——由 {@code kubejs.plugins.txt} 发现。
 *
 * <p>只注册一个绑定：脚本里通过 {@code UltraEnchantment.xxx(stack)} 查询/进化。
 * 没有 KubeJS 时这个类不会被加载。
 */
public final class StarlightKubeJSPlugin implements KubeJSPlugin {
    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("UltraEnchantment", StarlightKubeJS.class);
    }
}
