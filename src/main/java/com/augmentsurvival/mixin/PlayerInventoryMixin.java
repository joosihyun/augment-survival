package com.augmentsurvival.mixin;

import com.augmentsurvival.logic.AugmentItemHelper;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin {

    // 사망 시 가더브 곡괭이 등 소울바운드 아이템이 바닥에 드롭되지 않도록 보호
    @Redirect(
            method = "dropAll",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;"
            )
    )
    private ItemEntity augmentsurvival$preventSoulboundDrop(PlayerEntity player, ItemStack stack, boolean throwRandomly, boolean retainOwnership) {
        if (AugmentItemHelper.isSoulbound(stack)) {
            return null;
        }
        return player.dropItem(stack, throwRandomly, retainOwnership);
    }
}
