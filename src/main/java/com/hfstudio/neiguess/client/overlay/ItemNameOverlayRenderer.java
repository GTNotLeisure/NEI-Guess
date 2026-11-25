package com.hfstudio.neiguess.client.overlay;

import java.nio.FloatBuffer;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.Language;
import net.minecraft.item.ItemStack;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.gtnhlib.util.font.IFontParameters;
import com.hfstudio.neiguess.client.overlay.ItemNameOverlayLayoutEngine.LabelLayout;
import com.hfstudio.neiguess.client.overlay.ItemNameOverlayLayoutEngine.LabelLine;
import com.hfstudio.neiguess.client.overlay.ItemNameOverlayLayoutEngine.LayoutRequest;

import codechicken.nei.LRUCache;
import codechicken.nei.recipe.StackInfo;

public class ItemNameOverlayRenderer {

    public static final ItemNameOverlayRenderer INSTANCE = new ItemNameOverlayRenderer();

    private static final int[] OUTLINE_STEPS = { -1, 0, 1, -1, 1, 1, -1, 1 };

    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
    private final LRUCache<LayoutKey, LabelLayout> layouts = new LRUCache<>(2048);
    private final LRUCache<ItemStack, Boolean> failedStacks = new LRUCache<>(256);
    private final LayoutKey lookupKey = new LayoutKey(null, "", 0, 0, false, ItemNameOverlaySettings.defaults());
    private final LayoutKey fontLookupKey = new LayoutKey(null, "", 0, 0, false, ItemNameOverlaySettings.defaults());
    private final GuiScaleCache guiScaleCache = new GuiScaleCache();
    private final ItemNameOverlayLayoutEngine layoutEngine;
    private ItemNameOverlaySettings settings = ItemNameOverlaySettings.defaults();
    private LayoutKey fontKey;
    private Object fontState;
    private int guiScale;
    private long cacheRevision;

    public ItemNameOverlayRenderer() {
        this(new ItemNameOverlayLayoutEngine());
    }

    public ItemNameOverlayRenderer(ItemNameOverlayLayoutEngine layoutEngine) {
        this.layoutEngine = Objects.requireNonNull(layoutEngine);
    }

    public ItemNameOverlaySettings getSettings() {
        return settings;
    }

    public boolean hasFailed(ItemStack stack) {
        return failedStacks.get(stack) != null;
    }

    public void recordFailure(ItemStack stack) {
        failedStacks.put(stack, true);
    }

    public void render(ItemNameOverlayMode mode, ItemStack stack, FontRenderer font, int x, int y,
        int availableHeight) {
        if (mode == ItemNameOverlayMode.OFF || stack == null || font == null || stack.getItem() == null) {
            return;
        }

        final boolean fluidDisplay = StackInfo.isFluidDisplayItem(stack);
        if (!mode.includes(fluidDisplay)) {
            return;
        }

        final String name = stack.getDisplayName();
        if (fluidDisplay) {
            // Fluid renderers own their amount overlay, so reserve its bottom row.
            availableHeight = Math.min(availableHeight, font.getUnicodeFlag() ? 8 : 11);
        }

        final ItemNameOverlaySettings currentSettings = settings;
        drawLabel(
            font,
            name,
            x + (16 - currentSettings.width) / 2f,
            (float) y,
            currentSettings.width,
            availableHeight,
            currentSettings);
    }

    public void drawLabel(FontRenderer font, String text, float x, float y, int width, int height,
        ItemNameOverlaySettings currentSettings) {
        if (font == null || text == null || text.isEmpty() || width <= 0 || height <= 0) {
            return;
        }

        lookupKey.set(font, text, width, height, font.getUnicodeFlag(), currentSettings);
        LabelLayout layout = layouts.get(lookupKey);
        if (layout == null) {
            // Stored keys are copies; the reusable lookup key never enters the cache.
            final LayoutKey key = new LayoutKey(lookupKey);
            layout = layoutEngine
                .createLayout(new LayoutRequest(font, text, width, height, key.unicode, currentSettings));
            layouts.put(key, layout);
        }
        if (layout.lines()
            .isEmpty()) {
            return;
        }

        final int guiScale = readRenderTransform();
        final float scaleX = matrixBuffer.get(0);
        final float scaleY = matrixBuffer.get(5);
        final float translateX = matrixBuffer.get(12);
        final float translateY = matrixBuffer.get(13);
        final float pixelScaleX = Math.abs(scaleX) * guiScale;
        final float pixelScaleY = Math.abs(scaleY) * guiScale;
        if (!Float.isFinite(pixelScaleX) || !Float.isFinite(pixelScaleY) || pixelScaleX <= 0 || pixelScaleY <= 0) {
            return;
        }
        final float inverseScale = 1f / layout.scale();
        final float pixelWidth = inverseScale / pixelScaleX;
        final float pixelHeight = inverseScale / pixelScaleY;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glTranslatef(x, y, 0);
            GL11.glScalef(layout.scale(), layout.scale(), 1);

            for (LabelLine line : layout.lines()) {
                final float alignedX = (alignToPixel(x + line.x(), translateX, scaleX, guiScale) - x) * inverseScale;
                final float alignedY = (alignToPixel(y + line.y(), translateY, scaleY, guiScale) - y) * inverseScale;
                GL11.glTranslatef(alignedX, alignedY, 0);
                if (currentSettings.outline) {
                    drawOutlinedLine(font, line.text(), pixelWidth, pixelHeight);
                } else {
                    font.drawString(line.text(), 0, 0, 0xFFFFFFFF);
                }
                GL11.glTranslatef(-alignedX, -alignedY, 0);
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    public void setSettings(ItemNameOverlaySettings settings) {
        Objects.requireNonNull(settings);
        if (!this.settings.equals(settings)) {
            this.settings = settings;
            clearCache();
        }
    }

    public void refreshFontState() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        final Language language = minecraft.getLanguageManager()
            .getCurrentLanguage();
        final boolean languageChanged = layoutEngine.setLanguage(language != null ? language.getLanguageCode() : null);
        final Object currentState = ItemNameOverlayFont.currentState();
        final int currentScale = guiScaleCache.getScaleFactor(minecraft);
        final FontRenderer font = minecraft.fontRenderer;
        fontLookupKey.set(font, "", 0, 0, font.getUnicodeFlag(), settings);
        if (languageChanged || !Objects.equals(fontState, currentState)
            || !fontLookupKey.equals(fontKey)
            || guiScale != currentScale) {
            fontState = currentState;
            fontKey = new LayoutKey(fontLookupKey);
            guiScale = currentScale;
            clearCache();
        }
    }

    public void clearCache() {
        layouts.clear();
        failedStacks.clear();
        lookupKey.set(null, "", 0, 0, false, settings);
        layoutEngine.clear();
        cacheRevision++;
    }

    public long getCacheRevision() {
        return cacheRevision;
    }

    private int readRenderTransform() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        final int guiScale = guiScaleCache.getScaleFactor(minecraft);
        matrixBuffer.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrixBuffer);
        return guiScale;
    }

    private float alignToPixel(float coordinate, float translation, float scale, int guiScale) {
        final float screenPosition = (translation + coordinate * scale) * guiScale;
        return (Math.round(screenPosition) / (float) guiScale - translation) / scale;
    }

    private void drawOutlinedLine(FontRenderer font, String text, float pixelWidth, float pixelHeight) {
        // Draw the four neighboring screen pixels before returning to the center.
        for (int index = 0; index < OUTLINE_STEPS.length; index += 2) {
            GL11.glTranslatef(OUTLINE_STEPS[index] * pixelWidth, OUTLINE_STEPS[index + 1] * pixelHeight, 0);
            font.drawString(text, 0, 0, 0xFF000000);
        }
        GL11.glTranslatef(0, -pixelHeight, 0);
        font.drawString(text, 0, 0, 0xFFFFFFFF);
    }

    private static class GuiScaleCache {

        private int displayWidth = -1;
        private int displayHeight = -1;
        private int setting;
        private boolean unicode;
        private int scaleFactor;

        public int getScaleFactor(Minecraft minecraft) {
            final boolean currentUnicode = minecraft.func_152349_b();
            if (displayWidth != minecraft.displayWidth || displayHeight != minecraft.displayHeight
                || setting != minecraft.gameSettings.guiScale
                || unicode != currentUnicode) {
                displayWidth = minecraft.displayWidth;
                displayHeight = minecraft.displayHeight;
                setting = minecraft.gameSettings.guiScale;
                unicode = currentUnicode;
                scaleFactor = new ScaledResolution(minecraft, displayWidth, displayHeight).getScaleFactor();
            }
            return scaleFactor;
        }
    }

    private static class LayoutKey {

        private ItemNameOverlaySettings settings;
        private FontRenderer font;
        private String text;
        private int width;
        private int height;
        private int fontHeight;
        private float glyphScaleX;
        private float glyphScaleY;
        private float glyphSpacing;
        private float whitespaceScale;
        private boolean unicode;
        private int hash;

        public LayoutKey(FontRenderer font, String text, int width, int height, boolean unicode,
            ItemNameOverlaySettings settings) {
            set(font, text, width, height, unicode, settings);
        }

        public LayoutKey(LayoutKey key) {
            this.settings = key.settings;
            this.font = key.font;
            this.text = key.text;
            this.width = key.width;
            this.height = key.height;
            this.fontHeight = key.fontHeight;
            this.glyphScaleX = key.glyphScaleX;
            this.glyphScaleY = key.glyphScaleY;
            this.glyphSpacing = key.glyphSpacing;
            this.whitespaceScale = key.whitespaceScale;
            this.unicode = key.unicode;
            this.hash = key.hash;
        }

        public void set(FontRenderer font, String text, int width, int height, boolean unicode,
            ItemNameOverlaySettings settings) {
            this.settings = settings;
            this.font = font;
            this.text = text;
            this.width = width;
            this.height = height;
            this.fontHeight = font != null ? font.FONT_HEIGHT : 0;
            IFontParameters parameters = ItemNameOverlayFont.parameters(font);
            if (parameters != null) {
                this.glyphScaleX = parameters.getGlyphScaleX();
                this.glyphScaleY = parameters.getGlyphScaleY();
                this.glyphSpacing = parameters.getGlyphSpacing();
                this.whitespaceScale = parameters.getWhitespaceScale();
            } else {
                this.glyphScaleX = 1;
                this.glyphScaleY = 1;
                this.glyphSpacing = 0;
                this.whitespaceScale = 1;
            }
            this.unicode = unicode;
            int hash = System.identityHashCode(font);
            hash = 31 * hash + text.hashCode();
            hash = 31 * hash + settings.hashCode();
            hash = 31 * hash + width;
            hash = 31 * hash + height;
            hash = 31 * hash + fontHeight;
            hash = 31 * hash + Float.floatToIntBits(glyphScaleX);
            hash = 31 * hash + Float.floatToIntBits(glyphScaleY);
            hash = 31 * hash + Float.floatToIntBits(glyphSpacing);
            hash = 31 * hash + Float.floatToIntBits(whitespaceScale);
            this.hash = 31 * hash + Boolean.hashCode(unicode);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof LayoutKey key && settings.equals(key.settings)
                && font == key.font
                && text.equals(key.text)
                && width == key.width
                && height == key.height
                && fontHeight == key.fontHeight
                && Float.compare(glyphScaleX, key.glyphScaleX) == 0
                && Float.compare(glyphScaleY, key.glyphScaleY) == 0
                && Float.compare(glyphSpacing, key.glyphSpacing) == 0
                && Float.compare(whitespaceScale, key.whitespaceScale) == 0
                && unicode == key.unicode;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
