package com.theplumteam.multiplayerspawners.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** The screen shown, on whichever class holds it: 26.2 moved it from Minecraft to its Gui. */
public final class GuiAccess {

    private GuiAccess() {
    }

    public static Screen screen(Minecraft minecraft) {
        //? if >=26.2 {
        return minecraft.gui.screen();
        //?} else {
        /*return minecraft.screen;
        *///?}
    }

    public static void setScreen(Minecraft minecraft, Screen screen) {
        //? if >=26.2 {
        minecraft.gui.setScreen(screen);
        //?} else {
        /*minecraft.setScreen(screen);
        *///?}
    }
}
