package com.theplumteam.multiplayerspawners;

import com.theplumteam.multiplayerspawners.config.ModConfig;
import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import com.theplumteam.multiplayerspawners.platform.Services;
import com.theplumteam.multiplayerspawners.registration.ModBlocksItems;

public class CommonClass {

    public static void init() {
        // First, so that debug logging honours the client setting from the start.
        ModConfig.loadClient(Services.PLATFORM.getConfigDir());

        // Loading the class runs the registrations at a safe time.
        ModBlocksItems.init();

        NetworkHandler.getInstance().init();

        Constants.debug("Initialised on {} (development: {})",
                Services.PLATFORM.getPlatformName(), Services.PLATFORM.isDevelopmentEnvironment());
    }
}
