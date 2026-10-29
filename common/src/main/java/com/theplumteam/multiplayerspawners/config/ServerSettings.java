package com.theplumteam.multiplayerspawners.config;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The server settings an operator can change from the settings menu, by the key of each one in
 * the server file. Every value travels as an int (a toggle is 0 or 1) and is checked here against
 * the setting's own range before it is applied, so a packet can never set what the file could not
 * hold. Commands that change one setting keep working beside the menu.
 */
public final class ServerSettings {

    /** The breaking setting the menu shows as a toggle: one hit, or the usual two. */
    public static final String BREAK_ATTEMPTS = "requiredBreakAttempts";

    private static final List<ModConfig.Value<?>> EDITABLE = List.of(
            ModConfig.SERVER.enableGroupDisabling,
            ModConfig.SERVER.groupDisablingRadius,
            ModConfig.SERVER.spawnerRespawnMinutes,
            ModConfig.SERVER.oneShotSpawnerRange,
            ModConfig.SERVER.allowSpawnerBreakMessage,
            ModConfig.SERVER.requiredBreakAttempts,
            ModConfig.SERVER.breakWithSpawnerTools,
            ModConfig.SERVER.spawnEggsChangeSpawners,
            ModConfig.SERVER.treatPlacedSpawnersAsVanilla,
            ModConfig.SERVER.torchExtinguishDelaySeconds,
            ModConfig.SERVER.showNotices);

    private ServerSettings() {
    }

    /** The current value of every editable setting, in the menu's order. */
    public static Map<String, Integer> snapshot() {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (ModConfig.Value<?> value : EDITABLE) {
            values.put(value.key, asInt(value));
        }
        return values;
    }

    /**
     * Applies one change an operator made in the menu and saves the file.
     *
     * @return whether the key names an editable setting and the value fits it
     */
    public static boolean apply(MinecraftServer server, ServerPlayer player, String key, int newValue) {
        ModConfig.Value<?> value = find(key);
        if (value == null) {
            return false;
        }
        int oldValue = asInt(value);
        if (value instanceof ModConfig.BooleanValue toggle) {
            if (newValue != 0 && newValue != 1) {
                return false;
            }
            toggle.set(newValue == 1);
        } else if (value instanceof ModConfig.IntValue range) {
            if (newValue < range.min || newValue > range.max) {
                return false;
            }
            range.set(newValue);
        } else {
            return false;
        }
        if (oldValue == newValue) {
            return true;
        }
        ModConfig.saveServer();
        Object shown = value instanceof ModConfig.BooleanValue ? (Object) (newValue == 1) : newValue;
        Object before = value instanceof ModConfig.BooleanValue ? (Object) (oldValue == 1) : oldValue;
        Constants.LOG.info("{} changed {} from {} to {} in the settings menu", player.getName().getString(), key, before, shown);
        if (value == ModConfig.SERVER.treatPlacedSpawnersAsVanilla) {
            // Player-placed spawners change their look for everyone at once, as the command does.
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                NetworkHandler.getInstance().sendAllSpawnerStatesToPlayer(online);
            }
        }
        return true;
    }

    private static ModConfig.Value<?> find(String key) {
        for (ModConfig.Value<?> value : EDITABLE) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return null;
    }

    private static int asInt(ModConfig.Value<?> value) {
        Object current = value.get();
        return current instanceof Boolean on ? (on ? 1 : 0) : (Integer) current;
    }
}
