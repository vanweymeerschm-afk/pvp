package dev.pvpstyles;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;

/** Everything needed to put a player back exactly how they were before a match. */
record PlayerSnapshot(ItemStack[] storage, ItemStack[] armor, ItemStack offhand, Location location,
                      double health, int food, float saturation, int level, float exp,
                      GameMode gameMode, Collection<PotionEffect> effects, double attackSpeed,
                      int maxNoDamageTicks, boolean allowFlight) {

    static PlayerSnapshot capture(Player p) {
        PlayerInventory inv = p.getInventory();
        AttributeInstance speed = p.getAttribute(Attribute.ATTACK_SPEED);
        return new PlayerSnapshot(
                copy(inv.getStorageContents()), copy(inv.getArmorContents()),
                inv.getItemInOffHand().clone(), p.getLocation().clone(),
                p.getHealth(), p.getFoodLevel(), p.getSaturation(), p.getLevel(), p.getExp(),
                p.getGameMode(), new ArrayList<>(p.getActivePotionEffects()),
                speed != null ? speed.getBaseValue() : 4.0,
                p.getMaximumNoDamageTicks(), p.getAllowFlight());
    }

    void restore(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setStorageContents(copy(storage));
        inv.setArmorContents(copy(armor));
        inv.setItemInOffHand(offhand.clone());

        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        p.addPotionEffects(effects);

        AttributeInstance speed = p.getAttribute(Attribute.ATTACK_SPEED);
        if (speed != null) speed.setBaseValue(attackSpeed);
        p.setMaximumNoDamageTicks(maxNoDamageTicks);
        p.setNoDamageTicks(0);

        p.setGameMode(gameMode);
        p.setAllowFlight(allowFlight);
        p.setFoodLevel(food);
        p.setSaturation(saturation);
        p.setLevel(level);
        p.setExp(exp);
        AttributeInstance maxHp = p.getAttribute(Attribute.MAX_HEALTH);
        double cap = maxHp != null ? maxHp.getValue() : 20.0;
        p.setHealth(Math.max(0.5, Math.min(health, cap)));
        p.setFireTicks(0);
        p.setVelocity(new Vector());
        p.teleport(location);
        p.setFallDistance(0f);
    }

    private static ItemStack[] copy(ItemStack[] in) {
        ItemStack[] out = new ItemStack[in.length];
        for (int i = 0; i < in.length; i++) out[i] = in[i] == null ? null : in[i].clone();
        return out;
    }
}
