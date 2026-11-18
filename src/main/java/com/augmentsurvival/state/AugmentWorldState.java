package com.augmentsurvival.state;

import com.augmentsurvival.model.GoalType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.*;

public class AugmentWorldState extends PersistentState {
    private static final String STATE_ID = "augmentsurvival_world_state";

    private boolean gameActive = false;
    private GoalType currentGoal = GoalType.ENDER_DRAGON;
    private int currentProgress = 0;
    private long gameStartTick = 0L;

    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final Map<UUID, PlayerAugmentData> playerDataMap = new HashMap<>();

    public static AugmentWorldState get(MinecraftServer server) {
        PersistentStateManager manager = server.getOverworld().getPersistentStateManager();
        return manager.getOrCreate(
                AugmentWorldState::fromNbt,
                AugmentWorldState::new,
                STATE_ID
        );
    }

    public PlayerAugmentData getOrCreatePlayerData(UUID uuid) {
        return playerDataMap.computeIfAbsent(uuid, k -> new PlayerAugmentData());
    }

    public boolean isParticipant(UUID uuid) {
        return participants.contains(uuid);
    }

    public boolean isSpectator(UUID uuid) {
        return spectators.contains(uuid);
    }

    public void setParticipant(UUID uuid) {
        spectators.remove(uuid);
        participants.add(uuid);
        markDirty();
    }

    public void setSpectator(UUID uuid) {
        participants.remove(uuid);
        spectators.add(uuid);
        markDirty();
    }

    public void startNewGame(GoalType goal, long worldTick) {
        this.gameActive = true;
        this.currentGoal = goal;
        this.currentProgress = 0;
        this.gameStartTick = worldTick;
        for (UUID uuid : participants) {
            getOrCreatePlayerData(uuid).resetRunProgress();
        }
        markDirty();
    }

    public void finishGame() {
        this.gameActive = false;
        markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putBoolean("GameActive", gameActive);
        nbt.putString("CurrentGoal", currentGoal.name());
        nbt.putInt("CurrentProgress", currentProgress);
        nbt.putLong("GameStartTick", gameStartTick);

        NbtList partList = new NbtList();
        for (UUID uuid : participants) partList.add(NbtString.of(uuid.toString()));
        nbt.put("Participants", partList);

        NbtList specList = new NbtList();
        for (UUID uuid : spectators) specList.add(NbtString.of(uuid.toString()));
        nbt.put("Spectators", specList);

        NbtCompound playersNbt = new NbtCompound();
        for (Map.Entry<UUID, PlayerAugmentData> entry : playerDataMap.entrySet()) {
            playersNbt.put(entry.getKey().toString(), entry.getValue().toNbt());
        }
        nbt.put("Players", playersNbt);

        return nbt;
    }

    public static AugmentWorldState fromNbt(NbtCompound nbt) {
        AugmentWorldState state = new AugmentWorldState();
        state.gameActive = nbt.getBoolean("GameActive");
        try {
            state.currentGoal = GoalType.valueOf(nbt.getString("CurrentGoal"));
        } catch (Exception ignored) {
            state.currentGoal = GoalType.ENDER_DRAGON;
        }
        state.currentProgress = nbt.getInt("CurrentProgress");
        state.gameStartTick = nbt.getLong("GameStartTick");

        NbtList partList = nbt.getList("Participants", NbtElement.STRING_TYPE);
        for (int i = 0; i < partList.size(); i++) {
            state.participants.add(UUID.fromString(partList.getString(i)));
        }

        NbtList specList = nbt.getList("Spectators", NbtElement.STRING_TYPE);
        for (int i = 0; i < specList.size(); i++) {
            state.spectators.add(UUID.fromString(specList.getString(i)));
        }

        NbtCompound playersNbt = nbt.getCompound("Players");
        for (String key : playersNbt.getKeys()) {
            state.playerDataMap.put(UUID.fromString(key), PlayerAugmentData.fromNbt(playersNbt.getCompound(key)));
        }

        return state;
    }

    public boolean isGameActive() { return gameActive; }
    public GoalType getCurrentGoal() { return currentGoal; }
    public int getCurrentProgress() { return currentProgress; }
    public void setCurrentProgress(int currentProgress) {
        this.currentProgress = currentProgress;
        markDirty();
    }
    public long getGameStartTick() { return gameStartTick; }
    public Set<UUID> getParticipants() { return participants; }
    public Set<UUID> getSpectators() { return spectators; }
}
