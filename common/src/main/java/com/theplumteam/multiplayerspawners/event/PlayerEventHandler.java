package com.theplumteam.multiplayerspawners.event;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;

/**
 * Handles player events that require spawner state synchronization
 */
public class PlayerEventHandler {

    /**
     * Called when a player joins the world
     */
    public static void onPlayerJoin(ServerPlayer player) {
        Constants.LOG.debug("Player {} joined, syncing spawner states", player.getName().getString());
        // Send all spawner states to the joining player
        NetworkHandler.getInstance().sendAllSpawnerStatesToPlayer(player);
    }

    /**
     * Called when a player changes dimension
     */
    public static void onPlayerChangeDimension(ServerPlayer player) {
        Constants.LOG.debug("Player {} changed dimension, syncing spawner states", player.getName().getString());
        // Send all spawner states in the new dimension
        NetworkHandler.getInstance().sendAllSpawnerStatesToPlayer(player);
    }

    /**
     * Called when a player respawns
     */
    public static void onPlayerRespawn(ServerPlayer player) {
        Constants.LOG.debug("Player {} respawned, syncing spawner states", player.getName().getString());
        // Send all spawner states after respawn
        NetworkHandler.getInstance().sendAllSpawnerStatesToPlayer(player);
    }
}