package com.theplumteam.multiplayerspawners;

import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import com.theplumteam.multiplayerspawners.platform.Services;
import com.theplumteam.multiplayerspawners.registration.ModBlocksItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;

public class CommonClass {

    public static void init() {
        // Initialize our registrations now, at a safe time.
        ModBlocksItems.init();

        // Constants.init() is no longer needed for registrations.
        Constants.debug("Hello from Common init on {}! we are currently in a {} environment!", Services.PLATFORM.getPlatformName(), Services.PLATFORM.getEnvironmentName());
        Constants.debug("The ID for diamonds is {}", BuiltInRegistries.ITEM.getKey(Items.DIAMOND));

        // Initialize network handler
        NetworkHandler.getInstance().init();

        // Platform-specific config registration is now handled in each platform's entry point
        Services.PLATFORM.initializeConfig();

        if (Services.PLATFORM.isModLoaded("examplemod")) {
            Constants.debug("Hello to examplemod");
        }
    }
}
