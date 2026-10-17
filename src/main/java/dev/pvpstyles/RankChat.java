package dev.pvpstyles;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Puts the player's rank in front of their chat messages. */
public final class RankChat implements Listener {
    private final RankStore ranks;
    private final boolean enabled;

    public RankChat(PvPStyles plugin, RankStore ranks) {
        this.ranks = ranks;
        this.enabled = plugin.getConfig().getBoolean("ranks.chat-prefix", true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (!enabled) return;
        Component prefix = Msg.c(ranks.tag(e.getPlayer().getUniqueId()) + " ");
        e.renderer(ChatRenderer.viewerUnaware((source, name, message) ->
                prefix.append(Component.text("<")).append(name).append(Component.text("> ")).append(message)));
    }
}
