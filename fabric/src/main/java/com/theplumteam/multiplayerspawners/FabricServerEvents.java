package com.theplumteam.multiplayerspawners;

import com.theplumteam.multiplayerspawners.event.CommandHandler;
import com.theplumteam.multiplayerspawners.event.PlayerEventHandler;
import com.theplumteam.multiplayerspawners.event.ServerLifecycleHandler;
import com.theplumteam.multiplayerspawners.util.TorchTracker;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class FabricServerEvents {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                CommandHandler.register(dispatcher));

        // A first full sync on login and respawn. FabricChunkSyncMixin syncs each chunk as it is
        // sent, which covers walking, teleporting and dimension changes.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                PlayerEventHandler.onPlayerJoin(handler.getPlayer()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                PlayerEventHandler.onPlayerRespawn(newPlayer));
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (oldPlayer.level() != newPlayer.level()) {
                PlayerEventHandler.onPlayerChangeDimension(newPlayer);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(TorchTracker::onServerTick);
        ServerLifecycleEvents.SERVER_STARTING.register(ServerLifecycleHandler::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerLifecycleHandler::onServerStopping);
    }
}
