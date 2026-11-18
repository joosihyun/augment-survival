package com.augmentsurvival.logic;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Text;

public class AugmentItemHelper {
    public static final String NBT_SOULBOUND = "AugmentSoulbound";
    public static final String NBT_PORTABLE_STATION = "AugmentPortableStation";
    public static final String NBT_ODM_GEAR = "AugmentOdmGear";

    public static boolean isSoulbound(ItemStack stack) {
        return !stack.isEmpty() && stack.hasNbt() && stack.getNbt().getBoolean(NBT_SOULBOUND);
    }

    public static boolean isPortableStation(ItemStack stack) {
        return !stack.isEmpty() && stack.hasNbt() && stack.getNbt().getBoolean(NBT_PORTABLE_STATION);
    }

    public static boolean isOdmGear(ItemStack stack) {
        return !stack.isEmpty() && stack.hasNbt() && stack.getNbt().getBoolean(NBT_ODM_GEAR);
    }

    public static ItemStack createGadervePickaxe() {
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.addEnchantment(Enchantments.EFFICIENCY, 5);
        pickaxe.addEnchantment(Enchantments.FORTUNE, 3);
        pickaxe.setCustomName(Text.literal("§6§l가더브 곡괭이"));
        NbtCompound nbt = pickaxe.getOrCreateNbt();
        nbt.putBoolean(NBT_SOULBOUND, true);
        appendLore(pickaxe, "§e[증강 전용] 사망 시에도 영구 보존됩니다.");
        return pickaxe;
    }

    public static ItemStack createPortableStation() {
        ItemStack item = new ItemStack(Items.CRAFTING_TABLE);
        item.setCustomName(Text.literal("§a§l휴대용 제작·화롯대"));
        NbtCompound nbt = item.getOrCreateNbt();
        nbt.putBoolean(NBT_SOULBOUND, true);
        nbt.putBoolean(NBT_PORTABLE_STATION, true);
        appendLore(item, "§7우클릭: 제작대 열기 / 웅크리고 우클릭: 화로 열기");
        return item;
    }

    public static ItemStack createOdmGear() {
        ItemStack gear = new ItemStack(Items.TRIPWIRE_HOOK);
        gear.setCustomName(Text.literal("§b§l입체기동장치"));
        NbtCompound nbt = gear.getOrCreateNbt();
        nbt.putBoolean(NBT_SOULBOUND, true);
        nbt.putBoolean(NBT_ODM_GEAR, true);
        appendLore(gear, "§7왼손에 장착 후 활을 쏘면 화살 적중 지점으로 도약합니다.");
        return gear;
    }

    private static void appendLore(ItemStack stack, String line) {
        NbtCompound display = stack.getOrCreateSubNbt("display");
        NbtList lore = display.getList("Lore", 8);
        lore.add(NbtString.of(Text.Serializer.toJson(Text.literal(line))));
        display.put("Lore", lore);
    }
}
