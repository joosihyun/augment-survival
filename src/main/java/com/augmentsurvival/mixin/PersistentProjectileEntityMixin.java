package com.augmentsurvival.mixin;

import com.augmentsurvival.logic.AugmentItemHelper;
import com.augmentsurvival.model.AugmentType;
import com.augmentsurvival.state.AugmentWorldState;
import com.augmentsurvival.state.PlayerAugmentData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;

@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileEntityMixin {

    @Shadow protected boolean inGround;

    @Unique private boolean augmentsurvival$odmChecked = false;
    @Unique private boolean augmentsurvival$isOdmGrapple = false;

    // [8. 유도 화살] & [5. 입체기동장치 발사 판정]
    @Inject(method = "tick", at = @At("TAIL"))
    private void augmentsurvival$onArrowTick(CallbackInfo ci) {
        PersistentProjectileEntity arrow = (PersistentProjectileEntity) (Object) this;
        if (arrow.getWorld().isClient || !(arrow.getOwner() instanceof ServerPlayerEntity owner) || owner.getServer() == null) {
            return;
        }

        AugmentWorldState state = AugmentWorldState.get(owner.getServer());
        PlayerAugmentData data = state.getOrCreatePlayerData(owner.getUuid());

        // 발사 직후 왼손 입체기동장치 장착 여부 기록
        if (!augmentsurvival$odmChecked) {
            augmentsurvival$odmChecked = true;
            if (data.hasAugment(AugmentType.ODM_GEAR) && AugmentItemHelper.isOdmGear(owner.getOffHandStack())) {
                augmentsurvival$isOdmGrapple = true;
            }
        }

        if (augmentsurvival$isOdmGrapple && !this.inGround && arrow.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.CRIT, arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0, 0, 0, 0);
        }

        // [8. 유도 화살]: 비행 중인 화살이 주변 적대적 몹을 향해 부드럽게 궤적 유도
        if (!this.inGround && data.hasAugment(AugmentType.HOMING_ARROWS)) {
            Box searchBox = arrow.getBoundingBox().expand(14.0);
            HostileEntity target = arrow.getWorld().getEntitiesByClass(HostileEntity.class, searchBox, LivingEntity::isAlive)
                    .stream()
                    .filter(owner::canSee)
                    .min(Comparator.comparingDouble(arrow::squaredDistanceTo))
                    .orElse(null);

            if (target != null) {
                Vec3d currentVel = arrow.getVelocity();
                double speed = currentVel.length();
                if (speed > 0.1) {
                    Vec3d targetPos = new Vec3d(target.getX(), target.getBodyY(0.5), target.getZ());
                    Vec3d desiredDir = targetPos.subtract(arrow.getPos()).normalize();
                    Vec3d newDir = currentVel.normalize().lerp(desiredDir, 0.22).normalize();
                    arrow.setVelocity(newDir.multiply(speed));
                }
            }
        }
    }

    // [5. 입체기동장치]: 화살이 블록에 적중하는 순간 와이어 도약
    @Inject(method = "onBlockHit", at = @At("TAIL"))
    private void augmentsurvival$onOdmArrowBlockHit(BlockHitResult blockHitResult, CallbackInfo ci) {
        if (!augmentsurvival$isOdmGrapple) return;
        augmentsurvival$isOdmGrapple = false;

        PersistentProjectileEntity arrow = (PersistentProjectileEntity) (Object) this;
        Entity ownerEntity = arrow.getOwner();
        if (ownerEntity instanceof ServerPlayerEntity player && !player.getWorld().isClient) {
            Vec3d hitPos = blockHitResult.getPos();
            Vec3d pullVector = hitPos.subtract(player.getPos());
            double distance = Math.min(pullVector.length(), 32.0);
            Vec3d launchVel = pullVector.normalize().multiply(Math.max(1.2, distance * 0.14)).add(0.0, 0.48, 0.0);

            player.setVelocity(launchVel);
            player.velocityModified = true;
            player.fallDistance = 0.0f;

            ServerWorld sw = player.getServerWorld();
            sw.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE, SoundCategory.PLAYERS, 1.2f, 1.5f);
            sw.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.5, player.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
            arrow.discard();
        }
    }
}
