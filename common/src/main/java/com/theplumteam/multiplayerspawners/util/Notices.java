package com.theplumteam.multiplayerspawners.util;

import com.theplumteam.multiplayerspawners.config.ModConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The mod's informational chat messages, which a server can turn off ({@code showNotices}).
 * The message that offers to break a spawner is not a notice: it is the way to break one.
 */
public final class Notices {

    private Notices() {
    }

    public static void send(ServerPlayer player, Component message) {
        if (ModConfig.SERVER.showNotices.get()) {
            player.sendSystemMessage(message);
        }
    }
}
