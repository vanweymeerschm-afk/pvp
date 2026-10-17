package dev.pvpstyles;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** A hotbar item that opens the PvP style menu on right-click. */
public final class LobbyItem implements Listener {
    private final PvPStyles plugin;
    private final MatchManager mm;
    private final StyleMenu menu;
    private final NamespacedKey key;
    private final boolean giveOnJoin;
    private final int slot;
    private final Material material;

    public LobbyItem(PvPStyles plugin, MatchManager mm, StyleMenu menu) {
        this.plugin = plugin;
        this.mm = mm;
        this.menu = menu;
        this.key = new NamespacedKey(plugin, "lobby_item");
        this.giveOnJoin = plugin.getConfig().getBoolean("lobby-item.give-on-join", true);
        this.slot = Math.max(0, Math.min(8, plugin.getConfig().getInt("lobby-item.slot", 4)));
        Material m = Material.matchMaterial(plugin.getConfig().getString("lobby-item.material", "NETHER_STAR"));
        this.material = m != null ? m : Material.NETHER_STAR;
    }

    private ItemStack create() {
        ItemStack it = new ItemStack(material);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text("PvP Menu", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Right-click to choose a PvP style", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean is(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public void give(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (is(inv.getItem(i))) inv.setItem(i, null);
        }
        ItemStack it = create();
        if (inv.getItem(slot) == null) inv.setItem(slot, it);
        else inv.addItem(it);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (giveOnJoin && mm.get(e.getPlayer()) == null) give(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (!giveOnJoin) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && mm.get(p) == null) give(p);
        });
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!is(p.getInventory().getItemInMainHand())) return;
        e.setCancelled(true);
        if (mm.get(p) == null) menu.open(p);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (is(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }
}
