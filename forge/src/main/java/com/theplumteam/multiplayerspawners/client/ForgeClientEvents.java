package com.theplumteam.multiplayerspawners.client;

import com.theplumteam.multiplayerspawners.Constants;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public class ForgeClientEvents {

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
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientEventHandler.onClientTick();
        }
    }

    /**
     * Handle chunk loading to refresh spawner states
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        // Only handle client-side chunk loading
        if (event.getLevel().isClientSide() && event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk) {
            Constants.debug("CLIENT: Chunk load event for {}, {}", chunk.getPos().x, chunk.getPos().z);
            ClientEventHandler.onChunkLoad(chunk);
        }
    }

    /**
     * Handle chunk unloading to clean up spawner data
     */
    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        // Only handle client-side chunk unloading
        if (event.getLevel().isClientSide() && event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk) {
            Constants.debug("CLIENT: Chunk unload event for {}, {}", chunk.getPos().x, chunk.getPos().z);
            ClientEventHandler.onChunkUnload(chunk);
        }
    }

    /**
     * Handle player respawn events
     */
    @SubscribeEvent
    public static void onPlayerRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
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
    public static void onPlayerChangeDimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        // Only handle client-side dimension changes
        if (event.getEntity().level().isClientSide() && event.getEntity() == Minecraft.getInstance().player) {
            Constants.debug("CLIENT: Player dimension change event detected from {} to {}",
                    event.getFrom().location(), event.getTo().location());
            ClientEventHandler.onLevelChange();
        }
    }
}