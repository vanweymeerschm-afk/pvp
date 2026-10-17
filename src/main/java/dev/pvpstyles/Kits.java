package dev.pvpstyles;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

final class Kits {
    private Kits() {}

    static void give(Player p, PvpStyle style) {
        PlayerInventory inv = p.getInventory();
        switch (style) {
            case CLASSIC -> {
                armor(inv, "DIAMOND", Enchantment.PROTECTION, 2);
                inv.addItem(gear(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 2));
                inv.addItem(new ItemStack(Material.FISHING_ROD));
                inv.addItem(gear(Material.BOW, Enchantment.POWER, 1));
                inv.addItem(new ItemStack(Material.GOLDEN_APPLE, 4));
                inv.setItem(8, new ItemStack(Material.ARROW, 16));
            }
            case SWORD -> {
                armor(inv, "DIAMOND", Enchantment.PROTECTION, 3);
                inv.addItem(gear(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 3));
                inv.addItem(gear(Material.BOW, Enchantment.POWER, 2));
                inv.addItem(new ItemStack(Material.GOLDEN_APPLE, 8));
                inv.addItem(new ItemStack(Material.ENDER_PEARL, 4));
                inv.setItem(8, new ItemStack(Material.ARROW, 16));
            }
            case AXE_SHIELD -> {
                armor(inv, "DIAMOND", Enchantment.PROTECTION, 3);
                inv.addItem(gear(Material.DIAMOND_AXE, Enchantment.SHARPNESS, 3));
                inv.addItem(gear(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 3));
                inv.addItem(new ItemStack(Material.GOLDEN_APPLE, 8));
                inv.addItem(new ItemStack(Material.ENDER_PEARL, 4));
                inv.setItemInOffHand(gear(Material.SHIELD));
            }
            case NODEBUFF -> {
                armor(inv, "DIAMOND", Enchantment.PROTECTION, 4);
                inv.addItem(gear(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 2));
                inv.addItem(new ItemStack(Material.ENDER_PEARL, 16));
                inv.addItem(potion(Material.POTION, PotionType.STRONG_SWIFTNESS));
                inv.addItem(potion(Material.POTION, PotionType.STRONG_SWIFTNESS));
                while (inv.firstEmpty() != -1) {
                    inv.addItem(potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING));
                }
            }
            case CRYSTAL -> {
                armor(inv, "NETHERITE", Enchantment.PROTECTION, 4);
                inv.addItem(gear(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 5));
                inv.addItem(new ItemStack(Material.OBSIDIAN, 64));
                inv.addItem(new ItemStack(Material.OBSIDIAN, 64));
                inv.addItem(new ItemStack(Material.END_CRYSTAL, 64));
                inv.addItem(new ItemStack(Material.END_CRYSTAL, 64));
                inv.addItem(new ItemStack(Material.GOLDEN_APPLE, 16));
                inv.addItem(new ItemStack(Material.ENDER_PEARL, 16));
                for (int i = 0; i < 5; i++) inv.addItem(new ItemStack(Material.TOTEM_OF_UNDYING));
                inv.setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
            }
            case MACE -> {
                armor(inv, "NETHERITE", Enchantment.PROTECTION, 4);
                inv.setBoots(gear(Material.NETHERITE_BOOTS, Enchantment.PROTECTION, 4, Enchantment.FEATHER_FALLING, 4));
                inv.addItem(gear(Material.MACE, Enchantment.DENSITY, 5, Enchantment.WIND_BURST, 3));
                inv.addItem(new ItemStack(Material.WIND_CHARGE, 64));
                inv.addItem(new ItemStack(Material.GOLDEN_APPLE, 16));
            }
            case ARCHER -> {
                armor(inv, "LEATHER", Enchantment.PROTECTION, 3);
                inv.addItem(gear(Material.BOW, Enchantment.POWER, 4, Enchantment.INFINITY, 1, Enchantment.PUNCH, 1));
                inv.addItem(gear(Material.STONE_SWORD));
                inv.addItem(new ItemStack(Material.GOLDEN_APPLE, 4));
                inv.setItem(8, new ItemStack(Material.ARROW, 1));
            }
            case UHC -> {
                armor(inv, "IRON", Enchantment.PROTECTION, 2);
                inv.addItem(gear(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 3));
                inv.addItem(gear(Material.BOW, Enchantment.POWER, 2));
                inv.addItem(new ItemStack(Material.FISHING_ROD));
                inv.addItem(new ItemStack(Material.LAVA_BUCKET));
                inv.addItem(new ItemStack(Material.WATER_BUCKET));
                inv.addItem(new ItemStack(Material.WATER_BUCKET));
                inv.addItem(new ItemStack(Material.GOLDEN_APPLE, 6));
                inv.addItem(new ItemStack(Material.COBBLESTONE, 64));
                inv.addItem(new ItemStack(Material.OAK_PLANKS, 64));
                inv.addItem(new ItemStack(Material.ARROW, 32));
            }
            case COMBO -> {
                armor(inv, "DIAMOND", Enchantment.PROTECTION, 4);
                inv.addItem(gear(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 1));
                inv.addItem(new ItemStack(Material.GOLDEN_APPLE, 16));
            }
            case SUMO, BOXING -> { /* bare hands */ }
        }
        if (style.speedEffect()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, false));
        }
    }

    private static void armor(PlayerInventory inv, String tier, Object... enchants) {
        inv.setHelmet(gear(Material.valueOf(tier + "_HELMET"), enchants));
        inv.setChestplate(gear(Material.valueOf(tier + "_CHESTPLATE"), enchants));
        inv.setLeggings(gear(Material.valueOf(tier + "_LEGGINGS"), enchants));
        inv.setBoots(gear(Material.valueOf(tier + "_BOOTS"), enchants));
    }

    /** Unbreakable item with (Enchantment, Integer) pairs. */
    private static ItemStack gear(Material m, Object... enchants) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setUnbreakable(true);
        it.setItemMeta(meta);
        for (int i = 0; i + 1 < enchants.length; i += 2) {
            it.addUnsafeEnchantment((Enchantment) enchants[i], (Integer) enchants[i + 1]);
        }
        return it;
    }

    private static ItemStack potion(Material type, PotionType pt) {
        ItemStack it = new ItemStack(type);
        PotionMeta pm = (PotionMeta) it.getItemMeta();
        pm.setBasePotionType(pt);
        it.setItemMeta(pm);
        return it;
    }
}
