package com.theplumteam.multiplayerspawners.util;

import com.theplumteam.multiplayerspawners.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Utility class to manage spawner states across the mod.
 * This allows different mixins to share spawner state information.
 *
 * IMPORTANT: This version uses persistent data storage instead of in-memory maps,
 * so spawner states will survive server restarts and world reloads.
 */
public class SpawnerStateManager {

    /**
     * Creates a unique position key for a spawner based on its dimension and coordinates
     */
    private static String getPositionKey(Level level, BlockPos pos) {
        return level.dimension().location().toString() + "_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
    }

    /**
     * Marks a spawner as disabled for a specific player.
     * @param level The level containing the spawner
     * @param pos The position of the spawner
     * @param playerName The name of the player to disable it for
     */
    public static void disableSpawnerForPlayer(Level level, BlockPos pos, String playerName) {
        if (level.isClientSide()) {
            return;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        SpawnerPersistentData data = getOrCreateData(serverLevel.getServer());
        String key = getPositionKey(level, pos);
        data.setSpawnerDisabledForPlayer(key, playerName, true);
        data.setDirty();
    }

    /**
     * Checks if a spawner is disabled for a specific player
     * @param level The level containing the spawner
     * @param pos The position of the spawner
     * @param playerName The player name to check
     * @return true if this spawner is disabled for this player
     */
    public static boolean isSpawnerDisabledForPlayer(Level level, BlockPos pos, String playerName) {
        if (level.isClientSide()) {
            return false;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        SpawnerPersistentData data = getOrCreateData(serverLevel.getServer());
        String key = getPositionKey(level, pos);
        return data.isSpawnerDisabledForPlayer(key, playerName);
    }

    /**
     * Checks if a spawner has been disabled by at least one player.
     * @param level The level containing the spawner
     * @param pos The position of the spawner
     * @return true if any player has disabled this spawner
     */
    public static boolean isSpawnerDisabledByAnyone(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return false;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        SpawnerPersistentData data = getOrCreateData(serverLevel.getServer());
        String key = getPositionKey(level, pos);
        return data.isDisabledByAnyone(key);
    }

    /**
     * Gets or creates the persistent data instance using the platform-specific implementation
     */
    private static SpawnerPersistentData getOrCreateData(MinecraftServer server) {
        // Use platform-specific implementation via the service loader pattern
        return Services.PLATFORM.getOrCreateSpawnerData(server);
    }
}