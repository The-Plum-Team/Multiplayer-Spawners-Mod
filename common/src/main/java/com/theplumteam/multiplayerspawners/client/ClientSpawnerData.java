package com.theplumteam.multiplayerspawners.client;

import java.util.ArrayList;
import java.util.Comparator;
import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.block.SpawnerStateProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Client-side storage for spawner states with visual updates via "mark dirty" approach.
 * This class no longer uses Thread.sleep - instead it relies on the render engine and
 * the LevelChunkMixin "Enforcer" to handle visual updates correctly.
 */
public class ClientSpawnerData {

    // Map of spawner positions to whether they are disabled for the current player
    private static final Map<BlockPos, Boolean> playerDisabledSpawners = new HashMap<>();

    // Track spawners that need visual updates (to avoid unnecessary refreshes)
    private static final Set<BlockPos> spawnersNeedingUpdate = new HashSet<>();

    // Track spawners that should be treated as vanilla (player-placed with config enabled)
    private static final Set<BlockPos> vanillaTreatedSpawners = new HashSet<>();

    // Cache the current player name to avoid repeated lookups
    private static String cachedPlayerName = null;

    // Track the current dimension to handle dimension changes
    private static String currentDimension = null;

    // Track last refresh time to avoid excessive periodic updates
    private static long lastPeriodicRefresh = 0;

    /**
     * Updates the disabled state for a spawner based on the list of disabled players
     * @param pos The spawner position
     * @param disabledPlayers Set of player names who have this spawner disabled
     * @param treatedAsVanilla Whether this spawner should be treated as vanilla (use vanilla texture)
     */
    public static void updateSpawnerState(BlockPos pos, Set<String> disabledPlayers, boolean treatedAsVanilla) {
        String currentPlayerName = getCurrentPlayerName();
        if (currentPlayerName == null) {
            Constants.LOG.warn("CLIENT: Cannot update spawner state - no player available");
            return;
        }

        boolean isDisabledForPlayer = disabledPlayers.contains(currentPlayerName);
        Boolean previousState = playerDisabledSpawners.put(pos, isDisabledForPlayer);

        boolean wasVanillaTreated = vanillaTreatedSpawners.contains(pos);
        if (treatedAsVanilla) {
            vanillaTreatedSpawners.add(pos);
        } else {
            vanillaTreatedSpawners.remove(pos);
        }

        boolean stateChanged = (previousState == null || !previousState.equals(isDisabledForPlayer));
        boolean treatmentChanged = (wasVanillaTreated != treatedAsVanilla);

        if (stateChanged || treatmentChanged) {
            Constants.LOG.info("CLIENT: Spawner at {} updated - disabled: {}, treatedAsVanilla: {} (was: disabled={}, vanilla={})",
                    pos, isDisabledForPlayer, treatedAsVanilla, previousState, wasVanillaTreated);

            spawnersNeedingUpdate.add(pos);

            boolean success = updateBlockStateInWorld(pos, isDisabledForPlayer, false);

            if (success) {
                spawnersNeedingUpdate.remove(pos);
            }
            // If not successful, it stays in spawnersNeedingUpdate for the monitor to correct later
        } else {
            if (isBlockStateOutOfSync(pos, isDisabledForPlayer)) {
                Constants.LOG.debug("CLIENT: Spawner at {} needs visual sync", pos);
                spawnersNeedingUpdate.add(pos);
                updateBlockStateInWorld(pos, isDisabledForPlayer, false);
            }
        }
    }

    /**
     * Checks if a spawner's visual state is out of sync with our data
     */
    private static boolean isBlockStateOutOfSync(BlockPos pos, boolean shouldBeDisabled) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return false;

        var currentState = minecraft.level.getBlockState(pos);
        if (!(currentState.getBlock() instanceof SpawnerBlock)) return false;
        if (!currentState.hasProperty(SpawnerStateProperties.MULTIPLAYER_ACTIVATED)) return false;

        boolean currentActivated = currentState.getValue(SpawnerStateProperties.MULTIPLAYER_ACTIVATED);
        boolean isVanillaTreated = vanillaTreatedSpawners.contains(pos);

        boolean expectedActivated;
        if (isVanillaTreated) {
            expectedActivated = false; // Vanilla texture (MULTIPLAYER_ACTIVATED = false)
        } else {
            expectedActivated = !shouldBeDisabled;
        }

        return currentActivated != expectedActivated;
    }

    /**
     * Updates the block state in the world to reflect the spawner's status.
     * Uses the "mark dirty" approach - set the state and let the render engine handle updates.
     * @return true if the update was successful, false otherwise
     */
    private static boolean updateBlockStateInWorld(BlockPos pos, boolean isDisabledForPlayer, boolean forceUpdate) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }

        var currentState = minecraft.level.getBlockState(pos);

        if (!(currentState.getBlock() instanceof SpawnerBlock)) {
            return false;
        }

        if (!currentState.hasProperty(SpawnerStateProperties.MULTIPLAYER_ACTIVATED)) {
            Constants.LOG.warn("CLIENT: Spawner at {} does not have MULTIPLAYER_ACTIVATED property!", pos);
            return false;
        }

        boolean currentActivated = currentState.getValue(SpawnerStateProperties.MULTIPLAYER_ACTIVATED);
        boolean isVanillaTreated = vanillaTreatedSpawners.contains(pos);

        boolean newActivated;
        if (isVanillaTreated) {
            newActivated = false; // Vanilla texture (MULTIPLAYER_ACTIVATED = false)
        } else {
            newActivated = !isDisabledForPlayer; // Activated means NOT disabled
        }

        if (currentActivated != newActivated || forceUpdate) {
            try {
                var newState = currentState.setValue(SpawnerStateProperties.MULTIPLAYER_ACTIVATED, newActivated);

                // Set the block state and mark as dirty for rendering
                // Flag 3 = BLOCK_UPDATE (1) + SEND_TO_CLIENT (2) - standard update flags
                minecraft.level.setBlock(pos, newState, 3);

                // Mark the block as needing a render update
                markBlockForRerender(pos, currentState, newState);

                Constants.LOG.info("CLIENT: Updated spawner visual state at {} to MULTIPLAYER_ACTIVATED={} (treatedAsVanilla={})",
                        pos, newActivated, isVanillaTreated);
                return true;

            } catch (Exception e) {
                Constants.LOG.error("CLIENT: Failed to update block state at {}: {}", pos, e.getMessage());
                return false;
            }
        } else {
            spawnersNeedingUpdate.remove(pos);
            return true;
        }
    }

    /**
     * Marks a block position for re-rendering using the proper Minecraft approach.
     * This is the "Minecraft way" - update data, mark dirty, let engine redraw.
     */
    private static void markBlockForRerender(BlockPos pos, BlockState oldState, BlockState newState) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.levelRenderer == null) return;

        try {
            // Notify the level about the block update
            minecraft.level.sendBlockUpdated(pos, oldState, newState, 3);

            // Mark the blocks as dirty for re-rendering
            minecraft.level.setBlocksDirty(pos, oldState, newState);

            // Mark the section dirty in the level renderer
            minecraft.levelRenderer.setBlocksDirty(
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX(), pos.getY(), pos.getZ()
            );

            // Mark the chunk section as needing update
            var chunk = minecraft.level.getChunk(pos);
            if (chunk instanceof LevelChunk levelChunk) {
                int sectionX = pos.getX() >> 4;
                int sectionY = pos.getY() >> 4;
                int sectionZ = pos.getZ() >> 4;
                minecraft.level.setSectionDirtyWithNeighbors(sectionX, sectionY, sectionZ);
            }
        } catch (Exception e) {
            Constants.LOG.warn("CLIENT: Exception during mark dirty for {}: {}", pos, e.getMessage());
        }
    }

    /**
     * Checks if a spawner is disabled for the current player
     */
    public static boolean isSpawnerDisabledForCurrentPlayer(BlockPos pos) {
        return playerDisabledSpawners.getOrDefault(pos, false);
    }

    /**
     * Checks if a spawner is being treated as a player-placed (vanilla) spawner.
     * Player-placed spawners that are treated as vanilla should show normal particles and mob rendering.
     */
    public static boolean isPlayerPlacedSpawner(BlockPos pos) {
        return vanillaTreatedSpawners.contains(pos);
    }

    /**
     * Clears all cached spawner data (called on world change/disconnect)
     */
    public static void clearAll() {
        playerDisabledSpawners.clear();
        spawnersNeedingUpdate.clear();
        vanillaTreatedSpawners.clear();
        cachedPlayerName = null;
        currentDimension = null;
        lastPeriodicRefresh = 0;
        Constants.LOG.info("CLIENT: Cleared all client-side spawner data");
    }

    /**
     * Called when the player changes dimensions
     */
    public static void onDimensionChange(String newDimension) {
        Constants.LOG.info("CLIENT: Dimension changed from {} to {}", currentDimension, newDimension);
        currentDimension = newDimension;

        spawnersNeedingUpdate.addAll(playerDisabledSpawners.keySet());
        scheduleTargetedRefresh();
    }

    /**
     * Called when chunks are loaded to refresh spawner states
     */
    public static void onChunkLoaded(int chunkX, int chunkZ) {
        Constants.LOG.debug("CLIENT: Chunk loaded at {}, {} - checking for spawners needing updates", chunkX, chunkZ);
        refreshSpawnersInChunk(chunkX, chunkZ);
    }

    /**
     * Refreshes only spawners that need updates in a specific chunk
     */
    private static void refreshSpawnersInChunk(int chunkX, int chunkZ) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        var spawnersToCheck = new HashSet<>(spawnersNeedingUpdate);

        for (BlockPos pos : spawnersToCheck) {
            if ((pos.getX() >> 4) == chunkX && (pos.getZ() >> 4) == chunkZ) {
                Boolean isDisabled = playerDisabledSpawners.get(pos);
                if (isDisabled != null) {
                    boolean success = updateBlockStateInWorld(pos, isDisabled, true);
                    if (success) {
                        spawnersNeedingUpdate.remove(pos);
                        Constants.LOG.debug("CLIENT: Refreshed spawner at {} in loaded chunk", pos);
                    }
                }
            }
        }
    }

    /**
     * Removes data for a specific spawner (if it's broken, for example)
     */
    public static void removeSpawner(BlockPos pos) {
        playerDisabledSpawners.remove(pos);
        spawnersNeedingUpdate.remove(pos);
        vanillaTreatedSpawners.remove(pos);
        Constants.LOG.debug("CLIENT: Removed spawner data for {}", pos);
    }

    /**
     * Gets the current player's name
     */
    private static String getCurrentPlayerName() {
        if (cachedPlayerName == null && Minecraft.getInstance().player != null) {
            cachedPlayerName = Minecraft.getInstance().player.getName().getString();
        }
        return cachedPlayerName;
    }

    /**
     * Gets all spawner positions that are disabled for the current player
     */
    public static Map<BlockPos, Boolean> getAllSpawnerStates() {
        return new HashMap<>(playerDisabledSpawners);
    }

    /**
     * Refreshes only spawners that actually need updates
     */
    public static void refreshPendingSpawnerStates() {
        if (spawnersNeedingUpdate.isEmpty()) {
            return;
        }

        Constants.LOG.info("CLIENT: Refreshing {} spawners that need updates", spawnersNeedingUpdate.size());

        var spawnersToUpdate = new HashSet<>(spawnersNeedingUpdate);

        for (BlockPos pos : spawnersToUpdate) {
            Boolean isDisabled = playerDisabledSpawners.get(pos);
            if (isDisabled != null) {
                boolean success = updateBlockStateInWorld(pos, isDisabled, true);
                if (success) {
                    spawnersNeedingUpdate.remove(pos);
                }
            }
        }
    }

    /**
     * Schedules a targeted refresh of spawners that need updates.
     * Uses the main thread execute to refresh on the next tick instead of Thread.sleep.
     */
    private static void scheduleTargetedRefresh() {
        var minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            // Schedule refresh on the main thread for the next tick
            minecraft.execute(ClientSpawnerData::refreshPendingSpawnerStates);
        }
    }

    /**
     * Called periodically - now much less frequently and only when needed
     */
    public static void periodicRefresh() {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastPeriodicRefresh < 30000) {
            return;
        }

        lastPeriodicRefresh = currentTime;

        if (!spawnersNeedingUpdate.isEmpty() && getCurrentPlayerName() != null) {
            Constants.LOG.debug("CLIENT: Periodic check - {} spawners need updates", spawnersNeedingUpdate.size());
            refreshPendingSpawnerStates();
        }
    }

    /**
     * Forces an immediate visual update for a spawner.
     * Used when a spawner is first disabled to ensure immediate feedback.
     */
    public static void forceImmediateVisualUpdate(BlockPos pos) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        Constants.LOG.info("CLIENT: Forcing immediate visual update for spawner at {}", pos);

        Boolean isDisabled = playerDisabledSpawners.get(pos);
        if (isDisabled == null) {
            Constants.LOG.warn("CLIENT: No data for spawner at {} during immediate update", pos);
            return;
        }

        boolean success = updateBlockStateInWorld(pos, isDisabled, true);
        if (success) {
            Constants.LOG.info("CLIENT: Immediate visual update succeeded for spawner at {}", pos);
            spawnersNeedingUpdate.remove(pos);
        } else {
            Constants.LOG.warn("CLIENT: Immediate visual update failed - monitor will retry for spawner at {}", pos);
            spawnersNeedingUpdate.add(pos);
        }
    }

    /**
     * Monitors and corrects spawner states that have been reset (e.g., due to cancelled block breaks)
     * This method scans spawners within a reasonable range and immediately corrects any that have wrong states
     */
    public static void monitorAndCorrectSpawnerStates() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        int radius = 32;
        var playerPos = minecraft.player.blockPosition();
        var spawnersToCorrect = new ArrayList<BlockPos>();
        var spawnersToRemove = new HashSet<BlockPos>();

        var sortedSpawners = playerDisabledSpawners.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparingDouble(pos -> pos.distSqr(playerPos))))
                .toList();

        for (Map.Entry<BlockPos, Boolean> entry : sortedSpawners) {
            BlockPos spawnerPos = entry.getKey();
            if (spawnerPos.distSqr(playerPos) > radius * radius) {
                continue;
            }

            var currentState = minecraft.level.getBlockState(spawnerPos);
            if (!(currentState.getBlock() instanceof SpawnerBlock)) {
                spawnersToRemove.add(spawnerPos);
                continue;
            }

            if (!currentState.hasProperty(SpawnerStateProperties.MULTIPLAYER_ACTIVATED)) {
                continue;
            }

            boolean isDisabled = entry.getValue();
            boolean isVanillaTreated = vanillaTreatedSpawners.contains(spawnerPos);
            boolean currentActivated = currentState.getValue(SpawnerStateProperties.MULTIPLAYER_ACTIVATED);

            boolean expectedActivated;
            if (isVanillaTreated) {
                expectedActivated = false; // Vanilla texture
            } else {
                expectedActivated = !isDisabled;
            }

            if (currentActivated != expectedActivated) {
                Constants.debug("MONITOR: Found incorrect spawner state at {}. Expected: {}, Actual: {}. Queueing for correction.",
                        spawnerPos, expectedActivated, currentActivated);
                spawnersToCorrect.add(spawnerPos);
            }
        }

        if (!spawnersToCorrect.isEmpty()) {
            Constants.debug("MONITOR: Correcting {} incorrect spawner states.", spawnersToCorrect.size());
            for (BlockPos pos : spawnersToCorrect) {
                Boolean isDisabled = playerDisabledSpawners.get(pos);
                if (isDisabled != null) {
                    boolean success = updateBlockStateInWorld(pos, isDisabled, true);
                    if (!success) {
                        Constants.debug("MONITOR: Correction failed for {}. Will retry on next tick.", pos);
                        spawnersNeedingUpdate.add(pos);
                    }
                }
            }
        }

        if (!spawnersToRemove.isEmpty()) {
            for (BlockPos posToRemove : spawnersToRemove) {
                playerDisabledSpawners.remove(posToRemove);
                spawnersNeedingUpdate.remove(posToRemove);
                vanillaTreatedSpawners.remove(posToRemove);
                Constants.debug("Removed data for broken spawner at {}", posToRemove);
            }
        }
    }
}
