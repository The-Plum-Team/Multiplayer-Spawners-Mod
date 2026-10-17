package com.theplumteam.multiplayerspawners.client;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.block.SpawnerStateProperties;
import com.theplumteam.multiplayerspawners.registration.ModBlocksItems;
import com.theplumteam.multiplayerspawners.util.OneShotSpawners;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * What the server told this client about the spawners of the chunks it sees, and the look each
 * spawner gets from it: the mod's active look, or the vanilla one when it is disabled for the
 * current player or treated as vanilla. A chunk's entries are forgotten when the chunk unloads.
 */
public class ClientSpawnerData {

    private static final Map<BlockPos, Boolean> playerDisabledSpawners = new HashMap<>();
    private static final Set<BlockPos> spawnersNeedingUpdate = new HashSet<>();
    private static final Set<BlockPos> vanillaTreatedSpawners = new HashSet<>();
    /** Single-use spawners of other mods that are used up for the current player. */
    private static final Set<BlockPos> usedUpOneShots = new HashSet<>();
    /** The blocks the client replaced with their used-up look, to put back if that changes. */
    private static final Map<BlockPos, BlockState> replacedByUsedLook = new HashMap<>();

    /** Quark's monster box, which gets a used-up look (multiplayerspawners:used_monster_box). */
    private static final String MONSTER_BOX = "quark:monster_box";

    private static String cachedPlayerName = null;
    private static String currentDimension = null;
    private static long lastPeriodicRefresh = 0;

    public static void updateSpawnerState(BlockPos pos, Set<String> disabledPlayers, boolean treatedAsVanilla) {
        String currentPlayerName = getCurrentPlayerName();
        if (currentPlayerName == null) return;

        pos = pos.immutable();
        boolean isDisabledForPlayer = disabledPlayers.contains(currentPlayerName);
        playerDisabledSpawners.put(pos, isDisabledForPlayer);
        if (treatedAsVanilla) {
            vanillaTreatedSpawners.add(pos);
        } else {
            vanillaTreatedSpawners.remove(pos);
        }

        Constants.debug("CLIENT: Spawner at {} updated - disabled: {}, treatedAsVanilla: {}", pos, isDisabledForPlayer, treatedAsVanilla);

        if (applyLook(pos)) {
            spawnersNeedingUpdate.remove(pos);
        } else {
            // Its chunk or block has not arrived yet: applied when it does.
            spawnersNeedingUpdate.add(pos);
        }
    }

    /** Whether the spawner at {@code pos} must show the mod's active look for the current player. */
    private static boolean shouldLookActive(BlockPos pos) {
        return !vanillaTreatedSpawners.contains(pos) && !playerDisabledSpawners.getOrDefault(pos, false);
    }

    /**
     * Gives the spawner at {@code pos} the look its state asks for. Returns false when there is no
     * spawner there on this client yet; a spawner block that arrives later gets its look from
     * {@code LevelChunkMixin} or {@link #onChunkLoaded}.
     */
    private static boolean applyLook(BlockPos pos) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.isLoaded(pos)) return false;

        BlockState currentState = level.getBlockState(pos);
        if (!(currentState.getBlock() instanceof SpawnerBlock) || !currentState.hasProperty(SpawnerStateProperties.MULTIPLAYER_ACTIVATED)) {
            return false;
        }
        boolean activated = shouldLookActive(pos);
        if (currentState.getValue(SpawnerStateProperties.MULTIPLAYER_ACTIVATED) != activated) {
            // Flag 3 updates the block and redraws it.
            level.setBlock(pos, currentState.setValue(SpawnerStateProperties.MULTIPLAYER_ACTIVATED, activated), 3);
        }
        return true;
    }

    /**
     * Whether the client must not predict the break of the block at {@code pos} when {@code player}
     * mines it, because the server keeps it: a spawner the mod disables instead, unless it is
     * treated as vanilla, or a single-use spawner that a player not in Creative uses up. A spawner
     * tool may break a spawner when the server allows it (breakWithSpawnerTools): then the block
     * goes when the server says so, a moment later, instead of flickering when it does not.
     */
    public static boolean serverKeepsWhenMined(Level level, BlockPos pos, Player player) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof SpawnerBlock && state.hasProperty(SpawnerStateProperties.MULTIPLAYER_ACTIVATED)
                && level.getBlockEntity(pos) instanceof SpawnerBlockEntity) {
            return !vanillaTreatedSpawners.contains(pos);
        }
        return !player.isCreative() && (OneShotSpawners.isOneShot(state) || state.is(ModBlocksItems.USED_MONSTER_BOX.get()));
    }

    /**
     * A single-use spawner's state. Spawner logic never touches these blocks; a block with a used-up
     * look (Quark's monster box) shows it to a player it is used up for.
     */
    public static void updateOneShotState(BlockPos pos, Set<String> usedBy) {
        String currentPlayerName = getCurrentPlayerName();
        if (currentPlayerName != null && usedBy.contains(currentPlayerName)) {
            usedUpOneShots.add(pos.immutable());
        } else {
            usedUpOneShots.remove(pos);
        }
        applyUsedLook(pos);
    }

    /**
     * The look a block arriving at {@code pos} must have on this client: its used-up look when it is
     * a single-use spawner used up for the current player, else {@code state} itself.
     */
    public static BlockState usedLookFor(BlockPos pos, BlockState state) {
        if (usedUpOneShots.contains(pos) && MONSTER_BOX.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())) {
            replacedByUsedLook.put(pos.immutable(), state);
            return ModBlocksItems.USED_MONSTER_BOX.get().defaultBlockState();
        }
        return state;
    }

    private static void applyUsedLook(BlockPos pos) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.isLoaded(pos)) return;
        BlockState current = minecraft.level.getBlockState(pos);
        BlockState wanted = usedLookFor(pos, current);
        if (wanted != current) {
            minecraft.level.setBlock(pos, wanted, 3);
        } else if (!usedUpOneShots.contains(pos) && current.is(ModBlocksItems.USED_MONSTER_BOX.get())) {
            BlockState original = replacedByUsedLook.remove(pos);
            if (original != null) {
                minecraft.level.setBlock(pos, original, 3);
            }
        }
    }

    public static boolean isOneShotUsedUpForCurrentPlayer(BlockPos pos) {
        return usedUpOneShots.contains(pos);
    }

    public static boolean isSpawnerDisabledForCurrentPlayer(BlockPos pos) {
        return playerDisabledSpawners.getOrDefault(pos, false);
    }

    public static boolean isPlayerPlacedSpawner(BlockPos pos) {
        return vanillaTreatedSpawners.contains(pos);
    }

    public static void clearAll() {
        playerDisabledSpawners.clear();
        spawnersNeedingUpdate.clear();
        vanillaTreatedSpawners.clear();
        usedUpOneShots.clear();
        replacedByUsedLook.clear();
        cachedPlayerName = null;
        currentDimension = null;
        lastPeriodicRefresh = 0;
        Constants.debug("CLIENT: Cleared all client-side spawner data");
    }

    public static void onDimensionChange(String newDimension) {
        currentDimension = newDimension;
        spawnersNeedingUpdate.addAll(playerDisabledSpawners.keySet());
        scheduleTargetedRefresh();
    }

    public static void onChunkLoaded(int chunkX, int chunkZ) {
        for (BlockPos pos : new ArrayList<>(playerDisabledSpawners.keySet())) {
            if (inChunk(pos, chunkX, chunkZ) && applyLook(pos)) {
                spawnersNeedingUpdate.remove(pos);
            }
        }
        for (BlockPos pos : new ArrayList<>(usedUpOneShots)) {
            if (inChunk(pos, chunkX, chunkZ)) {
                applyUsedLook(pos);
            }
        }
    }

    /**
     * Forgets the spawners of a chunk this client no longer sees. The server sends their state
     * again with the chunk when it comes back, and a spawner that became active again meanwhile
     * (respawn time) is then not kept disabled here: the server sends no state for it at all.
     */
    public static void onChunkUnloaded(int chunkX, int chunkZ) {
        playerDisabledSpawners.keySet().removeIf(pos -> inChunk(pos, chunkX, chunkZ));
        vanillaTreatedSpawners.removeIf(pos -> inChunk(pos, chunkX, chunkZ));
        spawnersNeedingUpdate.removeIf(pos -> inChunk(pos, chunkX, chunkZ));
        usedUpOneShots.removeIf(pos -> inChunk(pos, chunkX, chunkZ));
        replacedByUsedLook.keySet().removeIf(pos -> inChunk(pos, chunkX, chunkZ));
    }

    private static boolean inChunk(BlockPos pos, int chunkX, int chunkZ) {
        return (pos.getX() >> 4) == chunkX && (pos.getZ() >> 4) == chunkZ;
    }

    private static String getCurrentPlayerName() {
        if (cachedPlayerName == null && Minecraft.getInstance().player != null) {
            cachedPlayerName = Minecraft.getInstance().player.getName().getString();
        }
        return cachedPlayerName;
    }

    public static void refreshPendingSpawnerStates() {
        if (spawnersNeedingUpdate.isEmpty()) return;
        spawnersNeedingUpdate.removeIf(ClientSpawnerData::applyLook);
    }

    private static void scheduleTargetedRefresh() {
        Minecraft.getInstance().execute(ClientSpawnerData::refreshPendingSpawnerStates);
    }

    public static void periodicRefresh() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPeriodicRefresh < 5000) return;
        lastPeriodicRefresh = currentTime;
        if (getCurrentPlayerName() != null) {
            refreshPendingSpawnerStates();
        }
    }

    /**
     * A safety net, once a second: puts back the look of known spawners near the player if
     * something else changed their block state.
     */
    public static void monitorAndCorrectSpawnerStates() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;

        int radius = 32;
        BlockPos playerPos = minecraft.player.blockPosition();
        for (BlockPos spawnerPos : playerDisabledSpawners.keySet()) {
            if (spawnerPos.distSqr(playerPos) <= radius * radius) {
                applyLook(spawnerPos);
            }
        }
    }
}
