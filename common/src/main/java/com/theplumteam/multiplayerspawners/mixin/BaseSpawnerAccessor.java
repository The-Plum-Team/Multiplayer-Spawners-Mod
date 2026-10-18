package com.theplumteam.multiplayerspawners.mixin;

import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.SpawnData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The spawner's activation range, which mods such as Apothic Spawners change per spawner, and the
 * mob it spawns next (an empty spawner has none).
 */
@Mixin(BaseSpawner.class)
public interface BaseSpawnerAccessor {

    @Accessor("requiredPlayerRange")
    int multiplayerspawners$getRequiredPlayerRange();

    @Accessor("nextSpawnData")
    SpawnData multiplayerspawners$getNextSpawnData();
}
