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
 * <h2>为什么不做 {@code sync}</h2>
 *
 * <p>图鉴的读取方是<b>服务端</b>：进阶台的「这个阶级能不能选」由服务端判定，
 * 客户端只需要知道<b>当前可见的那几行</b>的解锁掩码——那由菜单的
 * {@code ContainerData} 逐行下发（每行 1 个 int），比整份图鉴全量同步更省。
 * 图鉴目前没有独立的浏览界面，因此不需要全量同步。
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
                    .copyOnDeath()
                    .build());
}
