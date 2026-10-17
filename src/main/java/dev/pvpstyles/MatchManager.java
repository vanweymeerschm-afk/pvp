package dev.pvpstyles;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import net.kyori.adventure.title.Title;

import java.time.Duration;
import java.util.*;

public final class MatchManager {
    private static final int SLOT_SPACING = 128;

    private record Duel(UUID from, PvpStyle style, long expires) {}

    private final PvPStyles plugin;
    private final Map<UUID, Match> inMatch = new HashMap<>();
    private final Map<PvpStyle, UUID> queue = new EnumMap<>(PvpStyle.class);
    private final Map<UUID, Duel> duels = new HashMap<>();
    private final Set<Integer> usedSlots = new HashSet<>();

    private final World world;
    private final int baseX, baseZ, floorY, countdown, timeLimit, endDelay, boxingHits;

    public MatchManager(PvPStyles plugin) {
        this.plugin = plugin;
        FileConfiguration c = plugin.getConfig();
        World w = Bukkit.getWorld(c.getString("arena.world", ""));
        if (w == null) {
            w = Bukkit.getWorlds().get(0);
            plugin.getLogger().info("Using world '" + w.getName() + "' for arenas.");
        }
        this.world = w;
        this.baseX = c.getInt("arena.x", 50000);
        this.baseZ = c.getInt("arena.z", 50000);
        this.floorY = c.getInt("arena.floor-y", 120);
        this.countdown = c.getInt("match.countdown-seconds", 5);
        this.timeLimit = c.getInt("match.time-limit-seconds", 300);
        this.endDelay = c.getInt("match.end-delay-seconds", 3);
        this.boxingHits = c.getInt("match.boxing-hits-to-win", 100);
    }

    // ---------- lookups ----------

    Match get(Player p) { return inMatch.get(p.getUniqueId()); }

    boolean isArena(org.bukkit.Location l) {
        return l.getWorld() == world && l.getX() >= baseX - 64 && Math.abs(l.getZ() - baseZ) <= 64;
    }

    private boolean busy(Player p) {
        return inMatch.containsKey(p.getUniqueId()) || queue.containsValue(p.getUniqueId());
    }

    // ---------- queue & duels ----------

    public void queue(Player p, PvpStyle style) {
        if (inMatch.containsKey(p.getUniqueId())) { Msg.send(p, "<red>You're already in a match."); return; }
        leaveQueue(p);
        UUID waiting = queue.get(style);
        if (waiting != null && !waiting.equals(p.getUniqueId())) {
            Player other = Bukkit.getPlayer(waiting);
            if (other != null && other.isOnline() && !inMatch.containsKey(waiting)) {
                queue.remove(style);
                start(other, p, style);
                return;
            }
        }
        queue.put(style, p.getUniqueId());
        Msg.send(p, "Queued for <red>" + style.display() + "<gray>. Use <white>/pvp leave<gray> to cancel.");
    }

    public boolean leaveQueue(Player p) {
        return queue.values().removeIf(id -> id.equals(p.getUniqueId()));
    }

    public void leave(Player p) {
        Match m = get(p);
        if (m != null) {
            if (m.state != Match.State.ENDED) end(m, m.opponent(p.getUniqueId()), p.getName() + " forfeited");
            else Msg.send(p, "The match is over. You'll be sent back shortly.");
            return;
        }
        if (leaveQueue(p)) Msg.send(p, "You left the queue.");
        else Msg.send(p, "<red>You're not in a match or queue.");
    }

    public void challenge(Player from, Player to, PvpStyle style) {
        if (from.equals(to)) { Msg.send(from, "<red>You can't duel yourself."); return; }
        if (busy(from) || busy(to)) { Msg.send(from, "<red>One of you is already in a match or queue."); return; }
        duels.put(to.getUniqueId(), new Duel(from.getUniqueId(), style, System.currentTimeMillis() + 30_000));
        Msg.send(from, "Duel request sent to <white>" + to.getName() + "<gray> (" + style.display() + ").");
        Msg.send(to, "<white>" + from.getName() + "<gray> challenged you to <red>" + style.display()
                + "<gray>! <green>/duel accept<gray> or <red>/duel deny<gray> (30s)");
    }

    public void accept(Player target) {
        Duel d = duels.remove(target.getUniqueId());
        if (d == null || d.expires() < System.currentTimeMillis()) {
            Msg.send(target, "<red>You have no pending duel request."); return;
        }
        Player from = Bukkit.getPlayer(d.from());
        if (from == null || busy(from) || busy(target)) {
            Msg.send(target, "<red>That duel can't start right now."); return;
        }
        start(from, target, d.style());
    }

    public void deny(Player target) {
        Duel d = duels.remove(target.getUniqueId());
        if (d == null) { Msg.send(target, "<red>You have no pending duel request."); return; }
        Player from = Bukkit.getPlayer(d.from());
        Msg.send(target, "Duel declined.");
        if (from != null) Msg.send(from, "<white>" + target.getName() + "<gray> declined your duel.");
    }

    // ---------- match lifecycle ----------

    private void start(Player a, Player b, PvpStyle style) {
        for (Player p : List.of(a, b)) { leaveQueue(p); duels.remove(p.getUniqueId()); }

        int slot = 0;
        while (!usedSlots.add(slot)) slot++;
        Match m = new Match(style, a.getUniqueId(), b.getUniqueId(), slot, world,
                baseX + slot * SLOT_SPACING, floorY, baseZ);
        inMatch.put(a.getUniqueId(), m);
        inMatch.put(b.getUniqueId(), m);

        ArenaBuilder.build(m);

        int i = 0;
        for (Player p : List.of(a, b)) {
            m.snapshots.put(p.getUniqueId(), PlayerSnapshot.capture(p));
            prepare(p, style);
            p.teleport(m.spawn(i++ == 0));
            Msg.send(p, "Starting <red>" + style.display() + "<gray>: " + style.description());
        }

        m.tasks.add(new BukkitRunnable() {
            int n = countdown;
            @Override public void run() {
                if (m.state != Match.State.COUNTDOWN) { cancel(); return; }
                if (n > 0) {
                    titleAll(m, "<yellow>" + n, "<gray>" + style.display(), 1f);
                    n--;
                    return;
                }
                m.state = Match.State.ACTIVE;
                titleAll(m, "<green>FIGHT!", "", 2f);
                m.tasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> timeout(m), timeLimit * 20L));
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 20L));
    }

    private void prepare(Player p, PvpStyle style) {
        p.setGameMode(GameMode.SURVIVAL);
        p.setAllowFlight(false);
        p.getInventory().clear();
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        AttributeInstance maxHp = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(maxHp != null ? maxHp.getValue() : 20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setLevel(0);
        p.setExp(0f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setVelocity(new Vector());

        AttributeInstance speed = p.getAttribute(Attribute.ATTACK_SPEED);
        if (speed != null && style.noCooldown()) speed.setBaseValue(100.0);
        p.setMaximumNoDamageTicks(style.noHitDelay() ? 0 : 20);
        p.setNoDamageTicks(0);

        Kits.give(p, style);
    }

    void registerHit(Match m, Player attacker) {
        int h = m.hits.merge(attacker.getUniqueId(), 1, Integer::sum);
        attacker.sendActionBar(Msg.c("<red>Hits: <white>" + h + "<gray>/" + boxingHits));
        if (h >= boxingHits) end(m, attacker.getUniqueId(), attacker.getName() + " landed " + boxingHits + " hits");
    }

    private void timeout(Match m) {
        if (m.state != Match.State.ACTIVE) return;
        UUID winner = null;
        if (m.style == PvpStyle.BOXING) {
            int ha = m.hits.getOrDefault(m.a, 0), hb = m.hits.getOrDefault(m.b, 0);
            if (ha > hb) winner = m.a; else if (hb > ha) winner = m.b;
        } else {
            Player pa = Bukkit.getPlayer(m.a), pb = Bukkit.getPlayer(m.b);
            if (pa != null && pb != null) {
                if (pa.getHealth() > pb.getHealth()) winner = m.a;
                else if (pb.getHealth() > pa.getHealth()) winner = m.b;
            }
        }
        end(m, winner, "Time limit reached");
    }

    void end(Match m, UUID winner, String reason) {
        if (m.state == Match.State.ENDED) return;
        m.state = Match.State.ENDED;
        m.cancelTasks();

        String winnerName = winner == null ? null : name(winner);
        for (UUID id : List.of(m.a, m.b)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            if (winner == null) {
                Msg.send(p, "<yellow>Draw<gray> - " + reason);
                p.showTitle(title("<gray>DRAW", ""));
            } else if (winner.equals(id)) {
                Msg.send(p, "<green>You won<gray> - " + reason);
                p.showTitle(title("<green>VICTORY", "<gray>" + reason));
            } else {
                Msg.send(p, "<red>" + winnerName + " won<gray> - " + reason);
                p.showTitle(title("<red>DEFEAT", "<gray>" + reason));
            }
        }
        // Sumo losers are still falling; bring them back onto the platform while the result shows.
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (UUID id : List.of(m.a, m.b)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.getLocation().getY() < m.floorY) {
                    p.teleport(m.center());
                    p.setFallDistance(0f);
                    p.setVelocity(new Vector());
                }
            }
        });
        Bukkit.getScheduler().runTaskLater(plugin, () -> cleanup(m), endDelay * 20L);
    }

    private void cleanup(Match m) {
        ArenaBuilder.clear(m);
        for (UUID id : List.of(m.a, m.b)) {
            if (inMatch.get(id) != m) continue;
            Player p = Bukkit.getPlayer(id);
            if (p == null) { inMatch.remove(id); continue; }
            if (p.isDead()) continue; // restored on respawn
            inMatch.remove(id);
            PlayerSnapshot s = m.snapshots.get(id);
            if (s != null) s.restore(p);
        }
        usedSlots.remove(m.slot);
    }

    // ---------- player events ----------

    void handleQuit(Player p) {
        leaveQueue(p);
        duels.remove(p.getUniqueId());
        Match m = get(p);
        if (m == null) return;
        if (m.state != Match.State.ENDED) end(m, m.opponent(p.getUniqueId()), p.getName() + " disconnected");
        PlayerSnapshot s = m.snapshots.get(p.getUniqueId());
        inMatch.remove(p.getUniqueId());
        if (s != null) s.restore(p);
    }

    void handleRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Match m = get(p);
        if (m == null) return;
        PlayerSnapshot s = m.snapshots.get(p.getUniqueId());
        if (s != null) e.setRespawnLocation(s.location());
        Bukkit.getScheduler().runTask(plugin, () -> {
            inMatch.remove(p.getUniqueId());
            if (s != null) s.restore(p);
        });
    }

    void shutdown() {
        for (Match m : new HashSet<>(inMatch.values())) {
            m.state = Match.State.ENDED;
            m.cancelTasks();
            cleanup(m);
        }
        inMatch.clear();
        queue.clear();
    }

    // ---------- helpers ----------

    private String name(UUID id) {
        Player p = Bukkit.getPlayer(id);
        return p != null ? p.getName() : "Unknown";
    }

    private static Title title(String main, String sub) {
        return Title.title(Msg.c(main), Msg.c(sub),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1500), Duration.ofMillis(200)));
    }

    private void titleAll(Match m, String main, String sub, float pitch) {
        Title t = Title.title(Msg.c(main), Msg.c(sub),
                Title.Times.times(Duration.ofMillis(50), Duration.ofMillis(800), Duration.ofMillis(100)));
        for (UUID id : List.of(m.a, m.b)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.showTitle(t);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, pitch);
        }
    }
}
