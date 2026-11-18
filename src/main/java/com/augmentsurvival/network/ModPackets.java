package com.augmentsurvival.network;

import com.augmentsurvival.AugmentSurvivalMod;
import com.augmentsurvival.logic.AugmentEventHandler;
import com.augmentsurvival.model.AugmentType;
import com.augmentsurvival.model.GoalType;
import com.augmentsurvival.model.SynergyType;
import com.augmentsurvival.state.AugmentWorldState;
import com.augmentsurvival.state.PlayerAugmentData;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;

import java.util.Map;
import java.util.UUID;

public class ModPackets {
    // S2C
    public static final Identifier SYNC_STATE_S2C = new Identifier(AugmentSurvivalMod.MOD_ID, "sync_state");
    public static final Identifier OPEN_AUGMENT_SELECT_S2C = new Identifier(AugmentSurvivalMod.MOD_ID, "open_select");
    public static final Identifier OPEN_START_GUI_S2C = new Identifier(AugmentSurvivalMod.MOD_ID, "open_start_gui");
    public static final Identifier OPEN_CODEX_GUI_S2C = new Identifier(AugmentSurvivalMod.MOD_ID, "open_codex_gui");
    public static final Identifier GAME_RESULT_S2C = new Identifier(AugmentSurvivalMod.MOD_ID, "game_result");

    // C2S
    public static final Identifier SELECT_AUGMENT_C2S = new Identifier(AugmentSurvivalMod.MOD_ID, "select_augment");
    public static final Identifier REROLL_AUGMENT_C2S = new Identifier(AugmentSurvivalMod.MOD_ID, "reroll_augment");
    public static final Identifier USE_ACTIVE_SKILL_C2S = new Identifier(AugmentSurvivalMod.MOD_ID, "use_active");
    public static final Identifier START_GAME_C2S = new Identifier(AugmentSurvivalMod.MOD_ID, "start_game");
    public static final Identifier SET_ROLE_C2S = new Identifier(AugmentSurvivalMod.MOD_ID, "set_role");

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(SELECT_AUGMENT_C2S, (server, player, handler, buf, responseSender) -> {
            int choiceIndex = buf.readInt();
            server.execute(() -> AugmentEventHandler.handleSelectAugment(player, choiceIndex));
        });

        ServerPlayNetworking.registerGlobalReceiver(REROLL_AUGMENT_C2S, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> AugmentEventHandler.handleRerollAugment(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(USE_ACTIVE_SKILL_C2S, (server, player, handler, buf, responseSender) -> {
            int slotIndex = buf.readInt();
            server.execute(() -> AugmentEventHandler.handleUseActiveSkill(player, slotIndex));
        });

        ServerPlayNetworking.registerGlobalReceiver(SET_ROLE_C2S, (server, player, handler, buf, responseSender) -> {
            UUID targetUuid = buf.readUuid();
            boolean participant = buf.readBoolean();
            server.execute(() -> {
                AugmentWorldState state = AugmentWorldState.get(server);
                if (participant) {
                    state.setParticipant(targetUuid);
                } else {
                    state.setSpectator(targetUuid);
                }
                ServerPlayerEntity targetPlayer = server.getPlayerManager().getPlayer(targetUuid);
                if (targetPlayer != null && state.isGameActive()) {
                    targetPlayer.changeGameMode(participant ? GameMode.SURVIVAL : GameMode.SPECTATOR);
                }
                syncAllPlayers(server);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(START_GAME_C2S, (server, player, handler, buf, responseSender) -> {
            boolean randomGoal = buf.readBoolean();
            String goalName = buf.readString();
            server.execute(() -> {
                GoalType goal = randomGoal ? GoalType.randomGoal() : GoalType.valueOf(goalName);
                AugmentEventHandler.startGameSession(server, goal);
            });
        });
    }

    public static void syncAllPlayers(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            syncPlayerState(p);
        }
    }

    public static void syncPlayerState(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        AugmentWorldState state = AugmentWorldState.get(server);
        PlayerAugmentData data = state.getOrCreatePlayerData(player.getUuid());

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(state.isGameActive());
        buf.writeString(state.getCurrentGoal().name());
        buf.writeInt(state.getCurrentProgress());
        buf.writeInt(state.getCurrentGoal().getTargetAmount());
        buf.writeBoolean(state.isParticipant(player.getUuid()));
        buf.writeBoolean(state.isSpectator(player.getUuid()));

        // 보유 증강
        buf.writeInt(data.getOwnedAugments().size());
        for (AugmentType aug : data.getOwnedAugments()) {
            buf.writeString(aug.name());
        }

        // 시너지 카운트
        Map<SynergyType, Integer> synergies = data.calculateSynergies();
        buf.writeInt(synergies.size());
        for (Map.Entry<SynergyType, Integer> entry : synergies.entrySet()) {
            buf.writeString(entry.getKey().name());
            buf.writeInt(entry.getValue());
        }

        // 액티브 슬롯 & 남은 쿨타임
        buf.writeInt(data.getActiveSlots().size());
        for (AugmentType active : data.getActiveSlots()) {
            buf.writeString(active.name());
            buf.writeInt(data.getActiveCooldowns().getOrDefault(active, 0));
        }

        // 대기 중인 선택 카드
        buf.writeInt(data.getPendingChoices().size());
        for (AugmentType choice : data.getPendingChoices()) {
            buf.writeString(choice.name());
        }
        buf.writeInt(data.getRerollsRemaining());
        buf.writeInt(data.getPendingLevelUps());

        // 도감 데이터
        buf.writeInt(data.getSeenInPool().size());
        for (AugmentType seen : data.getSeenInPool()) {
            buf.writeString(seen.name());
        }
        buf.writeInt(data.getEverObtained().size());
        for (AugmentType obtained : data.getEverObtained()) {
            buf.writeString(obtained.name());
        }

        // 로비 플레이어 역할 목록
        var playerList = server.getPlayerManager().getPlayerList();
        buf.writeInt(playerList.size());
        for (ServerPlayerEntity sp : playerList) {
            buf.writeUuid(sp.getUuid());
            buf.writeString(sp.getGameProfile().getName());
            buf.writeBoolean(state.isParticipant(sp.getUuid()));
            buf.writeBoolean(state.isSpectator(sp.getUuid()));
        }

        ServerPlayNetworking.send(player, SYNC_STATE_S2C, buf);
    }

    public static void sendOpenSelectGui(ServerPlayerEntity player) {
        syncPlayerState(player);
        ServerPlayNetworking.send(player, OPEN_AUGMENT_SELECT_S2C, PacketByteBufs.empty());
    }

    public static void sendOpenStartGui(ServerPlayerEntity player) {
        syncPlayerState(player);
        ServerPlayNetworking.send(player, OPEN_START_GUI_S2C, PacketByteBufs.empty());
    }

    public static void sendOpenCodexGui(ServerPlayerEntity player) {
        syncPlayerState(player);
        ServerPlayNetworking.send(player, OPEN_CODEX_GUI_S2C, PacketByteBufs.empty());
    }

    public static void sendGameResult(ServerPlayerEntity player, long elapsedTicks) {
        syncPlayerState(player);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeLong(elapsedTicks);
        ServerPlayNetworking.send(player, GAME_RESULT_S2C, buf);
    }
}
