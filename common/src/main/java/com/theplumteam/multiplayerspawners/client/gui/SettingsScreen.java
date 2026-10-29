package com.theplumteam.multiplayerspawners.client.gui;

import com.theplumteam.multiplayerspawners.config.ModConfig;
import com.theplumteam.multiplayerspawners.config.ServerSettings;
import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import com.theplumteam.multiplayerspawners.network.SettingChangePacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * The server settings menu an operator opens from the chat (or with /multiplayerspawners
 * settings): a dialog over the blurred world, in the style of The Plum Team's other menus, with a
 * sidebar of tabs and a row for each setting. Every change goes to the server at once, which checks
 * it, saves it to the server file and answers with what it now holds; the dialog then shows those
 * values and says "Saved".
 */
public class SettingsScreen extends Screen {

    private static final int SCREEN_MARGIN = 8;
    private static final int PREFERRED_WIDTH = 470;
    private static final int PREFERRED_HEIGHT = 340;
    private static final int HEADER_HEIGHT = 26;
    private static final int HEADER_STRIP = 0xFF2E3A4A;
    private static final int HEADER_LINE = 0xFF6F8FB0;
    private static final int SIDEBAR_MIN_WIDTH = 96;
    private static final int SIDEBAR_MAX_WIDTH = 150;
    private static final int TAB_HEIGHT = 22;
    private static final int TAB_GAP = 4;
    private static final int INSET = 4;
    private static final int CONTENT_PADDING = 10;
    private static final int FOOTER_HEIGHT = 18;
    private static final int SLIDER_WIDTH = 132;
    private static final int SCROLL_STEP = 20;
    private static final int SCROLL_BAR = 0xFF6E6F73;
    private static final int SAVED_GREEN = 0xFF7FD67F;
    private static final long SAVED_SHOWN_MILLIS = 2000;

    private static final int[] RESPAWN_MINUTES = {0, 1, 2, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240, 360, 480,
            720, 1440, 2880, 4320, 10080, 20160, 43200};
    private static final int[] TORCH_SECONDS = {0, 5, 10, 15, 30, 45, 60, 90, 120, 180, 300, 600, 900, 1200, 1800,
            2700, 3600};

    /** The dialog's tabs, each a group of settings. */
    private enum Tab {
        SPAWNERS("spawners", PixelIcon.SPAWNER),
        BREAKING("breaking", PixelIcon.PICKAXE),
        TORCHES_AND_CHAT("torches_and_chat", PixelIcon.TORCH);

        final String key;
        final String[] icon;

        Tab(String key, String[] icon) {
            this.key = key;
            this.icon = icon;
        }
    }

    /** The tab shown last, opened again while the game runs. */
    private static Tab lastTab = Tab.SPAWNERS;

    private final Map<String, Integer> values;
    private final List<Row> rows = new ArrayList<>();
    private Tab tab = lastTab;
    private int dialogX;
    private int dialogY;
    private int dialogWidth;
    private int dialogHeight;
    private int sidebarRight;
    private int contentLeft;
    private int contentRight;
    private int contentTop;
    private int contentBottom;
    private int scroll;
    private int contentHeight;
    /** The settings of the changes sent and not answered yet, in order, and how many each has. */
    private final Deque<String> sent = new ArrayDeque<>();
    private final Map<String, Integer> inFlight = new HashMap<>();
    private long savedUntil;

    public SettingsScreen(Map<String, Integer> values) {
        super(Component.translatable("multiplayerspawners.settings.title"));
        this.values = new HashMap<>(values);
    }

    /**
     * The server's values after a change, in the order the changes were sent. A setting shows the
     * server's value once every change sent for it is answered, so a second click made before the
     * first answer came does not flicker back; the footer says Saved when the server kept the value.
     */
    public void update(Map<String, Integer> serverValues) {
        String answered = sent.poll();
        if (answered != null) {
            int left = inFlight.merge(answered, -1, Integer::sum);
            if (left <= 0) {
                inFlight.remove(answered);
                if (serverValues.get(answered) != null && serverValues.get(answered).equals(values.get(answered))) {
                    savedUntil = System.currentTimeMillis() + SAVED_SHOWN_MILLIS;
                }
            }
        }
        for (Map.Entry<String, Integer> entry : serverValues.entrySet()) {
            if (!inFlight.containsKey(entry.getKey())) {
                values.put(entry.getKey(), entry.getValue());
            }
        }
        for (Row row : rows) {
            if (!inFlight.containsKey(row.key)) {
                row.refresh();
            }
        }
        updateActive();
    }

    @Override
    protected void init() {
        MenuGuiScale.enter(this.minecraft, this);
        dialogWidth = Math.min(PREFERRED_WIDTH, this.width - 2 * SCREEN_MARGIN);
        dialogHeight = Math.min(PREFERRED_HEIGHT, this.height - 2 * SCREEN_MARGIN);
        dialogX = (this.width - dialogWidth) / 2;
        dialogY = (this.height - dialogHeight) / 2;
        int sidebarWidth = sidebarWidth();
        sidebarRight = dialogX + 2 + sidebarWidth;
        contentLeft = sidebarRight + 1 + CONTENT_PADDING;
        contentRight = dialogX + dialogWidth - 2 - CONTENT_PADDING;
        contentTop = dialogY + HEADER_HEIGHT + CONTENT_PADDING - 4;
        contentBottom = dialogY + dialogHeight - 2 - Surfaces.LIP_HEIGHT - FOOTER_HEIGHT;

        addRenderableWidget(new CloseButton(dialogX + dialogWidth - 6 - CloseButton.SIZE,
                dialogY + (HEADER_HEIGHT - CloseButton.SIZE) / 2, Component.translatable("multiplayerspawners.settings.close"),
                this::onClose));
        int tabY = dialogY + HEADER_HEIGHT + INSET;
        for (Tab each : Tab.values()) {
            TabButton button = new TabButton(dialogX + 2 + INSET, tabY, sidebarWidth - 2 * INSET, TAB_HEIGHT,
                    Component.translatable("multiplayerspawners.settings.tab." + each.key), each.icon, each == tab,
                    () -> switchTab(each));
            addRenderableWidget(button);
            tabY += TAB_HEIGHT + TAB_GAP;
        }
        buildRows();
    }

    /** Wide enough for the longest tab's name, within limits that leave the settings their room. */
    private int sidebarWidth() {
        int longest = 0;
        for (Tab each : Tab.values()) {
            longest = Math.max(longest, this.font.width(Component.translatable("multiplayerspawners.settings.tab." + each.key)));
        }
        int wanted = 2 * INSET + TabButton.TEXT_LEFT + longest + 8;
        return Mth.clamp(wanted, SIDEBAR_MIN_WIDTH, Math.max(SIDEBAR_MIN_WIDTH, Math.min(SIDEBAR_MAX_WIDTH, dialogWidth / 3)));
    }

    private void switchTab(Tab next) {
        tab = next;
        lastTab = next;
        scroll = 0;
        rebuildWidgets();
    }

    private void buildRows() {
        rows.clear();
        int width = contentRight - contentLeft;
        switch (tab) {
            case SPAWNERS -> {
                toggle(ModConfig.SERVER.enableGroupDisabling, "group_disabling", width);
                slider(ModConfig.SERVER.groupDisablingRadius, "group_radius", width,
                        range(ModConfig.SERVER.groupDisablingRadius), SettingsScreen::blocks);
                slider(ModConfig.SERVER.spawnerRespawnMinutes, "respawn", width, RESPAWN_MINUTES,
                        minutes -> duration(minutes * 60L));
                slider(ModConfig.SERVER.oneShotSpawnerRange, "one_shot_range", width,
                        range(ModConfig.SERVER.oneShotSpawnerRange), SettingsScreen::blocks);
            }
            case BREAKING -> {
                toggle(ModConfig.SERVER.allowSpawnerBreakMessage, "break_link", width);
                rows.add(new ToggleSetting(ServerSettings.BREAK_ATTEMPTS, "first_hit", width,
                        value -> value == 1, on -> on ? 1 : 2));
                toggle(ModConfig.SERVER.breakWithSpawnerTools, "break_tools", width);
                toggle(ModConfig.SERVER.spawnEggsChangeSpawners, "spawn_eggs", width);
                toggle(ModConfig.SERVER.treatPlacedSpawnersAsVanilla, "placed_vanilla", width);
            }
            case TORCHES_AND_CHAT -> {
                slider(ModConfig.SERVER.torchExtinguishDelaySeconds, "torch_delay", width, TORCH_SECONDS,
                        SettingsScreen::duration);
                toggle(ModConfig.SERVER.showNotices, "notices", width);
            }
        }
        contentHeight = 0;
        for (Row row : rows) {
            contentHeight += row.height();
        }
        scroll = Mth.clamp(scroll, 0, maxScroll());
        layoutRows();
        updateActive();
    }

    private void toggle(ModConfig.BooleanValue setting, String name, int width) {
        rows.add(new ToggleSetting(setting.key(), name, width, value -> value == 1, on -> on ? 1 : 0));
    }

    private void slider(ModConfig.Value<?> setting, String name, int width, int[] steps, IntFunction<Component> describe) {
        rows.add(new SliderSetting(setting.key(), name, width, withValue(steps, values.getOrDefault(setting.key(), steps[0])),
                describe));
    }

    private static int[] range(ModConfig.IntValue setting) {
        int[] steps = new int[setting.max() - setting.min() + 1];
        for (int i = 0; i < steps.length; i++) {
            steps[i] = setting.min() + i;
        }
        return steps;
    }

    /** The steps with {@code value} among them, so a value set in the file shows as it is. */
    private static int[] withValue(int[] steps, int value) {
        if (Arrays.stream(steps).anyMatch(step -> step == value)) {
            return steps;
        }
        int[] all = Arrays.copyOf(steps, steps.length + 1);
        all[steps.length] = value;
        Arrays.sort(all);
        return all;
    }

    private static Component blocks(int blocks) {
        return Component.translatable("multiplayerspawners.settings.blocks", blocks);
    }

    /** A duration in the largest units that fit it, at most two of them: "1 h 30 min". */
    private static Component duration(long seconds) {
        if (seconds <= 0) {
            return Component.translatable("multiplayerspawners.settings.never");
        }
        long[] sizes = {86400, 3600, 60, 1};
        String[] units = {"days", "hours", "minutes", "seconds"};
        List<Component> parts = new ArrayList<>();
        long rest = seconds;
        for (int i = 0; i < sizes.length && parts.size() < 2; i++) {
            long amount = rest / sizes[i];
            if (amount > 0) {
                parts.add(Component.translatable("multiplayerspawners.settings." + units[i], amount));
                rest -= amount * sizes[i];
            } else if (!parts.isEmpty()) {
                break;
            }
        }
        return parts.size() == 1 ? parts.get(0)
                : Component.translatable("multiplayerspawners.settings.and", parts.get(0), parts.get(1));
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (contentBottom - contentTop));
    }

    /**
     * Places the rows below each other from the scroll position. A row not wholly shown is hidden,
     * so that no part of it can be clicked outside the content.
     */
    private void layoutRows() {
        int y = contentTop - scroll;
        for (Row row : rows) {
            row.place(y);
            y += row.height();
        }
    }

    /** The group radius only matters with group disabling on. */
    private void updateActive() {
        boolean group = values.getOrDefault(ModConfig.SERVER.enableGroupDisabling.key(), 1) == 1;
        for (Row row : rows) {
            if (row.key.equals(ModConfig.SERVER.groupDisablingRadius.key())) {
                row.setEnabled(group);
            }
        }
    }

    private void send(String key, int value) {
        values.put(key, value);
        sent.add(key);
        inFlight.merge(key, 1, Integer::sum);
        NetworkHandler.getInstance().sendSettingChange(new SettingChangePacket(key, value));
        updateActive();
    }

    @Override
    public void tick() {
        super.tick();
        if (MenuGuiScale.apply(this.minecraft)) {
            rebuildWidgets();
            return;
        }
        // A slider sends its value once it is let go, by the mouse or after the arrow keys.
        if (!this.minecraft.mouseHandler.isLeftPressed()) {
            for (Row row : rows) {
                if (row instanceof SliderSetting slider) {
                    slider.sendIfMoved();
                }
            }
        }
    }

    //? if >=1.20.2 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollBy(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return scrollBy(mouseX, mouseY, delta) || super.mouseScrolled(mouseX, mouseY, delta);
    }
    *///?}

    private boolean scrollBy(double mouseX, double mouseY, double amount) {
        if (maxScroll() == 0 || mouseX < sidebarRight || mouseX > dialogX + dialogWidth
                || mouseY < contentTop || mouseY > contentBottom) {
            return false;
        }
        scroll = Mth.clamp(scroll - (int) Math.round(amount * SCROLL_STEP), 0, maxScroll());
        layoutRows();
        return true;
    }

    //? if >=1.20.2 {
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The world blurred and darkened as behind vanilla's menus, then the dialog's panels; the
        // widgets are drawn over them.
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        drawDialog(graphics);
    }
    //?}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //? if <1.20.2 {
        /*renderBackground(graphics);
        drawDialog(graphics);
        *///?}
        super.render(graphics, mouseX, mouseY, partialTick);
        drawLabels(graphics);
    }

    /** The frame, the header strip, the sidebar and the content's face. */
    private void drawDialog(GuiGraphics graphics) {
        int right = dialogX + dialogWidth;
        int bottom = dialogY + dialogHeight;
        Surfaces.raised(graphics, dialogX, dialogY, right, bottom, Surfaces.BORDER, Surfaces.DIALOG_FACE, Surfaces.LIP_HEIGHT);
        int lineY = dialogY + HEADER_HEIGHT - 1;
        graphics.fill(dialogX + 2, dialogY + 2, right - 2, lineY, HEADER_STRIP);
        graphics.fill(dialogX + 2, lineY, right - 2, lineY + 1, HEADER_LINE);
        int bodyBottom = bottom - 2 - Surfaces.LIP_HEIGHT;
        graphics.fill(dialogX + 2, lineY + 1, sidebarRight, bodyBottom, Surfaces.SIDEBAR);
        graphics.fill(sidebarRight, lineY + 1, sidebarRight + 1, bodyBottom, Surfaces.SIDEBAR_EDGE);
        graphics.fill(sidebarRight + 1, lineY + 1, right - 2, bodyBottom, Surfaces.CONTENT);
        graphics.fill(sidebarRight + 1, contentBottom, right - 2, contentBottom + 1, Surfaces.ROW_LINE);
    }

    /**
     * What is drawn over the widgets: the header's icon and title, the slider rows' names and
     * descriptions, the scroll bar and the footer.
     */
    private void drawLabels(GuiGraphics graphics) {
        int middle = dialogY + 2 + (HEADER_HEIGHT - 3) / 2;
        int iconX = dialogX + 10;
        PixelIcon.draw(graphics, PixelIcon.GEAR, iconX, middle - PixelIcon.GEAR.length / 2, Surfaces.TEXT);
        int titleX = iconX + PixelIcon.width(PixelIcon.GEAR) + 6;
        PixelFont.draw(graphics, this.title, titleX, middle + 0.5f, 10, dialogX + dialogWidth - 12 - CloseButton.SIZE - titleX,
                Surfaces.TEXT);

        for (Row row : rows) {
            if (row instanceof SliderSetting slider) {
                slider.drawText(graphics);
            }
        }

        if (maxScroll() > 0) {
            int track = contentBottom - contentTop;
            int barHeight = Math.max(12, track * track / contentHeight);
            int barTop = contentTop + (track - barHeight) * scroll / maxScroll();
            int barX = dialogX + dialogWidth - 6;
            graphics.fill(barX, barTop, barX + 2, barTop + barHeight, SCROLL_BAR);
        }

        Font font = this.font;
        int footerY = contentBottom + (FOOTER_HEIGHT - 8) / 2 + 1;
        int savedWidth = 0;
        if (System.currentTimeMillis() < savedUntil) {
            String saved = Component.translatable("multiplayerspawners.settings.saved").getString();
            savedWidth = PixelIcon.width(PixelIcon.CHECK) + 4 + font.width(saved);
            int savedX = contentRight - savedWidth;
            PixelIcon.draw(graphics, PixelIcon.CHECK, savedX, footerY, SAVED_GREEN);
            UiText.draw(graphics, font, saved, savedX + PixelIcon.width(PixelIcon.CHECK) + 4, footerY, SAVED_GREEN);
        }
        // The footer's note gives way to Saved where both do not fit.
        String note = Component.translatable("multiplayerspawners.settings.file").getString();
        int noteRoom = contentRight - contentLeft - (savedWidth > 0 ? savedWidth + 8 : 0);
        if (savedWidth == 0 || font.width(note) <= noteRoom) {
            UiText.drawFitted(graphics, font, note, contentLeft, footerY, contentRight - contentLeft, Surfaces.TEXT_DIM);
        }
    }

    @Override
    public void removed() {
        super.removed();
        MenuGuiScale.restore(this.minecraft);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** One setting's row. */
    private abstract class Row {
        final String key;

        Row(String key) {
            this.key = key;
        }

        abstract int height();

        /** Moves the row to {@code y}, hiding it when it is not wholly shown. */
        abstract void place(int y);

        /** Shows the value the server holds. */
        abstract void refresh();

        abstract void setEnabled(boolean enabled);

        int value() {
            return values.getOrDefault(key, 0);
        }

        void show(AbstractWidget widget, int top, int bottom) {
            widget.visible = top >= contentTop && bottom <= contentBottom;
        }
    }

    private final class ToggleSetting extends Row {
        private final ToggleRow toggle;
        private final IntFunction<Boolean> isOn;

        ToggleSetting(String key, String name, int width, IntFunction<Boolean> isOn, Function<Boolean, Integer> toValue) {
            super(key);
            this.isOn = isOn;
            this.toggle = addRenderableWidget(new ToggleRow(contentLeft, contentTop, width,
                    Component.translatable("multiplayerspawners.settings." + name),
                    Component.translatable("multiplayerspawners.settings." + name + ".description"),
                    isOn.apply(value()), on -> send(key, toValue.apply(on))));
        }

        @Override
        int height() {
            return toggle.getHeight();
        }

        @Override
        void place(int y) {
            toggle.setY(y);
            show(toggle, y, y + toggle.getHeight());
        }

        @Override
        void refresh() {
            toggle.setOn(isOn.apply(value()));
        }

        @Override
        void setEnabled(boolean enabled) {
            toggle.active = enabled;
        }
    }

    private final class SliderSetting extends Row {
        private final StepSlider slider;
        private final SettingRows.Layout layout;
        private final int width;
        private int y;
        private int sent;
        private boolean enabled = true;

        SliderSetting(String key, String name, int width, int[] steps, IntFunction<Component> describe) {
            super(key);
            this.width = width;
            int sliderWidth = Math.min(SLIDER_WIDTH, width / 2);
            this.layout = SettingRows.layout(font, Component.translatable("multiplayerspawners.settings." + name),
                    Component.translatable("multiplayerspawners.settings." + name + ".description"), width, sliderWidth);
            this.slider = addRenderableWidget(new StepSlider(contentLeft + width - SettingRows.SIDE - sliderWidth,
                    contentTop, sliderWidth, steps, value(), describe));
            this.sent = slider.current();
        }

        @Override
        int height() {
            return layout.height();
        }

        @Override
        void place(int y) {
            this.y = y;
            slider.setY(SettingRows.controlTop(y));
            show(slider, y, y + layout.height());
        }

        @Override
        void refresh() {
            // The handle stays where the player holds it.
            if (!(slider.isFocused() && minecraft.mouseHandler.isLeftPressed())) {
                slider.setStep(value());
                sent = slider.current();
            }
        }

        @Override
        void setEnabled(boolean enabled) {
            this.enabled = enabled;
            slider.active = enabled;
        }

        void sendIfMoved() {
            if (slider.current() != sent) {
                sent = slider.current();
                send(key, sent);
            }
        }

        void drawText(GuiGraphics graphics) {
            if (slider.visible) {
                SettingRows.drawRow(graphics, font, contentLeft, y, width, layout, false, enabled);
            }
        }
    }
}
