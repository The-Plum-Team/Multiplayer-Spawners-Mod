package com.theplumteam.multiplayerspawners.network;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.client.ClientSpawnerData;
import com.theplumteam.multiplayerspawners.config.ModConfig;
import com.theplumteam.multiplayerspawners.util.SpawnerDataAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FabricNetworkHandler extends NetworkHandler {

    public static final ResourceLocation SPAWNER_STATE_PACKET_ID = new ResourceLocation(Constants.MOD_ID, "spawner_state");

    @Override
    public void init() {
        Constants.LOG.info("Fabric network handler initialized");
    }

    @Environment(EnvType.CLIENT)
    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(SPAWNER_STATE_PACKET_ID, (client, handler, buf, responseSender) -> {
            SpawnerStatePacket packet = new SpawnerStatePacket(buf);
            client.execute(() -> {
                handleClientPacket(packet);
            });
        });
        Constants.LOG.info("Fabric client network handler initialized with instant packet processing");
    }

    @Environment(EnvType.CLIENT)
    private static void handleClientPacket(SpawnerStatePacket packet) {
        for (SpawnerStatePacket.SpawnerUpdate update : packet.getUpdates()) {
            ClientSpawnerData.updateSpawnerState(update.pos, update.disabledPlayers, update.treatedAsVanilla);
        }
    }

    @Override
    public void sendSpawnerStateToPlayer(ServerPlayer player, BlockPos pos, Set<String> disabledPlayers, boolean isFullSync, boolean treatedAsVanilla) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        SpawnerStatePacket packet = new SpawnerStatePacket(pos, disabledPlayers, isFullSync, treatedAsVanilla);
        packet.write(buf);
        ServerPlayNetworking.send(player, SPAWNER_STATE_PACKET_ID, buf);
        // CRASH FIX: Removed threaded redundant send
    }

    @Override
    public void sendSpawnerStateToTracking(Level level, BlockPos pos, Set<String> disabledPlayers, boolean treatedAsVanilla) {
        if (level instanceof ServerLevel serverLevel) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            SpawnerStatePacket packet = new SpawnerStatePacket(pos, disabledPlayers, false, treatedAsVanilla);
            packet.write(buf);

            for (ServerPlayer player : PlayerLookup.tracking(serverLevel, pos)) {
                FriendlyByteBuf playerBuf = PacketByteBufs.create();
                packet.write(playerBuf);
                ServerPlayNetworking.send(player, SPAWNER_STATE_PACKET_ID, playerBuf);
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

            FriendlyByteBuf buf = PacketByteBufs.create();
            SpawnerStatePacket packet = new SpawnerStatePacket(batch, isFullSync);
            packet.write(buf);

            ServerPlayNetworking.send(player, SPAWNER_STATE_PACKET_ID, buf);
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
        List<SpawnerStatePacket.SpawnerUpdate> updates = collectSpawnerUpdates(chunk);

        if (!updates.isEmpty()) {
            sendBatchedStatesToPlayer(player, updates, true);
        }
    }

    /**
     * Sends spawner states for a chunk to all players tracking it.
     * Used by chunk load events.
     */
    public static void sendChunkSpawnerStatesToTracking(ServerLevel serverLevel, LevelChunk chunk) {
        List<SpawnerStatePacket.SpawnerUpdate> updates = collectSpawnerUpdates(chunk);

        if (!updates.isEmpty()) {
            // Send to all players tracking this chunk
            for (ServerPlayer player : PlayerLookup.tracking(serverLevel, chunk.getPos())) {
                // Split into chunks of 100 to avoid packet size limits
                int batchSize = 100;
                for (int i = 0; i < updates.size(); i += batchSize) {
                    int end = Math.min(updates.size(), i + batchSize);
                    List<SpawnerStatePacket.SpawnerUpdate> batch = updates.subList(i, end);

                    FriendlyByteBuf buf = PacketByteBufs.create();
                    SpawnerStatePacket packet = new SpawnerStatePacket(batch, false);
                    packet.write(buf);

                    ServerPlayNetworking.send(player, SPAWNER_STATE_PACKET_ID, buf);
                }
            }
        }
    }

    /**
     * Collects spawner updates from a chunk.
     */
    public static List<SpawnerStatePacket.SpawnerUpdate> collectSpawnerUpdates(LevelChunk chunk) {
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
