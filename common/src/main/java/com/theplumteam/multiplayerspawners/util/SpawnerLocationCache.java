package com.theplumteam.multiplayerspawners.util;

import com.theplumteam.multiplayerspawners.Constants;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Caches the locations of all spawners for efficient distance checking.
 */
public class SpawnerLocationCache {
    private static final Map<ResourceKey<Level>, Set<BlockPos>> spawnerPositions = new ConcurrentHashMap<>();

    // Caching the reflective method access for performance
    private static Method getChunksMethod = null;

    public static void addSpawner(ResourceKey<Level> dimension, BlockPos pos) {
        spawnerPositions.computeIfAbsent(dimension, k -> new CopyOnWriteArraySet<>()).add(pos);
        Constants.LOG.debug("Added spawner to cache at {}", pos);
    }

    public static void removeSpawner(ResourceKey<Level> dimension, BlockPos pos) {
        Set<BlockPos> positions = spawnerPositions.get(dimension);
        if (positions != null) {
            positions.remove(pos);
            Constants.LOG.debug("Removed spawner from cache at {}", pos);
        }
    }

    public static boolean isFarFromSpawner(ResourceKey<Level> dimension, BlockPos torchPos, double distance) {
        Set<BlockPos> positions = spawnerPositions.get(dimension);
        if (positions == null || positions.isEmpty()) {
            Constants.logTorchMechanics("No spawners cached for dimension {}. Torch at {} is considered far.", dimension.location(), torchPos);
            return true; // No spawners in dimension, so definitely far.
        }

        double distSqr = distance * distance;
        Constants.logTorchMechanics("Checking torch at {} against {} spawners in dimension {}. Max distance squared: {}", torchPos, positions.size(), dimension.location(), distSqr);

        for (BlockPos spawnerPos : positions) {
            double currentDistSqr = torchPos.distSqr(spawnerPos);
            if (currentDistSqr <= distSqr) {
                Constants.logTorchMechanics("Torch at {} is NEAR spawner at {}. Distance squared: {} <= {}", torchPos, spawnerPos, currentDistSqr, distSqr);
                return false; // Found a spawner within range
            }
        }

        Constants.logTorchMechanics("Torch at {} is FAR from all spawners.", torchPos);
        return true; // No spawners found within range
    }

    public static void clear() {
        spawnerPositions.clear();
        Constants.LOG.info("Cleared spawner location cache.");
    }

    @SuppressWarnings("unchecked")
    public static void scanAndPopulate(ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        Set<BlockPos> positions = spawnerPositions.computeIfAbsent(dimension, k -> new CopyOnWriteArraySet<>());
        positions.clear();

        try {
            ChunkMap chunkMap = level.getChunkSource().chunkMap;

            // Use reflection to access the protected 'getChunks' method
            if (getChunksMethod == null) {
                getChunksMethod = ChunkMap.class.getDeclaredMethod("getChunks");
                getChunksMethod.setAccessible(true);
            }
            Iterable<ChunkHolder> chunkHolders = (Iterable<ChunkHolder>) getChunksMethod.invoke(chunkMap);

            for (ChunkHolder chunkHolder : chunkHolders) {
                LevelChunk chunk = chunkHolder.getTickingChunk();
                if (chunk != null) {
                    chunk.getBlockEntities().forEach((pos, be) -> {
                        if (be instanceof SpawnerBlockEntity) {
                            positions.add(pos);
                        }
                    });
                }
            }
        } catch (Exception e) {
            Constants.LOG.error("Failed to scan for spawners using reflection. This may happen on a Minecraft update.", e);
            // Re-throw as a runtime exception to make it clear that a critical function has failed.
            throw new RuntimeException("Failed to access ChunkMap#getChunks via reflection", e);
        }

        Constants.LOG.info("Populated spawner cache for dimension {}: found {} spawners", dimension.location(), positions.size());
    }

    /**
     * Gets all spawner positions in a specific dimension
     */
    public static Set<BlockPos> getSpawnersInDimension(ResourceKey<Level> dimension) {
        return spawnerPositions.get(dimension);
    }

    /**
     * Finds the nearest spawner to the given position in the specified dimension
     */
    public static BlockPos findNearestSpawner(ResourceKey<Level> dimension, BlockPos targetPos) {
        Set<BlockPos> positions = spawnerPositions.get(dimension);
        if (positions == null || positions.isEmpty()) {
            return null;
        }

        BlockPos nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (BlockPos spawnerPos : positions) {
            double distSq = targetPos.distSqr(spawnerPos);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = spawnerPos;
            }
        }

        return nearest;
    }


}