package com.theplumteam.multiplayerspawners.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Configuration class for MultiplayerSpawners mod
 * Uses Forge Config API Port for multiloader compatibility
 */
public class ModConfig {

    public static final ServerConfig SERVER;
    public static final ModConfigSpec SERVER_SPEC;

    public static final ClientConfig CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;
    public static boolean isClientConfigLoaded = false;

    static {
        final Pair<ServerConfig, ModConfigSpec> serverSpecPair = new ModConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = serverSpecPair.getRight();
        SERVER = serverSpecPair.getLeft();

        final Pair<ClientConfig, ModConfigSpec> clientSpecPair = new ModConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT_SPEC = clientSpecPair.getRight();
        CLIENT = clientSpecPair.getLeft();
    }

    public static class ServerConfig {
        public final ModConfigSpec.BooleanValue enableGroupDisabling;
        public final ModConfigSpec.IntValue groupDisablingRadius;
        public final ModConfigSpec.IntValue torchExtinguishDelaySeconds;
        public final ModConfigSpec.BooleanValue allowSpawnerBreakMessage;
        public final ModConfigSpec.IntValue requiredBreakAttempts;

        // Player-placed spawner behavior
        public final ModConfigSpec.BooleanValue treatPlacedSpawnersAsVanilla;

        public ServerConfig(ModConfigSpec.Builder builder) {
            builder.comment("MultiplayerSpawners Configuration")
                    .push("general");

            enableGroupDisabling = builder
                    .comment("Enable group disabling: When one player disables a spawner, also disable spawners for all players within the specified radius")
                    .define("enableGroupDisabling", true);

            groupDisablingRadius = builder
                    .comment("Radius in blocks to search for other players when group disabling is enabled",
                            "Players within this radius will have their spawners disabled when someone disables a spawner")
                    .defineInRange("groupDisablingRadius", 20, 1, 100);

            treatPlacedSpawnersAsVanilla = builder
                    .comment("If true, spawners placed by players (not generated naturally) will behave like Vanilla spawners.",
                            "They will NOT extinguish torches and can be broken instantly by everyone.",
                            "Use /multiplayerspawners toggle_placed_vanilla to change this in-game.")
                    .define("treatPlacedSpawnersAsVanilla", true);

            builder.pop();

            // NEW SECTION
            builder.comment("Breaking Mechanics")
                    .push("breaking");

            requiredBreakAttempts = builder
                    .comment("How many interactions are required to get the break message.",
                             "1 = One-hit mode. First hit disables AND shows the break message immediately.",
                             "2 = Standard Mod behavior. First hit disables, second hit shows the break message.",
                             "Values higher than 2 are currently treated as 2.")
                    .defineInRange("requiredBreakAttempts", 2, 1, 10);

            allowSpawnerBreakMessage = builder
                    .comment("If true, players receive a clickable message allowing them to break disabled spawners.",
                            "If false, only Creative mode players can break disabled spawners.",
                            "Use /multiplayerspawners toggle_break_message to toggle this setting in-game.")
                    .define("allowSpawnerBreakMessage", true);

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
        public final ModConfigSpec.BooleanValue enableDebugLogging;

        public ClientConfig(ModConfigSpec.Builder builder) {
            builder.comment("Client-side Configuration")
                    .push("client");

            enableDebugLogging = builder
                    .comment("Enable detailed debug logging for troubleshooting visual issues.")
                    .define("enableDebugLogging", false);

            builder.pop();
        }
    }
}