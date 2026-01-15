// NeoForgeNetworkHandler.java
package com.theplumteam.multiplayerspawners.network;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.client.ClientSpawnerData;
import com.theplumteam.multiplayerspawners.config.ModConfig;
import com.theplumteam.multiplayerspawners.util.SpawnerDataAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class NeoForgeNetworkHandler extends NetworkHandler {

    private static boolean initialized = false;

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Constants.MOD_ID).versioned("4");
        registrar.playToClient(
                SpawnerStatePayload.TYPE,
                SpawnerStatePayload.STREAM_CODEC,
                NeoForgeNetworkHandler::handleClientPayload
        );
        initialized = true;
        Constants.debug("NeoForge network handler registered payloads");
    }

    private static void handleClientPayload(SpawnerStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            handleClientPacket(payload);
        });
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClientPacket(SpawnerStatePayload payload) {
        Constants.debug("CLIENT: Received spawner state packet with {} updates, isFullSync: {}",
                payload.updates().size(), payload.isFullSync());
        // Iterate through the batched updates
        for (SpawnerStatePacket.SpawnerUpdate update : payload.updates()) {
            Constants.debug("CLIENT: Processing update for pos {}, disabledPlayers: {}, treatedAsVanilla: {}",
                    update.pos, update.disabledPlayers, update.treatedAsVanilla);
            ClientSpawnerData.updateSpawnerState(update.pos, update.disabledPlayers, update.treatedAsVanilla);
        }
    }

    @Override
    public void init() {
        // Initialization is done via the event bus subscriber
        Constants.debug("NeoForge network handler initialized");
    }

    @Override
    public void sendSpawnerStateToPlayer(ServerPlayer player, BlockPos pos, Set<String> disabledPlayers, boolean isFullSync, boolean treatedAsVanilla) {
        if (initialized) {
            List<SpawnerStatePacket.SpawnerUpdate> updates = new ArrayList<>();
            updates.add(new SpawnerStatePacket.SpawnerUpdate(pos, new HashSet<>(disabledPlayers), treatedAsVanilla));
            SpawnerStatePayload payload = new SpawnerStatePayload(updates, isFullSync);
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    @Override
    public void sendSpawnerStateToTracking(Level level, BlockPos pos, Set<String> disabledPlayers, boolean treatedAsVanilla) {
        if (initialized && level instanceof ServerLevel serverLevel) {
            LevelChunk chunk = serverLevel.getChunkAt(pos);
            List<SpawnerStatePacket.SpawnerUpdate> updates = new ArrayList<>();
            updates.add(new SpawnerStatePacket.SpawnerUpdate(pos, new HashSet<>(disabledPlayers), treatedAsVanilla));
            SpawnerStatePayload payload = new SpawnerStatePayload(updates, false);
            PacketDistributor.sendToPlayersTrackingChunk(serverLevel, chunk.getPos(), payload);
        }
    }

    @Override
    protected void sendBatchedStatesToPlayer(ServerPlayer player, List<SpawnerStatePacket.SpawnerUpdate> updates, boolean isFullSync) {
        if (updates.isEmpty() || !initialized) return;

        // Split into chunks of 100 to avoid packet size limits
        int batchSize = 100;
        for (int i = 0; i < updates.size(); i += batchSize) {
            int end = Math.min(updates.size(), i + batchSize);
            List<SpawnerStatePacket.SpawnerUpdate> batch = updates.subList(i, end);
            SpawnerStatePayload payload = new SpawnerStatePayload(batch, isFullSync);
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    @Override
    public void sendAllSpawnerStatesToPlayer(ServerPlayer player) {
        if (player.level() instanceof ServerLevel serverLevel) {
            int viewDistance = serverLevel.getServer().getPlayerList().getViewDistance();
            BlockPos playerPos = player.blockPosition();
            int chunkX = playerPos.getX() >> 4;
            int chunkZ = playerPos.getZ() >> 4;

            for (int dx = -viewDistance; dx <= viewDistance; dx++) {
                for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                    // Safe getChunk that doesn't force load
                    LevelChunk chunk = serverLevel.getChunkSource().getChunk(chunkX + dx, chunkZ + dz, false);
                    if (chunk != null) {
                        sendChunkSpawnerStatesToPlayer(player, chunk);
                    }
                }
            }
        }
    }

    @Override
    public void sendChunkSpawnerStatesToPlayer(ServerPlayer player, LevelChunk chunk) {
        if (!initialized) return;

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

        if (!updates.isEmpty()) {
            sendBatchedStatesToPlayer(player, updates, true);
        }
    }
}
