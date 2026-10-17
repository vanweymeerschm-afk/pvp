package dev.pvpstyles;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PvpCommand implements CommandExecutor, TabCompleter {
    private static final String USAGE =
            "<red>Usage: /pvp [list|kit <style>|editkit <style>|queue <style>|bot <style> [easy|normal|hard]|leave|stats [player]|top|item|menu]";

    private final MatchManager mm;
    private final StyleMenu menu;
    private final KitPreview kits;
    private final KitEditor editor;
    private final LobbyItem lobby;
    private final RankStore ranks;

    public PvpCommand(MatchManager mm, StyleMenu menu, KitPreview kits, KitEditor editor,
                      LobbyItem lobby, RankStore ranks) {
        this.mm = mm;
        this.menu = menu;
        this.kits = kits;
        this.editor = editor;
        this.lobby = lobby;
        this.ranks = ranks;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("duel")) return duel(p, args);

        if (args.length == 0) { menu.open(p); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "menu" -> menu.open(p);
            case "item" -> {
                lobby.give(p);
                Msg.send(p, "You got the PvP menu item.");
            }
            case "list", "styles" -> {
                for (PvpStyle s : PvpStyle.values()) {
                    Msg.send(p, "<red>" + s.display() + " <dark_gray>(" + s.name().toLowerCase(Locale.ROOT)
                            + ") <gray>- " + s.description());
                }
            }
            case "queue", "join" -> {
                PvpStyle s = style(args);
                if (s == null) Msg.send(p, "<red>Usage: /pvp queue <style>  (see /pvp list)");
                else mm.queue(p, s);
            }
            case "bot", "bots" -> {
                PvpStyle s = style(args);
                BotDifficulty d = args.length > 2 ? BotDifficulty.parse(args[2]) : null;
                if (s == null) Msg.send(p, "<red>Usage: /pvp bot <style> [easy|normal|hard]");
                else if (args.length > 2 && d == null) Msg.send(p, "<red>Difficulty must be easy, normal or hard.");
                else mm.startBot(p, s, d);
            }
            case "kit", "preview" -> {
                PvpStyle s = style(args);
                if (s == null) Msg.send(p, "<red>Usage: /pvp kit <style>  (see /pvp list)");
                else kits.open(p, s);
            }
            case "editkit", "edit" -> {
                PvpStyle s = style(args);
                if (s == null) Msg.send(p, "<red>Usage: /pvp editkit <style>  (see /pvp list)");
                else if (mm.get(p) != null) Msg.send(p, "<red>You can't edit kits during a match.");
                else editor.open(p, s);
            }
            case "stats", "rank" -> stats(p, args);
            case "top", "leaderboard" -> top(p);
            case "leave", "quit", "forfeit" -> mm.leave(p);
            default -> Msg.send(p, USAGE);
        }
        return true;
    }

    private static PvpStyle style(String[] args) {
        return args.length > 1 ? PvpStyle.parse(args[1]) : null;
    }

    private void stats(Player p, String[] args) {
        Player target = p;
        if (args.length > 1) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { Msg.send(p, "<red>Player not found (they must be online)."); return; }
        }
        UUID id = target.getUniqueId();
        RankStore.Stats s = ranks.get(id);
        int games = s.wins + s.losses;
        String ratio = games == 0 ? "-" : String.format(Locale.ROOT, "%.2f", s.wins / (double) Math.max(1, s.losses));
        Msg.send(p, "<white>" + target.getName() + " " + ranks.tag(id));
        Msg.send(p, "Rating <white>" + s.rating + "<gray> | Wins <green>" + s.wins + "<gray> | Losses <red>"
                + s.losses + "<gray> | W/L <white>" + ratio + "<gray> | Win streak <white>" + s.streak);
    }

    private void top(Player p) {
        List<Map.Entry<UUID, RankStore.Stats>> list = ranks.top(10);
        if (list.isEmpty()) { Msg.send(p, "No ranked matches have been played yet."); return; }
        Msg.send(p, "<gold>Top players");
        int i = 1;
        for (Map.Entry<UUID, RankStore.Stats> e : list) {
            RankStore.Stats s = e.getValue();
            Msg.send(p, "<yellow>#" + i++ + " <white>" + s.name + " " + ranks.tag(e.getKey())
                    + " <gray>- " + s.rating + " (" + s.wins + "W/" + s.losses + "L)");
        }
    }

    private boolean duel(Player p, String[] args) {
        if (args.length == 0) {
            Msg.send(p, "<red>Usage: /duel <player> <style> | accept | deny");
            return true;
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        if (first.equals("accept")) { mm.accept(p); return true; }
        if (first.equals("deny")) { mm.deny(p); return true; }
        if (args.length < 2) {
            Msg.send(p, "<red>Usage: /duel <player> <style>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        PvpStyle style = PvpStyle.parse(args[1]);
        if (target == null) Msg.send(p, "<red>Player not found.");
        else if (style == null) Msg.send(p, "<red>Unknown style. See /pvp list");
        else mm.challenge(p, target, style);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        boolean duel = cmd.getName().equalsIgnoreCase("duel");
        if (args.length == 1) {
            if (duel) {
                out.add("accept");
                out.add("deny");
                for (Player pl : Bukkit.getOnlinePlayers()) out.add(pl.getName());
            } else {
                out.addAll(List.of("list", "kit", "editkit", "queue", "bot", "leave", "stats", "top", "item", "menu"));
            }
        } else if (args.length == 2) {
            String a = args[0].toLowerCase(Locale.ROOT);
            if (duel || List.of("queue", "join", "kit", "preview", "editkit", "edit", "bot", "bots").contains(a)) {
                for (PvpStyle s : PvpStyle.values()) out.add(s.name().toLowerCase(Locale.ROOT));
            } else if (a.equals("stats") || a.equals("rank")) {
                for (Player pl : Bukkit.getOnlinePlayers()) out.add(pl.getName());
            }
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("bot") || args[0].equalsIgnoreCase("bots"))) {
            for (BotDifficulty d : BotDifficulty.values()) out.add(d.name().toLowerCase(Locale.ROOT));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
