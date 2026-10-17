package com.theplumteam.multiplayerspawners.util;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Utility class to manage spawner states.
 * This version reads and writes data directly to the spawner's BlockEntity NBT data,
 * making it robust and portable if the spawner is moved.
 */
public class SpawnerStateManager {

    /** Items that break a spawner like vanilla when {@code breakWithSpawnerTools} is on. */
    public static final TagKey<Item> BREAKS_SPAWNERS = TagKey.create(Registries.ITEM, Constants.id("breaks_spawners"));

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
                accessor.multiplayerspawners$getDisabledTimes().put(playerName, level.getGameTime());
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

    /**
     * Enables a spawner for a specific player (allows them to break it).
     */
    public static void enableSpawnerForPlayer(Level level, BlockPos pos, String playerName) {
        if (level.isClientSide()) return;

        SpawnerDataAccessor accessor = getAccessor(level, pos);
        if (accessor != null) {
            Set<String> disabledPlayers = accessor.multiplayerspawners$getDisabledPlayers();
            accessor.multiplayerspawners$getDisabledTimes().remove(playerName);
            if (disabledPlayers.remove(playerName)) {
                // Mark the block entity as dirty to ensure it saves
                level.getBlockEntity(pos).setChanged();
            }
        }
    }

    /** How long a spawner stays disabled for a player, in ticks, or 0 when it never becomes active again. */
    public static long respawnTicks() {
        return ModConfig.SERVER.spawnerRespawnMinutes.get() * 60L * 20L;
    }

    /**
     * Makes the spawner active again for every player who disabled it at least
     * {@code spawnerRespawnMinutes} ago. A player disabled before the spawner recorded times
     * (an older version of the mod) starts counting now.
     *
     * @return whether a player was removed, so that the clients must be told
     */
    public static boolean expireDisabledPlayers(Level level, BlockPos pos, SpawnerDataAccessor accessor) {
        Set<String> disabledPlayers = accessor.multiplayerspawners$getDisabledPlayers();
        Map<String, Long> times = accessor.multiplayerspawners$getDisabledTimes();
        if (disabledPlayers.isEmpty()) {
            times.clear();
            return false;
        }
        long respawnTicks = respawnTicks();
        if (respawnTicks <= 0) {
            // Never: nothing to expire. Each hit records its time, so it counts once respawn is on.
            return false;
        }
        long now = level.getGameTime();
        boolean changed = times.keySet().retainAll(disabledPlayers);
        for (String player : disabledPlayers) {
            if (times.putIfAbsent(player, now) == null) {
                changed = true;
            }
        }
        boolean expired = false;
        for (Iterator<String> players = disabledPlayers.iterator(); players.hasNext(); ) {
            String player = players.next();
            if (now - times.get(player) >= respawnTicks) {
                players.remove();
                times.remove(player);
                expired = true;
            }
        }
        if (changed || expired) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                blockEntity.setChanged();
            }
        }
        return expired;
    }
}
