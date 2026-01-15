package com.theplumteam.multiplayerspawners;

import com.theplumteam.multiplayerspawners.client.MultiplayerSpawnersForgeClient;
import com.theplumteam.multiplayerspawners.platform.ForgePlatformHelper;
import com.theplumteam.multiplayerspawners.platform.Services;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class MultiplayerSpawnersForge {

    public MultiplayerSpawnersForge() {

        // This method is invoked by the Forge mod loader when it is ready
        // to load your mod. You can access Forge and Common code in this
        // project.

        // Use Forge to bootstrap the Common mod.
        Constants.debug("Hello Forge world! MultiplayerSpawnersForge has been loaded!");
        CommonClass.init();

        if (Services.PLATFORM instanceof ForgePlatformHelper) {
            ((ForgePlatformHelper) Services.PLATFORM).register();
        }

        // Initialize client-side code only on client
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> MultiplayerSpawnersForgeClient::init);

        // While @Mod.EventBusSubscriber is intended to automatically register event handlers,
        // we register it manually here to ensure reliability, especially for the server tick event.
        MinecraftForge.EVENT_BUS.register(ForgeServerEvents.class);
    }
}