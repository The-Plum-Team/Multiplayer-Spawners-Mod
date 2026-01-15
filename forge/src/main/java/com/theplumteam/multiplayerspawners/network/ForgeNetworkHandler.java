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

        Constants.debug("Forge network handler initialized");
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
            Constants.debug("CLIENT: Received update for pos {}", update.pos);
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
        if (CHANNEL == null) return;

        List<SpawnerStatePacket.SpawnerUpdate> updates = collectSpawnerUpdates(chunk);

        if (!updates.isEmpty()) {
            sendBatchedStatesToPlayer(player, updates, true);
        }
    }

    /**
     * Sends spawner states for a chunk to all players tracking it.
     * Used by chunk load events.
     */
    public static void sendChunkSpawnerStatesToTracking(LevelChunk chunk) {
        if (CHANNEL == null) return;

        List<SpawnerStatePacket.SpawnerUpdate> updates = collectSpawnerUpdates(chunk);

        if (!updates.isEmpty()) {
            // Split into chunks of 100 to avoid packet size limits
            int batchSize = 100;
            for (int i = 0; i < updates.size(); i += batchSize) {
                int end = Math.min(updates.size(), i + batchSize);
                List<SpawnerStatePacket.SpawnerUpdate> batch = updates.subList(i, end);
                SpawnerStatePacket packet = new SpawnerStatePacket(batch, false);
                CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), packet);
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
