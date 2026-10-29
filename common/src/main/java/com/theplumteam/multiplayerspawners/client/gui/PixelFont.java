package com.theplumteam.multiplayerspawners.client.gui;

import com.theplumteam.multiplayerspawners.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The menu's blocky pixel capitals for its title, from assets/multiplayerspawners/font/pixel.json:
 * Latin capitals, digits and the accented capitals of the mod's languages. Characters the font
 * lacks fall back to Minecraft's own. It is drawn in upper case at a whole number of screen pixels
 * per font pixel, so it stays crisp at every GUI scale.
 */
public final class PixelFont {

    //? if >=1.21.9 {
    private static final net.minecraft.network.chat.FontDescription FONT =
            new net.minecraft.network.chat.FontDescription.Resource(Constants.id("pixel"));
    //?} else {
    /*private static final net.minecraft.resources.ResourceLocation FONT = Constants.id("pixel");
    *///?}

    /** Height of the capitals, in font pixels. */
    public static final int CAPITALS_HEIGHT = 5;
    /** Distance from the text's origin to the middle of its capitals, in font pixels. */
    private static final float CAPITALS_MIDDLE = 4.5f;

    private PixelFont() {
    }

    public static Component text(Component text) {
        return Component.literal(text.getString().toUpperCase(Locale.ROOT)).withStyle(style -> style.withFont(FONT));
    }

    /** The window's GUI scale, at least 1. */
    static double guiScale() {
        // A double before 1.21.8 and an int from it; both convert.
        return Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
    }

    /**
     * Draws text with its capitals centred on {@code middleY}, starting at {@code x}, at the largest
     * whole number of screen pixels per font pixel that keeps its capitals at most
     * {@code capitalsHeight} GUI units tall and its width within {@code room}.
     *
     * @return the width drawn, in GUI units
     */
    public static int draw(GuiGraphics graphics, Component label, int x, float middleY, float capitalsHeight, int room, int color) {
        Font font = Minecraft.getInstance().font;
        Component text = text(label);
        int textWidth = Math.max(1, font.width(text));
        double guiScale = guiScale();
        int pixels = Math.max(1, (int) Math.floor(capitalsHeight / CAPITALS_HEIGHT * guiScale + 1e-4));
        while (pixels > 1 && textWidth * pixels / guiScale > room) {
            pixels--;
        }
        float scale = (float) (pixels / guiScale);
        if (textWidth * scale > room) {
            scale = room / (float) textWidth;
        }
        float y = Math.round((middleY - CAPITALS_MIDDLE * scale) * guiScale) / (float) guiScale;
        GuiPose.push(graphics);
        GuiPose.translate(graphics, x, y);
        GuiPose.scale(graphics, scale, scale);
        graphics.drawString(font, text, 1, 1, (color & 0xFF000000) | (UiText.SHADOW & 0x00FFFFFF), false);
        GuiPose.lift(graphics);
        graphics.drawString(font, text, 0, 0, color, false);
        GuiPose.pop(graphics);
        return (int) Math.ceil(textWidth * scale);
    }
}
