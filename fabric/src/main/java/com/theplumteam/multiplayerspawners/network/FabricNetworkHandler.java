package com.theplumteam.multiplayerspawners.network;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.client.ClientSpawnerData;
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

import java.util.Set;

public class FabricNetworkHandler extends NetworkHandler {

    public static final ResourceLocation SPAWNER_STATE_PACKET_ID = new ResourceLocation(Constants.MOD_ID, "spawner_state");

    @Override
    public void init() {
        // Server-side initialization is handled when sending packets
        Constants.LOG.info("Fabric network handler initialized");
    }

    @Environment(EnvType.CLIENT)
    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(SPAWNER_STATE_PACKET_ID, (client, handler, buf, responseSender) -> {
            SpawnerStatePacket packet = new SpawnerStatePacket(buf);
            // Execute immediately on main thread for instant visual updates
            client.execute(() -> {
                handleClientPacket(packet);
            });
        });
        Constants.LOG.info("Fabric client network handler initialized with instant packet processing");
    }

    @Environment(EnvType.CLIENT)
    private static void handleClientPacket(SpawnerStatePacket packet) {
        Constants.LOG.debug("CLIENT: Received spawner state packet for pos {} with disabled players: {}",
                packet.getPos(), packet.getDisabledPlayers());

        // Get the previous state before updating
        boolean wasDisabled = ClientSpawnerData.isSpawnerDisabledForCurrentPlayer(packet.getPos());

        // Update the spawner state
        ClientSpawnerData.updateSpawnerState(packet.getPos(), packet.getDisabledPlayers());

        // Check if this is a new disable event (was not disabled, now is disabled)
        boolean isNowDisabled = ClientSpawnerData.isSpawnerDisabledForCurrentPlayer(packet.getPos());

        // IMMEDIATE VISUAL UPDATE: For any state change, force immediate visual update
        if (wasDisabled != isNowDisabled) {
            Constants.LOG.info("CLIENT: Spawner at {} state changed - forcing IMMEDIATE visual update", packet.getPos());
            // Force immediate visual update with maximum priority
            ClientSpawnerData.forceImmediateVisualUpdate(packet.getPos());
        } else if (isNowDisabled) {
            // This is a duplicate packet for an already disabled spawner - still ensure visual state is correct
            Constants.LOG.debug("CLIENT: Ensuring visual state is correct for already disabled spawner at {}", packet.getPos());
            ClientSpawnerData.forceImmediateVisualUpdate(packet.getPos());
        }
    }

    @Override
    public void sendSpawnerStateToPlayer(ServerPlayer player, BlockPos pos, Set<String> disabledPlayers, boolean isFullSync) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        SpawnerStatePacket packet = new SpawnerStatePacket(pos, disabledPlayers, isFullSync);
        packet.write(buf);

        Constants.LOG.info("SERVER: Sending PRIORITY spawner state packet to player {} for pos {} with disabled players: {}",
                player.getName().getString(), pos, disabledPlayers);

        // Send with high priority for immediate delivery
        ServerPlayNetworking.send(player, SPAWNER_STATE_PACKET_ID, buf);
    }

    @Override
    public void sendSpawnerStateToTracking(Level level, BlockPos pos, Set<String> disabledPlayers) {
        if (level instanceof ServerLevel serverLevel) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            SpawnerStatePacket packet = new SpawnerStatePacket(pos, disabledPlayers, false);
            packet.write(buf);

            Constants.LOG.debug("SERVER: Sending spawner state to all tracking players for pos {}", pos);

            // Send to all players tracking this position
            for (ServerPlayer player : PlayerLookup.tracking(serverLevel, pos)) {
                ServerPlayNetworking.send(player, SPAWNER_STATE_PACKET_ID, buf);
            }
        }
    }

    @Override
    public void sendAllSpawnerStatesToPlayer(ServerPlayer player) {
        if (player.level() instanceof ServerLevel serverLevel) {
            Constants.LOG.info("SERVER: Sending all spawner states to player {} in dimension {}",
                    player.getName().getString(), serverLevel.dimension().location());

            // Get the view distance to determine which chunks to check
            int viewDistance = serverLevel.getServer().getPlayerList().getViewDistance();
            BlockPos playerPos = player.blockPosition();
            int chunkX = playerPos.getX() >> 4;
            int chunkZ = playerPos.getZ() >> 4;

            final int[] spawnersFound = {0}; // Use array to work with lambda

            // Iterate through chunks within view distance
            for (int dx = -viewDistance; dx <= viewDistance; dx++) {
                for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                    LevelChunk chunk = serverLevel.getChunk(chunkX + dx, chunkZ + dz);
                    if (chunk != null) {
                        chunk.getBlockEntities().forEach((pos, blockEntity) -> {
                            if (blockEntity instanceof SpawnerBlockEntity spawnerBlockEntity) {
                                SpawnerDataAccessor accessor = (SpawnerDataAccessor) spawnerBlockEntity.getSpawner();
                                Set<String> disabledPlayers = accessor.multiplayerspawners$getDisabledPlayers();
                                if (!disabledPlayers.isEmpty()) {
                                    sendSpawnerStateToPlayer(player, pos, disabledPlayers, true);
                                    spawnersFound[0]++;
                                }
                            }
                        });
                    }
                }
            }

            if (spawnersFound[0] > 0) {
                Constants.LOG.info("SERVER: Sent {} spawner states to player {}", spawnersFound[0], player.getName().getString());
            }
        }
    }
}