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

public final class PvpCommand implements CommandExecutor, TabCompleter {
    private final MatchManager mm;
    private final StyleMenu menu;

    public PvpCommand(MatchManager mm, StyleMenu menu) {
        this.mm = mm;
        this.menu = menu;
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
            case "list", "styles" -> {
                for (PvpStyle s : PvpStyle.values()) {
                    Msg.send(p, "<red>" + s.display() + " <dark_gray>(" + s.name().toLowerCase(Locale.ROOT)
                            + ") <gray>- " + s.description());
                }
            }
            case "queue", "join" -> {
                PvpStyle s = args.length > 1 ? PvpStyle.parse(args[1]) : null;
                if (s == null) Msg.send(p, "<red>Usage: /pvp queue <style>  (see /pvp list)");
                else mm.queue(p, s);
            }
            case "leave", "quit", "forfeit" -> mm.leave(p);
            default -> Msg.send(p, "<red>Usage: /pvp [list|queue <style>|leave|menu]");
        }
        return true;
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
                out.addAll(List.of("list", "queue", "leave", "menu"));
            }
        } else if (args.length == 2 && (duel || args[0].equalsIgnoreCase("queue") || args[0].equalsIgnoreCase("join"))) {
            for (PvpStyle s : PvpStyle.values()) out.add(s.name().toLowerCase(Locale.ROOT));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
