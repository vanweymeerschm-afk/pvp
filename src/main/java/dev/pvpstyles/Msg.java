package dev.pvpstyles;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

final class Msg {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX = "<dark_gray>[<red>PvP<dark_gray>] <gray>";

    private Msg() {}

    static void send(CommandSender to, String text) {
        to.sendMessage(MM.deserialize(PREFIX + text));
    }

    static Component c(String text) {
        return MM.deserialize(text);
    }
}
