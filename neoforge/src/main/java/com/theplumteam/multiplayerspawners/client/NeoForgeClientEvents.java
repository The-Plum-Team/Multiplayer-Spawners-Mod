package com.theplumteam.multiplayerspawners.client;

import com.theplumteam.multiplayerspawners.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public class NeoForgeClientEvents {

    /**
     * Clear client-side spawner data when disconnecting from a server
     */
    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        if (event.getPlayer() != null) {
            Constants.debug("CLIENT: Player disconnecting, clearing spawner data");
            ClientEventHandler.onClientDisconnect();
        }
    }

    /**
     * Clear client-side spawner data when logging into a new world
     */
    @SubscribeEvent
    public static void onClientConnect(ClientPlayerNetworkEvent.LoggingIn event) {
        if (event.getPlayer() != null) {
            Constants.debug("CLIENT: Player connecting, clearing old spawner data");
            ClientEventHandler.onClientConnect();
        }
    }

    /**
     * Handle client ticks for dimension change detection and periodic updates
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientEventHandler.onClientTick();
    }

    /**
     * Handle chunk loading to refresh spawner states
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        // Only handle client-side chunk loading
        if (event.getLevel().isClientSide() && event.getChunk() instanceof LevelChunk chunk) {
            Constants.debug("CLIENT: Chunk load event for {}", chunk.getPos());
            ClientEventHandler.onChunkLoad(chunk);
        }
    }

    /**
     * Handle chunk unloading to clean up spawner data
     */
    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        // Only handle client-side chunk unloading
        if (event.getLevel().isClientSide() && event.getChunk() instanceof LevelChunk chunk) {
            Constants.debug("CLIENT: Chunk unload event for {}", chunk.getPos());
            ClientEventHandler.onChunkUnload(chunk);
        }
    }

    /**
     * Handle player respawn events
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        // Only handle client-side respawn events
        if (event.getEntity().level().isClientSide() && event.getEntity() == Minecraft.getInstance().player) {
            Constants.debug("CLIENT: Player respawn event detected");
            ClientEventHandler.onPlayerRespawn();
        }
    }

    /**
     * Handle dimension change events
     */
    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        // Only handle client-side dimension changes
        if (event.getEntity().level().isClientSide() && event.getEntity() == Minecraft.getInstance().player) {
            Constants.debug("CLIENT: Player dimension change event detected from {} to {}",
                    event.getFrom(), event.getTo());
            ClientEventHandler.onLevelChange();
        }
    }
}
