package dev.pvpstyles;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

/** Generates and wipes the temporary arena for a match. */
final class ArenaBuilder {
    private ArenaBuilder() {}

    private static int top(Match m) {
        return m.floorY + Math.max(m.style.wallHeight(), 12) + 2;
    }

    static void build(Match m) {
        PvpStyle s = m.style;
        World w = m.world;
        int r = s.radius(), R = r + 1, h = s.wallHeight(), top = top(m);
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                int x = m.cx + dx, z = m.cz + dz;
                for (int y = m.floorY; y <= top; y++) w.getBlockAt(x, y, z).setType(Material.AIR, false);
                if (Math.abs(dx) <= r && Math.abs(dz) <= r) {
                    w.getBlockAt(x, m.floorY, z).setType(s.floor(), false);
                }
                if (h > 0 && (Math.abs(dx) == R || Math.abs(dz) == R)) {
                    for (int y = m.floorY; y <= m.floorY + h; y++) {
                        w.getBlockAt(x, y, z).setType(Material.BARRIER, false);
                    }
                }
            }
        }
    }

    static void clear(Match m) {
        World w = m.world;
        int R = m.style.radius() + 1, top = top(m);
        BoundingBox box = new BoundingBox(m.cx - R - 1, m.floorY - 1, m.cz - R - 1,
                m.cx + R + 2, top + 2, m.cz + R + 2);
        for (Entity e : w.getNearbyEntities(box)) {
            if (!(e instanceof Player)) e.remove();
        }
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                for (int y = m.floorY; y <= top; y++) {
                    w.getBlockAt(m.cx + dx, y, m.cz + dz).setType(Material.AIR, false);
                }
            }
        }
    }
}
