package com.augmentsurvival.model;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.Arrays;
import java.util.List;

public enum AugmentType {
    AUTO_SMELT(
            "자동 제련",
            "철, 금, 구리 원석 채굴 시 즉시 주괴로 제련되어 드롭됩니다.",
            AugmentRarity.COMMON,
            false,
            0,
            Items.RAW_IRON,
            SynergyType.MINING, SynergyType.CRAFTING
    ),
    GEM_MERCHANT(
            "보석 상인",
            "철, 금, 다이아몬드 채굴 시 50% 확률로 1~2개 추가 드롭됩니다.",
            AugmentRarity.EPIC,
            false,
            0,
            Items.DIAMOND,
            SynergyType.MINING, SynergyType.EXP
    ),
    GADERVE_PICKAXE(
            "가더브 곡괭이",
            "효율 V, 행운 III가 부여되고 사망 시에도 사라지지 않는 전용 다이아몬드 곡괭이를 지급합니다.",
            AugmentRarity.LEGENDARY,
            false,
            0,
            Items.DIAMOND_PICKAXE,
            SynergyType.MINING, SynergyType.EXP
    ),
    PORTABLE_STATION(
            "휴대용 제작·화롯대",
            "우클릭으로 제작대, 웅크리고 우클릭으로 화로를 엽니다. 창을 닫아도 인벤토리에서 제련이 지속됩니다.",
            AugmentRarity.COMMON,
            false,
            0,
            Items.CRAFTING_TABLE,
            SynergyType.CRAFTING, SynergyType.UTILITY
    ),
    ODM_GEAR(
            "입체기동장치",
            "왼손에 전용 장치를 들고 활을 쏘면 화살이 블록에 적중하는 순간 해당 지점으로 빠르게 와이어 도약합니다.",
            AugmentRarity.LEGENDARY,
            false,
            0,
            Items.TRIPWIRE_HOOK,
            SynergyType.UTILITY, SynergyType.RANGED
    ),
    SHADOW_CLOAK(
            "그림자 망토",
            "웅크리기 시 투명화 상태가 되며 주변 몬스터의 어그로가 해제됩니다. (몹 타격 시 3초간 은신 해제)",
            AugmentRarity.LEGENDARY,
            false,
            0,
            Items.ENDER_PEARL,
            SynergyType.UTILITY, SynergyType.MELEE
    ),
    GOLEM_FRIEND(
            "골렘 친구",
            "주변 적대적 몹을 공격하는 아군 철 골렘을 소환합니다. (20칸 이상 이탈 시 텔레포트, 사망 시 60초 후 부활)",
            AugmentRarity.EPIC,
            false,
            0,
            Items.IRON_BLOCK,
            SynergyType.MELEE, SynergyType.UTILITY
    ),
    HOMING_ARROWS(
            "유도 화살",
            "발사한 화살이 주변 적대적 몹을 추적하여 궤적이 휘어지며 유도됩니다.",
            AugmentRarity.EPIC,
            false,
            0,
            Items.SPECTRAL_ARROW,
            SynergyType.RANGED, SynergyType.EXP
    ),
    PHOENIX_RELIC(
            "불사조의 유물",
            "사망에 이르는 피해를 입을 경우 1회에 한해 체력을 모두 회복하며 즉시 부활하고 아이템과 경험치를 보존합니다.",
            AugmentRarity.EPIC,
            false,
            0,
            Items.TOTEM_OF_UNDYING,
            SynergyType.UTILITY, SynergyType.EXP
    ),
    DYNAMITE_THROW(
            "다이너마이트 투척",
            "[액티브 R] 시선 방향으로 지형 파괴 없이 몬스터에게만 광역 피해를 주는 다이너마이트를 투척합니다. (쿨타임 30초)",
            AugmentRarity.EPIC,
            true,
            600,
            Items.TNT,
            SynergyType.RANGED, SynergyType.CRAFTING
    );

    private final String displayName;
    private final String description;
    private final AugmentRarity rarity;
    private final boolean activeSkill;
    private final int cooldownTicks;
    private final Item iconItem;
    private final List<SynergyType> synergies;

    AugmentType(String displayName, String description, AugmentRarity rarity, boolean activeSkill,
                int cooldownTicks, Item iconItem, SynergyType... synergies) {
        this.displayName = displayName;
        this.description = description;
        this.rarity = rarity;
        this.activeSkill = activeSkill;
        this.cooldownTicks = cooldownTicks;
        this.iconItem = iconItem;
        this.synergies = Arrays.asList(synergies);
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public AugmentRarity getRarity() {
        return rarity;
    }

    public boolean isActiveSkill() {
        return activeSkill;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public Item getIconItem() {
        return iconItem;
    }

    public List<SynergyType> getSynergies() {
        return synergies;
    }

    public static AugmentType fromName(String name) {
        for (AugmentType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}
