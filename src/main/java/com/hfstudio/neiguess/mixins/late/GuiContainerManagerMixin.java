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

    // Keep optional labels outside NEI's item failure tracking.
    @Inject(
        method = "drawItem(IILnet/minecraft/item/ItemStack;Lnet/minecraft/client/gui/FontRenderer;ZLjava/lang/String;)V",
        at = @At("RETURN"),
        require = 1)
    private static void neiguess$drawName(int x, int y, ItemStack stack, FontRenderer font, boolean smallAmount,
        String quantity, CallbackInfo ci) {
        ItemNameOverlay.render(smallAmount, quantity, stack, font, x, y);
    }
}
