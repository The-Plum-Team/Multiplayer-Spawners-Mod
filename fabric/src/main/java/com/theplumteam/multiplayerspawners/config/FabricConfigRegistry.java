package com.theplumteam.multiplayerspawners.config;

import com.theplumteam.multiplayerspawners.Constants;
import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Fabric-specific config registration using ForgeConfigAPIPort
 */
public class FabricConfigRegistry {

    public static void register() {
        ForgeConfigRegistry.INSTANCE.register(Constants.MOD_ID, ModConfig.Type.SERVER, com.theplumteam.multiplayerspawners.config.ModConfig.SERVER_SPEC);
        Constants.LOG.info("Registered Fabric config for MultiplayerSpawners");
    }
}