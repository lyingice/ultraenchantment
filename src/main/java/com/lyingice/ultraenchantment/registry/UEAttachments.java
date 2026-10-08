package com.lyingice.ultraenchantment.registry;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.content.CodexData;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 玩家数据附件注册。
 *
 * <h2>只有一个附件：图鉴</h2>
 *
 * <p>{@code serialize} 让它随玩家 NBT 存盘；{@code copyOnDeath()} 让死亡不掉——
 * 规格要求的是「<b>永久</b>解锁」，死了就忘掉显然不叫永久。
 *
 * <h2>为什么<b>要</b> {@code sync}（v4 起）</h2>
 *
 * <p>v3 时刻意不做同步，理由是「图鉴的读取方是服务端，客户端只需要当前可见的那几行，
 * 由菜单的 {@code ContainerData} 逐行下发更省」。那个理由在<b>没有图鉴浏览界面</b>时成立。
 *
 * <p>v4 要新增**图鉴界面**（由图书馆/进阶台提供按钮进入），玩家要看到<b>全部</b>
 * 谱系 × 阶级的解锁状态。逐行下发不再适用——那是「当前列表可见的几行」，
 * 而图鉴是「所有谱系的一张总表」。
 *
 * <p>数据量很小（30 条谱系封顶，每条一个 int），整份同步的开销可以忽略。
 */
public final class UEAttachments {
    private UEAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Ultraenchantment.MODID);

    /** 图鉴：玩家已见过的 (谱系, 阶级)。 */
    public static final Supplier<AttachmentType<CodexData>> CODEX =
            ATTACHMENT_TYPES.register("codex", () -> AttachmentType
                    .builder(() -> CodexData.EMPTY)
                    .serialize(CodexData.CODEC)
                    .sync(CodexData.STREAM_CODEC)
                    .copyOnDeath()
                    .build());

    /**
     * <b>「必进阶」剩余次数</b>（测试/调试用）。
     *
     * <p>由指令 {@code /ultraenchantment guarantee <n>} 设置：接下来 n 次**确实产生了进阶**的
     * 附魔台操作必定触发，不必再和低概率对赌。
     *
     * <p>存盘 + 死亡保留：测试期间重登、死一次都不该丢。
     */
    public static final Supplier<AttachmentType<Integer>> GUARANTEED_ASCENSIONS =
            ATTACHMENT_TYPES.register("guaranteed_ascensions", () -> AttachmentType
                    .builder(() -> 0)
                    .serialize(com.mojang.serialization.Codec.INT)
                    .copyOnDeath()
                    .build());

    /** 读「必进阶」剩余次数（没有附件时 0）。 */
    public static int guaranteedAscensions(net.minecraft.world.entity.player.Player player) {
        return player.getData(GUARANTEED_ASCENSIONS);
    }

    /** 写「必进阶」剩余次数（负数按 0 处理）。 */
    public static void setGuaranteedAscensions(net.minecraft.world.entity.player.Player player, int count) {
        player.setData(GUARANTEED_ASCENSIONS, Math.max(0, count));
    }
}
