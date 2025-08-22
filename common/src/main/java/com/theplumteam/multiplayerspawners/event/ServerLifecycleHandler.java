package com.theplumteam.multiplayerspawners.event;
import com.theplumteam.multiplayerspawners.util.SpawnerLocationCache;
import com.theplumteam.multiplayerspawners.util.TorchTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
/**
Handles server-side lifecycle events for managing caches.
*/
public class ServerLifecycleHandler {
public static void onLevelLoad(ServerLevel level) {
SpawnerLocationCache.scanAndPopulate(level);
}
public static void onServerStopping(MinecraftServer server) {
SpawnerLocationCache.clear();
TorchTracker.clear();
}
}