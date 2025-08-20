package com.theplumteam.multiplayerspawners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Constants {
	public static final String MOD_ID = "multiplayerspawners";
	public static final String MOD_NAME = "MultiplayerSpawners";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

	// Enhanced logging flags for debugging instant visual updates
	public static final boolean DEBUG_VISUAL_UPDATES = Boolean.parseBoolean(System.getProperty("multiplayerspawners.debug.visual", "false"));
	public static final boolean DEBUG_NETWORK_PACKETS = Boolean.parseBoolean(System.getProperty("multiplayerspawners.debug.network", "false"));
	public static final boolean DEBUG_BLOCK_STATES = Boolean.parseBoolean(System.getProperty("multiplayerspawners.debug.blocks", "false"));

	/**
	 * Enhanced logging method for visual update debugging
	 */
	public static void logVisualUpdate(String message, Object... args) {
		if (DEBUG_VISUAL_UPDATES) {
			LOG.info("[VISUAL] " + message, args);
		} else {
			LOG.debug("[VISUAL] " + message, args);
		}
	}

	/**
	 * Enhanced logging method for network packet debugging
	 */
	public static void logNetworkPacket(String message, Object... args) {
		if (DEBUG_NETWORK_PACKETS) {
			LOG.info("[NETWORK] " + message, args);
		} else {
			LOG.debug("[NETWORK] " + message, args);
		}
	}

	/**
	 * Enhanced logging method for block state debugging
	 */
	public static void logBlockState(String message, Object... args) {
		if (DEBUG_BLOCK_STATES) {
			LOG.info("[BLOCKS] " + message, args);
		} else {
			LOG.debug("[BLOCKS] " + message, args);
		}
	}
}