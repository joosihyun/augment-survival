package com.augmentsurvival.mixin;

import com.augmentsurvival.duck.DynamiteProjectileDuck;
import com.augmentsurvival.logic.AugmentEventHandler;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownEntity.class)
public abstract class ThrownEntityMixin {

    // [10. 다이너마이트 투척] 충돌 시 지형 파괴 없는 몬스터 광역 폭발
    @Inject(method = "onCollision", at = @At("HEAD"), cancellable = true)
    private void augmentsurvival$onDynamiteCollision(HitResult hitResult, CallbackInfo ci) {
        ThrownEntity self = (ThrownEntity) (Object) this;
        if (self instanceof DynamiteProjectileDuck duck && duck.augmentsurvival$isDynamite()) {
            if (self.getWorld() instanceof ServerWorld sw) {
                AugmentEventHandler.triggerDynamiteExplosion(sw, self.getOwner(), hitResult.getPos());
            }
            self.discard();
            ci.cancel();
        }
    }
}
