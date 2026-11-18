package com.augmentsurvival.state;

import com.augmentsurvival.model.AugmentRarity;
import com.augmentsurvival.model.AugmentType;
import com.augmentsurvival.model.SynergyType;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class PlayerAugmentData {
    private final List<AugmentType> ownedAugments = new ArrayList<>();
    private final List<AugmentType> activeSlots = new ArrayList<>();
    private final Map<AugmentType, Integer> activeCooldowns = new EnumMap<>(AugmentType.class);
    private final List<AugmentType> pendingChoices = new ArrayList<>();
    private final Set<AugmentType> seenInPool = EnumSet.noneOf(AugmentType.class);
    private final Set<AugmentType> everObtained = EnumSet.noneOf(AugmentType.class);

    private int pendingLevelUps = 0;
    private int rerollsRemaining = 0;
    private int lastProcessedLevel = 0;
    private boolean phoenixUsed = false;
    private long shadowCloakRevealUntilTick = 0L;
    private UUID activeGolemUuid = null;
    private int golemRespawnTimer = 0;

    // 휴대용 화롯대 인벤토리 및 제련 상태 (창을 닫아도 인벤토리 내에서 지속 제련)
    private final DefaultedList<ItemStack> furnaceItems = DefaultedList.ofSize(3, ItemStack.EMPTY);
    private int burnTime = 0;
    private int fuelTime = 0;
    private int cookTime = 0;
    private int cookTimeTotal = 200;

    private final PropertyDelegate furnacePropertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> burnTime;
                case 1 -> fuelTime;
                case 2 -> cookTime;
                case 3 -> cookTimeTotal;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> burnTime = value;
                case 1 -> fuelTime = value;
                case 2 -> cookTime = value;
                case 3 -> cookTimeTotal = value;
            }
        }

        @Override
        public int size() {
            return 4;
        }
    };

    public void resetRunProgress() {
        ownedAugments.clear();
        activeSlots.clear();
        activeCooldowns.clear();
        pendingChoices.clear();
        pendingLevelUps = 0;
        rerollsRemaining = 0;
        lastProcessedLevel = 0;
        phoenixUsed = false;
        shadowCloakRevealUntilTick = 0L;
        activeGolemUuid = null;
        golemRespawnTimer = 0;
        furnaceItems.clear();
        burnTime = 0;
        fuelTime = 0;
        cookTime = 0;
        cookTimeTotal = 200;
    }

    public boolean hasAugment(AugmentType type) {
        return ownedAugments.contains(type);
    }

    public void addAugment(AugmentType type) {
        if (!ownedAugments.contains(type)) {
            ownedAugments.add(type);
        }
        everObtained.add(type);
        seenInPool.add(type);
        if (type.isActiveSkill() && !activeSlots.contains(type) && activeSlots.size() < 4) {
            activeSlots.add(type);
            activeCooldowns.put(type, 0);
        }
    }

    public Map<SynergyType, Integer> calculateSynergies() {
        Map<SynergyType, Integer> counts = new EnumMap<>(SynergyType.class);
        for (AugmentType aug : ownedAugments) {
            for (SynergyType syn : aug.getSynergies()) {
                counts.merge(syn, 1, Integer::sum);
            }
        }
        return counts;
    }

    public int getSynergyCount(SynergyType type) {
        return calculateSynergies().getOrDefault(type, 0);
    }

    public void generateChoices(boolean allowUpgradeRoll) {
        pendingChoices.clear();
        List<AugmentType> candidates = new ArrayList<>();
        for (AugmentType type : AugmentType.values()) {
            if (!ownedAugments.contains(type)) {
                candidates.add(type);
            }
        }
        if (candidates.isEmpty()) {
            candidates.addAll(Arrays.asList(AugmentType.values()));
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int count = Math.min(3, candidates.size());
        while (pendingChoices.size() < count && !candidates.isEmpty()) {
            AugmentRarity targetRarity = rollRarity(random);
            if (allowUpgradeRoll && random.nextFloat() < 0.15f) {
                targetRarity = targetRarity.upgrade();
            }
            AugmentRarity finalRarity = targetRarity;
            List<AugmentType> matching = candidates.stream()
                    .filter(a -> a.getRarity() == finalRarity)
                    .toList();
            AugmentType picked;
            if (!matching.isEmpty()) {
                picked = matching.get(random.nextInt(matching.size()));
            } else {
                picked = candidates.get(random.nextInt(candidates.size()));
            }
            pendingChoices.add(picked);
            seenInPool.add(picked);
            candidates.remove(picked);
        }
    }

    private AugmentRarity rollRarity(ThreadLocalRandom random) {
        int totalWeight = 0;
        for (AugmentRarity r : AugmentRarity.values()) {
            totalWeight += r.getWeight();
        }
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (AugmentRarity r : AugmentRarity.values()) {
            cumulative += r.getWeight();
            if (roll < cumulative) {
                return r;
            }
        }
        return AugmentRarity.COMMON;
    }

    public void tickCooldowns() {
        for (AugmentType active : activeSlots) {
            int cd = activeCooldowns.getOrDefault(active, 0);
            if (cd > 0) {
                activeCooldowns.put(active, cd - 1);
            }
        }
    }

    public void tickPortableFurnace(ServerWorld world) {
        if (!hasAugment(AugmentType.PORTABLE_STATION)) return;

        boolean isBurning = burnTime > 0;
        if (isBurning) {
            burnTime--;
        }

        ItemStack input = furnaceItems.get(0);
        ItemStack fuel = furnaceItems.get(1);
        ItemStack output = furnaceItems.get(2);

        if (isBurning || (!fuel.isEmpty() && !input.isEmpty())) {
            SimpleInventory tempInv = new SimpleInventory(input);
            Optional<SmeltingRecipe> match = world.getRecipeManager()
                    .getFirstMatch(RecipeType.SMELTING, tempInv, world);

            if (match.isPresent()) {
                SmeltingRecipe recipe = match.get();
                ItemStack result = recipe.getOutput(world.getRegistryManager());
                boolean canAcceptOutput = output.isEmpty()
                        || (ItemStack.canCombine(output, result) && output.getCount() + result.getCount() <= output.getMaxCount());

                if (canAcceptOutput) {
                    if (!isBurning && !fuel.isEmpty()) {
                        Map<Item, Integer> fuelMap = AbstractFurnaceBlockEntity.createFuelTimeMap();
                        int fuelVal = fuelMap.getOrDefault(fuel.getItem(), 0);
                        if (fuelVal > 0) {
                            burnTime = fuelVal;
                            fuelTime = fuelVal;
                            fuel.decrement(1);
                            isBurning = true;
                        }
                    }

                    if (isBurning) {
                        cookTimeTotal = Math.max(1, recipe.getCookTime());
                        cookTime++;
                        if (cookTime >= cookTimeTotal) {
                            cookTime = 0;
                            if (output.isEmpty()) {
                                furnaceItems.set(2, result.copy());
                            } else {
                                output.increment(result.getCount());
                            }
                            int craftSyn = getSynergyCount(SynergyType.CRAFTING);
                            float bonusChance = craftSyn >= 4 ? 0.40f : (craftSyn >= 3 ? 0.25f : 0f);
                            if (bonusChance > 0f && ThreadLocalRandom.current().nextFloat() < bonusChance) {
                                ItemStack curOut = furnaceItems.get(2);
                                if (curOut.getCount() < curOut.getMaxCount()) {
                                    curOut.increment(1);
                                }
                            }
                            input.decrement(1);
                        }
                    } else {
                        cookTime = 0;
                    }
                } else {
                    cookTime = 0;
                }
            } else {
                cookTime = 0;
            }
        } else if (cookTime > 0) {
            cookTime = Math.max(0, cookTime - 2);
        }
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();

        NbtList ownedList = new NbtList();
        for (AugmentType a : ownedAugments) ownedList.add(NbtString.of(a.name()));
        nbt.put("OwnedAugments", ownedList);

        NbtList activeList = new NbtList();
        for (AugmentType a : activeSlots) activeList.add(NbtString.of(a.name()));
        nbt.put("ActiveSlots", activeList);

        NbtCompound cdNbt = new NbtCompound();
        for (Map.Entry<AugmentType, Integer> entry : activeCooldowns.entrySet()) {
            cdNbt.putInt(entry.getKey().name(), entry.getValue());
        }
        nbt.put("ActiveCooldowns", cdNbt);

        NbtList pendingList = new NbtList();
        for (AugmentType a : pendingChoices) pendingList.add(NbtString.of(a.name()));
        nbt.put("PendingChoices", pendingList);

        NbtList seenList = new NbtList();
        for (AugmentType a : seenInPool) seenList.add(NbtString.of(a.name()));
        nbt.put("SeenInPool", seenList);

        NbtList everList = new NbtList();
        for (AugmentType a : everObtained) everList.add(NbtString.of(a.name()));
        nbt.put("EverObtained", everList);

        nbt.putInt("PendingLevelUps", pendingLevelUps);
        nbt.putInt("RerollsRemaining", rerollsRemaining);
        nbt.putInt("LastProcessedLevel", lastProcessedLevel);
        nbt.putBoolean("PhoenixUsed", phoenixUsed);
        nbt.putLong("ShadowCloakRevealUntilTick", shadowCloakRevealUntilTick);
        if (activeGolemUuid != null) {
            nbt.putUuid("ActiveGolemUuid", activeGolemUuid);
        }
        nbt.putInt("GolemRespawnTimer", golemRespawnTimer);

        NbtCompound furnaceNbt = new NbtCompound();
        Inventories.writeNbt(furnaceNbt, furnaceItems);
        furnaceNbt.putInt("BurnTime", burnTime);
        furnaceNbt.putInt("FuelTime", fuelTime);
        furnaceNbt.putInt("CookTime", cookTime);
        furnaceNbt.putInt("CookTimeTotal", cookTimeTotal);
        nbt.put("PortableFurnace", furnaceNbt);

        return nbt;
    }

    public static PlayerAugmentData fromNbt(NbtCompound nbt) {
        PlayerAugmentData data = new PlayerAugmentData();

        NbtList ownedList = nbt.getList("OwnedAugments", NbtElement.STRING_TYPE);
        for (int i = 0; i < ownedList.size(); i++) {
            AugmentType a = AugmentType.fromName(ownedList.getString(i));
            if (a != null) data.ownedAugments.add(a);
        }

        NbtList activeList = nbt.getList("ActiveSlots", NbtElement.STRING_TYPE);
        for (int i = 0; i < activeList.size(); i++) {
            AugmentType a = AugmentType.fromName(activeList.getString(i));
            if (a != null) data.activeSlots.add(a);
        }

        NbtCompound cdNbt = nbt.getCompound("ActiveCooldowns");
        for (String key : cdNbt.getKeys()) {
            AugmentType a = AugmentType.fromName(key);
            if (a != null) data.activeCooldowns.put(a, cdNbt.getInt(key));
        }

        NbtList pendingList = nbt.getList("PendingChoices", NbtElement.STRING_TYPE);
        for (int i = 0; i < pendingList.size(); i++) {
            AugmentType a = AugmentType.fromName(pendingList.getString(i));
            if (a != null) data.pendingChoices.add(a);
        }

        NbtList seenList = nbt.getList("SeenInPool", NbtElement.STRING_TYPE);
        for (int i = 0; i < seenList.size(); i++) {
            AugmentType a = AugmentType.fromName(seenList.getString(i));
            if (a != null) data.seenInPool.add(a);
        }

        NbtList everList = nbt.getList("EverObtained", NbtElement.STRING_TYPE);
        for (int i = 0; i < everList.size(); i++) {
            AugmentType a = AugmentType.fromName(everList.getString(i));
            if (a != null) data.everObtained.add(a);
        }

        data.pendingLevelUps = nbt.getInt("PendingLevelUps");
        data.rerollsRemaining = nbt.getInt("RerollsRemaining");
        data.lastProcessedLevel = nbt.getInt("LastProcessedLevel");
        data.phoenixUsed = nbt.getBoolean("PhoenixUsed");
        data.shadowCloakRevealUntilTick = nbt.getLong("ShadowCloakRevealUntilTick");
        if (nbt.containsUuid("ActiveGolemUuid")) {
            data.activeGolemUuid = nbt.getUuid("ActiveGolemUuid");
        }
        data.golemRespawnTimer = nbt.getInt("GolemRespawnTimer");

        if (nbt.contains("PortableFurnace")) {
            NbtCompound furnaceNbt = nbt.getCompound("PortableFurnace");
            Inventories.readNbt(furnaceNbt, data.furnaceItems);
            data.burnTime = furnaceNbt.getInt("BurnTime");
            data.fuelTime = furnaceNbt.getInt("FuelTime");
            data.cookTime = furnaceNbt.getInt("CookTime");
            data.cookTimeTotal = Math.max(1, furnaceNbt.getInt("CookTimeTotal"));
        }

        return data;
    }

    public List<AugmentType> getOwnedAugments() { return ownedAugments; }
    public List<AugmentType> getActiveSlots() { return activeSlots; }
    public Map<AugmentType, Integer> getActiveCooldowns() { return activeCooldowns; }
    public List<AugmentType> getPendingChoices() { return pendingChoices; }
    public Set<AugmentType> getSeenInPool() { return seenInPool; }
    public Set<AugmentType> getEverObtained() { return everObtained; }
    public int getPendingLevelUps() { return pendingLevelUps; }
    public void setPendingLevelUps(int pendingLevelUps) { this.pendingLevelUps = pendingLevelUps; }
    public int getRerollsRemaining() { return rerollsRemaining; }
    public void setRerollsRemaining(int rerollsRemaining) { this.rerollsRemaining = rerollsRemaining; }
    public int getLastProcessedLevel() { return lastProcessedLevel; }
    public void setLastProcessedLevel(int lastProcessedLevel) { this.lastProcessedLevel = lastProcessedLevel; }
    public boolean isPhoenixUsed() { return phoenixUsed; }
    public void setPhoenixUsed(boolean phoenixUsed) { this.phoenixUsed = phoenixUsed; }
    public long getShadowCloakRevealUntilTick() { return shadowCloakRevealUntilTick; }
    public void setShadowCloakRevealUntilTick(long tick) { this.shadowCloakRevealUntilTick = tick; }
    public UUID getActiveGolemUuid() { return activeGolemUuid; }
    public void setActiveGolemUuid(UUID activeGolemUuid) { this.activeGolemUuid = activeGolemUuid; }
    public int getGolemRespawnTimer() { return golemRespawnTimer; }
    public void setGolemRespawnTimer(int golemRespawnTimer) { this.golemRespawnTimer = golemRespawnTimer; }
    public DefaultedList<ItemStack> getFurnaceItems() { return furnaceItems; }
    public PropertyDelegate getFurnacePropertyDelegate() { return furnacePropertyDelegate; }
}
