package com.theplumteam.multiplayerspawners.config;

import com.theplumteam.multiplayerspawners.Constants;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Forge-specific config registration using native Forge config system
 */
public class ForgeConfigRegistry {

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, com.theplumteam.multiplayerspawners.config.ModConfig.SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, com.theplumteam.multiplayerspawners.config.ModConfig.CLIENT_SPEC);
        com.theplumteam.multiplayerspawners.config.ModConfig.isClientConfigLoaded = true;
        Constants.debug("Registered Forge config for MultiplayerSpawners");
    }
}