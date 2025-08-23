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

    public static final ClientConfig CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static boolean isClientConfigLoaded = false;

    static {
        final Pair<ServerConfig, ForgeConfigSpec> serverSpecPair = new ForgeConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = serverSpecPair.getRight();
        SERVER = serverSpecPair.getLeft();

        final Pair<ClientConfig, ForgeConfigSpec> clientSpecPair = new ForgeConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT_SPEC = clientSpecPair.getRight();
        CLIENT = clientSpecPair.getLeft();
    }

    public static class ServerConfig {
        public final ForgeConfigSpec.BooleanValue enableGroupDisabling;
        public final ForgeConfigSpec.IntValue groupDisablingRadius;
        public final ForgeConfigSpec.IntValue torchExtinguishDelaySeconds;

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

            builder.comment("Torch Extinguishing System")
                    .push("torches");

            torchExtinguishDelaySeconds = builder
                    .comment("How many seconds a torch has before being extinguished by a nearby spawner",
                            "Torches placed within 7 blocks of a spawner will be extinguished after this delay",
                            "Set to 0 to disable torch extinguishing entirely",
                            "Minimum: 0 seconds, Maximum: 3600 seconds (1 hour)")
                    .defineInRange("torchExtinguishDelaySeconds", 600, 0, 3600);

            builder.pop();
        }
    }

    public static class ClientConfig {
        public final ForgeConfigSpec.BooleanValue enableDebugLogging;

        public ClientConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("Client-side Configuration")
                    .push("client");

            enableDebugLogging = builder
                    .comment("Enable detailed debug logging for troubleshooting visual issues.")
                    .define("enableDebugLogging", false);

            builder.pop();
        }
    }
}