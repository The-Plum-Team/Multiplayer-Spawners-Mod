package com.theplumteam.multiplayerspawners.client;

import com.theplumteam.multiplayerspawners.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Optimized client-side event handler for maintaining spawner states with minimal overhead
 */
public class ClientEventHandler {

    private static String lastDimension = null;
    private static int tickCounter = 0;

    /**
     * Called when a chunk is loaded on the client
     */
    public static void onChunkLoad(LevelChunk chunk) {
        Constants.debug("CLIENT: Chunk loaded at {} - checking for pending spawner updates", chunk.getPos());
        ClientSpawnerData.onChunkLoaded(chunk.getPos().getMinBlockX() >> 4, chunk.getPos().getMinBlockZ() >> 4);
    }

    /**
     * Called when a chunk is unloaded on the client
     */
    public static void onChunkUnload(LevelChunk chunk) {
        // Cleanup is handled automatically by the optimized system
        Constants.LOG.trace("CLIENT: Chunk unloaded at {}", chunk.getPos());
    }

    /**
     * Called every client tick - now with smarter update logic and spawner monitoring
     */
    public static void onClientTick() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !com.theplumteam.multiplayerspawners.config.ModConfig.isClientConfigLoaded) {
            return;
        }

        // Check for dimension changes
        String currentDimension = minecraft.level.dimension().toString();
        if (lastDimension == null) {
            lastDimension = currentDimension;
        } else if (!lastDimension.equals(currentDimension)) {
            Constants.debug("CLIENT: Detected dimension change from {} to {}", lastDimension, currentDimension);
            ClientSpawnerData.onDimensionChange(currentDimension);
            lastDimension = currentDimension;
        }

        tickCounter++;

        // Monitor and correct spawner states every tick for immediate response to texture resets
        // This is the key fix for the break cancellation issue
        ClientSpawnerData.monitorAndCorrectSpawnerStates();

        // Much less frequent periodic refresh - every 600 ticks (30 seconds)
        if (tickCounter >= 600) {
            ClientSpawnerData.periodicRefresh();
            tickCounter = 0;
        }
    }

    /**
     * Called when the client connects to a server/world
     */
    public static void onClientConnect() {
        Constants.debug("CLIENT: Connected - resetting state tracking");
        lastDimension = null;
        tickCounter = 0;
        ClientSpawnerData.clearAll();
    }

    /**
     * Called when the client disconnects from a server/world
     */
    public static void onClientDisconnect() {
        Constants.debug("CLIENT: Disconnected - clearing all data");
        lastDimension = null;
        tickCounter = 0;
        ClientSpawnerData.clearAll();
    }

    /**
     * Called when the player respawns (including after death)
     */
    public static void onPlayerRespawn() {
        Constants.debug("CLIENT: Player respawned - scheduling targeted refresh");
        // Use the new targeted refresh instead of refreshing everything
        var minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(() -> {
                try {
                    Thread.sleep(1000); // Wait for world to load
                    ClientSpawnerData.refreshPendingSpawnerStates();
                } catch (InterruptedException e) {
                    Constants.LOG.warn("CLIENT: Respawn refresh interrupted", e);
                }
            });
        }
    }

    /**
     * Called when the level/world changes
     */
    public static void onLevelChange() {
        Constants.debug("CLIENT: Level changed - clearing and preparing for new world");
        lastDimension = null;
        ClientSpawnerData.clearAll();
    }
}