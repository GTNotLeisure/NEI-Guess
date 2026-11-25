package com.hfstudio.neiguess.client.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.item.ItemStack;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.util.ReadableNumberConverter;

/** Integrates icon name rendering with NEI 2.8's configuration and render hooks. */
public final class ItemNameOverlay {

    private ItemNameOverlay() {}

    public static void registerResourceListener() {
        if (Minecraft.getMinecraft()
            .getResourceManager() instanceof IReloadableResourceManager manager) {
            manager.registerReloadListener(resources -> ItemNameOverlayRenderer.INSTANCE.clearCache());
        }
    }

    public static void refresh() {
        ItemNameOverlayConfig.reloadSettings();
        ItemNameOverlayRenderer renderer = ItemNameOverlayRenderer.INSTANCE;
        if (renderer.settings.mode != ItemNameOverlayMode.OFF) renderer.refreshFontState();
    }

    public static void render(boolean smallAmount, String quantity, ItemStack stack, FontRenderer font, int x, int y) {
        ItemNameOverlayRenderer renderer = ItemNameOverlayRenderer.INSTANCE;
        if (renderer.settings.mode == ItemNameOverlayMode.OFF || stack == null || stack.getItem() == null) return;

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
        int height = quantity.isEmpty() ? 11
            : Math.max(0, (int) (16 - (Math.max(font.FONT_HEIGHT, GuiDraw.fontRenderer.FONT_HEIGHT) - 1) * scale - 1));
        renderer.render(renderer.settings.mode, stack, font, x, y, height);
    }
}
