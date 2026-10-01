package com.lyingice.ultraenchantment.mixin;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.slf4j.Logger;

/**
 * <b>兼容补丁的门控</b>：本配置里的 mixin 只在「目标模组确实装了」时才应用。
 *
 * <p>为什么单独一份配置 + 插件，而不是塞进主 mixins.json：
 * <ul>
 *   <li>主配置的 mixin 是我们自己的目标（原版类），永远该应用；</li>
 *   <li>兼容补丁的目标是<b>别的模组</b>的类，没装那个模组时<b>不能</b>尝试应用
 *       （target 不存在，混入会失败）。</li>
 * </ul>
 *
 * <p>判据用 {@code ModList.isLoaded("dummmmmmy")} 而不是 {@code Class.forName}：
 * 前者不触发类加载，也不会因为对方改名/换包名而抛异常。
 */
public class UECompatMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 试验假人（dummmmmmy）的承载类——它是我们唯一要补的目标。 */
    private static final String DUMMY_TARGET = "net.mehvahdjukaar.dummmmmmy.common.DummyMobType";

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.debug("[UE-compat] 兼容配置已加载：{}", mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!DUMMY_TARGET.equals(targetClassName)) {
            return true;
        }

        boolean present;
        try {
            present = dummyModPresent();
        } catch (Throwable t) {
            // ⚠️ 绝不能从这里抛出去：shouldApplyMixin 抛异常会让**整份配置**作废
            // （InvalidMixinException），补丁静默失效。宁可退回「不应用」。
            LOGGER.warn("[UE-compat] 判定试验假人是否安装时出错，按未安装处理", t);
            return false;
        }

        LOGGER.info("[UE-compat] 试验假人{}：{}「附魔注册表反查」兼容补丁",
                present ? "（dummmmmmy）已安装" : "未安装",
                present ? "启用" : "跳过");
        return present;
    }

    /**
     * 试验假人在不在？
     *
     * <p><b>不能用 {@code ModList.get()}</b>：mixin 配置在「准备阶段」就要判定
     * （{@code targets} 是字符串的 mixin 早已需要知道对方在不在），而那一刻
     * ModList 还没建好——实测 {@code ModList.get()} 返回 {@code null}，
     * 直接 NPE（v2.10 第一版就是这么静默失效的）。
     *
     * <p>改用<b>加载期</b>的 {@code FMLLoader.getLoadingModList()}；
     * 万一那时也拿不到，退回「类在不在」的探测（{@code initialize=false}，不触发静态初始化）。
     */
    private static boolean dummyModPresent() {
        try {
            var loading = FMLLoader.getLoadingModList();
            if (loading != null) {
                return loading.getModFileById("dummmmmmy") != null;
            }
        } catch (Throwable ignored) {
            // 继续走类探测
        }
        try {
            Class.forName(DUMMY_TARGET, false, UECompatMixinPlugin.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                         IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                          IMixinInfo mixinInfo) {
    }
}
