package com.theplumteam.multiplayerspawners.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The menu's palette and its two surfaces, in the dark Bedrock style The Plum Team's mods share:
 * a raised face (a charcoal face in a thin grey border and a black outline, resting on a darker
 * lip) for buttons, knobs and the dialog itself, and a sunk well (a dark field with a shaded top
 * edge) for tracks.
 */
public final class Surfaces {

    public static final int OUTLINE = 0xFF0E0E0F;
    public static final int BORDER = 0xFF6E6F73;
    public static final int BORDER_HOVER = 0xFF8D8E93;
    public static final int BORDER_DISABLED = 0xFF46474A;
    public static final int FACE = 0xFF3A3B3E;
    public static final int FACE_HOVER = 0xFF4A4B4F;
    public static final int FACE_DISABLED = 0xFF2B2C2E;
    public static final int LIP = 0xFF1C1D1F;
    /** Height of the lip along the bottom of a raised surface. */
    public static final int LIP_HEIGHT = 3;

    public static final int WELL = 0xFF1E1F21;
    public static final int WELL_HOVER = 0xFF2A2B2E;
    private static final int WELL_SHADE = 0xFF151617;

    /** The dialog's face, its sidebar and the darker face of its content. */
    public static final int DIALOG_FACE = 0xFF2E2F32;
    public static final int SIDEBAR = 0xFF3A3B3E;
    public static final int SIDEBAR_EDGE = 0xFF55565B;
    public static final int CONTENT = 0xFF26272A;
    /** The line between two rows of settings, and a row under the mouse. */
    public static final int ROW_LINE = 0xFF323336;
    public static final int ROW_HOVER = 0xFF2E2F33;

    /** The green of a setting that is on, and of a slider's filled track. */
    public static final int ON = 0xFF3C8A3C;
    public static final int ON_HOVER = 0xFF46A046;

    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_LABEL = 0xFFE0E0E0;
    public static final int TEXT_DIM = 0xFFA0A0A4;
    public static final int TEXT_DISABLED = 0xFF7A7A7E;

    private Surfaces() {
    }

    /**
     * A raised surface: the outline, the lip along the bottom, then the border and the face resting
     * on the lip.
     *
     * @return the bottom edge of the face, above the lip
     */
    public static int raised(GuiGraphics graphics, int left, int top, int right, int bottom, int border, int face, int lipHeight) {
        graphics.fill(left, top, right, bottom, OUTLINE);
        int faceBottom = bottom - 1 - lipHeight;
        if (lipHeight > 0) {
            graphics.fill(left + 1, faceBottom, right - 1, bottom - 1, LIP);
        }
        graphics.fill(left + 1, top + 1, right - 1, faceBottom, border);
        graphics.fill(left + 2, top + 2, right - 2, faceBottom - 1, face);
        return faceBottom;
    }

    /** A sunk well: the outline, a border, the dark field and the shade along its top edge. */
    public static void well(GuiGraphics graphics, int left, int top, int right, int bottom, int border, int field) {
        graphics.fill(left, top, right, bottom, OUTLINE);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, border);
        graphics.fill(left + 2, top + 2, right - 2, bottom - 2, field);
        graphics.fill(left + 2, top + 2, right - 2, Math.min(bottom - 2, top + 4), WELL_SHADE);
    }
}
