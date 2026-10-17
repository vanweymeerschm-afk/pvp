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
    private final KitPreview preview;
    private final KitEditor editor;
    private final RankStore ranks;

    public StyleMenu(MatchManager mm, KitPreview preview, KitEditor editor, RankStore ranks) {
        this.mm = mm;
        this.preview = preview;
        this.editor = editor;
        this.ranks = ranks;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    public void open(Player p) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 27, Component.text("PvP Styles"));
        h.inv = inv;
        PvpStyle[] styles = PvpStyle.values();
        for (int i = 0; i < styles.length && i < SLOTS.length; i++) {
            PvpStyle s = styles[i];
            ItemStack it = new ItemStack(s.icon());
            ItemMeta meta = it.getItemMeta();
            meta.displayName(line(s.display(), NamedTextColor.RED));
            meta.lore(List.of(
                    line(s.description(), NamedTextColor.GRAY),
                    Component.empty(),
                    line("Left-click: ranked queue", NamedTextColor.GREEN),
                    line("Right-click: preview kit", NamedTextColor.AQUA),
                    line("Shift-click: edit kit layout", NamedTextColor.YELLOW),
                    line("Press Q: fight a bot", NamedTextColor.LIGHT_PURPLE)));
            meta.addItemFlags(ItemFlag.values());
            it.setItemMeta(meta);
            inv.setItem(SLOTS[i], it);
        }

        RankStore.Stats st = ranks.get(p.getUniqueId());
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.displayName(line("Your rank", NamedTextColor.GOLD));
        im.lore(List.of(
                Msg.c(ranks.tag(p.getUniqueId())).decoration(TextDecoration.ITALIC, false),
                line("Rating: " + st.rating, NamedTextColor.GRAY),
                line("Wins: " + st.wins + "  Losses: " + st.losses, NamedTextColor.GRAY)));
        info.setItemMeta(im);
        inv.setItem(4, info);

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
            if (SLOTS[i] != e.getSlot()) continue;
            if (e.getClick() == ClickType.DROP || e.getClick() == ClickType.CONTROL_DROP) {
                p.closeInventory();
                mm.startBot(p, styles[i], null);
            } else if (e.getClick().isShiftClick()) {
                editor.open(p, styles[i]);
            } else if (e.isRightClick()) {
                preview.open(p, styles[i]);
            } else {
                p.closeInventory();
                mm.queue(p, styles[i]);
            }
            return;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
