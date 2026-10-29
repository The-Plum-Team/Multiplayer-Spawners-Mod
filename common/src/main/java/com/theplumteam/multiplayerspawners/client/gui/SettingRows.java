package com.theplumteam.multiplayerspawners.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The layout every row of settings shares: the setting's name beside its control on the right, what
 * it does below in dim text across the row, and a hairline under it. A name too long for the room
 * beside the control goes on to a second line.
 */
final class SettingRows {

    /** Room at the row's sides and above its first line. */
    static final int SIDE = 6;
    private static final int TOP = 6;
    /** The first line, as tall as the controls. */
    private static final int FIRST_LINE = 16;
    private static final int LINE_HEIGHT = 10;
    private static final int GAP = 3;
    private static final int BOTTOM = 7;
    private static final int LABEL_GAP = 8;
    private static final int MAX_LABEL_LINES = 2;
    /** No description runs past this many lines: the rest is cut. */
    private static final int MAX_DESCRIPTION_LINES = 3;

    /** What a row draws, laid out for its width. */
    record Layout(List<FormattedCharSequence> label, List<FormattedCharSequence> description, int height) {
    }

    private SettingRows() {
    }

    static Layout layout(Font font, Component label, Component description, int rowWidth, int controlWidth) {
        List<FormattedCharSequence> labelLines = limit(UiText.wrap(font, label, rowWidth - 2 * SIDE - controlWidth - LABEL_GAP, LABEL_GAP),
                MAX_LABEL_LINES);
        List<FormattedCharSequence> descriptionLines = limit(UiText.wrap(font, description, rowWidth - 2 * SIDE, 2 * SIDE),
                MAX_DESCRIPTION_LINES);
        int height = TOP + FIRST_LINE + (labelLines.size() - 1) * LINE_HEIGHT
                + (descriptionLines.isEmpty() ? 0 : GAP + descriptionLines.size() * LINE_HEIGHT - 1) + BOTTOM;
        return new Layout(labelLines, descriptionLines, height);
    }

    private static List<FormattedCharSequence> limit(List<FormattedCharSequence> lines, int max) {
        return lines.size() > max ? lines.subList(0, max) : lines;
    }

    /** The top of a control on the row's first line. */
    static int controlTop(int rowTop) {
        return rowTop + TOP;
    }

    /** Draws the row: its highlight under the mouse, the hairline under it, its name and description. */
    static void drawRow(GuiGraphics graphics, Font font, int x, int y, int width, Layout layout, boolean hovered, boolean enabled) {
        if (hovered) {
            graphics.fill(x, y, x + width, y + layout.height() - 1, Surfaces.ROW_HOVER);
        }
        graphics.fill(x, y + layout.height() - 1, x + width, y + layout.height(), Surfaces.ROW_LINE);
        int textX = x + SIDE;
        int lineY = y + TOP + (FIRST_LINE - 8) / 2 + 1;
        int labelColor = !enabled ? Surfaces.TEXT_DISABLED : hovered ? Surfaces.TEXT : Surfaces.TEXT_LABEL;
        for (FormattedCharSequence line : layout.label()) {
            UiText.draw(graphics, font, line, textX, lineY, labelColor);
            lineY += LINE_HEIGHT;
        }
        lineY = y + TOP + FIRST_LINE + (layout.label().size() - 1) * LINE_HEIGHT + GAP;
        for (FormattedCharSequence line : layout.description()) {
            UiText.draw(graphics, font, line, textX, lineY, enabled ? Surfaces.TEXT_DIM : Surfaces.TEXT_DISABLED);
            lineY += LINE_HEIGHT;
        }
    }
}
