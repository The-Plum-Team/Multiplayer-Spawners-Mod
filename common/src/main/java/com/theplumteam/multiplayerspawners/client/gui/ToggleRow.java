package com.theplumteam.multiplayerspawners.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.function.Consumer;

/**
 * An on/off setting as a row of the dialog: its name and, below it, what it does, with a switch on
 * the right (a sunk track, green while on, under a raised knob on the side it is set to). The row
 * lightens under the mouse and a click anywhere on it flips the switch, which clicks a little
 * higher when it goes on than when it goes off.
 */
public class ToggleRow extends UiButton {

    static final int SWITCH_WIDTH = 30;
    static final int SWITCH_HEIGHT = 16;
    private static final int KNOB = 14;
    private static final int OFF_SHADE = 0xFF151617;

    private final SettingRows.Layout layout;
    private final Consumer<Boolean> onChange;
    private boolean on;

    public ToggleRow(int x, int y, int width, Component label, Component description, boolean on, Consumer<Boolean> onChange) {
        super(x, y, width, 0, label);
        this.layout = SettingRows.layout(Minecraft.getInstance().font, label, description, width, SWITCH_WIDTH);
        this.height = layout.height();
        this.on = on;
        this.onChange = onChange;
    }

    public boolean isOn() {
        return on;
    }

    /** Sets the switch without telling the listener, as when the server says what it holds. */
    public void setOn(boolean on) {
        this.on = on;
    }

    @Override
    protected void press() {
        on = !on;
        onChange.accept(on);
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
        // The sound plays before the press: it is for the side the switch goes to.
        soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, on ? 0.85f : 1.15f));
    }

    @Override
    protected void draw(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean enabled = this.active;
        boolean hovered = lit();
        SettingRows.drawRow(graphics, Minecraft.getInstance().font, getX(), getY(), width, layout, hovered, enabled);
        int switchLeft = getX() + width - SettingRows.SIDE - SWITCH_WIDTH;
        int switchTop = SettingRows.controlTop(getY());
        drawSwitch(graphics, switchLeft, switchTop, enabled, hovered);
    }

    private void drawSwitch(GuiGraphics graphics, int left, int top, boolean enabled, boolean hovered) {
        int right = left + SWITCH_WIDTH;
        int trackTop = top + 1;
        int trackBottom = top + SWITCH_HEIGHT - 1;
        graphics.fill(left, trackTop, right, trackBottom, Surfaces.OUTLINE);
        int track = !enabled ? Surfaces.FACE_DISABLED : on ? (hovered ? Surfaces.ON_HOVER : Surfaces.ON) : Surfaces.WELL;
        graphics.fill(left + 1, trackTop + 1, right - 1, trackBottom - 1, track);
        if (!on && enabled) {
            graphics.fill(left + 1, trackTop + 1, right - 1, trackTop + 3, OFF_SHADE);
        }
        int knobLeft = on ? right - KNOB : left;
        Surfaces.raised(graphics, knobLeft, top, knobLeft + KNOB, top + SWITCH_HEIGHT,
                !enabled ? Surfaces.BORDER_DISABLED : hovered ? Surfaces.BORDER_HOVER : Surfaces.BORDER,
                !enabled ? Surfaces.FACE_DISABLED : hovered ? Surfaces.FACE_HOVER : Surfaces.FACE, 2);
    }
}
