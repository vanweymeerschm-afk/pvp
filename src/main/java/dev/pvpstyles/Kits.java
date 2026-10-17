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

    /** A kit as plain data, so it can be given to a player or just previewed. */
    static final class Loadout {
        final ItemStack[] slots = new ItemStack[36]; // 0-8 hotbar, 9-35 storage
        ItemStack helmet, chest, legs, boots, offhand;

        void add(ItemStack it) {
            for (int i = 0; i < slots.length; i++) {
                if (slots[i] == null) { slots[i] = it; return; }
            }
        }

        boolean full() {
            for (ItemStack s : slots) if (s == null) return false;
            return true;
        }

        boolean isEmpty() {
            for (ItemStack s : slots) if (s != null) return false;
            return helmet == null && chest == null && legs == null && boots == null && offhand == null;
        }
    }

    static void give(Player p, PvpStyle style) { give(p, style, null); }

    static void give(Player p, PvpStyle style, ItemStack[] layout) {
        Loadout l = build(style, layout);
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < l.slots.length; i++) inv.setItem(i, l.slots[i]);
        inv.setHelmet(l.helmet);
        inv.setChestplate(l.chest);
        inv.setLeggings(l.legs);
        inv.setBoots(l.boots);
        inv.setItemInOffHand(l.offhand == null ? new ItemStack(Material.AIR) : l.offhand);
        if (style.speedEffect()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, false));
        }
    }

    /** Default kit, with the player's saved layout applied if it still matches the kit's items. */
    static Loadout build(PvpStyle style, ItemStack[] layout) {
        Loadout l = build(style);
        if (layout != null && layout.length == 36 && sameItems(l.slots, layout)) {
            for (int i = 0; i < l.slots.length; i++) l.slots[i] = layout[i] == null ? null : layout[i].clone();
        }
        return l;
    }

    static boolean sameItems(ItemStack[] a, ItemStack[] b) {
        return tally(a).equals(tally(b));
    }

    private static java.util.Map<Material, Integer> tally(ItemStack[] items) {
        java.util.Map<Material, Integer> m = new java.util.HashMap<>();
        for (ItemStack it : items) {
            if (it != null && it.getType() != Material.AIR) m.merge(it.getType(), it.getAmount(), Integer::sum);
        }
        return m;
    }

    static Loadout build(PvpStyle style) {
        Loadout l = new Loadout();
        switch (style) {
            case CLASSIC -> {
                armor(l, "DIAMOND", Enchantment.PROTECTION, 2);
                l.add(gear(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 2));
                l.add(new ItemStack(Material.FISHING_ROD));
                l.add(gear(Material.BOW, Enchantment.POWER, 1));
                l.add(new ItemStack(Material.GOLDEN_APPLE, 4));
                l.slots[8] = new ItemStack(Material.ARROW, 16);
            }
            case SWORD -> {
                armor(l, "DIAMOND", Enchantment.PROTECTION, 3);
                l.add(gear(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 3));
                l.add(gear(Material.BOW, Enchantment.POWER, 2));
                l.add(new ItemStack(Material.GOLDEN_APPLE, 8));
                l.add(new ItemStack(Material.ENDER_PEARL, 4));
                l.slots[8] = new ItemStack(Material.ARROW, 16);
            }
            case AXE_SHIELD -> {
                armor(l, "DIAMOND", Enchantment.PROTECTION, 3);
                l.add(gear(Material.DIAMOND_AXE, Enchantment.SHARPNESS, 3));
                l.add(gear(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 3));
                l.add(new ItemStack(Material.GOLDEN_APPLE, 8));
                l.add(new ItemStack(Material.ENDER_PEARL, 4));
                l.offhand = gear(Material.SHIELD);
            }
            case NODEBUFF -> {
                armor(l, "DIAMOND", Enchantment.PROTECTION, 4);
                l.add(gear(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 2));
                l.add(new ItemStack(Material.ENDER_PEARL, 16));
                l.add(potion(Material.POTION, PotionType.STRONG_SWIFTNESS));
                l.add(potion(Material.POTION, PotionType.STRONG_SWIFTNESS));
                while (!l.full()) l.add(potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING));
            }
            case CRYSTAL -> {
                armor(l, "NETHERITE", Enchantment.PROTECTION, 4);
                l.add(gear(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 5));
                l.add(new ItemStack(Material.OBSIDIAN, 64));
                l.add(new ItemStack(Material.OBSIDIAN, 64));
                l.add(new ItemStack(Material.END_CRYSTAL, 64));
                l.add(new ItemStack(Material.END_CRYSTAL, 64));
                l.add(new ItemStack(Material.GOLDEN_APPLE, 16));
                l.add(new ItemStack(Material.ENDER_PEARL, 16));
                for (int i = 0; i < 5; i++) l.add(new ItemStack(Material.TOTEM_OF_UNDYING));
                l.offhand = new ItemStack(Material.TOTEM_OF_UNDYING);
            }
            case MACE -> {
                armor(l, "NETHERITE", Enchantment.PROTECTION, 4);
                l.boots = gear(Material.NETHERITE_BOOTS, Enchantment.PROTECTION, 4, Enchantment.FEATHER_FALLING, 4);
                l.add(gear(Material.MACE, Enchantment.DENSITY, 5, Enchantment.WIND_BURST, 3));
                l.add(new ItemStack(Material.WIND_CHARGE, 64));
                l.add(new ItemStack(Material.GOLDEN_APPLE, 16));
            }
            case ARCHER -> {
                armor(l, "LEATHER", Enchantment.PROTECTION, 3);
                l.add(gear(Material.BOW, Enchantment.POWER, 4, Enchantment.INFINITY, 1, Enchantment.PUNCH, 1));
                l.add(gear(Material.STONE_SWORD));
                l.add(new ItemStack(Material.GOLDEN_APPLE, 4));
                l.slots[8] = new ItemStack(Material.ARROW, 1);
            }
            case UHC -> {
                armor(l, "IRON", Enchantment.PROTECTION, 2);
                l.add(gear(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 3));
                l.add(gear(Material.BOW, Enchantment.POWER, 2));
                l.add(new ItemStack(Material.FISHING_ROD));
                l.add(new ItemStack(Material.LAVA_BUCKET));
                l.add(new ItemStack(Material.WATER_BUCKET));
                l.add(new ItemStack(Material.WATER_BUCKET));
                l.add(new ItemStack(Material.GOLDEN_APPLE, 6));
                l.add(new ItemStack(Material.COBBLESTONE, 64));
                l.add(new ItemStack(Material.OAK_PLANKS, 64));
                l.add(new ItemStack(Material.ARROW, 32));
            }
            case COMBO -> {
                armor(l, "DIAMOND", Enchantment.PROTECTION, 4);
                l.add(gear(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 1));
                l.add(new ItemStack(Material.GOLDEN_APPLE, 16));
            }
            case SUMO, BOXING -> { /* bare hands */ }
        }
        return l;
    }

    private static void armor(Loadout l, String tier, Object... enchants) {
        l.helmet = gear(Material.valueOf(tier + "_HELMET"), enchants);
        l.chest = gear(Material.valueOf(tier + "_CHESTPLATE"), enchants);
        l.legs = gear(Material.valueOf(tier + "_LEGGINGS"), enchants);
        l.boots = gear(Material.valueOf(tier + "_BOOTS"), enchants);
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
