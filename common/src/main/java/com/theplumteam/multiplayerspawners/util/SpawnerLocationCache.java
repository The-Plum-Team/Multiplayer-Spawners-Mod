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
        Constants.debug("Added spawner to cache at {}", pos);
    }

    public static void removeSpawner(ResourceKey<Level> dimension, BlockPos pos) {
        Set<BlockPos> positions = spawnerPositions.get(dimension);
        if (positions != null) {
            positions.remove(pos);
            Constants.debug("Removed spawner from cache at {}", pos);
        }
    }

    public static void clear() {
        spawnerPositions.clear();
        Constants.debug("Cleared spawner location cache.");
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
                // In production environments, the method name "getChunks" is obfuscated.
                // To maintain compatibility, we find the method by its unique signature:
                // it's the only method in ChunkMap that takes no arguments and returns an Iterable.
                for (final Method method : ChunkMap.class.getDeclaredMethods()) {
                    if (method.getParameterCount() == 0 && method.getReturnType() == Iterable.class) {
                        Constants.debug("Found ChunkMap#getChunks method by signature: {}", method.getName());
                        getChunksMethod = method;
                        getChunksMethod.setAccessible(true);
                        break;
                    }
                }
                if (getChunksMethod == null) {
                    throw new NoSuchMethodException("Could not find ChunkMap.getChunks() method via reflection. This is likely due to a Minecraft update breaking compatibility.");
                }
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

        Constants.debug("Populated spawner cache for dimension {}: found {} spawners", dimension.location(), positions.size());
    }
}