package com.theplumteam.multiplayerspawners;

import com.theplumteam.multiplayerspawners.config.ModConfig;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Constants {
	public static final String MOD_ID = "multiplayerspawners";
	public static final String MOD_NAME = "MultiplayerSpawners";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);
	/**
	 * The level event of a block breaking, with its particles and sound: what vanilla's
	 * Block.spawnDestroyParticles sends. Its LevelEvent name changed in 26.1, where
	 * PARTICLES_DESTROY_BLOCK became a particles-only event, so the number is used.
	 */
	public static final int BREAK_EFFECTS = 2001;

	public static ResourceLocation id(String path) {
		//? if >=1.21 {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
		//?} else {
		/*return new ResourceLocation(MOD_ID, path);
		*///?}
	}

	public static void debug(String message, Object... args) {
		if (ModConfig.CLIENT.enableDebugLogging.get()) {
			LOG.info("[DEBUG] " + message, args);
		}
	}

	public static void logTorchMechanics(String message, Object... args) {
		debug("[TORCH] " + message, args);
	}
}
