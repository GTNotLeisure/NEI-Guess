package com.hfstudio.neiguess.mixins.late;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hfstudio.neiguess.client.overlay.ItemNameOverlay;

import codechicken.nei.guihook.GuiContainerManager;

@Mixin(value = GuiContainerManager.class, remap = false)
public abstract class GuiContainerManagerMixin {

    @Inject(method = "preDraw", at = @At("HEAD"), require = 1)
    private void neiguess$refreshNames(CallbackInfo ci) {
        ItemNameOverlay.refresh();
    }

    // Run inside NEI's safe render context, after both the icon and its amount have been drawn.
    @Inject(method = "lambda$drawItem$0", at = @At("RETURN"), require = 1)
    private static void neiguess$drawName(boolean smallAmount, String quantity, ItemStack stack, FontRenderer font,
        int x, int y, CallbackInfo ci) {
        ItemNameOverlay.render(smallAmount, quantity, stack, font, x, y);
    }
}
