package com.augmentsurvival.logic;

import com.augmentsurvival.model.SynergyType;
import com.augmentsurvival.state.PlayerAugmentData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class SynergyManager {
    private static final UUID MELEE_ATTR_UUID = UUID.fromString("a31d9c10-6a8f-4c72-9e12-100000000001");
    private static final UUID SPEED_ATTR_UUID = UUID.fromString("a31d9c10-6a8f-4c72-9e12-100000000002");
    private static final UUID HEALTH_ATTR_UUID = UUID.fromString("a31d9c10-6a8f-4c72-9e12-100000000003");

    private static final List<Item> BONUS_ORES = List.of(
            Items.COAL, Items.RAW_IRON, Items.RAW_GOLD,
            Items.LAPIS_LAZULI, Items.REDSTONE, Items.DIAMOND, Items.EMERALD
    );

    public static void applyTickPassives(ServerPlayerEntity player, PlayerAugmentData data) {
        int melee = data.getSynergyCount(SynergyType.MELEE);
        int mining = data.getSynergyCount(SynergyType.MINING);
        int crafting = data.getSynergyCount(SynergyType.CRAFTING);
        int utility = data.getSynergyCount(SynergyType.UTILITY);

        // 1. 근접 시너지: 공격력 보너스
        double attackBonus = melee >= 4 ? 0.50 : (melee == 3 ? 0.30 : (melee == 2 ? 0.15 : 0.0));
        updateMultiplierAttribute(player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE),
                MELEE_ATTR_UUID, "augment_melee_synergy", attackBonus);

        // 2. 유틸 시너지: 이동 속도 & 최대 체력 보너스 & 아이템 자석
        double speedBonus = utility >= 4 ? 0.35 : (utility == 3 ? 0.22 : (utility == 2 ? 0.12 : 0.0));
        updateMultiplierAttribute(player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED),
                SPEED_ATTR_UUID, "augment_utility_speed", speedBonus);

        double healthBonus = utility >= 4 ? 4.0 : 0.0;
        updateAdditionAttribute(player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH),
                HEALTH_ATTR_UUID, "augment_utility_health", healthBonus);

        if (utility >= 3) {
            Box magnetBox = player.getBoundingBox().expand(3.5);
            for (ItemEntity item : player.getWorld().getEntitiesByClass(ItemEntity.class, magnetBox, e -> !e.cannotPickup())) {
                item.addVelocity(
                        (player.getX() - item.getX()) * 0.08,
                        (player.getY() + 0.5 - item.getY()) * 0.08,
                        (player.getZ() - item.getZ()) * 0.08
                );
            }
        }

        // 3. 광질 4스택: 야간 투시
        if (mining >= 4) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 260, 0, true, false, false));
        }

        // 4. 제작 4스택: 5초마다 장착 및 손에 든 장비 내구도 1 수선
        if (crafting >= 4 && player.age % 100 == 0) {
            repairStack(player.getMainHandStack());
            repairStack(player.getOffHandStack());
            for (ItemStack armor : player.getInventory().armor) {
                repairStack(armor);
            }
        }
    }

    public static void onStoneMined(ServerWorld world, BlockPos pos, BlockState state, PlayerAugmentData data) {
        int mining = data.getSynergyCount(SynergyType.MINING);
        if (mining < 3) return;

        Block block = state.getBlock();
        if (block == Blocks.STONE || block == Blocks.DEEPSLATE || block == Blocks.ANDESITE
                || block == Blocks.DIORITE || block == Blocks.GRANITE) {
            float chance = mining >= 4 ? 0.025f : 0.01f;
            if (ThreadLocalRandom.current().nextFloat() < chance) {
                Item drop = BONUS_ORES.get(ThreadLocalRandom.current().nextInt(BONUS_ORES.size()));
                Block.dropStack(world, pos, new ItemStack(drop, 1));
            }
        }
    }

    public static float getMiningSpeedMultiplier(PlayerAugmentData data) {
        int mining = data.getSynergyCount(SynergyType.MINING);
        if (mining >= 4) return 1.65f;
        if (mining == 3) return 1.40f;
        if (mining == 2) return 1.20f;
        return 1.0f;
    }

    public static float getExperienceMultiplier(PlayerAugmentData data) {
        int exp = data.getSynergyCount(SynergyType.EXP);
        float synergyBonus = exp >= 4 ? 1.0f : (exp == 3 ? 0.50f : (exp == 2 ? 0.25f : 0.0f));
        return 1.5f * (1.0f + synergyBonus);
    }

    private static void repairStack(ItemStack stack) {
        if (!stack.isEmpty() && stack.isDamaged()) {
            stack.setDamage(stack.getDamage() - 1);
        }
    }

    private static void updateMultiplierAttribute(EntityAttributeInstance instance, UUID uuid, String name, double value) {
        if (instance == null) return;
        EntityAttributeModifier existing = instance.getModifier(uuid);
        if (value <= 0.0) {
            if (existing != null) instance.removeModifier(uuid);
            return;
        }
        if (existing == null || existing.getValue() != value) {
            if (existing != null) instance.removeModifier(uuid);
            instance.addTemporaryModifier(new EntityAttributeModifier(uuid, name, value, EntityAttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    private static void updateAdditionAttribute(EntityAttributeInstance instance, UUID uuid, String name, double value) {
        if (instance == null) return;
        EntityAttributeModifier existing = instance.getModifier(uuid);
        if (value <= 0.0) {
            if (existing != null) instance.removeModifier(uuid);
            return;
        }
        if (existing == null || existing.getValue() != value) {
            if (existing != null) instance.removeModifier(uuid);
            instance.addTemporaryModifier(new EntityAttributeModifier(uuid, name, value, EntityAttributeModifier.Operation.ADDITION));
        }
    }
}
