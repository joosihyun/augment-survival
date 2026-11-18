package com.augmentsurvival.logic;

import com.augmentsurvival.state.PlayerAugmentData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.FurnaceScreenHandler;
import net.minecraft.util.collection.DefaultedList;

public class PortableFurnaceScreenHandler extends FurnaceScreenHandler {
    public PortableFurnaceScreenHandler(int syncId, PlayerInventory playerInventory, PlayerAugmentData data) {
        super(syncId, playerInventory, new PersistentFurnaceInventory(data), data.getFurnacePropertyDelegate());
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    private static class PersistentFurnaceInventory implements Inventory {
        private final DefaultedList<ItemStack> items;

        public PersistentFurnaceInventory(PlayerAugmentData data) {
            this.items = data.getFurnaceItems();
        }

        @Override
        public int size() {
            return items.size();
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) return false;
            }
            return true;
        }

        @Override
        public ItemStack getStack(int slot) {
            return items.get(slot);
        }

        @Override
        public ItemStack removeStack(int slot, int amount) {
            ItemStack result = Inventories.splitStack(items, slot, amount);
            if (!result.isEmpty()) markDirty();
            return result;
        }

        @Override
        public ItemStack removeStack(int slot) {
            return Inventories.removeStack(items, slot);
        }

        @Override
        public void setStack(int slot, ItemStack stack) {
            items.set(slot, stack);
            if (stack.getCount() > getMaxCountPerStack()) {
                stack.setCount(getMaxCountPerStack());
            }
            markDirty();
        }

        @Override
        public void markDirty() {
        }

        @Override
        public boolean canPlayerUse(PlayerEntity player) {
            return true;
        }

        @Override
        public void clear() {
            items.clear();
        }
    }
}
