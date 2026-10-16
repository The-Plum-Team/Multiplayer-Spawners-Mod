package com.theplumteam.multiplayerspawners;

import com.theplumteam.multiplayerspawners.event.CommandHandler;
import com.theplumteam.multiplayerspawners.event.PlayerEventHandler;
import com.theplumteam.multiplayerspawners.event.ServerLifecycleHandler;
import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import com.theplumteam.multiplayerspawners.util.TorchTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.TickTask;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ForgeServerEvents {

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            PlayerEventHandler.onPlayerJoin(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            PlayerEventHandler.onPlayerChangeDimension(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            PlayerEventHandler.onPlayerRespawn(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandHandler.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            TorchTracker.onServerTick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            ServerLifecycleHandler.onLevelLoad(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        ServerLifecycleHandler.onServerStarting(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ServerLifecycleHandler.onServerStopping(event.getServer());
    }

    /**
     * Called when a chunk is loaded on the server.
     * We send spawner data to all players currently watching this chunk.
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && event.getChunk() instanceof LevelChunk chunk) {
            NetworkHandler.getInstance().sendChunkSpawnerStatesToTracking(serverLevel, chunk);
        }
    }

    /**
     * Called when a player starts watching a chunk.
     * We send spawner data to this specific player.
     * We delay it by one tick to ensure the Chunk Data packet (sent by vanilla) arrives first.
     */
    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        ServerPlayer player = event.getPlayer();
        ServerLevel serverLevel = event.getLevel();
        // Check if chunk is already loaded without forcing a load
        LevelChunk chunk = serverLevel.getChunkSource().getChunk(event.getPos().x, event.getPos().z, false);

        if (chunk != null) {
            // Chunk is loaded. Vanilla sends the chunk data immediately.
            // We schedule our packet for the next tick to ensure it arrives AFTER the chunk geometry.
            serverLevel.getServer().tell(new TickTask(serverLevel.getServer().getTickCount() + 1, () -> {
                if (player.connection != null) {
                    NetworkHandler.getInstance().sendChunkSpawnerStatesToPlayer(player, chunk);
                }
            }));
        }
        // If chunk is NOT loaded, do nothing.
        // When it loads later, onChunkLoad will fire and handle the sync.
    }
}