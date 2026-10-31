package com.theplumteam.multiplayerspawners.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A tab of the dialog's sidebar: a row across the sidebar with a pixel icon and the tab's name.
 * The selected tab is dark blue with a blue bar along its left edge and a notch in its right edge,
 * cut in the colour of the settings beside it so the tab points at them; the others are plain and
 * lighten under the mouse.
 */
public class TabButton extends UiButton {

    private static final int SELECTED_FACE = 0xFF2F3A4F;
    private static final int SELECTED_BAR = 0xFF4080FF;
    private static final int HOVER_FACE = 0xFF3C3D42;
    private static final int TEXT_IDLE = 0xFFB0B0B0;
    /** Half the height of the notch, which is also how deep it cuts. */
    private static final int NOTCH = 4;

    /** Where the name starts, from the tab's left edge: after the icon. */
    static final int TEXT_LEFT = 8 + 9 + 6;

    private final String[] icon;
    private final Runnable onSelect;
    private final boolean selected;
    private final int notchColor;

    /**
     * @param notchColor the colour of what lies right of the tab; the selected tab's notch is cut
     *                   in it, one unit past the tab's right edge so it also cuts the sidebar's edge
     */
    public TabButton(int x, int y, int width, int height, Component label, String[] icon, boolean selected,
                     int notchColor, Runnable onSelect) {
        super(x, y, width, height, label);
        this.icon = icon;
        this.selected = selected;
        this.notchColor = notchColor;
        this.onSelect = onSelect;
    }

    @Override
    protected void press() {
        if (!selected) {
            onSelect.run();
        }
    }

    @Override
    protected void draw(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        boolean hovered = !selected && lit();
        if (selected) {
            graphics.fill(x, y, x + width, y + height, SELECTED_FACE);
            graphics.fill(x, y, x + 2, y + height, SELECTED_BAR);
            int right = x + width + 1;
            int middle = y + height / 2;
            for (int i = 0; i < NOTCH; i++) {
                graphics.fill(right - (NOTCH - i), middle - i - 1, right, middle - i, notchColor);
                graphics.fill(right - (NOTCH - i), middle + i, right, middle + i + 1, notchColor);
            }
        } else if (hovered) {
            graphics.fill(x, y, x + width, y + height, HOVER_FACE);
        }
        int color = selected || hovered ? Surfaces.TEXT : TEXT_IDLE;
        PixelIcon.draw(graphics, icon, x + 8, y + (height - icon.length) / 2, color);
        int textX = x + TEXT_LEFT;
        UiText.drawFitted(graphics, Minecraft.getInstance().font, getMessage().getString(), textX, y + (height - 8) / 2,
                x + width - 4 - textX, color);
    }
}
