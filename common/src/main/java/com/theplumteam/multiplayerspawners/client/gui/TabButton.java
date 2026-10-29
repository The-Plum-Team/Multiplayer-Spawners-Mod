package com.theplumteam.multiplayerspawners.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A tab of the dialog's sidebar: a pixel icon and the tab's name. The selected tab has a slate
 * highlight with a brighter bar along its left edge, in the header's colours; the others are
 * plain and lighten under the mouse.
 */
public class TabButton extends UiButton {

    private static final int SELECTED_FACE = 0xFF4E6078;
    private static final int SELECTED_BAR = 0xFF6F8FB0;
    private static final int HOVER_FACE = 0xFF444549;
    private static final int TEXT_HOVER = 0xFFDDDDDD;
    private static final int TEXT_IDLE = 0xFFB0B0B0;

    /** Where the name starts, from the tab's left edge: after the icon. */
    static final int TEXT_LEFT = 8 + 9 + 6;

    private final String[] icon;
    private final Runnable onSelect;
    private final boolean selected;

    public TabButton(int x, int y, int width, int height, Component label, String[] icon, boolean selected, Runnable onSelect) {
        super(x, y, width, height, label);
        this.icon = icon;
        this.selected = selected;
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
        } else if (hovered) {
            graphics.fill(x, y, x + width, y + height, HOVER_FACE);
        }
        int color = selected ? Surfaces.TEXT : hovered ? TEXT_HOVER : TEXT_IDLE;
        PixelIcon.draw(graphics, icon, x + 8, y + (height - icon.length) / 2, color);
        int textX = x + TEXT_LEFT;
        UiText.drawFitted(graphics, Minecraft.getInstance().font, getMessage().getString(), textX, y + (height - 8) / 2,
                x + width - 4 - textX, color);
    }
}
