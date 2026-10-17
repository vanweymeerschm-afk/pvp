package dev.pvpstyles;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class PvPStyles extends JavaPlugin {

    private MatchManager matches;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        KitStore kitStore = new KitStore(this);
        RankStore ranks = new RankStore(this);
        matches = new MatchManager(this, kitStore, ranks);

        KitPreview preview = new KitPreview();
        KitEditor editor = new KitEditor(kitStore);
        StyleMenu menu = new StyleMenu(matches, preview, editor, ranks);
        LobbyItem lobby = new LobbyItem(this, matches, menu);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new CombatListener(matches), this);
        pm.registerEvents(menu, this);
        pm.registerEvents(preview, this);
        pm.registerEvents(editor, this);
        pm.registerEvents(lobby, this);
        pm.registerEvents(new RankChat(this, ranks), this);

        PvpCommand cmd = new PvpCommand(matches, menu, preview, editor, lobby, ranks);
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
