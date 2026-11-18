package com.augmentsurvival.mixin;

import com.augmentsurvival.logic.SynergyManager;
import com.augmentsurvival.state.AugmentWorldState;
import com.augmentsurvival.state.PlayerAugmentData;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    // 모든 경험치 획득량 기본 1.5배 증폭 (+ EXP 시너지 배율)
    @ModifyVariable(method = "addExperience", at = @At("HEAD"), argsOnly = true)
    private int augmentsurvival$multiplyExperience(int experience) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (experience > 0 && self instanceof ServerPlayerEntity serverPlayer && serverPlayer.getServer() != null) {
            AugmentWorldState state = AugmentWorldState.get(serverPlayer.getServer());
            if (state.isGameActive() && state.isParticipant(serverPlayer.getUuid())) {
                PlayerAugmentData data = state.getOrCreatePlayerData(serverPlayer.getUuid());
                return Math.round(experience * SynergyManager.getExperienceMultiplier(data));
            }
        }
        return experience;
    }

    // 광질 시너지 채굴 속도 적용
    @Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
    private void augmentsurvival$applyMiningSynergySpeed(BlockState block, CallbackInfoReturnable<Float> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self instanceof ServerPlayerEntity serverPlayer && serverPlayer.getServer() != null) {
            AugmentWorldState state = AugmentWorldState.get(serverPlayer.getServer());
            if (state.isGameActive() && state.isParticipant(serverPlayer.getUuid())) {
                PlayerAugmentData data = state.getOrCreatePlayerData(serverPlayer.getUuid());
                cir.setReturnValue(cir.getReturnValue() * SynergyManager.getMiningSpeedMultiplier(data));
            }
        }
    }
}
