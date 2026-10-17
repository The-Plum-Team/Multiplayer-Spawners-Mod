package com.theplumteam.multiplayerspawners.mixin;

import net.minecraft.world.level.BaseSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The spawner's activation range, which mods such as Apothic Spawners change per spawner.
 */
@Mixin(BaseSpawner.class)
public interface BaseSpawnerAccessor {

    @Accessor("requiredPlayerRange")
    int multiplayerspawners$getRequiredPlayerRange();
}
