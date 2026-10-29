package com.theplumteam.multiplayerspawners.client;

import com.theplumteam.multiplayerspawners.client.gui.GuiAccess;
import com.theplumteam.multiplayerspawners.client.gui.SettingsScreen;
import com.theplumteam.multiplayerspawners.network.SettingsPacket;
import net.minecraft.client.Minecraft;

/**
 * The client's end of the settings menu: opens it when the server sends the settings to open, and
 * shows the server's values in it after a change.
 */
public final class ClientSettings {

    private ClientSettings() {
    }

    public static void receive(SettingsPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (GuiAccess.screen(minecraft) instanceof SettingsScreen screen) {
            screen.update(packet.values());
        } else if (packet.open()) {
            GuiAccess.setScreen(minecraft, new SettingsScreen(packet.values()));
        }
    }
}
