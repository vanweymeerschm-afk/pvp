package dev.pvpstyles;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class Match {
    enum State { COUNTDOWN, ACTIVE, ENDED }

    final PvpStyle style;
    final boolean ranked;
    final UUID a;
    UUID b;                 // for bot matches this is the bot's entity UUID
    LivingEntity bot;       // null in player-vs-player matches
    String botName;
    BotDifficulty difficulty = BotDifficulty.NORMAL;
    final int slot;
    final World world;
    final int cx, floorY, cz;
    State state = State.COUNTDOWN;
    final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
    final Map<UUID, Integer> hits = new HashMap<>();
    final List<BukkitTask> tasks = new ArrayList<>();

    Match(PvpStyle style, UUID a, UUID b, int slot, World world, int cx, int floorY, int cz, boolean ranked) {
        this.style = style;
        this.ranked = ranked;
        this.a = a;
        this.b = b;
        this.slot = slot;
        this.world = world;
        this.cx = cx;
        this.floorY = floorY;
        this.cz = cz;
    }

    boolean has(UUID id) { return a.equals(id) || (b != null && b.equals(id)); }

    UUID opponent(UUID id) { return a.equals(id) ? b : a; }

    Location center() { return new Location(world, cx + 0.5, floorY + 1, cz + 0.5); }

    Location spawn(boolean first) {
        int off = Math.max(2, style.radius() * 2 / 3);
        return first
                ? new Location(world, cx + 0.5 - off, floorY + 1, cz + 0.5, -90f, 0f)
                : new Location(world, cx + 0.5 + off, floorY + 1, cz + 0.5, 90f, 0f);
    }

    void cancelTasks() {
        for (BukkitTask t : new ArrayList<>(tasks)) t.cancel();
        tasks.clear();
    }
}
