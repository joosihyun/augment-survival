package com.augmentsurvival.model;

public enum AugmentRarity {
    COMMON("일반", 65, 0xFFB0B0B0, 0xFF2B2B2B, "§7"),
    EPIC("영웅", 27, 0xFFA335EE, 0xFF291438, "§5"),
    LEGENDARY("전설", 8, 0xFFFFAA00, 0xFF382608, "§6");

    private final String displayName;
    private final int weight;
    private final int borderColor;
    private final int backgroundColor;
    private final String formattingCode;

    AugmentRarity(String displayName, int weight, int borderColor, int backgroundColor, String formattingCode) {
        this.displayName = displayName;
        this.weight = weight;
        this.borderColor = borderColor;
        this.backgroundColor = backgroundColor;
        this.formattingCode = formattingCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getWeight() {
        return weight;
    }

    public int getBorderColor() {
        return borderColor;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public String getFormattingCode() {
        return formattingCode;
    }

    public AugmentRarity upgrade() {
        return switch (this) {
            case COMMON -> EPIC;
            case EPIC, LEGENDARY -> LEGENDARY;
        };
    }
}
