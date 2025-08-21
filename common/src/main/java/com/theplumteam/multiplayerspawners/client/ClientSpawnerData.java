package com.theplumteam.multiplayerspawners.client;

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
 * Optimized client-side storage for spawner states with instant visual updates
 */
public class ClientSpawnerData {

    // Map of spawner positions to whether they are disabled for the current player
    private static final Map<BlockPos, Boolean> playerDisabledSpawners = new HashMap<>();

    // Track spawners that need visual updates (to avoid unnecessary refreshes)
    private static final Set<BlockPos> spawnersNeedingUpdate = new HashSet<>();

    // Cache the current player name to avoid repeated lookups
    private static String cachedPlayerName = null;

    // Track the current dimension to handle dimension changes
    private static String currentDimension = null;

    // Track last refresh time to avoid excessive periodic updates
    private static long lastPeriodicRefresh = 0;

    /**
     * Updates the disabled state for a spawner based on the list of disabled players
     */
    public static void updateSpawnerState(BlockPos pos, Set<String> disabledPlayers) {
        String currentPlayerName = getCurrentPlayerName();
        if (currentPlayerName == null) {
            Constants.LOG.warn("CLIENT: Cannot update spawner state - no player available");
            return;
        }

        boolean isDisabledForPlayer = disabledPlayers.contains(currentPlayerName);
        Boolean previousState = playerDisabledSpawners.put(pos, isDisabledForPlayer);

        // Only log and update if the state actually changed
        if (previousState == null || !previousState.equals(isDisabledForPlayer)) {
            Constants.LOG.info("CLIENT: Spawner at {} state changed - disabled: {} (was: {})",
                    pos, isDisabledForPlayer, previousState);

            // Mark this spawner as needing a visual update
            spawnersNeedingUpdate.add(pos);

            // Attempt immediate visual update with instant flags
            boolean success = updateBlockStateInWorld(pos, isDisabledForPlayer, false);

            if (!success) {
                // If immediate update failed, schedule a retry
                scheduleRetryUpdate(pos, isDisabledForPlayer);
            } else {
                // Update succeeded, remove from pending updates
                spawnersNeedingUpdate.remove(pos);
            }
        } else {
            // State didn't change, but make sure visual state is correct
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
        boolean expectedActivated = !shouldBeDisabled;

        return currentActivated != expectedActivated;
    }

    /**
     * Directly updates the block state in the world to reflect the spawner's status with instant visual feedback
     * @return true if the update was successful, false otherwise
     */
    private static boolean updateBlockStateInWorld(BlockPos pos, boolean isDisabledForPlayer, boolean forceUpdate) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }

        var currentState = minecraft.level.getBlockState(pos);

        // Only process spawner blocks
        if (!(currentState.getBlock() instanceof SpawnerBlock)) {
            return false;
        }

        // Check if the block has our property
        if (!currentState.hasProperty(SpawnerStateProperties.MULTIPLAYER_ACTIVATED)) {
            Constants.LOG.warn("CLIENT: Spawner at {} does not have MULTIPLAYER_ACTIVATED property!", pos);
            return false;
        }

        boolean currentActivated = currentState.getValue(SpawnerStateProperties.MULTIPLAYER_ACTIVATED);
        boolean newActivated = !isDisabledForPlayer; // Activated means NOT disabled

        // Only update if the state actually needs to change OR if we're forcing an update
        if (currentActivated != newActivated || forceUpdate) {
            try {
                var newState = currentState.setValue(SpawnerStateProperties.MULTIPLAYER_ACTIVATED, newActivated);

                // IMMEDIATE UPDATE: Use flag 11 for instant visual update
                // Flag 11 = 1 (BLOCK_UPDATE) + 2 (UPDATE_CLIENTS) + 8 (UPDATE_IMMEDIATE)
                minecraft.level.setBlock(pos, newState, 11);

                // Force multiple types of visual updates for immediate feedback
                forceInstantVisualUpdate(pos, currentState, newState);

                Constants.LOG.info("CLIENT: INSTANTLY updated spawner visual state at {} to MULTIPLAYER_ACTIVATED={}", pos, newActivated);
                return true;

            } catch (Exception e) {
                Constants.LOG.error("CLIENT: Failed to update block state at {}: {}", pos, e.getMessage());
                return false;
            }
        } else {
            // No change needed - remove from pending updates if it was there
            spawnersNeedingUpdate.remove(pos);
            return true;
        }
    }

    /**
     * Forces instant visual updates using multiple approaches for maximum immediate feedback
     */
    private static void forceInstantVisualUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.levelRenderer == null) return;

        try {
            // Multiple approaches for maximum instant visual feedback:

            // 1. Force block update notification
            minecraft.level.sendBlockUpdated(pos, oldState, newState, 3);

            // 2. Mark blocks dirty for immediate re-rendering
            minecraft.level.setBlocksDirty(pos, oldState, newState);

            // 3. Mark for immediate re-rendering via level renderer
            minecraft.levelRenderer.setBlocksDirty(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());

            // 4. Force chunk section update
            var chunk = minecraft.level.getChunk(pos);
            if (chunk instanceof LevelChunk levelChunk) {
                levelChunk.setUnsaved(true);
                // Mark the section containing this block as dirty
                int sectionX = pos.getX() >> 4;
                int sectionY = pos.getY() >> 4;
                int sectionZ = pos.getZ() >> 4;
                minecraft.level.setSectionDirtyWithNeighbors(sectionX, sectionY, sectionZ);
            }

            // 5. Trigger a level event for additional visual feedback
            minecraft.level.levelEvent(null, 2001, pos, 0); // Block break effect for visual feedback

            Constants.LOG.info("CLIENT: Applied instant visual updates for spawner at {}", pos);

        } catch (Exception e) {
            Constants.LOG.warn("CLIENT: Exception during instant visual update for {}: {}", pos, e.getMessage());
        }
    }

    /**
     * Schedules a single retry update for a spawner position
     */
    private static void scheduleRetryUpdate(BlockPos pos, boolean isDisabledForPlayer) {
        var minecraft = Minecraft.getInstance();
        if (minecraft == null) return;

        Constants.LOG.debug("CLIENT: Scheduling retry update for spawner at {}", pos);

        // Schedule retry after a short delay
        new Thread(() -> {
            try {
                Thread.sleep(100); // Reduced to 100ms for faster retry
                minecraft.execute(() -> {
                    if (playerDisabledSpawners.containsKey(pos)) {
                        boolean success = updateBlockStateInWorld(pos, isDisabledForPlayer, true);
                        if (success) {
                            spawnersNeedingUpdate.remove(pos);
                        }
                    }
                });
            } catch (InterruptedException e) {
                Constants.LOG.debug("CLIENT: Retry update interrupted for {}", pos);
            }
        }).start();
    }

    /**
     * Checks if a spawner is disabled for the current player
     */
    public static boolean isSpawnerDisabledForCurrentPlayer(BlockPos pos) {
        return playerDisabledSpawners.getOrDefault(pos, false);
    }

    /**
     * Clears all cached spawner data (called on world change/disconnect)
     */
    public static void clearAll() {
        playerDisabledSpawners.clear();
        spawnersNeedingUpdate.clear();
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

        // Mark all spawners as needing updates after dimension change
        spawnersNeedingUpdate.addAll(playerDisabledSpawners.keySet());

        // Schedule a targeted refresh
        scheduleTargetedRefresh();
    }

    /**
     * Called when chunks are loaded to refresh spawner states
     */
    public static void onChunkLoaded(int chunkX, int chunkZ) {
        Constants.LOG.debug("CLIENT: Chunk loaded at {}, {} - checking for spawners needing updates", chunkX, chunkZ);

        // Only check spawners that we know need updates
        refreshSpawnersInChunk(chunkX, chunkZ);
    }

    /**
     * Refreshes only spawners that need updates in a specific chunk
     */
    private static void refreshSpawnersInChunk(int chunkX, int chunkZ) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        // Create a copy to avoid concurrent modification
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
            return; // Nothing to do
        }

        Constants.LOG.info("CLIENT: Refreshing {} spawners that need updates", spawnersNeedingUpdate.size());

        // Create a copy to avoid concurrent modification
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
     * Schedules a targeted refresh of spawners that need updates
     */
    private static void scheduleTargetedRefresh() {
        var minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(() -> {
                try {
                    Thread.sleep(200); // Reduced delay for faster response
                    refreshPendingSpawnerStates();
                } catch (InterruptedException e) {
                    Constants.LOG.warn("CLIENT: Targeted refresh interrupted", e);
                }
            });
        }
    }

    /**
     * Called periodically - now much less frequently and only when needed
     */
    public static void periodicRefresh() {
        long currentTime = System.currentTimeMillis();

        // Only run periodic refresh every 30 seconds (instead of 5)
        if (currentTime - lastPeriodicRefresh < 30000) {
            return;
        }

        lastPeriodicRefresh = currentTime;

        // Only refresh if we have spawners that need updates
        if (!spawnersNeedingUpdate.isEmpty() && getCurrentPlayerName() != null) {
            Constants.LOG.debug("CLIENT: Periodic check - {} spawners need updates", spawnersNeedingUpdate.size());
            refreshPendingSpawnerStates();
        }
    }

    /**
     * Forces an immediate visual update with maximum aggressiveness and instant response
     * Used when a spawner is first disabled to ensure instant feedback
     */
    public static void forceImmediateVisualUpdate(BlockPos pos) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        Constants.LOG.info("CLIENT: Forcing INSTANT visual update for spawner at {}", pos);

        // Get current data
        Boolean isDisabled = playerDisabledSpawners.get(pos);
        if (isDisabled == null) {
            Constants.LOG.warn("CLIENT: No data for spawner at {} during immediate update", pos);
            return;
        }

        // Try the update immediately with force flag
        boolean success = updateBlockStateInWorld(pos, isDisabled, true);
        if (success) {
            Constants.LOG.info("CLIENT: INSTANT visual update succeeded for spawner at {}", pos);
            spawnersNeedingUpdate.remove(pos);
        } else {
            Constants.LOG.warn("CLIENT: INSTANT visual update failed - will retry for spawner at {}", pos);
            spawnersNeedingUpdate.add(pos);
            scheduleRetryUpdate(pos, isDisabled);
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

        // Only check spawners within 32 blocks to avoid performance issues
        int radius = 32;
        var playerPos = minecraft.player.blockPosition();
        
        // Create a copy to avoid ConcurrentModificationException
        var spawnersToCheck = new HashMap<>(playerDisabledSpawners);
        var spawnersToRemove = new HashSet<BlockPos>();
        
        // Check only spawners we have data for within range
        for (Map.Entry<BlockPos, Boolean> entry : spawnersToCheck.entrySet()) {
            BlockPos spawnerPos = entry.getKey();
            Boolean isDisabled = entry.getValue();
            
            // Skip if out of range
            if (spawnerPos.distSqr(playerPos) > radius * radius) {
                continue;
            }
            
            // Check if spawner block state is incorrect
            var currentState = minecraft.level.getBlockState(spawnerPos);
            if (!(currentState.getBlock() instanceof SpawnerBlock)) {
                // Spawner was broken/removed, mark for cleanup
                spawnersToRemove.add(spawnerPos);
                Constants.logVisualUpdate("Marked broken spawner at {} for removal", spawnerPos);
                continue;
            }
            
            if (!currentState.hasProperty(SpawnerStateProperties.MULTIPLAYER_ACTIVATED)) {
                Constants.LOG.warn("CLIENT: Spawner at {} missing MULTIPLAYER_ACTIVATED property during monitoring", spawnerPos);
                continue;
            }
            
            boolean currentActivated = currentState.getValue(SpawnerStateProperties.MULTIPLAYER_ACTIVATED);
            boolean expectedActivated = !isDisabled; // Activated means NOT disabled
            
            // If state is incorrect, immediately fix it
            if (currentActivated != expectedActivated) {
                Constants.logVisualUpdate("MONITOR: Detected incorrect spawner state at {} - current: {}, expected: {}", 
                    spawnerPos, currentActivated, expectedActivated);
                
                // Force immediate correction
                var newState = currentState.setValue(SpawnerStateProperties.MULTIPLAYER_ACTIVATED, expectedActivated);
                minecraft.level.setBlock(spawnerPos, newState, 11); // Immediate update flag
                
                // Apply instant visual updates
                forceInstantVisualUpdate(spawnerPos, currentState, newState);
                
                Constants.logVisualUpdate("MONITOR: Corrected spawner state at {} to MULTIPLAYER_ACTIVATED={}", 
                    spawnerPos, expectedActivated);
            }
        }
        
        // Clean up removed spawners outside the iteration loop
        for (BlockPos posToRemove : spawnersToRemove) {
            playerDisabledSpawners.remove(posToRemove);
            spawnersNeedingUpdate.remove(posToRemove);
            Constants.logVisualUpdate("Removed data for broken spawner at {}", posToRemove);
        }
    }
}