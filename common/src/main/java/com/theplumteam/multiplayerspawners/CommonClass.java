package com.theplumteam.multiplayerspawners;

import com.theplumteam.multiplayerspawners.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;

public class CommonClass {

    public static void init() {
        Constants.LOG.info("Hello from Common init on {}! we are currently in a {} environment!", Services.PLATFORM.getPlatformName(), Services.PLATFORM.getEnvironmentName());
        Constants.LOG.info("The ID for diamonds is {}", BuiltInRegistries.ITEM.getKey(Items.DIAMOND));

        // Platform-specific config registration is now handled in each platform's entry point
        Services.PLATFORM.initializeConfig();

        if (Services.PLATFORM.isModLoaded("examplemod")) {
            Constants.LOG.info("Hello to examplemod");
        }
    }
}