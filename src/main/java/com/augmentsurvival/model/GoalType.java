package com.augmentsurvival.model;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.concurrent.ThreadLocalRandom;

public enum GoalType {
    ENDER_DRAGON("엔더 드래곤 처치", 1, Items.DRAGON_HEAD),
    DIAMOND_100("다이아몬드 100개 보유", 100, Items.DIAMOND),
    WITHER("위더 처치", 1, Items.NETHER_STAR),
    NETHERITE_16("네더라이트 주괴 16개 보유", 16, Items.NETHERITE_INGOT);

    private final String displayName;
    private final int targetAmount;
    private final Item iconItem;

    GoalType(String displayName, int targetAmount, Item iconItem) {
        this.displayName = displayName;
        this.targetAmount = targetAmount;
        this.iconItem = iconItem;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getTargetAmount() {
        return targetAmount;
    }

    public Item getIconItem() {
        return iconItem;
    }

    public static GoalType randomGoal() {
        GoalType[] values = values();
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }
}
