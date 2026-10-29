package com.theplumteam.multiplayerspawners.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The menu's small icons, one GUI pixel per {@code '#'} with the text's dark drop shadow, so they
 * stay crisp at every scale.
 */
public final class PixelIcon {

    public static final String[] GEAR = {
            "....#....", ".#.###.#.", "..#####..", ".##...##.", "###...###",
            ".##...##.", "..#####..", ".#.###.#.", "....#...."};
    public static final String[] CLOSE = {
            "##.....##", "###...###", ".###.###.", "..#####..", "...###...",
            "..#####..", ".###.###.", "###...###", "##.....##"};
    /** A spawner's cage with a mob's eyes inside. */
    public static final String[] SPAWNER = {
            "#########", "#.#.#.#.#", "#########", "#.#.#.#.#", "#.##.##.#",
            "#.#.#.#.#", "#########", "#.#.#.#.#", "#########"};
    public static final String[] PICKAXE = {
            ".#####...", "#.....#..", "......##.", ".....#.#.", "....#...#",
            "...#....#", "..#......", ".#.......", "#........"};
    public static final String[] TORCH = {
            "....#....", "...###...", "...###...", "....#....", "...###...",
            "...###...", "...###...", "...###...", "...###..."};
    public static final String[] CHAT = {
            "#########", "#.......#", "#.#####.#", "#.......#", "#.###...#",
            "#.......#", "#########", ".##......", "#........"};
    public static final String[] CHECK = {
            "........#", ".......##", "......##.", "#....##..", "##..##...",
            ".####....", "..##.....", ".........", "........."};

    private PixelIcon() {
    }

    public static int width(String[] icon) {
        return icon.length == 0 ? 0 : icon[0].length();
    }

    /** Draws an icon with its top-left corner at {@code (x, y)}, with the dark drop shadow. */
    public static void draw(GuiGraphics graphics, String[] icon, int x, int y, int color) {
        drawPass(graphics, icon, x + 1, y + 1, (color & 0xFF000000) | (UiText.SHADOW & 0x00FFFFFF));
        drawPass(graphics, icon, x, y, color);
    }

    /** Draws an icon centred on a rectangle. */
    public static void drawCentered(GuiGraphics graphics, String[] icon, int x, int y, int width, int height, int color) {
        draw(graphics, icon, x + (width - width(icon)) / 2, y + (height - icon.length) / 2, color);
    }

    private static void drawPass(GuiGraphics graphics, String[] icon, int x, int y, int color) {
        for (int row = 0; row < icon.length; row++) {
            String line = icon[row];
            int column = 0;
            while (column < line.length()) {
                if (line.charAt(column) != '#') {
                    column++;
                    continue;
                }
                // One fill per run of pixels on the row.
                int start = column;
                while (column < line.length() && line.charAt(column) == '#') {
                    column++;
                }
                graphics.fill(x + start, y + row, x + column, y + row + 1, color);
            }
        }
    }
}
