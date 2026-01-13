package com.theplumteam.multiplayerspawners;

import com.theplumteam.multiplayerspawners.event.CommandHandler;
import com.theplumteam.multiplayerspawners.event.PlayerEventHandler;
import com.theplumteam.multiplayerspawners.event.ServerLifecycleHandler;
import com.theplumteam.multiplayerspawners.util.TorchTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Constants.MOD_ID)
public class NeoForgeServerEvents {

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            PlayerEventHandler.onPlayerJoin(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            PlayerEventHandler.onPlayerChangeDimension(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            PlayerEventHandler.onPlayerRespawn(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandHandler.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        TorchTracker.onServerTick(event.getServer());
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            ServerLifecycleHandler.onLevelLoad(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ServerLifecycleHandler.onServerStopping(event.getServer());
    }
}
