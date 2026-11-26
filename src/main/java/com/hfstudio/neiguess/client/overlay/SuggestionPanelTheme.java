package com.hfstudio.neiguess.client.overlay;

import net.minecraft.client.gui.Gui;

public class SuggestionPanelTheme {

    public static final int BACKGROUND = 0xF6242424;
    public static final int HEADER = 0xFF303030;
    public static final int BORDER = 0xFF707070;
    public static final int SEPARATOR = 0xFF484848;
    public static final int TEXT = 0xFFE0E0E0;
    public static final int MUTED_TEXT = 0xFFA0A0A0;
    public static final int SUBTLE_TEXT = 0xFF787878;
    public static final int CONTROL = 0xFF383838;
    public static final int CONTROL_HOVER = 0xFF505050;
    public static final int HOVER_TEXT = 0xFFFFFFA0;
    public static final int ROW_HOVER = 0xFF424242;
    public static final int ACCENT = 0xFF8BAA65;
    public static final int SELECTED_TEXT = 0xFFFFFFFF;
    private static final int LABEL_NEUTRAL = 0xC0;

    private SuggestionPanelTheme() {}

    public static void drawFrame(int x, int y, int width, int height) {
        Gui.drawRect(x - 1, y - 1, x + width + 1, y + height + 1, BORDER);
        Gui.drawRect(x, y, x + width, y + height, BACKGROUND);
    }

    public static void drawHeader(int x, int y, int width, int bottom) {
        Gui.drawRect(x, y, x + width, bottom, HEADER);
        Gui.drawRect(x, bottom - 1, x + width, bottom, SEPARATOR);
    }

    public static void drawControl(int x, int y, int width, int height, boolean hovered) {
        Gui.drawRect(x, y, x + width, y + height, hovered ? CONTROL_HOVER : CONTROL);
    }

    public static void drawSelection(int x, int y, int width, int height) {
        Gui.drawRect(x, y, x + width, y + height, ROW_HOVER);
        Gui.drawRect(x, y, x + 1, y + height, ACCENT);
    }

    public static int labelColor(int color) {
        int red = (3 * LABEL_NEUTRAL + (color >>> 16 & 255)) / 4;
        int green = (3 * LABEL_NEUTRAL + (color >>> 8 & 255)) / 4;
        int blue = (3 * LABEL_NEUTRAL + (color & 255)) / 4;
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
