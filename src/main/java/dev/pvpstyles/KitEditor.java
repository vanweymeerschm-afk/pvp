package dev.pvpstyles;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Lets a player rearrange where kit items sit. Items can only be moved around,
 * never added or removed. The layout saves when the menu closes.
 */
public final class KitEditor implements Listener {

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        final PvpStyle style;
        boolean skipSave;
        Holder(PvpStyle style) { this.style = style; }
        @Override public Inventory getInventory() { return inv; }
    }

    private static final int RESET_SLOT = 53;
    private final KitStore store;

    public KitEditor(KitStore store) { this.store = store; }

    private static int idx(int slot) { return slot >= 9 ? slot - 9 : 27 + slot; }

    public void open(Player p, PvpStyle style) {
        Kits.Loadout l = Kits.build(style, store.get(p.getUniqueId(), style));
        boolean any = false;
        for (ItemStack s : l.slots) if (s != null) { any = true; break; }
        if (!any) {
            Msg.send(p, "<red>" + style.display() + "<gray> has no kit to edit.");
            return;
        }
        Holder h = new Holder(style);
        Inventory inv = Bukkit.createInventory(h, 54, Component.text("Edit kit: " + style.display()));
        h.inv = inv;

        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 36; i < 54; i++) inv.setItem(i, filler);

        for (int s = 0; s < 36; s++) {
            if (l.slots[s] != null) inv.setItem(idx(s), l.slots[s].clone());
        }
        if (l.helmet != null) inv.setItem(45, l.helmet.clone());
        if (l.chest != null) inv.setItem(46, l.chest.clone());
        if (l.legs != null) inv.setItem(47, l.legs.clone());
        if (l.boots != null) inv.setItem(48, l.boots.clone());
        if (l.offhand != null) inv.setItem(50, l.offhand.clone());

        inv.setItem(49, named(Material.BOOK, "How to edit",
                List.of("Left-click an item, then left-click", "another slot to move or swap it.", "Close this menu to save.",
                        "Bottom row = hotbar.")));
        inv.setItem(RESET_SLOT, named(Material.BARRIER, "Reset to default", List.of("Click to restore the original layout.")));
        p.openInventory(inv);
    }

    private static ItemStack named(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) {
            meta.lore(lore.stream()
                    .map(s -> (Component) Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                    .toList());
        }
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true); // deny everything by default
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;

        int slot = e.getSlot();
        if (slot == RESET_SLOT) {
            store.reset(p.getUniqueId(), h.style);
            h.skipSave = true;
            Msg.send(p, "Kit layout reset to default.");
            open(p, h.style);
            return;
        }
        // Only plain left-clicks inside the 36-slot grid may move items.
        if (slot >= 0 && slot < 36 && e.getClick() == ClickType.LEFT) {
            e.setCancelled(false);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        if (h.skipSave) return;

        ItemStack[] layout = new ItemStack[36];
        for (int s = 0; s < 36; s++) {
            ItemStack it = h.inv.getItem(idx(s));
            layout[s] = it == null ? null : it.clone();
        }
        // An item left on the cursor goes back into the layout, never into the player's real inventory.
        ItemStack cursor = p.getItemOnCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            for (int s = 0; s < 36; s++) {
                if (layout[s] == null) { layout[s] = cursor.clone(); break; }
            }
            p.setItemOnCursor(null);
        }

        Kits.Loadout def = Kits.build(h.style);
        if (!Kits.sameItems(def.slots, layout)) {
            Msg.send(p, "<red>Kit layout looked invalid, so it was not saved.");
            return;
        }
        store.set(p.getUniqueId(), h.style, layout);
        Msg.send(p, "<green>Saved your <white>" + h.style.display() + "<green> kit layout.");
    }
}
