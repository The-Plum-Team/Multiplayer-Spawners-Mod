package com.theplumteam.multiplayerspawners.network;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.client.ClientSpawnerData;
import com.theplumteam.multiplayerspawners.config.ModConfig;
import com.theplumteam.multiplayerspawners.platform.Services;
import com.theplumteam.multiplayerspawners.util.OneShotData;
import com.theplumteam.multiplayerspawners.util.OneShotSpawners;
import com.theplumteam.multiplayerspawners.util.SpawnerDataAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Spawner state sync. This class decides what to send; each loader only moves packets.
 */
public abstract class NetworkHandler {

    /** Updates per packet, so that a chunk full of spawners stays well below the packet size limit. */
    private static final int BATCH_SIZE = 100;

    private static NetworkHandler INSTANCE;

    public static NetworkHandler getInstance() {
        if (INSTANCE == null) {
            INSTANCE = Services.PLATFORM.createNetworkHandler();
        }
        return INSTANCE;
    }

    /** Registers the packet with the loader. */
    public abstract void init();

    protected abstract void sendToPlayer(ServerPlayer player, SpawnerStatePacket packet);

    /**
     * Sends one spawner's state to one player.
     *
     * @param treatedAsVanilla whether the spawner is player-placed and the config treats those as vanilla
     */
    public void sendSpawnerStateToPlayer(ServerPlayer player, BlockPos pos, Set<String> disabledPlayers, boolean isFullSync, boolean treatedAsVanilla) {
        sendToPlayer(player, SpawnerStatePacket.single(pos, disabledPlayers, isFullSync, treatedAsVanilla));
    }

    /**
     * Sends one spawner's state to every player tracking its chunk, each with only what concerns
     * them.
     */
    public void sendSpawnerStateToTracking(Level level, BlockPos pos, Set<String> disabledPlayers, boolean treatedAsVanilla) {
        if (level instanceof ServerLevel serverLevel) {
            for (ServerPlayer player : tracking(serverLevel, pos)) {
                sendToPlayer(player, SpawnerStatePacket.single(pos, forPlayer(disabledPlayers, player), false, treatedAsVanilla));
            }
        }
    }

    /**
     * Sends who a single-use spawner is used up for to every player tracking its chunk.
     */
    public void sendOneShotStateToTracking(Level level, BlockPos pos, Set<String> usedBy) {
        if (level instanceof ServerLevel serverLevel) {
            for (ServerPlayer player : tracking(serverLevel, pos)) {
                List<SpawnerStatePacket.SpawnerUpdate> updates = new ArrayList<>(1);
                updates.add(new SpawnerStatePacket.SpawnerUpdate(pos, forPlayer(usedBy, player), false, true));
                sendToPlayer(player, new SpawnerStatePacket(updates, false));
            }
        }
    }

    /**
     * Sends the state of every spawner within view distance (login, respawn, dimension change).
     */
    public void sendAllSpawnerStatesToPlayer(ServerPlayer player) {
        if (player.level() instanceof ServerLevel serverLevel) {
            int viewDistance = serverLevel.getServer().getPlayerList().getViewDistance();
            BlockPos playerPos = player.blockPosition();
            int chunkX = playerPos.getX() >> 4;
            int chunkZ = playerPos.getZ() >> 4;
            for (int dx = -viewDistance; dx <= viewDistance; dx++) {
                for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                    // Only chunks that are already loaded: this must never load one.
                    LevelChunk chunk = serverLevel.getChunkSource().getChunk(chunkX + dx, chunkZ + dz, false);
                    if (chunk != null) {
                        sendChunkSpawnerStatesToPlayer(player, chunk);
                    }
                }
            }
        }
    }

    /**
     * Sends the state of the spawners of one chunk, as the chunk streams to a player.
     */
    public void sendChunkSpawnerStatesToPlayer(ServerPlayer player, LevelChunk chunk) {
        sendChunkUpdates(player, collectSpawnerUpdates(chunk));
    }

    /**
     * Sends the state of the spawners of one chunk to every player tracking it.
     */
    public void sendChunkSpawnerStatesToTracking(ServerLevel level, LevelChunk chunk) {
        List<ServerPlayer> players = tracking(level, new BlockPos(chunk.getPos().getMinBlockX(), 0, chunk.getPos().getMinBlockZ()));
        if (players.isEmpty()) {
            return;
        }
        List<SpawnerStatePacket.SpawnerUpdate> updates = collectSpawnerUpdates(chunk);
        for (ServerPlayer player : players) {
            sendChunkUpdates(player, updates);
        }
    }

    /**
     * Sends a chunk's updates to one player: only the spawners that are not in their default state
     * for them (the client has none of the chunk's spawners when the chunk arrives, so the others
     * need nothing), with only their own name, in batches.
     */
    private void sendChunkUpdates(ServerPlayer player, List<SpawnerStatePacket.SpawnerUpdate> updates) {
        List<SpawnerStatePacket.SpawnerUpdate> forPlayer = new ArrayList<>();
        for (SpawnerStatePacket.SpawnerUpdate update : updates) {
            Set<String> names = forPlayer(update.disabledPlayers(), player);
            if (!names.isEmpty() || update.treatedAsVanilla()) {
                forPlayer.add(new SpawnerStatePacket.SpawnerUpdate(update.pos(), names, update.treatedAsVanilla(), update.oneShot()));
            }
        }
        for (int i = 0; i < forPlayer.size(); i += BATCH_SIZE) {
            List<SpawnerStatePacket.SpawnerUpdate> batch = new ArrayList<>(forPlayer.subList(i, Math.min(forPlayer.size(), i + BATCH_SIZE)));
            sendToPlayer(player, new SpawnerStatePacket(batch, true));
        }
    }

    /**
     * The players whose client has the chunk of {@code pos}, without asking the level for that
     * chunk: this also runs from the chunk's own load event, and looking the loading chunk up from
     * there waits for itself forever.
     */
    private static List<ServerPlayer> tracking(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().chunkMap.getPlayers(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4), false);
    }

    /**
     * What a client needs of a list of player names: only whether its own player is in it. The
     * names of the others stay on the server.
     */
    private static Set<String> forPlayer(Set<String> names, ServerPlayer player) {
        String name = player.getName().getString();
        return names.contains(name) ? Set.of(name) : Set.of();
    }

    /**
     * The spawners of a chunk whose state differs from the default (disabled by someone, or vanilla).
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
                    updates.add(new SpawnerStatePacket.SpawnerUpdate(pos, Set.copyOf(disabledPlayers), treatedAsVanilla));
                }
            } else if (blockEntity instanceof OneShotData oneShot && OneShotSpawners.isOneShot(blockEntity.getBlockState())
                    && !oneShot.multiplayerspawners$getUsedBy().isEmpty()) {
                updates.add(new SpawnerStatePacket.SpawnerUpdate(pos, Set.copyOf(oneShot.multiplayerspawners$getUsedBy()), false, true));
            }
        });
        return updates;
    }

    /**
     * Applies a received packet. Runs on the client thread.
     */
    public static void handleOnClient(SpawnerStatePacket packet) {
        Constants.debug("CLIENT: Received spawner state packet with {} updates, isFullSync: {}",
                packet.updates().size(), packet.isFullSync());
        for (SpawnerStatePacket.SpawnerUpdate update : packet.updates()) {
            if (update.oneShot()) {
                ClientSpawnerData.updateOneShotState(update.pos(), update.disabledPlayers());
            } else {
                ClientSpawnerData.updateSpawnerState(update.pos(), update.disabledPlayers(), update.treatedAsVanilla());
            }
        }
    }
}
