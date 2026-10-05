package com.lyingice.ultraenchantment.logic;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.api.UltraEnchantImc;
import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

/**
 * 运行期的「受支持附魔调参表」——目前唯一来源是 IMC。
 *
 * <p>为什么要与数据包<b>分开存</b>：IMC 在模组加载期就到齐，而数据包（阶段条目）可能在之后才加载；
 * 合并读取（{@code 数据包谱系 ∪ IMC 调参}）才不会因为时序把注册弄丢。
 *
 * <p>这里只承载<b>调参</b>：上限收紧、自定义键值、升级花费。
 * 每一阶的<b>效果</b>始终来自数据包（见 {@link UltraEnchantImc} 的说明）。
 */
public final class UEEnchantRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 一条 IMC 调参记录；各字段可缺省（缺省 = 不覆盖）。 */
    public record Tuning(OptionalInt maxTier, Map<String, String> attributes,
                         OptionalInt upgradeCostBase, OptionalInt upgradeCostPerTier) {
        public Tuning {
            attributes = Map.copyOf(attributes);
        }
    }

    private static final Map<ResourceLocation, Tuning> TUNINGS = new ConcurrentHashMap<>();

    private UEEnchantRegistry() {}

    /** 模组构造期挂到 mod bus（{@code FMLCommonSetupEvent}）。 */
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(UEEnchantRegistry::collectImc);
    }

    /** 收集三类 IMC 消息。坏格式只 WARN，不中断其它注册。 */
    static void collectImc() {
        int applied = 0;
        for (String channel : new String[]{UltraEnchantImc.CHANNEL_REGISTER,
                UltraEnchantImc.CHANNEL_ATTRIBUTES, UltraEnchantImc.CHANNEL_UPGRADE_COST}) {
            for (InterModComms.IMCMessage message
                    : InterModComms.getMessages(Ultraenchantment.MODID, channel::equals).toList()) {
                Object payload = message.messageSupplier().get();
                if (!(payload instanceof String text)) {
                    LOGGER.warn("[IMC] {} 的载荷必须是 String，来自 {}，已忽略（实际 {}）",
                            channel, message.senderModId(), payload == null ? "null" : payload.getClass().getName());
                    continue;
                }
                if (applyPayload(channel, text) != null) {
                    applied++;
                }
            }
        }
        if (applied > 0) {
            LOGGER.info("[IMC] 已应用 {} 条来自其它模组的进阶调参（共 {} 条附魔）", applied, TUNINGS.size());
        }
    }

    /**
     * 应用一条 payload：{@code "<ns:path>[;key=value]..."}。
     *
     * @return 解析出的附魔 id；格式非法时返回 {@code null}（已记 WARN）
     */
    public static ResourceLocation applyPayload(String channel, String payload) {
        if (payload == null || payload.isBlank()) {
            LOGGER.warn("[IMC] {} 收到空载荷，已忽略", channel);
            return null;
        }
        String[] parts = payload.split(";");
        ResourceLocation root = ResourceLocation.tryParse(parts[0].trim());
        if (root == null) {
            LOGGER.warn("[IMC] {} 载荷首段不是合法附魔 id：{}", channel, payload);
            return null;
        }

        Map<String, String> kv = new LinkedHashMap<>();
        for (int i = 1; i < parts.length; i++) {
            String segment = parts[i].trim();
            if (segment.isEmpty()) {
                continue;
            }
            int eq = segment.indexOf('=');
            if (eq <= 0) {
                LOGGER.warn("[IMC] {} 的 {} 不是 key=value，已忽略", root, segment);
                continue;
            }
            kv.put(segment.substring(0, eq).trim(), segment.substring(eq + 1).trim());
        }

        Tuning old = TUNINGS.get(root);
        OptionalInt maxTier = old == null ? OptionalInt.empty() : old.maxTier();
        Map<String, String> attributes = new LinkedHashMap<>(old == null ? Map.of() : old.attributes());
        OptionalInt base = old == null ? OptionalInt.empty() : old.upgradeCostBase();
        OptionalInt perTier = old == null ? OptionalInt.empty() : old.upgradeCostPerTier();

        if (UltraEnchantImc.CHANNEL_REGISTER.equals(channel)) {
            Integer value = parseInt(root, UltraEnchantImc.KEY_MAX_TIER, kv.get(UltraEnchantImc.KEY_MAX_TIER), 1, 3);
            if (value != null) {
                maxTier = OptionalInt.of(value);
            }
        } else if (UltraEnchantImc.CHANNEL_ATTRIBUTES.equals(channel)) {
            attributes.putAll(kv);
        } else if (UltraEnchantImc.CHANNEL_UPGRADE_COST.equals(channel)) {
            Integer b = parseInt(root, UltraEnchantImc.KEY_BASE, kv.get(UltraEnchantImc.KEY_BASE), 0, 64);
            Integer p = parseInt(root, UltraEnchantImc.KEY_PER_TIER, kv.get(UltraEnchantImc.KEY_PER_TIER), 0, 64);
            if (b != null) {
                base = OptionalInt.of(b);
            }
            if (p != null) {
                perTier = OptionalInt.of(p);
            }
        } else {
            LOGGER.warn("[IMC] 未知通道 {}，已忽略", channel);
            return null;
        }

        TUNINGS.put(root, new Tuning(maxTier, attributes, base, perTier));
        LOGGER.info("[IMC] 已登记 {}（通道 {}）", root, channel);
        return root;
    }

    private static Integer parseInt(ResourceLocation root, String key, String raw, int min, int max) {
        if (raw == null) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value < min || value > max) {
                LOGGER.warn("[IMC] {} 的 {}={} 超出 {}..{}，已忽略", root, key, raw, min, max);
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            LOGGER.warn("[IMC] {} 的 {}={} 不是整数，已忽略", root, key, raw);
            return null;
        }
    }

    public static Optional<Tuning> tuningOf(ResourceLocation root) {
        return Optional.ofNullable(TUNINGS.get(root));
    }

    /** IMC 收紧后的阶级上限；{@code 0} = 未限制。 */
    public static int maxTierCap(ResourceLocation root) {
        Tuning tuning = TUNINGS.get(root);
        return tuning == null || tuning.maxTier().isEmpty() ? 0 : tuning.maxTier().getAsInt();
    }

    /** 升级书花费：{@code base + per_tier × (目标等级 - 1)}；没有调参就用 {@code fallback}。 */
    public static int upgradeCost(ResourceLocation root, int fallback, int targetCurveLevel) {
        Tuning tuning = TUNINGS.get(root);
        if (tuning == null || (tuning.upgradeCostBase().isEmpty() && tuning.upgradeCostPerTier().isEmpty())) {
            return fallback;
        }
        int base = tuning.upgradeCostBase().orElse(0);
        int perTier = tuning.upgradeCostPerTier().orElse(0);
        return Math.max(1, base + perTier * Math.max(0, targetCurveLevel - 1));
    }

    public static Optional<String> attribute(ResourceLocation root, String key) {
        Tuning tuning = TUNINGS.get(root);
        return tuning == null ? Optional.empty() : Optional.ofNullable(tuning.attributes().get(key));
    }

    public static boolean isImcRegistered(ResourceLocation root) {
        return TUNINGS.containsKey(root);
    }

    public static Map<ResourceLocation, Tuning> all() {
        return Map.copyOf(TUNINGS);
    }

    /** 自检用：清空全部调参（正式流程不会调用）。 */
    public static void clearAll() {
        TUNINGS.clear();
    }
}
