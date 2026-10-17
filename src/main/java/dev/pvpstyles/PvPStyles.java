package dev.pvpstyles;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class PvPStyles extends JavaPlugin {

    private MatchManager matches;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        matches = new MatchManager(this);
        StyleMenu menu = new StyleMenu(matches);
        getServer().getPluginManager().registerEvents(new CombatListener(matches), this);
        getServer().getPluginManager().registerEvents(menu, this);

        PvpCommand cmd = new PvpCommand(matches, menu);
        for (String name : List.of("pvp", "duel")) {
            PluginCommand c = getCommand(name);
            if (c != null) {
                c.setExecutor(cmd);
                c.setTabCompleter(cmd);
            }
        }
    }

    @Override
    public void onDisable() {
        if (matches != null) matches.shutdown();
    }
}
