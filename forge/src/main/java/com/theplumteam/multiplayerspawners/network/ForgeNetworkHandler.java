package com.theplumteam.multiplayerspawners.network;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.client.ClientSpawnerData;
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

import java.util.Set;
import java.util.function.Supplier;

public class ForgeNetworkHandler extends NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";
    private static SimpleChannel CHANNEL;

    @Override
    public void init() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(Constants.MOD_ID, "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );

        // Register the spawner state packet
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
            // This runs on the client with immediate execution
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                handleClientPacket(packet);
            });
        });
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClientPacket(SpawnerStatePacket packet) {
        if (!packet.isChecksumValid()) {
            Constants.LOG.warn("CLIENT: Received corrupted spawner state packet for pos {}", packet.getPos());
            return;
        }

        Constants.LOG.debug("CLIENT: Received spawner state packet for pos {} with disabled players: {}",
                packet.getPos(), packet.getDisabledPlayers());

        // This single call now handles both the data update and the necessary visual update.
        ClientSpawnerData.updateSpawnerState(packet.getPos(), packet.getDisabledPlayers());
    }

    @Override
    public void sendSpawnerStateToPlayer(ServerPlayer player, BlockPos pos, Set<String> disabledPlayers, boolean isFullSync) {
        if (CHANNEL != null) {
            Constants.LOG.info("SERVER: Sending PRIORITY spawner state packet to player {} for pos {} with disabled players: {}",
                    player.getName().getString(), pos, disabledPlayers);
            SpawnerStatePacket packet = new SpawnerStatePacket(pos, disabledPlayers, isFullSync);

            // Send with high priority for immediate delivery
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);

            // Send a redundant packet after a short delay
            new Thread(() -> {
                try {
                    Thread.sleep(100);
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }).start();
        } else {
            Constants.LOG.warn("SERVER: Cannot send packet - CHANNEL is null");
        }
    }

    @Override
    public void sendSpawnerStateToTracking(Level level, BlockPos pos, Set<String> disabledPlayers) {
        if (CHANNEL != null && level instanceof ServerLevel serverLevel) {
            LevelChunk chunk = serverLevel.getChunkAt(pos);
            SpawnerStatePacket packet = new SpawnerStatePacket(pos, disabledPlayers, false);

            Constants.LOG.debug("SERVER: Sending spawner state to all tracking players for pos {} in chunk {}, {}",
                    pos, chunk.getPos().x, chunk.getPos().z);
            CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), packet);
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

            int spawnersFound = 0;

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
                                }
                            }
                        });
                    }
                }
            }

            if (spawnersFound > 0) {
                Constants.LOG.info("SERVER: Sent {} spawner states to player {}", spawnersFound, player.getName().getString());
            }
        }
    }
}