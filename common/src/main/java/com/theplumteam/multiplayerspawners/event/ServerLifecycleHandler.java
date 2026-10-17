package com.theplumteam.multiplayerspawners.event;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.config.ModConfig;
import com.theplumteam.multiplayerspawners.util.NearPlayers;
import com.theplumteam.multiplayerspawners.util.TorchTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.spongepowered.asm.mixin.MixinEnvironment;

/**
 * Server lifecycle hooks: the per-world config and the torch tracker.
 */
public class ServerLifecycleHandler {

    public static void onServerStarting(MinecraftServer server) {
        ModConfig.loadServer(server.getWorldPath(LevelResource.ROOT));
        if (Boolean.getBoolean("multiplayerspawners.mixinAudit")) {
            auditMixins();
        }
    }

    /**
     * Diagnostics for the server smoke test (-Dmultiplayerspawners.mixinAudit=true): makes Mixin
     * apply every pending mixin now, so that a target that moved fails at start instead of when a
     * player first breaks a spawner.
     */
    private static void auditMixins() {
        try {
            MixinEnvironment.getCurrentEnvironment().audit();
            Constants.LOG.info("Mixin audit passed");
        } catch (Throwable t) {
            Constants.LOG.error("Mixin audit failed", t);
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        TorchTracker.clear();
        NearPlayers.clear();
    }
}
