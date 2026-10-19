package com.theplumteam.multiplayerspawners;

import com.theplumteam.multiplayerspawners.event.CommandHandler;
import com.theplumteam.multiplayerspawners.event.PlayerEventHandler;
import com.theplumteam.multiplayerspawners.event.ServerLifecycleHandler;
import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import com.theplumteam.multiplayerspawners.util.SpawnerBreaking;
import com.theplumteam.multiplayerspawners.util.TorchTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
//? if >=26.1.2 {
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
//?} else {
/*import net.neoforged.neoforge.event.level.BlockEvent;
*///?}
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class NeoForgeServerEvents {

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

    /**
     * A block broken without the player's own mining, by another mod (an area tool's extra blocks, a
     * spell, a machine): a spawner gets what a hit gives it. After the other listeners, so that a
     * break a protection mod refused disables nothing.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    //? if >=26.1.2 {
    public static void onBlockBreak(BreakBlockEvent event) {
    //?} else {
    /*public static void onBlockBreak(BlockEvent.BreakEvent event) {
    *///?}
        if (event.getLevel() instanceof Level level && SpawnerBreaking.onModBreak(level, event.getPos(), event.getPlayer())) {
            event.setCanceled(true);
            //? if >=26.1.2 {
            event.setNotifyClient(true);
            //?}
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandHandler.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        TorchTracker.onServerTick(event.getServer());
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
     * Called when a chunk is loaded on the server: its spawner states go to the players watching it.
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
        LevelChunk chunk = event.getChunk();
        MinecraftServer server = event.getLevel().getServer();
        // Vanilla sends the chunk data now; ours goes on the next tick so that it arrives after it.
        //? if >=1.21.2 {
        server.schedule(new TickTask(server.getTickCount() + 1, () -> {
        //?} else {
        /*server.tell(new TickTask(server.getTickCount() + 1, () -> {
        *///?}
            if (player.connection != null) {
                NetworkHandler.getInstance().sendChunkSpawnerStatesToPlayer(player, chunk);
            }
        }));
    }
}
