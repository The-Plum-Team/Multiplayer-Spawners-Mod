package com.theplumteam.multiplayerspawners.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Text on the menu's dark faces, with the pixel icons' drop shadow: a dark grey one pixel down and
 * right. Minecraft's own shadow is the text's colour at a quarter of its brightness, which on these
 * faces is almost the face itself and disappears.
 */
public final class UiText {

    static final int SHADOW = 0xFF2A2A2A;

    private UiText() {
    }

    public static void draw(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        draw(graphics, font, Component.literal(text), x, y, color);
    }

    public static void draw(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
        graphics.drawString(font, text, x + 1, y + 1, (color & 0xFF000000) | (SHADOW & 0x00FFFFFF), false);
        GuiPose.push(graphics);
        GuiPose.lift(graphics);
        graphics.drawString(font, text, x, y, color, false);
        GuiPose.pop(graphics);
    }

    public static void draw(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color) {
        graphics.drawString(font, text, x + 1, y + 1, (color & 0xFF000000) | (SHADOW & 0x00FFFFFF), false);
        GuiPose.push(graphics);
        GuiPose.lift(graphics);
        graphics.drawString(font, text, x, y, color, false);
        GuiPose.pop(graphics);
    }

    /** Draws {@code text} cut short with "..." when it is wider than {@code room}. */
    public static void drawFitted(GuiGraphics graphics, Font font, String text, int x, int y, int room, int color) {
        draw(graphics, font, fit(font, text, room), x, y, color);
    }

    public static void drawCentered(GuiGraphics graphics, Font font, String text, int centerX, int y, int color) {
        draw(graphics, font, text, centerX - font.width(text) / 2, y, color);
    }

    /** {@code text}, cut short with "..." when it is wider than {@code room}. */
    public static String fit(Font font, String text, int room) {
        if (font.width(text) <= room) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, room - font.width("..."))) + "...";
    }

    /**
     * {@code text} broken into lines no wider than {@code width}, by the game's own line breaking,
     * which also breaks the languages written without spaces. A line would never start with a
     * closing mark there ("。", "、", ")"...): the text then may run up to {@code slack} wider to keep
     * the mark with its word, as Japanese and Chinese set it. The game measures Arabic before it
     * joins its letters, so its lines can come out wider than asked: they are broken narrower until
     * they fit.
     */
    public static List<FormattedCharSequence> wrap(Font font, Component text, int width, int slack) {
        int room = width;
        List<FormattedCharSequence> lines = font.split(text, room);
        for (int attempt = 0; attempt < 4; attempt++) {
            int over = widest(font, lines) - width;
            if (over <= 0 || room - over - 2 < width / 2) {
                break;
            }
            room -= over + 2;
            lines = font.split(text, room);
        }
        for (int line = 1; line < lines.size(); line++) {
            if (startsWithClosingMark(lines.get(line))) {
                List<FormattedCharSequence> wider = font.split(text, room + slack);
                return widest(font, wider) <= width + slack ? wider : lines;
            }
        }
        return lines;
    }

    private static int widest(Font font, List<FormattedCharSequence> lines) {
        int widest = 0;
        for (FormattedCharSequence line : lines) {
            widest = Math.max(widest, font.width(line));
        }
        return widest;
    }

    private static boolean startsWithClosingMark(FormattedCharSequence line) {
        StringBuilder first = new StringBuilder();
        line.accept((index, style, codePoint) -> {
            first.appendCodePoint(codePoint);
            return false;
        });
        return first.length() > 0 && CLOSING_MARKS.indexOf(first.charAt(0)) >= 0;
    }

    private static final String CLOSING_MARKS = "。、，．！？：；）」』】〉》・ー.,!?:;)";
}
