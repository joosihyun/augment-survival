package com.augmentsurvival.command;

import com.augmentsurvival.logic.AugmentEventHandler;
import com.augmentsurvival.model.GoalType;
import com.augmentsurvival.network.ModPackets;
import com.augmentsurvival.state.AugmentWorldState;
import com.augmentsurvival.state.PlayerAugmentData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

public class AugmentCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("augment")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    ModPackets.sendOpenStartGui(player);
                    return 1;
                })
                .then(CommandManager.literal("start")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            ModPackets.sendOpenStartGui(player);
                            return 1;
                        })
                        .then(CommandManager.argument("goal", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("RANDOM");
                                    for (GoalType g : GoalType.values()) {
                                        builder.suggest(g.name());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String goalArg = StringArgumentType.getString(ctx, "goal").toUpperCase();
                                    GoalType goal = goalArg.equals("RANDOM") ? GoalType.randomGoal() : GoalType.valueOf(goalArg);
                                    AugmentEventHandler.startGameSession(ctx.getSource().getServer(), goal);
                                    return 1;
                                })
                        )
                )
                .then(CommandManager.literal("role")
                        .then(CommandManager.argument("target", EntityArgumentType.player())
                                .then(CommandManager.literal("participant")
                                        .executes(ctx -> {
                                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                                            AugmentWorldState state = AugmentWorldState.get(ctx.getSource().getServer());
                                            state.setParticipant(target.getUuid());
                                            if (state.isGameActive()) target.changeGameMode(GameMode.SURVIVAL);
                                            ModPackets.syncAllPlayers(ctx.getSource().getServer());
                                            ctx.getSource().sendFeedback(() -> Text.literal("§a" + target.getGameProfile().getName() + "님을 참여자로 설정했습니다."), true);
                                            return 1;
                                        })
                                )
                                .then(CommandManager.literal("spectator")
                                        .executes(ctx -> {
                                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                                            AugmentWorldState state = AugmentWorldState.get(ctx.getSource().getServer());
                                            state.setSpectator(target.getUuid());
                                            if (state.isGameActive()) target.changeGameMode(GameMode.SPECTATOR);
                                            ModPackets.syncAllPlayers(ctx.getSource().getServer());
                                            ctx.getSource().sendFeedback(() -> Text.literal("§7" + target.getGameProfile().getName() + "님을 관전자로 설정했습니다."), true);
                                            return 1;
                                        })
                                )
                        )
                )
                .then(CommandManager.literal("codex")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            ModPackets.sendOpenCodexGui(player);
                            return 1;
                        })
                )
                .then(CommandManager.literal("reroll_test")
                        .requires(src -> src.hasPermissionLevel(2))
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            AugmentWorldState state = AugmentWorldState.get(ctx.getSource().getServer());
                            PlayerAugmentData data = state.getOrCreatePlayerData(player.getUuid());
                            data.setRerollsRemaining(1);
                            data.generateChoices(false);
                            ModPackets.sendOpenSelectGui(player);
                            return 1;
                        })
                )
        );
    }
}
