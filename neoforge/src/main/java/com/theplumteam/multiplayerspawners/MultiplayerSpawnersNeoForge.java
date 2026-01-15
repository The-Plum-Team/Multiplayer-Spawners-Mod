package com.theplumteam.multiplayerspawners;

import com.theplumteam.multiplayerspawners.client.MultiplayerSpawnersNeoForgeClient;
import com.theplumteam.multiplayerspawners.platform.NeoForgePlatformHelper;
import com.theplumteam.multiplayerspawners.platform.Services;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Constants.MOD_ID)
public class MultiplayerSpawnersNeoForge {

    public MultiplayerSpawnersNeoForge(IEventBus modEventBus, ModContainer modContainer) {

        // This method is invoked by the NeoForge mod loader when it is ready
        // to load your mod. You can access NeoForge and Common code in this
        // project.

        // Use NeoForge to bootstrap the Common mod.
        Constants.debug("Hello NeoForge world! MultiplayerSpawnersNeoForge has been loaded!");
        CommonClass.init();

        if (Services.PLATFORM instanceof NeoForgePlatformHelper) {
            ((NeoForgePlatformHelper) Services.PLATFORM).register(modEventBus);
        }

        // Initialize client-side code only on client
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MultiplayerSpawnersNeoForgeClient.init(modEventBus);
        }

        // Register server events on the NeoForge event bus
        NeoForge.EVENT_BUS.register(NeoForgeServerEvents.class);
    }
}
