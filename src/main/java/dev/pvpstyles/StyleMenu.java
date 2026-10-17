package dev.pvpstyles;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class StyleMenu implements Listener {
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 20, 21, 22, 23};

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private final MatchManager mm;

    public StyleMenu(MatchManager mm) { this.mm = mm; }

    public void open(Player p) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 27, Component.text("PvP Styles"));
        h.inv = inv;
        PvpStyle[] styles = PvpStyle.values();
        for (int i = 0; i < styles.length && i < SLOTS.length; i++) {
            PvpStyle s = styles[i];
            ItemStack it = new ItemStack(s.icon());
            ItemMeta meta = it.getItemMeta();
            meta.displayName(Component.text(s.display(), NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text(s.description(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("Click to queue", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)));
            meta.addItemFlags(ItemFlag.values());
            it.setItemMeta(meta);
            inv.setItem(SLOTS[i], it);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        PvpStyle[] styles = PvpStyle.values();
        for (int i = 0; i < styles.length && i < SLOTS.length; i++) {
            if (SLOTS[i] == e.getSlot()) {
                p.closeInventory();
                mm.queue(p, styles[i]);
                return;
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
