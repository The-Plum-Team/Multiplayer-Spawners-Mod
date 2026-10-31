package com.theplumteam.multiplayerspawners.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.IntFunction;

/**
 * A slider over a list of values, in the menu's style: a sunk well, filled in the settings' green
 * up to a raised handle, with the chosen value written on it. It snaps to the listed values.
 */
public class StepSlider extends AbstractSliderButton {

    public static final int HEIGHT = 16;
    private static final int HANDLE_WIDTH = 8;
    private static final int FILL = 0xFF2E6A2E;

    private final int[] steps;
    private final IntFunction<Component> describe;
    private int index;

    /**
     * @param steps    the values the slider can take, in order
     * @param describe the text shown on the slider for a value
     */
    public StepSlider(int x, int y, int width, int[] steps, int current, IntFunction<Component> describe) {
        super(x, y, width, HEIGHT, Component.empty(), 0.0);
        this.steps = steps;
        this.describe = describe;
        setStep(current);
    }

    /** Only the part between these heights takes the mouse: the rest is scrolled out of view. */
    private int clipTop = Integer.MIN_VALUE;
    private int clipBottom = Integer.MAX_VALUE;

    public void clip(int top, int bottom) {
        clipTop = top;
        clipBottom = bottom;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseY >= clipTop && mouseY < clipBottom && super.isMouseOver(mouseX, mouseY);
    }

    // Before 1.21.4 a click is tested by clicked(), not by isMouseOver().
    //? if <1.21.4 {
    /*@Override
    protected boolean clicked(double mouseX, double mouseY) {
        return mouseY >= clipTop && mouseY < clipBottom && super.clicked(mouseX, mouseY);
    }
    *///?}

    /** The value chosen. */
    public int current() {
        return steps[index];
    }

    /** Moves the handle to the listed value nearest {@code target}, without a change of its own. */
    public void setStep(int target) {
        int nearest = 0;
        for (int i = 1; i < steps.length; i++) {
            if (Math.abs((long) steps[i] - target) < Math.abs((long) steps[nearest] - target)) {
                nearest = i;
            }
        }
        index = nearest;
        this.value = steps.length > 1 ? index / (double) (steps.length - 1) : 0.0;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(describe.apply(steps[index]));
    }

    @Override
    protected void applyValue() {
        if (steps.length < 2) {
            return;
        }
        double snapped = index / (double) (steps.length - 1);
        int target = (int) Math.round(this.value * (steps.length - 1));
        // The arrow keys move the handle by one pixel's worth, less than a step on a short list:
        // that moves it a whole step.
        if (target == index && Math.abs(Math.abs(this.value - snapped) - 1.0 / (width - 8)) < 1e-9) {
            target = index + (this.value > snapped ? 1 : -1);
        }
        index = Math.max(0, Math.min(steps.length - 1, target));
        this.value = index / (double) (steps.length - 1);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean enabled = this.active;
        boolean hovered = enabled && (isHovered() || UiButton.keyboardFocused(isFocused()));
        int left = getX();
        int top = getY();
        int right = left + width;
        int bottom = top + height;
        Surfaces.well(graphics, left, top, right, bottom,
                !enabled ? Surfaces.BORDER_DISABLED : hovered ? Surfaces.BORDER_HOVER : Surfaces.BORDER,
                hovered ? Surfaces.WELL_HOVER : Surfaces.WELL);
        int handleLeft = left + (int) Math.round(this.value * (width - HANDLE_WIDTH));
        if (enabled && handleLeft > left + 2) {
            graphics.fill(left + 2, top + 4, handleLeft, bottom - 2, hovered ? Surfaces.ON : FILL);
        }
        Surfaces.raised(graphics, handleLeft, top, handleLeft + HANDLE_WIDTH, bottom,
                !enabled ? Surfaces.BORDER_DISABLED : hovered ? Surfaces.BORDER_HOVER : Surfaces.BORDER,
                !enabled ? Surfaces.FACE_DISABLED : hovered ? Surfaces.FACE_HOVER : Surfaces.FACE, 2);
        Font font = Minecraft.getInstance().font;
        String label = UiText.fit(font, getMessage().getString(), width - 8);
        UiText.drawCentered(graphics, font, label, left + width / 2, top + (height - 8) / 2 + 1,
                enabled ? Surfaces.TEXT : Surfaces.TEXT_DISABLED);
    }
}
