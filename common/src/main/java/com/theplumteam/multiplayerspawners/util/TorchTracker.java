package com.theplumteam.multiplayerspawners.util;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.config.ModConfig;
import com.theplumteam.multiplayerspawners.registration.ModBlocksItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
//? if <1.21.2 {
/*import org.joml.Vector3f;
*///?}

import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Tracks recently placed torches that are in close proximity to a spawner.
 * If a torch is too close, it will be extinguished after a configurable delay with dramatic particle effects.
 */
public class TorchTracker {
    private static final Map<ResourceKey<Level>, Map<BlockPos, Long>> trackedTorches = new ConcurrentHashMap<>();
    private static final Queue<ScheduledTask> scheduledTasks = new ConcurrentLinkedQueue<>();
    private static final int SPAWNER_DISTANCE = 7; // The radius within which spawners will extinguish torches
    private static final int SHOT_DELAY_TICKS = 40; // 2 seconds additional delay before shooting
    private static final int PARTICLE_BEAM_DURATION_TICKS = 4; // 0.2 seconds - much faster!
    private static final double PARTICLE_STEP_SIZE = 0.15; // Larger steps for faster movement

    /**
     * Simple scheduled task class
     */
    private static class ScheduledTask {
        final long executeAtTick;
        final Runnable task;

        ScheduledTask(long executeAtTick, Runnable task) {
            this.executeAtTick = executeAtTick;
            this.task = task;
        }
    }

    /**
     * A dust particle of the given colour (components 0-1) and size.
     */
    private static DustParticleOptions dust(float red, float green, float blue, float scale) {
        //? if >=1.21.2 {
        int rgb = (Math.round(red * 255) << 16) | (Math.round(green * 255) << 8) | Math.round(blue * 255);
        return new DustParticleOptions(rgb, scale);
        //?} else {
        /*return new DustParticleOptions(new Vector3f(red, green, blue), scale);
        *///?}
    }

    /**
     * Gets the configurable delay in ticks for torch extinguishing.
     * Returns 0 if torch extinguishing is disabled.
     */
    private static int getExtinguishDelayTicks() {
        int seconds = ModConfig.SERVER.torchExtinguishDelaySeconds.get();
        if (seconds <= 0) {
            return 0; // Disabled
        }
        return seconds * 20; // Convert to ticks
    }

    /**
     * Checks if torch extinguishing is enabled in the configuration.
     */
    private static boolean isTorchExtinguishingEnabled() {
        return ModConfig.SERVER.torchExtinguishDelaySeconds.get() > 0;
    }

    /**
     * Calculates the precise tip position of a torch based on its type and facing direction.
     */
    private static Vec3 getTorchTipPosition(BlockPos torchPos, BlockState torchState) {
        Vec3 basePos = Vec3.atCenterOf(torchPos);

        if (torchState.is(Blocks.TORCH)) {
            // Regular torch on the ground - tip is at the top center
            return basePos.add(0, 0.4, 0); // Torch is 10/16 blocks tall, so tip is at 0.625
        } else if (torchState.is(Blocks.WALL_TORCH)) {
            // Wall torch - tip position depends on facing direction
            Direction facing = torchState.getValue(WallTorchBlock.FACING);

            // Wall torches are attached 3/16 blocks from the wall and stick out about 7/16 blocks
            // The tip is at the end of the torch, leaning slightly upward
            double wallOffset = 3.0 / 16.0; // Distance from wall to torch base
            double torchLength = 7.0 / 16.0; // How far the torch extends from wall
            double tipHeight = 8.0 / 16.0; // Height of torch tip above block base

            switch (facing) {
                case NORTH: // Torch points north, attached to south wall
                    return basePos.add(0, tipHeight, -wallOffset - torchLength);
                case SOUTH: // Torch points south, attached to north wall
                    return basePos.add(0, tipHeight, wallOffset + torchLength);
                case WEST: // Torch points west, attached to east wall
                    return basePos.add(-wallOffset - torchLength, tipHeight, 0);
                case EAST: // Torch points east, attached to west wall
                    return basePos.add(wallOffset + torchLength, tipHeight, 0);
                default:
                    return basePos.add(0, tipHeight, 0);
            }
        }

        // Fallback for unknown torch types
        return basePos.add(0, 0.5, 0);
    }

    /**
     * The nearest spawner within SPAWNER_DISTANCE of a position, or null. Spawners are block
     * entities, so this looks only at the block entities of the loaded chunks the range covers
     * (at most four), instead of every block of the range.
     * Player-placed spawners are ignored if the config option treatPlacedSpawnersAsVanilla is enabled.
     */
    private static BlockPos findNearbySpawner(Level level, BlockPos centerPos) {
        BlockPos nearest = null;
        double nearestDistSq = SPAWNER_DISTANCE * SPAWNER_DISTANCE + 1;
        boolean skipPlaced = ModConfig.SERVER.treatPlacedSpawnersAsVanilla.get();

        for (int chunkX = (centerPos.getX() - SPAWNER_DISTANCE) >> 4; chunkX <= (centerPos.getX() + SPAWNER_DISTANCE) >> 4; chunkX++) {
            for (int chunkZ = (centerPos.getZ() - SPAWNER_DISTANCE) >> 4; chunkZ <= (centerPos.getZ() + SPAWNER_DISTANCE) >> 4; chunkZ++) {
                // Only loaded chunks: this must never load one.
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof SpawnerBlockEntity spawnerBlockEntity) || !blockEntity.getBlockState().is(Blocks.SPAWNER)) {
                        continue;
                    }
                    if (skipPlaced && ((SpawnerDataAccessor) spawnerBlockEntity.getSpawner()).multiplayerspawners$isPlayerPlaced()) {
                        // Treated as vanilla: it puts out no torches.
                        continue;
                    }
                    BlockPos pos = blockEntity.getBlockPos();
                    double distSq = centerPos.distSqr(pos);
                    if (distSq <= SPAWNER_DISTANCE * SPAWNER_DISTANCE && distSq < nearestDistSq) {
                        nearestDistSq = distSq;
                        nearest = pos.immutable();
                    }
                }
            }
        }
        return nearest;
    }

    /**
     * Called when a torch is placed. Checks for nearby spawners and only begins tracking
     * if a spawner is within the extinguish range and torch extinguishing is enabled.
     */
    public static void addTorch(Level level, BlockPos pos) {
        if (level.isClientSide()) return;

        // Check if torch extinguishing is enabled
        if (!isTorchExtinguishingEnabled()) {
            Constants.logTorchMechanics("Torch extinguishing is disabled. Torch at {} will not be tracked.", pos);
            return;
        }

        BlockPos nearestSpawner = findNearbySpawner(level, pos);

        // If no spawner is found, the torch is safe. Do nothing.
        if (nearestSpawner == null) {
            Constants.logTorchMechanics("Torch at {} is safe. No spawner in extinguish range.", pos);
            return;
        }

        // A spawner is close enough to be a threat. Start the tracking process.
        long serverTickCount = ((ServerLevel) level).getServer().getTickCount();
        trackedTorches.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>()).put(pos, serverTickCount);

        int delaySeconds = ModConfig.SERVER.torchExtinguishDelaySeconds.get();
        Constants.logTorchMechanics("Spawner at {} is in range of new torch at {}. Starting {}-second extinguish timer.",
                nearestSpawner, pos, delaySeconds);
    }

    public static void removeTorch(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        Map<BlockPos, Long> torchesInDim = trackedTorches.get(level.dimension());
        if (torchesInDim != null) {
            torchesInDim.remove(pos);
            Constants.logTorchMechanics("Stopped tracking torch at {}", pos);
        }
    }

    public static void onServerTick(MinecraftServer server) {
        // Process scheduled tasks first
        processScheduledTasks(server);

        // Skip processing if torch extinguishing is disabled
        if (!isTorchExtinguishingEnabled()) {
            // Clear any existing tracked torches if the feature was just disabled
            if (!trackedTorches.isEmpty()) {
                Constants.logTorchMechanics("Torch extinguishing disabled - clearing {} tracked torches.",
                        trackedTorches.values().stream().mapToInt(Map::size).sum());
                trackedTorches.clear();
            }
            return;
        }

        if (trackedTorches.isEmpty()) return;

        long currentTime = server.getTickCount();
        int extinguishDelayTicks = getExtinguishDelayTicks();

        // Log torch tracking status less frequently (every 30 seconds instead of every 5 seconds)
        if (currentTime % 600 == 0) {
            int totalTorches = trackedTorches.values().stream().mapToInt(Map::size).sum();
            if (totalTorches > 0) {
                Constants.debug("Tracking {} threatened torches across {} dimensions (delay: {}s)",
                        totalTorches, trackedTorches.size(), extinguishDelayTicks / 20);
            }
        }

        Iterator<Map.Entry<ResourceKey<Level>, Map<BlockPos, Long>>> dimIterator = trackedTorches.entrySet().iterator();
        while (dimIterator.hasNext()) {
            Map.Entry<ResourceKey<Level>, Map<BlockPos, Long>> dimEntry = dimIterator.next();
            ResourceKey<Level> dimensionKey = dimEntry.getKey();
            Map<BlockPos, Long> torchesInDim = dimEntry.getValue();

            ServerLevel level = server.getLevel(dimensionKey);
            if (level == null) {
                Constants.debug("Level is null for dimension {}, skipping torch checks.", dimensionKey);
                continue;
            }

            Iterator<Map.Entry<BlockPos, Long>> iterator = torchesInDim.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<BlockPos, Long> entry = iterator.next();
                BlockPos pos = entry.getKey();
                long placementTime = entry.getValue();
                long ticksElapsed = currentTime - placementTime;

                // OPTIMIZATION: Only process this torch once every 20 ticks (1 second)
                // Use hash code to stagger the checks across different ticks
                // This distributes the load so not all torches are checked in the same tick
                if ((currentTime + pos.hashCode()) % 20 != 0) {
                    // Still check if the delay definitely passed to avoid missing torches
                    // that have been waiting longer than the check interval
                    if (ticksElapsed < extinguishDelayTicks + 20) {
                        continue;
                    }
                }

                if (ticksElapsed >= extinguishDelayTicks) {
                    iterator.remove();
                    Constants.logTorchMechanics("Checking torch at {} after delay of {} ticks ({}s) (placed at tick {}, current tick {}).",
                            pos, extinguishDelayTicks, extinguishDelayTicks / 20, placementTime, currentTime);

                    BlockState currentState = level.getBlockState(pos);
                    if (currentState.is(Blocks.TORCH) || currentState.is(Blocks.WALL_TORCH)) {
                        Constants.logTorchMechanics("Torch at {} still exists. Re-checking for nearby spawners (extinguish distance {}).", pos, SPAWNER_DISTANCE);
                        BlockPos nearestSpawner = findNearbySpawner(level, pos);
                        if (nearestSpawner != null) {
                            double distanceToSpawner = Math.sqrt(pos.distSqr(nearestSpawner));
                            Constants.logTorchMechanics("Spawner at {} will target torch at {} after additional delay (distance: {} <= {})",
                                    nearestSpawner, pos, String.format("%.1f", distanceToSpawner), SPAWNER_DISTANCE);

                            // Add additional delay before shooting
                            scheduleTask(server, SHOT_DELAY_TICKS, () -> {
                                // Double-check the torch is still there before shooting
                                BlockState stillCurrentState = level.getBlockState(pos);
                                if (stillCurrentState.is(Blocks.TORCH) || stillCurrentState.is(Blocks.WALL_TORCH)) {
                                    beginTorchExtinguishSequence(level, pos, stillCurrentState, nearestSpawner);
                                }
                            });
                        } else {
                            Constants.logTorchMechanics("No spawner found nearby. Torch at {} is now safe and remains lit.", pos);
                        }
                    } else {
                        Constants.logTorchMechanics("Block at {} is no longer a torch. Current block: {}. Ignoring.", pos, currentState.getBlock());
                    }
                }
            }

            if (torchesInDim.isEmpty()) {
                dimIterator.remove();
            }
        }
    }

    /**
     * Processes scheduled tasks that are ready to execute
     */
    private static void processScheduledTasks(MinecraftServer server) {
        long currentTick = server.getTickCount();
        Iterator<ScheduledTask> iterator = scheduledTasks.iterator();

        while (iterator.hasNext()) {
            ScheduledTask task = iterator.next();
            if (currentTick >= task.executeAtTick) {
                try {
                    task.task.run();
                } catch (Exception e) {
                    Constants.LOG.error("Error executing scheduled task", e);
                }
                iterator.remove();
            }
        }
    }

    /**
     * Begins the dramatic torch extinguishing sequence with particle beam effect.
     */
    private static void beginTorchExtinguishSequence(ServerLevel level, BlockPos torchPos, BlockState torchState, BlockPos spawnerPos) {
        // Start the particle beam effect from spawner to torch
        createParticleBeam(level, spawnerPos, torchPos, torchState);

        // Play the wither firing sound at the spawner (the classic wither head firing sound)
        level.playSound(null, spawnerPos, SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 0.8f, 1.2f);

        // Schedule the actual torch extinguishing after the much faster particle beam travels
        scheduleTask(level.getServer(), PARTICLE_BEAM_DURATION_TICKS, () -> {
            // Double-check the torch is still there
            BlockState currentState = level.getBlockState(torchPos);
            if (currentState.is(Blocks.TORCH) || currentState.is(Blocks.WALL_TORCH)) {
                extinguishTorchWithEffects(level, torchPos, currentState, spawnerPos);
            }
        });
    }

    /**
     * Creates a very fast dramatic particle beam from the exact center of the spawner to the precise torch tip.
     */
    private static void createParticleBeam(ServerLevel level, BlockPos spawnerPos, BlockPos torchPos, BlockState torchState) {
        Vec3 start = Vec3.atCenterOf(spawnerPos); // Exact center of spawner block
        Vec3 end = getTorchTipPosition(torchPos, torchState); // Precise torch tip position

        // Calculate direction vector (from spawner center to torch)
        Vec3 direction = end.subtract(start);
        double distance = direction.length();
        Vec3 step = direction.normalize().scale(PARTICLE_STEP_SIZE);

        int particleCount = (int)(distance / PARTICLE_STEP_SIZE);

        // Create small dark particles - wither-like colors
        DustParticleOptions darkWitherDust = dust(0.05f, 0.05f, 0.1f, 0.2f * 3); // Very dark with blue tint
        DustParticleOptions tinyBlackDust = dust(0.02f, 0.02f, 0.02f, 0.15f * 3); // Almost black, tiny

        // Create the particle beam over very few ticks for super fast movement
        for (int tick = 0; tick < PARTICLE_BEAM_DURATION_TICKS; tick += 1) {
            final int currentTick = tick;

            scheduleTask(level.getServer(), tick, () -> {
                // Calculate how far along the beam we should be (0.0 to 1.0)
                double progress = (double) currentTick / PARTICLE_BEAM_DURATION_TICKS;

                // Create particles from spawner center to current progress point
                int currentParticleCount = (int)(particleCount * progress);

                for (int i = 0; i <= currentParticleCount && i < particleCount; i++) {
                    Vec3 particlePos = start.add(step.scale(i));

                    // Use dark wither-like particles
                    level.sendParticles(darkWitherDust,
                            particlePos.x, particlePos.y, particlePos.z,
                            1, 0.005, 0.005, 0.005, 0.0);

                    // Add tinier particles for density (less frequent due to faster speed)
                    if (i % 2 == 0) {
                        level.sendParticles(tinyBlackDust,
                                particlePos.x, particlePos.y, particlePos.z,
                                1, 0.003, 0.003, 0.003, 0.0);
                    }
                }

                // At the end of the beam, create dramatic effect at torch tip
                if (progress > 0.5) { // Earlier trigger due to faster beam
                    level.sendParticles(darkWitherDust,
                            end.x, end.y, end.z,
                            3, 0.01, 0.01, 0.01, 0.0);
                }
            });
        }
    }

    /**
     * Replaces the torch with an unlit version, creating a burst of particles and sound.
     */
    private static void extinguishTorchWithEffects(ServerLevel level, BlockPos pos, BlockState oldState, BlockPos spawnerPos) {
        Vec3 torchTip = getTorchTipPosition(pos, oldState); // Use precise torch tip position

        // Create small dark particles for dark magic impact
        DustParticleOptions darkMagicDust = dust(0.1f, 0.02f, 0.15f, 0.25f); // Dark purple
        DustParticleOptions smallBlackDust = dust(0.02f, 0.02f, 0.02f, 0.2f); // Almost black

        // Dark magic impact particles at the precise torch tip
        level.sendParticles(darkMagicDust, torchTip.x, torchTip.y, torchTip.z, 6, 0.02, 0.02, 0.02, 0.0);
        level.sendParticles(smallBlackDust, torchTip.x, torchTip.y, torchTip.z, 4, 0.015, 0.015, 0.015, 0.0);

        // A burst of red embers and smoke shoot out of the torch as it is extinguished
        level.sendParticles(ParticleTypes.LAVA, torchTip.x, torchTip.y, torchTip.z, 4, 0.02, 0.02, 0.02, 0.01);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, torchTip.x, torchTip.y, torchTip.z, 3, 0.04, 0.04, 0.04, 0.01);

        // Add the sound of the fire being extinguished with some dark magic
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 0.8f);
        level.playSound(null, pos, SoundEvents.WITHER_HURT, SoundSource.BLOCKS, 0.2f, 1.5f);

        BlockState newState = null;
        if (oldState.is(Blocks.TORCH)) {
            newState = ModBlocksItems.UNLIT_TORCH.get().defaultBlockState();
        } else if (oldState.is(Blocks.WALL_TORCH)) {
            newState = ModBlocksItems.UNLIT_WALL_TORCH.get().defaultBlockState().setValue(WallTorchBlock.FACING, oldState.getValue(WallTorchBlock.FACING));
        }
        if (newState != null) {
            level.setBlock(pos, newState, 3);
            double distance = Math.sqrt(pos.distSqr(spawnerPos));
            int delaySeconds = ModConfig.SERVER.torchExtinguishDelaySeconds.get();
            Constants.logTorchMechanics("Spawner at {} extinguished torch at {} with lightning-fast dark magic after {}s delay! (distance: {})",
                    spawnerPos, pos, delaySeconds, String.format("%.1f", distance));
        }
    }

    /**
     * Schedules a task to run after a specified number of ticks.
     */
    private static void scheduleTask(MinecraftServer server, int delayTicks, Runnable task) {
        long executeTime = server.getTickCount() + delayTicks;
        scheduledTasks.offer(new ScheduledTask(executeTime, task));
    }

    public static void clear() {
        trackedTorches.clear();
        scheduledTasks.clear();
        Constants.debug("Cleared torch tracker and scheduled tasks.");
    }

    /**
     * Gets the current torch extinguish delay in seconds for display purposes.
     */
    public static int getCurrentDelaySeconds() {
        return ModConfig.SERVER.torchExtinguishDelaySeconds.get();
    }
}