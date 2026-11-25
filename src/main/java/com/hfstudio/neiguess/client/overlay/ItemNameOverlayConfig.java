package com.hfstudio.neiguess.client.overlay;

import codechicken.lib.config.ConfigTag;
import codechicken.lib.config.ConfigTagParent;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.api.API;
import codechicken.nei.config.OptionCycled;
import codechicken.nei.config.OptionIntegerField;
import codechicken.nei.config.OptionToggleButton;

/** Registers the icon name settings page in NEI's own inventory options. */
public final class ItemNameOverlayConfig {

    private static final String PREFIX = "inventory.itemNameOverlay.";

    private ItemNameOverlayConfig() {}

    public static void register() {
        // Newer NEI versions register the original settings themselves.
        if (ItemNameOverlayConfig.class.getClassLoader()
            .getResource("codechicken/nei/item/ItemNameOverlayRenderer.class") != null) return;

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

    private static float readScale(String name, float fallback) {
        try {
            float value = Float.parseFloat(
                NEIClientConfig.getSetting(PREFIX + name)
                    .getValue(Float.toString(fallback)));
            return Float.isFinite(value) ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static void reloadSettings() {
        ItemNameOverlayRenderer.INSTANCE.setSettings(
            new ItemNameOverlaySettings(
                ItemNameOverlayMode.fromConfigValue(NEIClientConfig.getIntSetting(PREFIX + "mode")),
                readScale("fontScale", 0.5f),
                readScale("unicodeFontScale", 0.5625f),
                readScale("minimumScale", 0.3f),
                NEIClientConfig.getIntSetting(PREFIX + "width"),
                NEIClientConfig.getIntSetting(PREFIX + "maxCharacters"),
                NEIClientConfig.getIntSetting(PREFIX + "maxLines"),
                NEIClientConfig.getBooleanSetting(PREFIX + "outline")));
    }
}
