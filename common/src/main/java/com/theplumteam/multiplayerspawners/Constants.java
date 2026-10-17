package com.theplumteam.multiplayerspawners;

import com.theplumteam.multiplayerspawners.config.ModConfig;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Constants {
	// Deliberately broken (do not merge): checks that the shared gates report a failure.
	private static final int DELIBERATELY_BROKEN = "not an int";

	public static final String MOD_ID = "multiplayerspawners";
	public static final String MOD_NAME = "MultiplayerSpawners";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

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
