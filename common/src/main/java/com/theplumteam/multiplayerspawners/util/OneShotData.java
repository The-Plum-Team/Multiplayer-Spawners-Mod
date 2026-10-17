package com.theplumteam.multiplayerspawners.util;

import net.minecraft.nbt.CompoundTag;

import java.util.Set;

/**
 * What a block entity of a single-use spawner (block tag multiplayerspawners:one_shot_spawners)
 * remembers: the players it has been used up for, and its saved data from before anyone used it.
 * Every block entity carries these fields; only those spawners fill them.
 */
public interface OneShotData {

    Set<String> multiplayerspawners$getUsedBy();

    CompoundTag multiplayerspawners$getPristine();

    void multiplayerspawners$setPristine(CompoundTag pristine);
}
