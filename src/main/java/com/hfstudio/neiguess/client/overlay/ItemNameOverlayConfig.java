package com.hfstudio.neiguess.client.overlay;

import codechicken.lib.config.ConfigTag;
import codechicken.lib.config.ConfigTagParent;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.api.API;
import codechicken.nei.config.OptionCycled;
import codechicken.nei.config.OptionIntegerField;
import codechicken.nei.config.OptionToggleButton;

public class ItemNameOverlayConfig {

    private static final String PREFIX = "inventory.itemNameOverlay.";
    private static RawSettings lastSettings;

    private ItemNameOverlayConfig() {}

    public static void register() {
        ConfigTagParent tag = NEIClientConfig.global.config;
        ConfigTag overlay = tag.getTag("inventory.itemNameOverlay");
        int previousMode = overlay.getValue() != null ? overlay.getIntValue(3) : 3;
        tag.getTag(PREFIX + "mode")
            .setComment("Icon name labels: 0 = items, 1 = fluids, 2 = all, 3 = off")
            .getIntValue(previousMode);
        if (overlay.getValue() != null) overlay.setValue(null);
        API.addOption(new OptionCycled(PREFIX + "mode", 4, true) {

            @Override
            public void setValue(int value) {
                super.setValue(value);
                reloadSettings();
            }
        });

        addScaleOption(tag, "fontScale", "Preferred scale for ordinary fonts", 0.5f);
        addScaleOption(tag, "unicodeFontScale", "Preferred scale for Unicode text and fonts", 0.5625f);
        addScaleOption(tag, "minimumScale", "Minimum scale for fitting a name into its label", 0.3f);
        addIntegerOption(tag, "width", "Total label width in icon coordinates", 18, 4, 32);
        addIntegerOption(tag, "maxCharacters", "Maximum characters per line; 0 means automatic", 0, 0, 256);
        addIntegerOption(tag, "maxLines", "Maximum number of name lines", 2, 1, 4);

        tag.getTag(PREFIX + "outline")
            .setComment("Draw a one-screen-pixel outline around names")
            .getBooleanValue(true);
        API.addOption(new OptionToggleButton(PREFIX + "outline", true) {

            @Override
            public boolean onClick(int button) {
                boolean changed = super.onClick(button);
                reloadSettings();
                return changed;
            }
        });
        reloadSettings();
    }

    private static void addScaleOption(ConfigTagParent tag, String name, String comment, float defaultValue) {
        String path = PREFIX + name;
        tag.getTag(path)
            .setComment(comment)
            .setDefaultValue(Float.toString(defaultValue));
        API.addOption(new OptionFloatField(path, 0.1f, 2f) {

            @Override
            public void onTextChange(String text) {
                reloadSettings();
            }
        });
    }

    private static void addIntegerOption(ConfigTagParent tag, String name, String comment, int defaultValue, int min,
        int max) {
        String path = PREFIX + name;
        tag.getTag(path)
            .setComment(comment)
            .getIntValue(defaultValue);
        API.addOption(new OptionIntegerField(path, min, max) {

            @Override
            public void onTextChange(String text) {
                reloadSettings();
            }
        });
    }

    private static float readScale(String text, float fallback) {
        try {
            float value = Float.parseFloat(text);
            return Float.isFinite(value) ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int readInteger(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String readValue(String name, String fallback) {
        return NEIClientConfig.getSetting(PREFIX + name)
            .getValue(fallback);
    }

    private static boolean readBoolean(String text) {
        if ("true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text)) return true;
        if ("false".equalsIgnoreCase(text) || "no".equalsIgnoreCase(text)) return false;
        return true;
    }

    public static void reloadSettings() {
        String mode = readValue("mode", "3");
        String fontScale = readValue("fontScale", "0.5");
        String unicodeFontScale = readValue("unicodeFontScale", "0.5625");
        String minimumScale = readValue("minimumScale", "0.3");
        String width = readValue("width", "18");
        String maxCharacters = readValue("maxCharacters", "0");
        String maxLines = readValue("maxLines", "2");
        String outline = readValue("outline", "true");
        if (lastSettings != null && lastSettings.mode.equals(mode)
            && lastSettings.fontScale.equals(fontScale)
            && lastSettings.unicodeFontScale.equals(unicodeFontScale)
            && lastSettings.minimumScale.equals(minimumScale)
            && lastSettings.width.equals(width)
            && lastSettings.maxCharacters.equals(maxCharacters)
            && lastSettings.maxLines.equals(maxLines)
            && lastSettings.outline.equals(outline)) return;

        ItemNameOverlayRenderer.INSTANCE.setSettings(
            new ItemNameOverlaySettings(
                ItemNameOverlayMode.fromConfigValue(readInteger(mode, 3)),
                readScale(fontScale, 0.5f),
                readScale(unicodeFontScale, 0.5625f),
                readScale(minimumScale, 0.3f),
                readInteger(width, 18),
                readInteger(maxCharacters, 0),
                readInteger(maxLines, 2),
                readBoolean(outline)));
        lastSettings = new RawSettings(
            mode,
            fontScale,
            unicodeFontScale,
            minimumScale,
            width,
            maxCharacters,
            maxLines,
            outline);
    }

    private record RawSettings(String mode, String fontScale, String unicodeFontScale, String minimumScale,
        String width, String maxCharacters, String maxLines, String outline) {}
}
