package com.lyingice.ultraenchantment.command;

import com.lyingice.ultraenchantment.registry.UEAttachments;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 本模组的指令。
 *
 * <h2>目前只有一条：{@code /ultraenchantment guarantee <n>}（别名 {@code /ue}）</h2>
 *
 * <p>附魔台出进阶是<b>低概率事件</b>（每点附魔能力不到 1%，满书架第 3 档也就几个百分点），
 * 想验证机制得刷几百次。这条指令把接下来 n 次<b>确实有可进阶附魔</b>的附魔台操作
 * 变成必定触发 —— 注意是「确实产生了进阶才扣一次」，所以不会因为附了几次没料的白板而浪费。
 *
 * <p>{@code n = 0} 清除；不带参数则查询当前剩余次数。
 * 需要 OP（权限等级 2）。
 *
 * <p>⚠️ 只影响附魔台的两条路径（原版 / 神化），不影响进阶台与铁砧 —— 那些本来就是确定性的。
 */
public final class UECommands {
    private UECommands() {}

    /** {@code RegisterCommandsEvent} 的监听入口（game bus）。 */
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(root("ultraenchantment"));
        // 测试时少打几个字
        event.getDispatcher().register(root("ue"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("guarantee")
                        .executes(UECommands::query)
                        .then(Commands.argument("count", IntegerArgumentType.integer(0, 100000))
                                .executes(ctx -> set(ctx, IntegerArgumentType.getInteger(ctx, "count")))));
    }

    private static int query(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int left = UEAttachments.guaranteedAscensions(player);
        ctx.getSource().sendSuccess(() -> Component.literal(
                left <= 0 ? "[UE] 当前没有待触发的「必进阶」"
                          : "[UE] 还剩 " + left + " 次「必进阶」"), false);
        return left;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        UEAttachments.setGuaranteedAscensions(player, count);
        ctx.getSource().sendSuccess(() -> Component.literal(count <= 0
                ? "[UE] 已清除「必进阶」"
                : "[UE] 接下来 " + count + " 次进阶必定触发（只对附魔台生效；确实进阶才扣次数）"), true);
        return count;
    }
}
