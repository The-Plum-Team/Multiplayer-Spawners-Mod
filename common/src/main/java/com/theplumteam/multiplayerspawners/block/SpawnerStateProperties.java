package com.theplumteam.multiplayerspawners.block;

import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A dedicated class to hold block state properties that we add to vanilla blocks.
 * This avoids Mixin errors about non-private static fields.
 */
public class SpawnerStateProperties {

    public static final BooleanProperty MULTIPLAYER_ACTIVATED = BooleanProperty.create("multiplayer_activated");

}