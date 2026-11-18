package com.augmentsurvival.network;

import com.augmentsurvival.model.AugmentType;
import com.augmentsurvival.model.GoalType;
import com.augmentsurvival.model.SynergyType;
import net.minecraft.network.PacketByteBuf;

import java.util.*;

public class ClientStateCache {
    public record LobbyPlayerEntry(UUID uuid, String name, boolean participant, boolean spectator) {}
    public record ActiveSlotEntry(AugmentType augment, int cooldownTicks) {}

    public static boolean gameActive = false;
    public static GoalType currentGoal = GoalType.ENDER_DRAGON;
    public static int currentProgress = 0;
    public static int targetProgress = 1;
    public static boolean isParticipant = false;
    public static boolean isSpectator = false;

    public static final List<AugmentType> ownedAugments = new ArrayList<>();
    public static final Map<SynergyType, Integer> synergies = new EnumMap<>(SynergyType.class);
    public static final List<ActiveSlotEntry> activeSlots = new ArrayList<>();
    public static final List<AugmentType> pendingChoices = new ArrayList<>();
    public static int rerollsRemaining = 0;
    public static int pendingLevelUps = 0;
    public static int selectedActiveIndex = 0;

    public static final Set<AugmentType> seenInPool = EnumSet.noneOf(AugmentType.class);
    public static final Set<AugmentType> everObtained = EnumSet.noneOf(AugmentType.class);
    public static final List<LobbyPlayerEntry> lobbyPlayers = new ArrayList<>();

    public static void readFromBuf(PacketByteBuf buf) {
        gameActive = buf.readBoolean();
        try {
            currentGoal = GoalType.valueOf(buf.readString());
        } catch (Exception ignored) {
            currentGoal = GoalType.ENDER_DRAGON;
        }
        currentProgress = buf.readInt();
        targetProgress = Math.max(1, buf.readInt());
        isParticipant = buf.readBoolean();
        isSpectator = buf.readBoolean();

        ownedAugments.clear();
        int ownedCount = buf.readInt();
        for (int i = 0; i < ownedCount; i++) {
            AugmentType a = AugmentType.fromName(buf.readString());
            if (a != null) ownedAugments.add(a);
        }

        synergies.clear();
        int synCount = buf.readInt();
        for (int i = 0; i < synCount; i++) {
            try {
                SynergyType s = SynergyType.valueOf(buf.readString());
                int count = buf.readInt();
                synergies.put(s, count);
            } catch (Exception ignored) {
                buf.readInt();
            }
        }

        activeSlots.clear();
        int activeCount = buf.readInt();
        for (int i = 0; i < activeCount; i++) {
            AugmentType a = AugmentType.fromName(buf.readString());
            int cd = buf.readInt();
            if (a != null) activeSlots.add(new ActiveSlotEntry(a, cd));
        }
        if (selectedActiveIndex >= activeSlots.size()) {
            selectedActiveIndex = 0;
        }

        pendingChoices.clear();
        int choiceCount = buf.readInt();
        for (int i = 0; i < choiceCount; i++) {
            AugmentType a = AugmentType.fromName(buf.readString());
            if (a != null) pendingChoices.add(a);
        }
        rerollsRemaining = buf.readInt();
        pendingLevelUps = buf.readInt();

        seenInPool.clear();
        int seenCount = buf.readInt();
        for (int i = 0; i < seenCount; i++) {
            AugmentType a = AugmentType.fromName(buf.readString());
            if (a != null) seenInPool.add(a);
        }

        everObtained.clear();
        int everCount = buf.readInt();
        for (int i = 0; i < everCount; i++) {
            AugmentType a = AugmentType.fromName(buf.readString());
            if (a != null) everObtained.add(a);
        }

        lobbyPlayers.clear();
        int lobbyCount = buf.readInt();
        for (int i = 0; i < lobbyCount; i++) {
            UUID uuid = buf.readUuid();
            String name = buf.readString();
            boolean part = buf.readBoolean();
            boolean spec = buf.readBoolean();
            lobbyPlayers.add(new LobbyPlayerEntry(uuid, name, part, spec));
        }
    }

    public static void cycleActiveSlot() {
        if (!activeSlots.isEmpty()) {
            selectedActiveIndex = (selectedActiveIndex + 1) % activeSlots.size();
        }
    }
}
