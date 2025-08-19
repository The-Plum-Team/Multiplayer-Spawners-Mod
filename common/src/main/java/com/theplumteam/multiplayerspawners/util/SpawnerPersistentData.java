package com.theplumteam.multiplayerspawners.util;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for spawner persistent data that contains the common logic
 * This class is extended by platform-specific implementations that handle
 * the actual saving/loading to disk using NBT format.
 */
public abstract class SpawnerPersistentData {
    // A map from a spawner's position key to a thread-safe set of player names who have disabled it.
    protected final ConcurrentHashMap<String, ConcurrentHashMap.KeySetView<String, Boolean>> disabledSpawners = new ConcurrentHashMap<>();

    /**
     * Checks if a spawner is disabled for a specific player.
     * @param key The position key for the spawner.
     * @param playerName The name of the player to check.
     * @return true if the spawner is disabled for the player, false otherwise.
     */
    public boolean isSpawnerDisabledForPlayer(String key, String playerName) {
        Set<String> players = disabledSpawners.get(key);
        return players != null && players.contains(playerName);
    }

    /**
     * Sets the disabled state of a spawner for a specific player.
     * @param key The position key for the spawner.
     * @param playerName The name of the player.
     * @param disabled true to disable, false to enable.
     */
    public void setSpawnerDisabledForPlayer(String key, String playerName, boolean disabled) {
        if (disabled) {
            disabledSpawners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(playerName);
        } else {
            Set<String> players = disabledSpawners.get(key);
            if (players != null) {
                players.remove(playerName);
                if (players.isEmpty()) {
                    disabledSpawners.remove(key);
                }
            }
        }
    }

    /**
     * Checks if the spawner is disabled by any player.
     * @param key The position key for the spawner.
     * @return true if at least one player has disabled this spawner.
     */
    public boolean isDisabledByAnyone(String key) {
        Set<String> players = disabledSpawners.get(key);
        return players != null && !players.isEmpty();
    }


    /**
     * Marks this data as dirty, indicating it needs to be saved
     * This method must be implemented by platform-specific subclasses
     */
    public abstract void setDirty();
}