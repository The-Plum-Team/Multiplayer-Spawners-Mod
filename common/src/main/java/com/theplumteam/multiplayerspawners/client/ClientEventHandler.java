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

    /** Ticks until the refresh after a respawn, or -1 when none is pending. */
    private static int respawnRefreshTicks = -1;

    /** About a second: the respawned player's chunks arrive first. */
    private static final int RESPAWN_REFRESH_DELAY_TICKS = 20;

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
        ClientSpawnerData.onChunkUnloaded(chunk.getPos().getMinBlockX() >> 4, chunk.getPos().getMinBlockZ() >> 4);
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

        if (respawnRefreshTicks >= 0 && respawnRefreshTicks-- == 0) {
            ClientSpawnerData.refreshPendingSpawnerStates();
        }

        if (tickCounter % 20 == 0) {
            ClientSpawnerData.monitorAndCorrectSpawnerStates();
        }

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
        respawnRefreshTicks = -1;
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
        // Refresh once the respawned player's chunks have arrived, from the client tick: waiting
        // here would freeze the game.
        respawnRefreshTicks = RESPAWN_REFRESH_DELAY_TICKS;
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