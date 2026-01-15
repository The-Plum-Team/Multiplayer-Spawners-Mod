// NeoForgeServerEvents.java
package com.theplumteam.multiplayerspawners;

import com.theplumteam.multiplayerspawners.event.CommandHandler;
import com.theplumteam.multiplayerspawners.event.PlayerEventHandler;
import com.theplumteam.multiplayerspawners.event.ServerLifecycleHandler;
import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import com.theplumteam.multiplayerspawners.network.SpawnerStatePacket;
import com.theplumteam.multiplayerspawners.network.SpawnerStatePayload;
import com.theplumteam.multiplayerspawners.config.ModConfig;
import com.theplumteam.multiplayerspawners.util.SpawnerDataAccessor;
import com.theplumteam.multiplayerspawners.util.TorchTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = Constants.MOD_ID)
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

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandHandler.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        TorchTracker.onServerTick(event.getServer());
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            ServerLifecycleHandler.onLevelLoad(serverLevel);
        }
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
        if (!event.getLevel().isClientSide() && event.getChunk() instanceof LevelChunk chunk && event.getLevel() instanceof ServerLevel serverLevel) {
            List<SpawnerStatePacket.SpawnerUpdate> updates = collectSpawnerUpdates(chunk);
            if (!updates.isEmpty()) {
                SpawnerStatePayload payload = new SpawnerStatePayload(updates, false);
                PacketDistributor.sendToPlayersTrackingChunk(serverLevel, chunk.getPos(), payload);
            }
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
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            // Check if chunk is already loaded without forcing a load
            LevelChunk chunk = serverLevel.getChunkSource().getChunk(event.getPos().x, event.getPos().z, false);

            if (chunk != null) {
                // Chunk is loaded. Vanilla sends the chunk data immediately.
                // We schedule our packet for the next tick to ensure it arrives AFTER the chunk geometry.
                serverLevel.getServer().tell(new net.minecraft.server.TickTask(serverLevel.getServer().getTickCount() + 1, () -> {
                    if (player.connection != null) {
                        NetworkHandler.getInstance().sendChunkSpawnerStatesToPlayer(player, chunk);
                    }
                }));
            }
            // If chunk is NOT loaded, do nothing.
            // When it loads later, onChunkLoad will fire and handle the sync.
        }
    }

    private static List<SpawnerStatePacket.SpawnerUpdate> collectSpawnerUpdates(LevelChunk chunk) {
        List<SpawnerStatePacket.SpawnerUpdate> updates = new ArrayList<>();
        chunk.getBlockEntities().forEach((pos, blockEntity) -> {
            if (blockEntity instanceof SpawnerBlockEntity spawnerBlockEntity) {
                SpawnerDataAccessor accessor = (SpawnerDataAccessor) spawnerBlockEntity.getSpawner();
                Set<String> disabledPlayers = accessor.multiplayerspawners$getDisabledPlayers();

                boolean treatedAsVanilla = accessor.multiplayerspawners$isPlayerPlaced()
                        && ModConfig.SERVER.treatPlacedSpawnersAsVanilla.get();

                if (!disabledPlayers.isEmpty() || treatedAsVanilla) {
                    updates.add(new SpawnerStatePacket.SpawnerUpdate(pos, disabledPlayers, treatedAsVanilla));
                }
            }
        });
        return updates;
    }
}
