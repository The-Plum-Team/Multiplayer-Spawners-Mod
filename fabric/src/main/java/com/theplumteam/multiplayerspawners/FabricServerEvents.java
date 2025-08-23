package com.theplumteam.multiplayerspawners;

import com.theplumteam.multiplayerspawners.event.CommandHandler;
import com.theplumteam.multiplayerspawners.event.PlayerEventHandler;
import com.theplumteam.multiplayerspawners.event.ServerLifecycleHandler;
import com.theplumteam.multiplayerspawners.util.TorchTracker;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class FabricServerEvents {

    public static void register() {
        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            CommandHandler.register(dispatcher);
        });

        // Register event handlers
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PlayerEventHandler.onPlayerJoin(handler.getPlayer());
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            PlayerEventHandler.onPlayerRespawn(newPlayer);
        });

        // For dimension changes, we use copy from event
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (oldPlayer.serverLevel() != newPlayer.serverLevel()) {
                PlayerEventHandler.onPlayerChangeDimension(newPlayer);
            }
        });

        // Register server lifecycle events for new mechanic
        ServerTickEvents.END_SERVER_TICK.register(TorchTracker::onServerTick);
        ServerWorldEvents.LOAD.register((server, level) -> ServerLifecycleHandler.onLevelLoad(level));
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerLifecycleHandler::onServerStopping);
    }
}
