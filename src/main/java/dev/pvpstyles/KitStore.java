package dev.pvpstyles;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Per-player kit layouts (where each item sits), saved to kits.yml. */
public final class KitStore {
    private final PvPStyles plugin;
    private final File file;
    private final YamlConfiguration cfg;

    public KitStore(PvPStyles plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kits.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    /** 36-slot layout, or null if the player never edited this kit. */
    public ItemStack[] get(UUID id, PvpStyle style) {
        ConfigurationSection sec = cfg.getConfigurationSection(id + "." + style.name());
        if (sec == null) return null;
        ItemStack[] out = new ItemStack[36];
        for (String k : sec.getKeys(false)) {
            try {
                int slot = Integer.parseInt(k);
                if (slot >= 0 && slot < 36) out[slot] = sec.getItemStack(k);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public void set(UUID id, PvpStyle style, ItemStack[] layout) {
        String base = id + "." + style.name();
        cfg.set(base, null);
        for (int i = 0; i < layout.length && i < 36; i++) {
            if (layout[i] != null) cfg.set(base + "." + i, layout[i]);
        }
        save();
    }

    public void reset(UUID id, PvpStyle style) {
        cfg.set(id + "." + style.name(), null);
        save();
    }

    private void save() {
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save kits.yml: " + e.getMessage());
        }
    }
}
