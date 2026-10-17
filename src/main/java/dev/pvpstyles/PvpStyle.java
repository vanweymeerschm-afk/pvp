package dev.pvpstyles;

import org.bukkit.Material;

import java.util.Locale;

/** Every playable PvP style. Add a constant here + a case in Kits.give() to add a new one. */
public enum PvpStyle {
    CLASSIC("Classic 1.8", Material.IRON_SWORD, "Spam-click combat with no attack cooldown."),
    SWORD("Modern Sword", Material.DIAMOND_SWORD, "1.9+ sword duel. Time your swings."),
    AXE_SHIELD("Axe & Shield", Material.SHIELD, "Axes break shields. Block smart."),
    NODEBUFF("NoDebuff Pot", Material.SPLASH_POTION, "Splash health potions and pearls."),
    CRYSTAL("Crystal", Material.END_CRYSTAL, "Obsidian, end crystals and totems."),
    MACE("Mace", Material.MACE, "Wind charges and smash attacks in a tall arena."),
    ARCHER("Archer", Material.BOW, "Long-range bow fight."),
    UHC("UHC", Material.GOLDEN_APPLE, "No natural regen. Gapples, rods, lava, building."),
    COMBO("Combo", Material.COOKED_BEEF, "No hit delay. Chain hits together."),
    SUMO("Sumo", Material.SMOOTH_QUARTZ, "No damage. Knock your opponent off the platform."),
    BOXING("Boxing", Material.RED_WOOL, "No damage. First to the hit target wins.");

    private final String display;
    private final Material icon;
    private final String description;

    PvpStyle(String display, Material icon, String description) {
        this.display = display;
        this.icon = icon;
        this.description = description;
    }

    public String display() { return display; }
    public Material icon() { return icon; }
    public String description() { return description; }

    /** Hits deal knockback only (damage zeroed). */
    public boolean noDamage() { return this == SUMO || this == BOXING; }
    /** Attack speed attribute raised so every click does full damage. */
    public boolean noCooldown() { return this == CLASSIC || this == COMBO || this == BOXING; }
    /** Invulnerability frames removed. */
    public boolean noHitDelay() { return this == COMBO; }
    public boolean noRegen() { return this == UHC; }
    public boolean allowsBuilding() { return this == UHC || this == CRYSTAL; }
    public boolean speedEffect() { return this == COMBO || this == BOXING; }

    public int radius() {
        return switch (this) {
            case SUMO -> 5;
            case BOXING -> 8;
            case MACE -> 12;
            case ARCHER -> 20;
            default -> 15;
        };
    }

    public int wallHeight() {
        return switch (this) {
            case SUMO -> 0;
            case MACE -> 40;
            default -> 10;
        };
    }

    public Material floor() {
        return switch (this) {
            case SUMO -> Material.SMOOTH_QUARTZ;
            case BOXING -> Material.WHITE_CONCRETE;
            case CRYSTAL -> Material.OBSIDIAN;
            case UHC, ARCHER -> Material.GRASS_BLOCK;
            default -> Material.SMOOTH_STONE;
        };
    }

    public static PvpStyle parse(String s) {
        if (s == null) return null;
        String n = s.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (PvpStyle v : values()) if (v.name().equals(n)) return v;
        return null;
    }
}
