package com.theplumteam.multiplayerspawners.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Moves and scales GUI drawing on whichever pose stack the version has: 1.21.6 made it a
 * two-dimensional {@code Matrix3x2fStack}, with its own push and pop, and draws the GUI from a
 * deferred state ordered by stratum instead of batches flushed in order.
 */
public final class GuiPose {

    private GuiPose() {
    }

    public static void push(GuiGraphics graphics) {
        //? if >=1.21.6 {
        graphics.pose().pushMatrix();
        //?} else {
        /*graphics.pose().pushPose();
        *///?}
    }

    public static void pop(GuiGraphics graphics) {
        //? if >=1.21.6 {
        graphics.pose().popMatrix();
        //?} else {
        /*graphics.pose().popPose();
        *///?}
    }

    public static void translate(GuiGraphics graphics, float x, float y) {
        //? if >=1.21.6 {
        graphics.pose().translate(x, y);
        //?} else {
        /*graphics.pose().translate(x, y, 0.0f);
        *///?}
    }

    public static void scale(GuiGraphics graphics, float x, float y) {
        //? if >=1.21.6 {
        graphics.pose().scale(x, y);
        //?} else {
        /*graphics.pose().scale(x, y, 1.0f);
        *///?}
    }

    /**
     * Draws later text in front of what was drawn at the same depth before 1.21.6, so a glyph is
     * never hidden by its own shadow; later GUI states are drawn in order from 1.21.6.
     */
    public static void lift(GuiGraphics graphics) {
        //? if <1.21.6 {
        /*graphics.pose().translate(0.0f, 0.0f, 0.03f);
        *///?}
    }
}
