package com.augmentsurvival.mixin;

import com.augmentsurvival.duck.DynamiteProjectileDuck;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SnowballEntity.class)
public class SnowballEntityMixin implements DynamiteProjectileDuck {
    @Unique
    private boolean augmentsurvival$dynamite = false;

    @Override
    public boolean augmentsurvival$isDynamite() {
        return augmentsurvival$dynamite;
    }

    @Override
    public void augmentsurvival$setDynamite(boolean dynamite) {
        this.augmentsurvival$dynamite = dynamite;
    }
}
