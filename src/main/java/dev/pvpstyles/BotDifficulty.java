package dev.pvpstyles;

import java.util.Locale;

public enum BotDifficulty {
    EASY("Easy", 0.20, 16.0, 0.5),
    NORMAL("Normal", 0.25, 20.0, 0.8),
    HARD("Hard", 0.30, 24.0, 1.0);

    private final String label;
    private final double speed, health, damageMult;

    BotDifficulty(String label, double speed, double health, double damageMult) {
        this.label = label;
        this.speed = speed;
        this.health = health;
        this.damageMult = damageMult;
    }

    public String label() { return label; }
    public double speed() { return speed; }
    public double health() { return health; }
    public double damageMult() { return damageMult; }

    public static BotDifficulty parse(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
