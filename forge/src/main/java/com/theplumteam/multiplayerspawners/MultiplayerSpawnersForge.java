package com.theplumteam.multiplayerspawners;

import com.theplumteam.multiplayerspawners.client.MultiplayerSpawnersForgeClient;
import com.theplumteam.multiplayerspawners.event.PlayerEventHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class MultiplayerSpawnersForge {

    public MultiplayerSpawnersForge() {

        // This method is invoked by the Forge mod loader when it is ready
        // to load your mod. You can access Forge and Common code in this
        // project.

        // Use Forge to bootstrap the Common mod.
        Constants.LOG.info("Hello Forge world! MultiplayerSpawnersForge has been loaded!");
        CommonClass.init();
        
        // Initialize client-side code only on client
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> MultiplayerSpawnersForgeClient::init);
        
        // Register event handlers
        MinecraftForge.EVENT_BUS.register(this);
    }
    
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            PlayerEventHandler.onPlayerJoin(serverPlayer);
        }
    }
    
    @SubscribeEvent
    public void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            PlayerEventHandler.onPlayerChangeDimension(serverPlayer);
        }
    }
    
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            PlayerEventHandler.onPlayerRespawn(serverPlayer);
        }
    }
}
