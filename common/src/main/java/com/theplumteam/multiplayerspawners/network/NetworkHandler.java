package com.theplumteam.multiplayerspawners.network;

import com.theplumteam.multiplayerspawners.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;
import java.util.Set;

/**
 * Platform-agnostic network handler interface
 */
public abstract class NetworkHandler {

    private static NetworkHandler INSTANCE;

    public static NetworkHandler getInstance() {
        if (INSTANCE == null) {
            INSTANCE = Services.PLATFORM.createNetworkHandler();
        }
        return INSTANCE;
    }

    /**
     * Initialize the network handler (register packets)
     */
    public abstract void init();

    /**
     * Send spawner state to a specific player
     * @param player The player to send to
     * @param pos The spawner position
     * @param disabledPlayers Set of player names who have this spawner disabled
     * @param isFullSync Whether this is a full sync (initial load) or an update
     * @param treatedAsVanilla Whether this spawner should be treated as vanilla (player-placed and config enabled)
     */
    public abstract void sendSpawnerStateToPlayer(ServerPlayer player, BlockPos pos, Set<String> disabledPlayers, boolean isFullSync, boolean treatedAsVanilla);

    /**
     * Send spawner state to all players tracking a chunk
     * @param level The level
     * @param pos The spawner position
     * @param disabledPlayers Set of player names who have this spawner disabled
     * @param treatedAsVanilla Whether this spawner should be treated as vanilla (player-placed and config enabled)
     */
    public abstract void sendSpawnerStateToTracking(Level level, BlockPos pos, Set<String> disabledPlayers, boolean treatedAsVanilla);

    /**
     * Send all spawner states to a player (used on login/dimension change)
     */
    public abstract void sendAllSpawnerStatesToPlayer(ServerPlayer player);

    /**
     * Internal helper for batching multiple spawner updates into fewer packets.
     * @param player The player to send to
     * @param updates List of spawner updates to batch
     * @param isFullSync Whether this is a full sync
     */
    protected abstract void sendBatchedStatesToPlayer(ServerPlayer player, List<SpawnerStatePacket.SpawnerUpdate> updates, boolean isFullSync);

    /**
     * Sends spawner states for a specific chunk to a specific player.
     * Used by the chunk load/watch listeners to sync data as chunks stream in.
     */
    public abstract void sendChunkSpawnerStatesToPlayer(ServerPlayer player, LevelChunk chunk);
}
