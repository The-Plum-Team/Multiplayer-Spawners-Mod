package com.theplumteam.multiplayerspawners.network;

import com.theplumteam.multiplayerspawners.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

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
     */
    public abstract void sendSpawnerStateToPlayer(ServerPlayer player, BlockPos pos, Set<String> disabledPlayers, boolean isFullSync);

    /**
     * Send spawner state to all players tracking a chunk
     */
    public abstract void sendSpawnerStateToTracking(Level level, BlockPos pos, Set<String> disabledPlayers);

    /**
     * Send all spawner states to a player (used on login/dimension change)
     */
    public abstract void sendAllSpawnerStatesToPlayer(ServerPlayer player);
}