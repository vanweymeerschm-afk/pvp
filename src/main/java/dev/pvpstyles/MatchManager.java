package dev.pvpstyles;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;

public final class MatchManager {
    private static final int SLOT_SPACING = 128;

    private record Duel(UUID from, PvpStyle style, long expires) {}

    private final PvPStyles plugin;
    private final KitStore kitStore;
    private final RankStore ranks;
    private final Map<UUID, Match> inMatch = new HashMap<>();
    private final Map<UUID, Match> botMatches = new HashMap<>(); // bot entity uuid -> match
    private final Map<PvpStyle, UUID> queue = new EnumMap<>(PvpStyle.class);
    private final Map<UUID, Duel> duels = new HashMap<>();
    private final Set<Integer> usedSlots = new HashSet<>();

    private final World world;
    private final int baseX, baseZ, floorY, countdown, timeLimit, endDelay, boxingHits;
    private final boolean botsEnabled;
    private final BotDifficulty defaultDifficulty;
    private final int queueFallback;

    public MatchManager(PvPStyles plugin, KitStore kitStore, RankStore ranks) {
        this.plugin = plugin;
        this.kitStore = kitStore;
        this.ranks = ranks;
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
        this.botsEnabled = c.getBoolean("bots.enabled", true);
        BotDifficulty d = BotDifficulty.parse(c.getString("bots.default-difficulty", "NORMAL"));
        this.defaultDifficulty = d != null ? d : BotDifficulty.NORMAL;
        this.queueFallback = c.getInt("bots.queue-fallback-seconds", 15);
    }

    // ---------- lookups ----------

    Match get(Player p) { return inMatch.get(p.getUniqueId()); }

    /** The match an entity (player or bot) is fighting in, or null. */
    Match matchOf(Entity e) {
        if (e instanceof Player p) return inMatch.get(p.getUniqueId());
        return botMatches.get(e.getUniqueId());
    }

    boolean isArena(org.bukkit.Location l) {
        return l.getWorld() == world && l.getX() >= baseX - 64 && Math.abs(l.getZ() - baseZ) <= 64;
    }

    private boolean busy(Player p) {
        return inMatch.containsKey(p.getUniqueId()) || queue.containsValue(p.getUniqueId());
    }

    /** Display name for a combatant (player or bot). */
    String nameIn(Match m, UUID id) {
        if (m.bot != null && id.equals(m.b)) return m.botName;
        return name(id);
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
                start(other, p, style, true);
                return;
            }
        }
        queue.put(style, p.getUniqueId());
        Msg.send(p, "Queued (ranked) for <red>" + style.display() + "<gray>. Use <white>/pvp leave<gray> to cancel.");

        if (botsEnabled && queueFallback > 0) {
            UUID id = p.getUniqueId();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player pl = Bukkit.getPlayer(id);
                if (pl != null && id.equals(queue.get(style)) && !inMatch.containsKey(id)) {
                    queue.remove(style);
                    Msg.send(pl, "No opponent found - matching you with a bot.");
                    startBot(pl, style, null);
                }
            }, queueFallback * 20L);
        }
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
        start(from, target, d.style(), false);
    }

    public void deny(Player target) {
        Duel d = duels.remove(target.getUniqueId());
        if (d == null) { Msg.send(target, "<red>You have no pending duel request."); return; }
        Player from = Bukkit.getPlayer(d.from());
        Msg.send(target, "Duel declined.");
        if (from != null) Msg.send(from, "<white>" + target.getName() + "<gray> declined your duel.");
    }

    // ---------- match lifecycle ----------

    private int allocSlot() {
        int slot = 0;
        while (!usedSlots.add(slot)) slot++;
        return slot;
    }

    private void start(Player a, Player b, PvpStyle style, boolean ranked) {
        for (Player p : List.of(a, b)) { leaveQueue(p); duels.remove(p.getUniqueId()); }

        int slot = allocSlot();
        Match m = new Match(style, a.getUniqueId(), b.getUniqueId(), slot, world,
                baseX + slot * SLOT_SPACING, floorY, baseZ, ranked);
        inMatch.put(a.getUniqueId(), m);
        inMatch.put(b.getUniqueId(), m);

        ArenaBuilder.build(m);

        int i = 0;
        for (Player p : List.of(a, b)) {
            m.snapshots.put(p.getUniqueId(), PlayerSnapshot.capture(p));
            prepare(p, style);
            p.teleport(m.spawn(i++ == 0));
            Msg.send(p, "Starting <red>" + style.display() + (ranked ? " <gold>(Ranked)" : " <gray>(Unranked)")
                    + "<gray>: " + style.description());
        }
        runCountdown(m);
    }

    /** Starts a fight against an AI bot. difficulty == null uses the config default. */
    public void startBot(Player p, PvpStyle style, BotDifficulty difficulty) {
        if (!botsEnabled) { Msg.send(p, "<red>Bots are disabled on this server."); return; }
        if (inMatch.containsKey(p.getUniqueId())) { Msg.send(p, "<red>You're already in a match."); return; }
        leaveQueue(p);
        duels.remove(p.getUniqueId());
        BotDifficulty diff = difficulty != null ? difficulty : defaultDifficulty;

        int slot = allocSlot();
        Match m = new Match(style, p.getUniqueId(), null, slot, world,
                baseX + slot * SLOT_SPACING, floorY, baseZ, false);
        m.difficulty = diff;
        inMatch.put(p.getUniqueId(), m);

        ArenaBuilder.build(m);

        m.snapshots.put(p.getUniqueId(), PlayerSnapshot.capture(p));
        prepare(p, style);
        p.teleport(m.spawn(true));

        Mob bot = BotFactory.spawn(style, diff, m.spawn(false));
        m.bot = bot;
        m.b = bot.getUniqueId();
        m.botName = "Bot (" + diff.label() + ")";
        botMatches.put(m.b, m);

        Msg.send(p, "Starting <red>" + style.display() + "<gray> vs <red>" + m.botName
                + " <gray>(unranked): " + style.description());
        runCountdown(m);
    }

    private void runCountdown(Match m) {
        PvpStyle style = m.style;
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
                activateBot(m);
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 20L));
    }

    private void activateBot(Match m) {
        if (m.bot == null) return;
        Player p = Bukkit.getPlayer(m.a);
        if (m.bot instanceof Mob mob) {
            mob.setAI(true);
            if (p != null) mob.setTarget(p);
        }
        m.tasks.add(new BukkitRunnable() {
            @Override public void run() {
                if (m.state != Match.State.ACTIVE || m.bot == null || !m.bot.isValid()) { cancel(); return; }
                Player pl = Bukkit.getPlayer(m.a);
                if (pl != null && m.bot instanceof Mob mob && mob.getTarget() == null) mob.setTarget(pl);
                double limit = m.floorY - (m.style == PvpStyle.SUMO ? 1.5 : 10);
                if (m.bot.getLocation().getY() < limit) end(m, m.a, m.botName + " fell off");
            }
        }.runTaskTimer(plugin, 5L, 5L));
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

        Kits.give(p, style, kitStore.get(p.getUniqueId(), style));
    }

    void registerHit(Match m, UUID attackerId) {
        int h = m.hits.merge(attackerId, 1, Integer::sum);
        Player attacker = Bukkit.getPlayer(attackerId);
        if (attacker != null) attacker.sendActionBar(Msg.c("<red>Hits: <white>" + h + "<gray>/" + boxingHits));
        if (h >= boxingHits) end(m, attackerId, nameIn(m, attackerId) + " landed " + boxingHits + " hits");
    }

    private double healthOf(Match m, UUID id) {
        if (m.bot != null && id.equals(m.b)) return m.bot.isValid() ? m.bot.getHealth() : 0;
        Player p = Bukkit.getPlayer(id);
        return p != null ? p.getHealth() : 0;
    }

    private void timeout(Match m) {
        if (m.state != Match.State.ACTIVE) return;
        UUID winner = null;
        if (m.style == PvpStyle.BOXING) {
            int ha = m.hits.getOrDefault(m.a, 0), hb = m.hits.getOrDefault(m.b, 0);
            if (ha > hb) winner = m.a; else if (hb > ha) winner = m.b;
        } else {
            double ha = healthOf(m, m.a), hb = healthOf(m, m.b);
            if (ha > hb) winner = m.a; else if (hb > ha) winner = m.b;
        }
        end(m, winner, "Time limit reached");
    }

    void end(Match m, UUID winner, String reason) {
        if (m.state == Match.State.ENDED) return;
        m.state = Match.State.ENDED;
        m.cancelTasks();

        if (m.bot != null && m.bot instanceof Mob mob) {
            mob.setTarget(null);
            mob.setAI(false);
        }

        String winnerName = winner == null ? null : nameIn(m, winner);
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
        if (m.ranked && winner != null) applyRank(m, winner);

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
        if (m.bot != null) {
            m.bot.remove();
            botMatches.remove(m.b);
        }
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
        for (Match m : new HashSet<>(botMatches.values())) {
            m.state = Match.State.ENDED;
            m.cancelTasks();
            cleanup(m);
        }
        inMatch.clear();
        botMatches.clear();
        queue.clear();
    }

    // ---------- helpers ----------

    private String name(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return p.getName();
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n != null ? n : "Unknown";
    }

    private void applyRank(Match m, UUID winner) {
        UUID loser = m.opponent(winner);
        RankStore.Tier before = ranks.tier(ranks.get(winner).rating);
        int[] d = ranks.record(winner, name(winner), loser, name(loser));
        RankStore.Stats ws = ranks.get(winner), ls = ranks.get(loser);
        RankStore.Tier after = ranks.tier(ws.rating);

        Player w = Bukkit.getPlayer(winner), l = Bukkit.getPlayer(loser);
        if (w != null) {
            Msg.send(w, "<green>+" + d[0] + " rating <gray>(" + ws.rating + ") " + ranks.tag(winner));
            if (!after.name().equals(before.name())) {
                Msg.send(w, "<gold>Rank up! <gray>You are now " + ranks.tag(winner) + "<gray>.");
            }
        }
        if (l != null) {
            Msg.send(l, "<red>-" + d[1] + " rating <gray>(" + ls.rating + ") " + ranks.tag(loser));
        }
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
