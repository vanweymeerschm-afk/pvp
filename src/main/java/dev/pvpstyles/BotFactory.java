package dev.pvpstyles;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Spawns the AI opponent: a kitted-out zombie (skeleton for Archer). AI stays off until the fight starts. */
final class BotFactory {
    private BotFactory() {}

    static Mob spawn(PvpStyle style, BotDifficulty diff, Location loc) {
        Kits.Loadout kit = Kits.build(style);
        World w = loc.getWorld();
        if (style == PvpStyle.ARCHER) {
            return w.spawn(loc, Skeleton.class, s -> setup(s, style, diff, kit));
        }
        return w.spawn(loc, Zombie.class, z -> {
            z.setBaby(false);
            setup(z, style, diff, kit);
        });
    }

    private static void setup(Mob mob, PvpStyle style, BotDifficulty diff, Kits.Loadout kit) {
        mob.setAI(false);
        mob.setPersistent(true);
        mob.setRemoveWhenFarAway(false);
        mob.setCanPickupItems(false);
        mob.customName(Msg.c("<red>Bot <gray>(" + diff.label() + ")"));
        mob.setCustomNameVisible(true);

        set(mob, Attribute.MAX_HEALTH, diff.health());
        set(mob, Attribute.MOVEMENT_SPEED, diff.speed());
        set(mob, Attribute.FOLLOW_RANGE, 64.0);
        set(mob, Attribute.ATTACK_DAMAGE, 1.0); // the held weapon adds its own damage on top
        set(mob, Attribute.KNOCKBACK_RESISTANCE, 0.0);
        mob.setHealth(diff.health());
        mob.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,
                PotionEffect.INFINITE_DURATION, 0, false, false));

        EntityEquipment eq = mob.getEquipment();
        if (eq == null) return;
        eq.setHelmet(kit.helmet);
        eq.setChestplate(kit.chest);
        eq.setLeggings(kit.legs);
        eq.setBoots(kit.boots);
        eq.setItemInMainHand(weapon(style, kit));
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInMainHandDropChance(0f);
    }

    private static void set(Mob mob, Attribute attr, double value) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null) inst.setBaseValue(value);
    }

    private static ItemStack weapon(PvpStyle style, Kits.Loadout kit) {
        for (ItemStack it : kit.slots) {
            if (it == null) continue;
            Material t = it.getType();
            boolean ok = style == PvpStyle.ARCHER
                    ? t == Material.BOW
                    : t.name().endsWith("_SWORD") || t.name().endsWith("_AXE") || t == Material.MACE;
            if (ok) return it.clone();
        }
        return null;
    }
}
