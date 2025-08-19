package com.theplumteam.multiplayerspawners.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Configuration class for MultiplayerSpawners mod
 * Uses Forge Config API Port for multiloader compatibility
 */
public class ModConfig {

    public static final ServerConfig SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;

    static {
        final Pair<ServerConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = specPair.getRight();
        SERVER = specPair.getLeft();
    }

    public static class ServerConfig {
        public final ForgeConfigSpec.BooleanValue enableGroupDisabling;
        public final ForgeConfigSpec.IntValue groupDisablingRadius;

        public ServerConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("MultiplayerSpawners Configuration")
                    .push("general");

            enableGroupDisabling = builder
                    .comment("Enable group disabling: When one player disables a spawner, also disable spawners for all players within the specified radius")
                    .define("enableGroupDisabling", true);

            groupDisablingRadius = builder
                    .comment("Radius in blocks to search for other players when group disabling is enabled",
                            "Players within this radius will have their spawners disabled when someone disables a spawner")
                    .defineInRange("groupDisablingRadius", 20, 1, 100);

            builder.pop();
        }
    }
}