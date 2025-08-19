package com.theplumteam.multiplayerspawners.config;

import com.theplumteam.multiplayerspawners.Constants;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Fabric-specific config registration using ForgeConfigAPIPort
 */
public class FabricConfigRegistry {

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, com.theplumteam.multiplayerspawners.config.ModConfig.SERVER_SPEC);
        Constants.LOG.info("Registered Fabric config for MultiplayerSpawners");
    }
}