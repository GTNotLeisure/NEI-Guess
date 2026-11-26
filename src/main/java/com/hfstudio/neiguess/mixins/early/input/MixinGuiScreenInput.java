package com.hfstudio.neiguess.mixins.early.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.hfstudio.neiguess.client.SuggestOverlay;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(GuiScreen.class)
public abstract class MixinGuiScreenInput {

    @WrapOperation(
        method = "handleInput",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;handleMouseInput()V"),
        require = 1)
    private void neiguess$capturePopupMouse(GuiScreen screen, Operation<Void> original) {
        Minecraft minecraft = Minecraft.getMinecraft();
        int mouseX = Mouse.getEventX() * screen.width / minecraft.displayWidth;
        int mouseY = screen.height - Mouse.getEventY() * screen.height / minecraft.displayHeight - 1;
        if (!SuggestOverlay.INSTANCE.handleMouseInput(
            screen,
            mouseX,
            mouseY,
            Mouse.getEventButton(),
            Mouse.getEventButtonState(),
            Mouse.getEventDWheel())) original.call(screen);
    }
}
