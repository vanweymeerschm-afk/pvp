package dev.pvpstyles;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.Location;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class CombatListener implements Listener {
    private static final Set<String> ALLOWED_COMMANDS = Set.of("pvp", "pvps", "duel", "pvpstyles:pvp", "pvpstyles:duel");

    private final MatchManager mm;

    public CombatListener(MatchManager mm) { this.mm = mm; }

    /** Who dealt the damage: the shooter for projectiles, otherwise the damager itself. */
    private static UUID attackerId(Entity d) {
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) return shooter.getUniqueId();
        return d.getUniqueId();
    }

    private static boolean hasTotem(Player p) {
        return p.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
                || p.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        Match m = mm.matchOf(victim);
        if (m == null) return;
        if (m.state != Match.State.ACTIVE) { e.setCancelled(true); return; }

        PvpStyle s = m.style;
        UUID victimId = victim.getUniqueId();
        UUID attacker = (e instanceof EntityDamageByEntityEvent be) ? attackerId(be.getDamager()) : null;

        // Bot difficulty scales the damage the bot deals.
        if (attacker != null && m.bot != null && attacker.equals(m.b) && !attacker.equals(victimId)) {
            e.setDamage(e.getDamage() * m.difficulty.damageMult());
        }

        if (s.noDamage()) {
            if (attacker != null && !attacker.equals(victimId) && m.has(attacker)) {
                e.setDamage(0); // keep knockback, no health loss
                if (s == PvpStyle.BOXING) mm.registerHit(m, attacker);
            } else {
                e.setCancelled(true);
            }
            return;
        }

        // Intercept lethal damage so nobody sees a death screen.
        boolean totem = victim instanceof Player pl && hasTotem(pl);
        if (e.getFinalDamage() >= victim.getHealth() && !totem) {
            e.setCancelled(true);
            mm.end(m, m.opponent(victimId), mm.nameIn(m, victimId) + " was defeated");
        }
    }

    @EventHandler
    public void onBotDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;
        Match m = mm.matchOf(e.getEntity());
        if (m == null || m.bot == null) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        mm.end(m, m.opponent(e.getEntity().getUniqueId()), m.botName + " was defeated");
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        if (mm.isArena(e.getLocation())) e.setCancelled(true); // no zombie reinforcements or natural spawns
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Match m = mm.get(p);
        if (m == null) return;
        Location from = e.getFrom(), to = e.getTo();
        if (to == null) return;

        if (m.state == Match.State.COUNTDOWN) {
            if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
                Location t = from.clone();
                t.setYaw(to.getYaw());
                t.setPitch(to.getPitch());
                e.setTo(t);
            }
            return;
        }
        if (m.state == Match.State.ACTIVE) {
            double limit = m.floorY - (m.style == PvpStyle.SUMO ? 1.5 : 10);
            if (to.getY() < limit) {
                mm.end(m, m.opponent(p.getUniqueId()), p.getName() + " fell off");
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Match m = mm.get(e.getPlayer());
        if (m == null) return;
        if (!m.style.allowsBuilding() || m.state != Match.State.ACTIVE
                || e.getBlock().getY() > m.floorY + m.style.wallHeight()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Match m = mm.get(e.getPlayer());
        if (m == null) return;
        Block b = e.getBlock();
        if (!m.style.allowsBuilding() || m.state != Match.State.ACTIVE
                || b.getY() <= m.floorY || b.getType() == Material.BARRIER) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        Match m = mm.get(e.getPlayer());
        if (m == null) return;
        if (!m.style.allowsBuilding() || m.state != Match.State.ACTIVE) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (mm.get(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && mm.get(p) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Match m = mm.get(p);
        if (m == null || !m.style.noRegen()) return;
        EntityRegainHealthEvent.RegainReason r = e.getRegainReason();
        if (r == EntityRegainHealthEvent.RegainReason.SATIATED || r == EntityRegainHealthEvent.RegainReason.REGEN) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (mm.isArena(e.getLocation())) e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (mm.get(p) == null || p.hasPermission("pvpstyles.bypass")) return;
        String cmd = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (!ALLOWED_COMMANDS.contains(cmd)) {
            e.setCancelled(true);
            Msg.send(p, "<red>You can't use that command during a match. Use /pvp leave to forfeit.");
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Match m = mm.get(p);
        if (m == null) return;
        e.setKeepInventory(true);
        e.setKeepLevel(true);
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
        mm.end(m, m.opponent(p.getUniqueId()), p.getName() + " was defeated");
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) { mm.handleRespawn(e); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { mm.handleQuit(e.getPlayer()); }
}
