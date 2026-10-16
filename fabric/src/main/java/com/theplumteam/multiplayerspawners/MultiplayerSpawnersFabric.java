package com.theplumteam.multiplayerspawners;

import net.fabricmc.api.ModInitializer;

public class MultiplayerSpawnersFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        CommonClass.init();
        FabricServerEvents.register();
    }
}
