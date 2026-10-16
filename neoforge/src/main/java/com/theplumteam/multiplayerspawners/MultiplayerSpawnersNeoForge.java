package com.theplumteam.multiplayerspawners;

import com.theplumteam.multiplayerspawners.client.MultiplayerSpawnersNeoForgeClient;
import com.theplumteam.multiplayerspawners.network.NeoForgeNetworkHandler;
import com.theplumteam.multiplayerspawners.platform.NeoForgePlatformHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Constants.MOD_ID)
public class MultiplayerSpawnersNeoForge {

    public MultiplayerSpawnersNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        CommonClass.init();
        NeoForgePlatformHelper.register(modEventBus);
        modEventBus.addListener(NeoForgeNetworkHandler::registerPayloads);

        //? if >=1.21.9 {
        boolean client = FMLEnvironment.getDist() == Dist.CLIENT;
        //?} else {
        /*boolean client = FMLEnvironment.dist == Dist.CLIENT;
        *///?}
        if (client) {
            MultiplayerSpawnersNeoForgeClient.init(modEventBus);
        }

        NeoForge.EVENT_BUS.register(NeoForgeServerEvents.class);
    }
}
