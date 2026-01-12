package com.theplumteam.multiplayerspawners.network;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.client.ClientSpawnerData;
import com.theplumteam.multiplayerspawners.config.ModConfig;
import com.theplumteam.multiplayerspawners.util.SpawnerDataAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class ForgeNetworkHandler extends NetworkHandler {

    // Bump protocol version because packet structure changed
    private static final String PROTOCOL_VERSION = "4";
    private static SimpleChannel CHANNEL;

    @Override
    public void init() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(Constants.MOD_ID, "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );

        CHANNEL.registerMessage(
                0,
                SpawnerStatePacket.class,
                SpawnerStatePacket::write,
                SpawnerStatePacket::new,
                ForgeNetworkHandler::handleSpawnerStatePacket
        );

        Constants.LOG.info("Forge network handler initialized");
    }

    private static void handleSpawnerStatePacket(SpawnerStatePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                handleClientPacket(packet);
            });
        });
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClientPacket(SpawnerStatePacket packet) {
        // Iterate through the batched updates
        for (SpawnerStatePacket.SpawnerUpdate update : packet.getUpdates()) {
            Constants.LOG.debug("CLIENT: Received update for pos {}", update.pos);
            ClientSpawnerData.updateSpawnerState(update.pos, update.disabledPlayers, update.treatedAsVanilla);
        }
    }

    @Override
    public void sendSpawnerStateToPlayer(ServerPlayer player, BlockPos pos, Set<String> disabledPlayers, boolean isFullSync, boolean treatedAsVanilla) {
        if (CHANNEL != null) {
            SpawnerStatePacket packet = new SpawnerStatePacket(pos, disabledPlayers, isFullSync, treatedAsVanilla);
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
            // CRASH FIX: Removed the "new Thread()" redundant packet logic.
            // The batching system below ensures reliability without crashing the OS.
        }
    }

    @Override
    public void sendSpawnerStateToTracking(Level level, BlockPos pos, Set<String> disabledPlayers, boolean treatedAsVanilla) {
        if (CHANNEL != null && level instanceof ServerLevel serverLevel) {
            LevelChunk chunk = serverLevel.getChunkAt(pos);
            SpawnerStatePacket packet = new SpawnerStatePacket(pos, disabledPlayers, false, treatedAsVanilla);
            CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), packet);
        }
    }

    @Override
    protected void sendBatchedStatesToPlayer(ServerPlayer player, List<SpawnerStatePacket.SpawnerUpdate> updates, boolean isFullSync) {
        if (updates.isEmpty()) return;

        // Split into chunks of 100 to avoid packet size limits
        int batchSize = 100;
        for (int i = 0; i < updates.size(); i += batchSize) {
            int end = Math.min(updates.size(), i + batchSize);
            List<SpawnerStatePacket.SpawnerUpdate> batch = updates.subList(i, end);
            SpawnerStatePacket packet = new SpawnerStatePacket(batch, isFullSync);
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    @Override
    public void sendAllSpawnerStatesToPlayer(ServerPlayer player) {
        if (player.level() instanceof ServerLevel serverLevel) {
            Constants.LOG.info("SERVER: Collecting spawner states for player batch sync...");

            int viewDistance = serverLevel.getServer().getPlayerList().getViewDistance();
            BlockPos playerPos = player.blockPosition();
            int chunkX = playerPos.getX() >> 4;
            int chunkZ = playerPos.getZ() >> 4;

            List<SpawnerStatePacket.SpawnerUpdate> updates = new ArrayList<>();

            for (int dx = -viewDistance; dx <= viewDistance; dx++) {
                for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                    LevelChunk chunk = serverLevel.getChunk(chunkX + dx, chunkZ + dz);
                    if (chunk != null) {
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
                    }
                }
            }

            if (!updates.isEmpty()) {
                Constants.LOG.info("SERVER: Sending {} spawner states in batches to player {}", updates.size(), player.getName().getString());
                sendBatchedStatesToPlayer(player, updates, true);
            }
        }
    }
}
