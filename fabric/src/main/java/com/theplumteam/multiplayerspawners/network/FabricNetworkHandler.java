// FabricNetworkHandler.java
package com.theplumteam.multiplayerspawners.network;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.client.ClientSpawnerData;
import com.theplumteam.multiplayerspawners.config.ModConfig;
import com.theplumteam.multiplayerspawners.util.SpawnerDataAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FabricNetworkHandler extends NetworkHandler {

    @Override
    public void init() {
        PayloadTypeRegistry.playS2C().register(SpawnerStatePayload.TYPE, SpawnerStatePayload.STREAM_CODEC);
        Constants.debug("Fabric network handler initialized");
    }

    @Environment(EnvType.CLIENT)
    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(SpawnerStatePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                handleClientPacket(payload);
            });
        });
        Constants.debug("Fabric client network handler initialized with payload processing");
    }

    @Environment(EnvType.CLIENT)
    private static void handleClientPacket(SpawnerStatePayload payload) {
        for (SpawnerStatePacket.SpawnerUpdate update : payload.updates()) {
            ClientSpawnerData.updateSpawnerState(update.pos, update.disabledPlayers, update.treatedAsVanilla);
        }
    }

    @Override
    public void sendSpawnerStateToPlayer(ServerPlayer player, BlockPos pos, Set<String> disabledPlayers, boolean isFullSync, boolean treatedAsVanilla) {
        List<SpawnerStatePacket.SpawnerUpdate> updates = new ArrayList<>();
        updates.add(new SpawnerStatePacket.SpawnerUpdate(pos, new HashSet<>(disabledPlayers), treatedAsVanilla));
        SpawnerStatePayload payload = new SpawnerStatePayload(updates, isFullSync);
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendSpawnerStateToTracking(Level level, BlockPos pos, Set<String> disabledPlayers, boolean treatedAsVanilla) {
        if (level instanceof ServerLevel serverLevel) {
            List<SpawnerStatePacket.SpawnerUpdate> updates = new ArrayList<>();
            updates.add(new SpawnerStatePacket.SpawnerUpdate(pos, new HashSet<>(disabledPlayers), treatedAsVanilla));
            SpawnerStatePayload payload = new SpawnerStatePayload(updates, false);

            for (ServerPlayer player : PlayerLookup.tracking(serverLevel, pos)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    @Override
    protected void sendBatchedStatesToPlayer(ServerPlayer player, List<SpawnerStatePacket.SpawnerUpdate> updates, boolean isFullSync) {
        if (updates.isEmpty()) return;

        int batchSize = 100;
        for (int i = 0; i < updates.size(); i += batchSize) {
            int end = Math.min(updates.size(), i + batchSize);
            List<SpawnerStatePacket.SpawnerUpdate> batch = updates.subList(i, end);
            SpawnerStatePayload payload = new SpawnerStatePayload(batch, isFullSync);
            ServerPlayNetworking.send(player, payload);
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
