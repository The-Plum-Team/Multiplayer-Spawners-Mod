package com.theplumteam.multiplayerspawners.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

import java.util.Set;

/**
 * Utility class to manage spawner states.
 * This version reads and writes data directly to the spawner's BlockEntity NBT data,
 * making it robust and portable if the spawner is moved.
 */
public class SpawnerStateManager {

    /**
     * Gets the data accessor for a spawner at a given position.
     * @return The accessor, or null if the block is not a spawner.
     */
    private static SpawnerDataAccessor getAccessor(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SpawnerBlockEntity) {
            // SpawnerBlockEntity delegates to BaseSpawner, which we have mixed into.
            return (SpawnerDataAccessor) ((SpawnerBlockEntity) be).getSpawner();
        }
        return null;
    }

    /**
     * Marks a spawner as disabled for a specific player.
     */
    public static void disableSpawnerForPlayer(Level level, BlockPos pos, String playerName) {
        if (level.isClientSide()) return;

        SpawnerDataAccessor accessor = getAccessor(level, pos);
        if (accessor != null) {
            Set<String> disabledPlayers = accessor.multiplayerspawners$getDisabledPlayers();
            if (disabledPlayers.add(playerName)) {
                // Mark the block entity as dirty to ensure it saves
                level.getBlockEntity(pos).setChanged();
            }
        }
    }

    /**
     * Checks if a spawner is disabled for a specific player.
     */
    public static boolean isSpawnerDisabledForPlayer(Level level, BlockPos pos, String playerName) {
        if (level.isClientSide()) return false;

        SpawnerDataAccessor accessor = getAccessor(level, pos);
        if (accessor != null) {
            return accessor.multiplayerspawners$getDisabledPlayers().contains(playerName);
        }
        return false;
    }

    /**
     * Checks if a spawner has been disabled by at least one player.
     */
    public static boolean isSpawnerDisabledByAnyone(Level level, BlockPos pos) {
        if (level.isClientSide()) return false;

        SpawnerDataAccessor accessor = getAccessor(level, pos);
        if (accessor != null) {
            return !accessor.multiplayerspawners$getDisabledPlayers().isEmpty();
        }
        return false;
    }
}