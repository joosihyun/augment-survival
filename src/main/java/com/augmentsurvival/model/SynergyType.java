package com.augmentsurvival.model;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

public enum SynergyType {
    MELEE("근접", Items.IRON_SWORD, 0xFFE05252,
            "2스택: 근접 공격력 +15%",
            "3스택: 근접 공격력 +30%, 피해 흡혈 10%",
            "4스택: 근접 공격력 +50%, 흡혈 15%, 치명타 광역 충격파"),
    RANGED("원거리", Items.BOW, 0xFF52C46B,
            "2스택: 투사체 피해량 +15%",
            "3스택: 투사체 피해량 +30%, 화살 미소모 25%",
            "4스택: 투사체 피해량 +50%, 미소모 40%, 적중 시 발광/둔화"),
    MINING("광질", Items.DIAMOND_PICKAXE, 0xFF4CC9F0,
            "2스택: 채굴 속도 +20%",
            "3스택: 채굴 속도 +40%, 돌 채굴 시 1% 무작위 광물 드롭",
            "4스택: 채굴 속도 +65%, 돌 채굴 시 2.5% 광물 드롭 + 야간 투시"),
    CRAFTING("제작", Items.CRAFTING_TABLE, 0xFFF4A261,
            "2스택: 도구 내구도 소모 20% 감소",
            "3스택: 제작/제련 시 25% 확률로 추가 결과물 획득",
            "4스택: 보유 장비 내구도 자동 미세 수선 + 추가 결과물 40%"),
    UTILITY("유틸", Items.FEATHER, 0xFF9B5DE5,
            "2스택: 이동 속도 +12%, 낙하 피해 -30%",
            "3스택: 이동 속도 +22%, 낙하 피해 -60%, 아이템 자석 +3칸",
            "4스택: 이동 속도 +35%, 낙하 피해 면역, 최대 체력 +4"),
    EXP("경험치", Items.EXPERIENCE_BOTTLE, 0xFFAACC00,
            "2스택: 경험치 획득량 추가 +25%",
            "3스택: 경험치 획득량 추가 +50%, 레벨업 시 체력/허기 회복",
            "4스택: 경험치 획득량 추가 +100%, 몹 처치 시 추가 경험치 드롭");

    private final String displayName;
    private final Item iconItem;
    private final int color;
    private final String tier2Desc;
    private final String tier3Desc;
    private final String tier4Desc;

    SynergyType(String displayName, Item iconItem, int color, String tier2Desc, String tier3Desc, String tier4Desc) {
        this.displayName = displayName;
        this.iconItem = iconItem;
        this.color = color;
        this.tier2Desc = tier2Desc;
        this.tier3Desc = tier3Desc;
        this.tier4Desc = tier4Desc;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Item getIconItem() {
        return iconItem;
    }

    public int getColor() {
        return color;
    }

    public String getActiveTierSummary(int count) {
        if (count >= 4) return tier4Desc;
        if (count == 3) return tier3Desc;
        if (count == 2) return tier2Desc;
        return "비활성 (2스택 필요)";
    }
}
