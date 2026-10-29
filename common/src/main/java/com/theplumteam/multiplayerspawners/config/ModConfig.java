package com.theplumteam.multiplayerspawners.config;

import com.theplumteam.multiplayerspawners.Constants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration of MultiplayerSpawners.
 * <p>
 * The files keep the layout and names the mod used with Forge's config system, so existing
 * settings carry over: the server file is {@code <world>/serverconfig/multiplayerspawners-server.toml}
 * and the client file is {@code config/multiplayerspawners-client.toml}. Only the small subset of
 * TOML those files use is supported: {@code [section]} headers, {@code #} comments and
 * {@code key = value} pairs holding booleans or integers.
 */
public final class ModConfig {

    public static final ServerConfig SERVER = new ServerConfig();
    public static final ClientConfig CLIENT = new ClientConfig();
    public static boolean isClientConfigLoaded = false;

    private static Path serverFile;

    private ModConfig() {
    }

    public static final class ServerConfig extends Spec {
        public final BooleanValue enableGroupDisabling;
        public final IntValue groupDisablingRadius;
        public final IntValue torchExtinguishDelaySeconds;
        public final BooleanValue allowSpawnerBreakMessage;
        public final BooleanValue breakWithSpawnerTools;
        public final IntValue requiredBreakAttempts;
        public final BooleanValue treatPlacedSpawnersAsVanilla;
        public final IntValue spawnerRespawnMinutes;
        public final BooleanValue showNotices;
        public final IntValue oneShotSpawnerRange;
        public final BooleanValue spawnEggsChangeSpawners;

        private ServerConfig() {
            push("general", "MultiplayerSpawners Configuration");
            enableGroupDisabling = define("enableGroupDisabling", true,
                    "Enable group disabling: When one player disables a spawner, also disable spawners for all players within the specified radius");
            groupDisablingRadius = defineInRange("groupDisablingRadius", 20, 1, 100,
                    "Radius in blocks to search for other players when group disabling is enabled",
                    "Players within this radius will have their spawners disabled when someone disables a spawner");
            treatPlacedSpawnersAsVanilla = define("treatPlacedSpawnersAsVanilla", true,
                    "If true, spawners placed by players (not generated naturally) will behave like Vanilla spawners.",
                    "They will NOT extinguish torches and can be broken instantly by everyone.",
                    "Use /multiplayerspawners toggle_placed_vanilla to change this in-game.");
            spawnerRespawnMinutes = defineInRange("spawnerRespawnMinutes", 0, 0, 43200,
                    "Minutes after which a spawner a player disabled becomes active again for that player.",
                    "Counted in game time, while the server runs. 0 = never: a disabled spawner stays disabled.",
                    "Use /multiplayerspawners respawn <minutes> to change this in-game.");
            showNotices = define("showNotices", true,
                    "If false, the mod sends no informational chat messages (a spawner was disabled for you or nearby",
                    "players, when it comes back, when it ran out). The message with the break link is still sent.",
                    "Use /multiplayerspawners toggle_notices to change this in-game.");
            oneShotSpawnerRange = defineInRange("oneShotSpawnerRange", 16, 2, 64,
                    "Single-use spawners (block tag multiplayerspawners:one_shot_spawners, such as Quark's monster box",
                    "and Apotheosis' boss spawner) are used up for the players within this many blocks when they",
                    "trigger, and do not trigger again while only such players are this near.");
            spawnEggsChangeSpawners = define("spawnEggsChangeSpawners", true,
                    "If true, any player can change a spawner's mob with a spawn egg, as in vanilla. A spawner is one",
                    "block for the whole server, so the new mob spawns for every player.",
                    "If false, only Creative mode players can.",
                    "Use /multiplayerspawners toggle_spawn_eggs to toggle this setting in-game.");

            push("breaking", "Breaking Mechanics");
            requiredBreakAttempts = defineInRange("requiredBreakAttempts", 2, 1, 10,
                    "How many interactions are required to get the break message.",
                    "1 = One-hit mode. First hit disables AND shows the break message immediately.",
                    "2 = Standard Mod behavior. First hit disables, second hit shows the break message.",
                    "Values higher than 2 are currently treated as 2.");
            allowSpawnerBreakMessage = define("allowSpawnerBreakMessage", true,
                    "If true, players receive a clickable message allowing them to break disabled spawners.",
                    "If false, only Creative mode players can break disabled spawners.",
                    "Use /multiplayerspawners toggle_break_message to toggle this setting in-game.");
            breakWithSpawnerTools = define("breakWithSpawnerTools", false,
                    "If true, a player holding an item of the item tag multiplayerspawners:breaks_spawners breaks a",
                    "spawner like vanilla, for everyone (the tool's own mod decides what drops). Add the spawner-moving",
                    "tools of your mods to that tag with a datapack. With allowSpawnerBreakMessage = false, spawners can",
                    "then only be broken with those tools (or in Creative).",
                    "Use /multiplayerspawners toggle_break_tools to toggle this setting in-game.");

            push("torches", "Torch Extinguishing System");
            torchExtinguishDelaySeconds = defineInRange("torchExtinguishDelaySeconds", 600, 0, 3600,
                    "How many seconds a torch has before being extinguished by a nearby spawner",
                    "Torches placed within 7 blocks of a spawner will be extinguished after this delay",
                    "Set to 0 to disable torch extinguishing entirely",
                    "Minimum: 0 seconds, Maximum: 3600 seconds (1 hour)");
        }
    }

    public static final class ClientConfig extends Spec {
        public final BooleanValue enableDebugLogging;

        private ClientConfig() {
            push("client", "Client-side Configuration");
            enableDebugLogging = define("enableDebugLogging", false,
                    "Enable detailed debug logging for troubleshooting visual issues.");
        }
    }

    /**
     * Loads (or creates) the client file. Called once at mod initialisation.
     */
    public static void loadClient(Path configDir) {
        CLIENT.load(configDir.resolve(Constants.MOD_ID + "-client.toml"));
        isClientConfigLoaded = true;
    }

    /**
     * Loads (or creates) the server file of the world that is starting.
     */
    public static void loadServer(Path worldDir) {
        serverFile = worldDir.resolve("serverconfig").resolve(Constants.MOD_ID + "-server.toml");
        SERVER.load(serverFile);
    }

    /**
     * Writes the current server values back to the world's file, after a command changed them.
     */
    public static void saveServer() {
        if (serverFile == null) {
            Constants.LOG.warn("Could not save configuration: no world is loaded.");
            return;
        }
        SERVER.save(serverFile);
    }

    public abstract static class Value<T> {
        final String section;
        final String key;
        final T defaultValue;
        final List<String> comments;
        T value;

        Value(String section, String key, T defaultValue, List<String> comments) {
            this.section = section;
            this.key = key;
            this.defaultValue = defaultValue;
            this.comments = comments;
            this.value = defaultValue;
        }

        public T get() {
            return value;
        }

        /** The setting's name in the file. */
        public String key() {
            return key;
        }

        void reset() {
            value = defaultValue;
        }

        public void set(T newValue) {
            value = newValue;
        }

        abstract boolean parse(String text);
    }

    public static final class BooleanValue extends Value<Boolean> {
        BooleanValue(String section, String key, boolean defaultValue, List<String> comments) {
            super(section, key, defaultValue, comments);
        }

        @Override
        boolean parse(String text) {
            if (text.equals("true") || text.equals("false")) {
                value = Boolean.parseBoolean(text);
                return true;
            }
            return false;
        }
    }

    public static final class IntValue extends Value<Integer> {
        final int min;
        final int max;

        IntValue(String section, String key, int defaultValue, int min, int max, List<String> comments) {
            super(section, key, defaultValue, comments);
            this.min = min;
            this.max = max;
        }

        public int min() {
            return min;
        }

        public int max() {
            return max;
        }

        @Override
        public void set(Integer newValue) {
            super.set(Math.max(min, Math.min(max, newValue)));
        }

        @Override
        boolean parse(String text) {
            try {
                int parsed = Integer.parseInt(text);
                if (parsed < min || parsed > max) {
                    return false;
                }
                value = parsed;
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }

    abstract static class Spec {
        private final Map<String, String> sectionComments = new LinkedHashMap<>();
        private final List<Value<?>> values = new ArrayList<>();
        private String section;

        void push(String name, String comment) {
            section = name;
            sectionComments.put(name, comment);
        }

        BooleanValue define(String key, boolean defaultValue, String... comments) {
            BooleanValue value = new BooleanValue(section, key, defaultValue, List.of(comments));
            values.add(value);
            return value;
        }

        IntValue defineInRange(String key, int defaultValue, int min, int max, String... comments) {
            IntValue value = new IntValue(section, key, defaultValue, min, max, List.of(comments));
            values.add(value);
            return value;
        }

        void load(Path file) {
            values.forEach(Value::reset);
            boolean complete = false;
            if (Files.isRegularFile(file)) {
                try {
                    complete = read(Files.readAllLines(file, StandardCharsets.UTF_8));
                } catch (IOException e) {
                    Constants.LOG.error("Could not read {}; using defaults", file, e);
                }
            }
            // Missing, invalid or out-of-range entries are rewritten with their defaults.
            if (!complete) {
                save(file);
            }
        }

        private boolean read(List<String> lines) {
            Map<String, String> found = new HashMap<>();
            String current = "";
            for (String raw : lines) {
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    current = line.substring(1, line.length() - 1).strip();
                    continue;
                }
                int equals = line.indexOf('=');
                if (equals > 0) {
                    String key = line.substring(0, equals).strip();
                    String text = line.substring(equals + 1).strip();
                    int comment = text.indexOf('#');
                    if (comment >= 0) {
                        text = text.substring(0, comment).strip();
                    }
                    found.put(current + "." + key, text);
                }
            }
            boolean complete = true;
            for (Value<?> value : values) {
                String text = found.get(value.section + "." + value.key);
                if (text == null || !value.parse(text)) {
                    complete = false;
                }
            }
            return complete;
        }

        void save(Path file) {
            StringBuilder out = new StringBuilder();
            for (Map.Entry<String, String> entry : sectionComments.entrySet()) {
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append('#').append(entry.getValue()).append('\n');
                out.append('[').append(entry.getKey()).append("]\n");
                for (Value<?> value : values) {
                    if (!value.section.equals(entry.getKey())) {
                        continue;
                    }
                    for (String comment : value.comments) {
                        out.append("\t#").append(comment).append('\n');
                    }
                    if (value instanceof IntValue range) {
                        out.append("\t#Range: ").append(range.min).append(" ~ ").append(range.max).append('\n');
                    }
                    out.append('\t').append(value.key).append(" = ").append(value.value).append('\n');
                }
            }
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                Constants.LOG.error("Could not write {}", file, e);
            }
        }
    }
}
