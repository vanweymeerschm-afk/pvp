package dev.pvpstyles;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** ELO rating + win/loss stats, saved to ranks.yml. Only ranked (queue) matches count. */
public final class RankStore {

    public static final class Stats {
        public volatile int rating = 1000;
        public volatile int wins, losses, streak;
        public volatile String name = "Unknown";
    }

    public record Tier(String name, String color, int min) {}

    private static final List<Tier> TIERS = List.of(
            new Tier("Bronze", "<#cd7f32>", 0),
            new Tier("Silver", "<gray>", 1050),
            new Tier("Gold", "<gold>", 1200),
            new Tier("Platinum", "<aqua>", 1350),
            new Tier("Diamond", "<#4be3ff>", 1500),
            new Tier("Master", "<light_purple>", 1700),
            new Tier("Grandmaster", "<red>", 1900));

    private static final int K = 32;

    private final PvPStyles plugin;
    private final File file;
    private final Map<UUID, Stats> stats = new ConcurrentHashMap<>();

    public RankStore(PvPStyles plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ranks.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection s = cfg.getConfigurationSection(key);
                if (s == null) continue;
                Stats st = new Stats();
                st.rating = s.getInt("rating", 1000);
                st.wins = s.getInt("wins", 0);
                st.losses = s.getInt("losses", 0);
                st.streak = s.getInt("streak", 0);
                st.name = s.getString("name", "Unknown");
                stats.put(id, st);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public Stats get(UUID id) { return stats.computeIfAbsent(id, k -> new Stats()); }

    public Tier tier(int rating) {
        Tier best = TIERS.get(0);
        for (Tier t : TIERS) if (rating >= t.min()) best = t;
        return best;
    }

    /** MiniMessage tag such as "<gold>[Gold]". */
    public String tag(UUID id) {
        Stats s = stats.get(id);
        if (s == null || s.wins + s.losses == 0) return "<dark_gray>[Unranked]";
        Tier t = tier(s.rating);
        return t.color() + "[" + t.name() + "]";
    }

    /** Applies an ELO result. Returns {winnerGain, loserLoss}. */
    public int[] record(UUID winner, String winnerName, UUID loser, String loserName) {
        Stats w = get(winner), l = get(loser);
        double expected = 1.0 / (1.0 + Math.pow(10, (l.rating - w.rating) / 400.0));
        int gain = Math.max(1, (int) Math.round(K * (1 - expected)));
        int newLoser = Math.max(0, l.rating - gain);
        int loss = l.rating - newLoser;
        w.rating += gain;
        l.rating = newLoser;
        w.wins++;
        l.losses++;
        w.streak = w.streak > 0 ? w.streak + 1 : 1;
        l.streak = 0;
        w.name = winnerName;
        l.name = loserName;
        save();
        return new int[]{gain, loss};
    }

    public List<Map.Entry<UUID, Stats>> top(int n) {
        return stats.entrySet().stream()
                .filter(e -> e.getValue().wins + e.getValue().losses > 0)
                .sorted((a, b) -> Integer.compare(b.getValue().rating, a.getValue().rating))
                .limit(n)
                .toList();
    }

    private synchronized void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Stats> e : stats.entrySet()) {
            String base = e.getKey().toString();
            Stats s = e.getValue();
            cfg.set(base + ".rating", s.rating);
            cfg.set(base + ".wins", s.wins);
            cfg.set(base + ".losses", s.losses);
            cfg.set(base + ".streak", s.streak);
            cfg.set(base + ".name", s.name);
        }
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save ranks.yml: " + ex.getMessage());
        }
    }
}
