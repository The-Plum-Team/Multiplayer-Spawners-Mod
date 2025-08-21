package com.theplumteam.multiplayerspawners;

import com.theplumteam.multiplayerspawners.event.CommandHandler;
import com.theplumteam.multiplayerspawners.event.PlayerEventHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class MultiplayerSpawnersFabric implements ModInitializer {
    
    @Override
    public void onInitialize() {
        
        // This method is invoked by the Fabric mod loader when it is ready
        // to load your mod. You can access Fabric and Common code in this
        // project.

        // Use Fabric to bootstrap the Common mod.
        Constants.LOG.info("Hello Fabric world!");
        CommonClass.init();
        
        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            CommandHandler.register(dispatcher);
        });
        
        // Register event handlers
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PlayerEventHandler.onPlayerJoin(handler.getPlayer());
        });
        
        ServerPlayerEvents.ALLOW_DEATH.register((player, damageSource, damageAmount) -> {
            return true; // Allow death, we just want to hook the event
        });
        
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            PlayerEventHandler.onPlayerRespawn(newPlayer);
        });
        
        // For dimension changes, we use copy from event
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) { // This indicates a dimension change rather than death
                PlayerEventHandler.onPlayerChangeDimension(newPlayer);
            }
        });
    }
}
