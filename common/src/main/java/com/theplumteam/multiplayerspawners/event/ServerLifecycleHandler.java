package com.theplumteam.multiplayerspawners.event;

import com.theplumteam.multiplayerspawners.config.ModConfig;
import com.theplumteam.multiplayerspawners.util.SpawnerLocationCache;
import com.theplumteam.multiplayerspawners.util.TorchTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Server lifecycle hooks: the per-world config and the caches.
 */
public class ServerLifecycleHandler {

    public static void onServerStarting(MinecraftServer server) {
        ModConfig.loadServer(server.getWorldPath(LevelResource.ROOT));
    }

    public static void onLevelLoad(ServerLevel level) {
        SpawnerLocationCache.scanAndPopulate(level);
    }

    public static void onServerStopping(MinecraftServer server) {
        SpawnerLocationCache.clear();
        TorchTracker.clear();
    }
}
