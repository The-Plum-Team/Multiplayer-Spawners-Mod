package com.theplumteam.multiplayerspawners.config;

import com.theplumteam.multiplayerspawners.Constants;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;

/**
 * NeoForge-specific config registration using native NeoForge config system
 */
public class NeoForgeConfigRegistry {

    public static void register() {
        ModContainer modContainer = ModLoadingContext.get().getActiveContainer();
        modContainer.registerConfig(ModConfig.Type.SERVER, com.theplumteam.multiplayerspawners.config.ModConfig.SERVER_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, com.theplumteam.multiplayerspawners.config.ModConfig.CLIENT_SPEC);
        com.theplumteam.multiplayerspawners.config.ModConfig.isClientConfigLoaded = true;
        Constants.debug("Registered NeoForge config for MultiplayerSpawners");
    }
}
