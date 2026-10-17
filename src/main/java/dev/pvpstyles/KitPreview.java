package dev.pvpstyles;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Read-only view of a style's kit. Nothing is given to the player. */
public final class KitPreview implements Listener {

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player p, PvpStyle style) {
        Kits.Loadout l = Kits.build(style);
        if (l.isEmpty()) {
            Msg.send(p, "<red>" + style.display() + "<gray> has no kit - bare hands only.");
            return;
        }
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 54, Component.text("Kit: " + style.display()));
        h.inv = inv;

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.text(" "));
        filler.setItemMeta(fm);
        for (int i = 36; i < 54; i++) inv.setItem(i, filler);

        // Same layout as a player inventory: storage on top, hotbar underneath.
        for (int s = 0; s < 36; s++) {
            if (l.slots[s] == null) continue;
            int idx = s >= 9 ? s - 9 : 27 + s;
            inv.setItem(idx, l.slots[s].clone());
        }
        put(inv, 45, l.helmet);
        put(inv, 46, l.chest);
        put(inv, 47, l.legs);
        put(inv, 48, l.boots);
        put(inv, 50, l.offhand);
        p.openInventory(inv);
    }

    private static void put(Inventory inv, int slot, ItemStack it) {
        if (it != null) inv.setItem(slot, it.clone());
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
