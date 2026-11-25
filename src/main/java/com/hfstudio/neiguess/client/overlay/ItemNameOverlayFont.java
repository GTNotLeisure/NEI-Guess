package com.hfstudio.neiguess.client.overlay;

import java.lang.reflect.Method;

import net.minecraft.client.gui.FontRenderer;

import com.gtnewhorizon.gtnhlib.util.font.FontRendering;
import com.gtnewhorizon.gtnhlib.util.font.IFontParameters;

import cpw.mods.fml.common.Loader;

public class ItemNameOverlayFont {

    private static final boolean GTNH_FONTS = hasFontAPI();
    private static Method settingsMethod = findSettingsMethod();

    private ItemNameOverlayFont() {}

    private static boolean hasFontAPI() {
        if (!Loader.isModLoaded("gtnhlib")) {
            return false;
        }
        try {
            FontRendering.class.getMethod("preprocessText", String.class);
            IFontParameters.class.getMethod("getCharWidthFine", char.class);
            IFontParameters.class.getMethod("getGlyphScaleX");
            IFontParameters.class.getMethod("getGlyphScaleY");
            IFontParameters.class.getMethod("getGlyphSpacing");
            IFontParameters.class.getMethod("getWhitespaceScale");
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static Method findSettingsMethod() {
        if (!Loader.isModLoaded("angelica")) {
            return null;
        }
        try {
            return Class
                .forName(
                    "com.gtnewhorizons.angelica.client.font.GlyphSettings",
                    false,
                    ItemNameOverlayFont.class.getClassLoader())
                .getMethod("current");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    public static Object currentState() {
        if (settingsMethod != null) {
            try {
                return settingsMethod.invoke(null, (Object[]) null);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                settingsMethod = null;
            }
        }
        return null;
    }

    public static String preprocess(String text) {
        return GTNH_FONTS ? FontRendering.preprocessText(text) : text;
    }

    public static int width(FontRenderer font, String text) {
        // Preserve custom FontRenderer subclasses' own measurement overrides.
        return GTNH_FONTS && font.getClass() == FontRenderer.class && font instanceof IFontParameters
            ? FontRendering.getStringWidth(text, font)
            : font.getStringWidth(text);
    }

    public static String trimToWidth(FontRenderer font, String text, int width) {
        return GTNH_FONTS && font.getClass() == FontRenderer.class && font instanceof IFontParameters
            ? FontRendering.trimStringToWidth(text, width, false, font)
            : font.trimStringToWidth(text, width);
    }

    public static float lineHeight(FontRenderer font) {
        if (GTNH_FONTS && font instanceof IFontParameters parameters) {
            final float scale = parameters.getGlyphScaleY();
            if (Float.isFinite(scale) && scale > 0) {
                return font.FONT_HEIGHT * scale;
            }
        }
        return font.FONT_HEIGHT;
    }

    public static IFontParameters parameters(FontRenderer font) {
        return GTNH_FONTS && font instanceof IFontParameters parameters ? parameters : null;
    }
}
