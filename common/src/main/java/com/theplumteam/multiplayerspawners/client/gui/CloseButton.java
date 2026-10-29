package com.theplumteam.multiplayerspawners.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * The X at the right end of the dialog's header: a square in the header's colours with a light
 * border, lightening under the mouse. Its message is the close label, also its tooltip, so it is
 * narrated by it.
 */
public class CloseButton extends UiButton {

    public static final int SIZE = 17;

    private static final int BORDER = 0xFF6F8FB0;
    private static final int BORDER_HOVER = 0xFF9DB8D6;
    private static final int FACE = 0xFF3A4656;
    private static final int FACE_HOVER = 0xFF4A5A70;

    private final Runnable onClose;

    public CloseButton(int x, int y, Component label, Runnable onClose) {
        super(x, y, SIZE, SIZE, label);
        this.onClose = onClose;
        setTooltip(Tooltip.create(label));
    }

    @Override
    protected void press() {
        onClose.run();
    }

    @Override
    protected void draw(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = lit();
        int x = getX();
        int y = getY();
        graphics.fill(x, y, x + width, y + height, Surfaces.OUTLINE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, hovered ? BORDER_HOVER : BORDER);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, hovered ? FACE_HOVER : FACE);
        PixelIcon.drawCentered(graphics, PixelIcon.CLOSE, x, y, width, height, Surfaces.TEXT);
    }
}
