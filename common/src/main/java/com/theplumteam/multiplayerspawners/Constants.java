package com.theplumteam.multiplayerspawners;

import com.theplumteam.multiplayerspawners.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Constants {
	public static final String MOD_ID = "multiplayerspawners";
	public static final String MOD_NAME = "MultiplayerSpawners";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

	public static void init() {
	}

	public static void debug(String message, Object... args) {
		// Only check client config if it's actually loaded (not available on dedicated servers)
		if (ModConfig.CLIENT_SPEC.isLoaded() && ModConfig.CLIENT.enableDebugLogging.get()) {
			LOG.info("[DEBUG] " + message, args);
		}
	}

	public static void logTorchMechanics(String message, Object... args) {
		LOG.info("[TORCH] " + message, args);
	}
}