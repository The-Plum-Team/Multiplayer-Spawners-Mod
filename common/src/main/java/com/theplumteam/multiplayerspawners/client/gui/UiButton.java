package com.theplumteam.multiplayerspawners.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * The base of the menu's own buttons. It holds what differs between versions, so each button only
 * draws itself ({@link #draw}) and says what a press does ({@link #press}): 1.21.9 passes the
 * input to {@code onPress} and {@code isValidClickButton}, and 1.21.11 has buttons draw their
 * contents in {@code renderContents} (renamed {@code extractContents} with the rest of the GUI in
 * 26.1). Only the left button presses them.
 */
public abstract class UiButton extends Button {

    protected UiButton(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message, button -> {
        }, DEFAULT_NARRATION);
    }

    /** What a press does, by the mouse or by Enter or Space on the focused button. */
    protected abstract void press();

    protected abstract void draw(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    //? if >=1.21.9 {
    @Override
    public void onPress(net.minecraft.client.input.InputWithModifiers input) {
        press();
    }
    //?} else {
    /*@Override
    public void onPress() {
        press();
    }
    *///?}

    //? if >=1.21.11 {
    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        draw(graphics, mouseX, mouseY, partialTick);
    }
    //?} else {
    /*@Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        draw(graphics, mouseX, mouseY, partialTick);
    }
    *///?}

    //? if >=1.21.9 {
    @Override
    protected boolean isValidClickButton(net.minecraft.client.input.MouseButtonInfo info) {
        return info.button() == InputConstants.MOUSE_BUTTON_LEFT;
    }
    //?} else {
    /*@Override
    protected boolean isValidClickButton(int button) {
        return button == InputConstants.MOUSE_BUTTON_LEFT;
    }
    *///?}

    /**
     * Whether the button is lit: under the mouse, or reached with the keyboard. A button the mouse
     * clicked keeps the focus but not the light.
     */
    protected boolean lit() {
        return this.active && (this.isHovered() || keyboardFocused(this.isFocused()));
    }

    static boolean keyboardFocused(boolean focused) {
        return focused && Minecraft.getInstance().getLastInputType().isKeyboard();
    }
}
