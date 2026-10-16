package com.theplumteam.multiplayerspawners;

import com.theplumteam.multiplayerspawners.client.MultiplayerSpawnersForgeClient;
import com.theplumteam.multiplayerspawners.platform.ForgePlatformHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(Constants.MOD_ID)
public class MultiplayerSpawnersForge {

    public MultiplayerSpawnersForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        CommonClass.init();
        ForgePlatformHelper.register(modEventBus);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            MultiplayerSpawnersForgeClient.init(modEventBus);
        }

        MinecraftForge.EVENT_BUS.register(ForgeServerEvents.class);
    }
}
