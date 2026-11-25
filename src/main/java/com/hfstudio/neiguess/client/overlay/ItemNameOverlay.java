package com.hfstudio.neiguess.client.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.item.ItemStack;

import com.hfstudio.neiguess.NEIGuess;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.LRUCache;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.util.ReadableNumberConverter;

public class ItemNameOverlay {

    private static final LRUCache<Class<?>, Boolean> REPORTED_FAILURES = new LRUCache<>(32);

    private ItemNameOverlay() {}

    public static void registerResourceListener() {
        if (Minecraft.getMinecraft()
            .getResourceManager() instanceof IReloadableResourceManager manager) {
            manager.registerReloadListener(resources -> ItemNameOverlayRenderer.INSTANCE.clearCache());
        }
    }

    public static void refresh() {
        try {
            ItemNameOverlayConfig.reloadSettings();
            ItemNameOverlayRenderer renderer = ItemNameOverlayRenderer.INSTANCE;
            if (renderer.getSettings().mode != ItemNameOverlayMode.OFF) renderer.refreshFontState();
        } catch (Throwable failure) {
            reportFailure(failure);
        }
    }

    public static void render(boolean smallAmount, String quantity, ItemStack stack, FontRenderer font, int x, int y) {
        ItemNameOverlayRenderer renderer = ItemNameOverlayRenderer.INSTANCE;
        if (renderer.getSettings().mode == ItemNameOverlayMode.OFF || stack == null
            || font == null
            || renderer.hasFailed(stack)) return;

        try {
            renderName(smallAmount, quantity, stack, font, x, y, renderer);
        } catch (Throwable failure) {
            renderer.recordFailure(stack);
            reportFailure(failure);
        }
    }

    private static void renderName(boolean smallAmount, String quantity, ItemStack stack, FontRenderer font, int x,
        int y, ItemNameOverlayRenderer renderer) {

        // Match NEI's quantity scaling before it clears the separately drawn small overlay.
        float scale = smallAmount ? 0.5f : 1f;
        if (quantity == null) {
            quantity = "";
            if (stack.stackSize > 1) {
                if (NEIClientConfig.getBooleanSetting("inventory.dynamicFontSize")) {
                    quantity = ReadableNumberConverter.INSTANCE.toWideReadableForm(stack.stackSize);
                    if (quantity.length() == 3) scale = 0.8f;
                    else if (quantity.length() == 4) scale = 0.6f;
                    else if (quantity.length() > 4) scale = 0.5f;
                } else {
                    quantity = String.valueOf(stack.stackSize);
                }
            }
        }
        int height = 11;
        if (!quantity.isEmpty()) {
            float quantityHeight = Math
                .max(ItemNameOverlayFont.lineHeight(font), ItemNameOverlayFont.lineHeight(GuiDraw.fontRenderer));
            height = Math.max(0, (int) (16 - (quantityHeight - 1) * scale - 1));
        }
        renderer.render(renderer.getSettings().mode, stack, font, x, y, height);
    }

    public static long getCacheRevision() {
        return ItemNameOverlayRenderer.INSTANCE.getCacheRevision();
    }

    // Legacy runtimes still use ThreadDeath to signal thread termination.
    @SuppressWarnings("removal")
    private static void reportFailure(Throwable failure) {
        if (failure instanceof VirtualMachineError error) throw error;
        if (failure instanceof ThreadDeath death) throw death;
        Class<?> failureType = failure.getClass();
        if (REPORTED_FAILURES.get(failureType) == null) {
            REPORTED_FAILURES.put(failureType, true);
            NEIGuess.LOG.warn("Unable to render an icon name overlay", failure);
        }
    }
}
