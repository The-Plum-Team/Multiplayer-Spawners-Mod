package com.theplumteam.multiplayerspawners.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.Set;

/**
 * The players every spawner of a level checks during one tick, read once per level and tick
 * instead of once per spawner: with thousands of spawners and many players, the spawners'
 * checks would otherwise ask every player for its position, state and name thousands of times
 * per tick. Server thread only.
 */
public final class NearPlayers {

    private static Level level;
    private static long gameTime = Long.MIN_VALUE;
    private static int count;
    private static double[] xs = new double[16];
    private static double[] ys = new double[16];
    private static double[] zs = new double[16];
    private static String[] names = new String[16];

    private NearPlayers() {
    }

    /**
     * Whether the spawner logic at the centre {@code x, y, z} must not run: at least one player
     * is within {@code range} (the same test as vanilla's isNearPlayer: alive and not a spectator),
     * and every such player has disabled it.
     */
    public static boolean allNearHaveDisabled(Level level, double x, double y, double z, double range, Set<String> disabledPlayers) {
        refresh(level);
        double rangeSq = range * range;
        boolean anyNear = false;
        for (int i = 0; i < count; i++) {
            double dx = xs[i] - x, dy = ys[i] - y, dz = zs[i] - z;
            if (dx * dx + dy * dy + dz * dz >= rangeSq) {
                continue;
            }
            if (!disabledPlayers.contains(names[i])) {
                // Someone near still has it active.
                return false;
            }
            anyNear = true;
        }
        return anyNear;
    }

    private static void refresh(Level current) {
        long time = current.getGameTime();
        if (current == level && time == gameTime) {
            return;
        }
        level = current;
        gameTime = time;
        count = 0;
        for (Player player : current.players()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            if (count == xs.length) {
                int size = count * 2;
                xs = Arrays.copyOf(xs, size);
                ys = Arrays.copyOf(ys, size);
                zs = Arrays.copyOf(zs, size);
                names = Arrays.copyOf(names, size);
            }
            xs[count] = player.getX();
            ys[count] = player.getY();
            zs[count] = player.getZ();
            names[count] = player.getName().getString();
            count++;
        }
    }

    /** Forgets the level, when the server stops. */
    public static void clear() {
        level = null;
        gameTime = Long.MIN_VALUE;
        count = 0;
        Arrays.fill(names, null);
    }
}
