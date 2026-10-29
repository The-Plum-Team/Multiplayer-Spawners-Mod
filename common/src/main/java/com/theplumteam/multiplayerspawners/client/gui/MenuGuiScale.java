package com.theplumteam.multiplayerspawners.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Gives the menu its own GUI scale, chosen from the window size, so it looks the same whatever GUI
 * Scale the player picked, as The Plum Team's other menus do: a 1080p window gets a 640x360 canvas.
 *
 * <p>Only the window's current scale changes, while the menu is shown. The player's GUI Scale
 * option is never written and nothing goes through {@code resizeDisplay()}, which other mods react
 * to. Rendering, the mouse and scissoring all read the window's scale, so they follow the menu's
 * scale together; {@link #restore} puts back the scale the player's options give.
 */
public final class MenuGuiScale {

    /** The menu is laid out for a canvas of at least about this size... */
    private static final int TARGET_WIDTH = 520;
    private static final int TARGET_HEIGHT = 300;
    /** ...and looks as intended at this height, the canvas of a 1080p window at scale 3... */
    private static final int REFERENCE_HEIGHT = 360;
    /** ...and Minecraft allows no scale whose canvas is smaller than this. */
    private static final int MIN_WIDTH = 320;
    private static final int MIN_HEIGHT = 240;

    private MenuGuiScale() {
    }

    /** Gives the screen the menu's scale. Call first in its {@code init()}, which a resize runs again. */
    public static void enter(Minecraft minecraft, Screen screen) {
        apply(minecraft);
        screen.width = minecraft.getWindow().getGuiScaledWidth();
        screen.height = minecraft.getWindow().getGuiScaledHeight();
    }

    /**
     * Sets the window to the menu's scale for its size, again if another mod put the player's
     * scale back meanwhile.
     *
     * @return whether the scale changed, so the screen has to lay itself out again
     */
    public static boolean apply(Minecraft minecraft) {
        var window = minecraft.getWindow();
        int scale = forFramebuffer(window.getWidth(), window.getHeight());
        // A double before 1.21.8 and an int from it; both compare and convert.
        if (window.getGuiScale() == scale) {
            return false;
        }
        window.setGuiScale(scale);
        return true;
    }

    /** Puts back the scale the player's GUI Scale option gives, as {@code resizeDisplay()} computes it. */
    public static void restore(Minecraft minecraft) {
        var window = minecraft.getWindow();
        int scale = window.calculateScale(minecraft.options.guiScale().get(), minecraft.options.forceUnicodeFont().get());
        if (window.getGuiScale() != scale) {
            window.setGuiScale(scale);
        }
    }

    /**
     * Of the scales that still leave the target canvas, the one whose canvas comes nearest the
     * reference height: 640x360 at 720p, 1080p, 1440p and 4K. It is at least 2 wherever Minecraft
     * allows it, so text stays readable in small windows.
     */
    static int forFramebuffer(int width, int height) {
        int fitting = Math.min(width / TARGET_WIDTH, height / TARGET_HEIGHT);
        double ideal = Math.min((double) width / TARGET_WIDTH, (double) height / REFERENCE_HEIGHT);
        int nearest = (int) Math.floor(ideal);
        // Nearest by ratio: a canvas a fifth too tall is as far off as one a fifth too short.
        if (nearest + 1 <= fitting && ideal * ideal > nearest * (nearest + 1.0)) {
            nearest++;
        }
        int largestAllowed = Math.max(1, Math.min(width / MIN_WIDTH, height / MIN_HEIGHT));
        return Math.min(largestAllowed, Math.max(2, nearest));
    }
}
