package com.hfstudio.neiguess.mixins.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hfstudio.neiguess.client.overlay.ItemNameOverlayRenderer;

@Mixin(targets = "codechicken.nei.ItemsGrid$ScreenCapture", remap = false)
public abstract class ScreenCaptureMixin {

    @Unique
    private long neiguess$nameRevision = -1;

    @Inject(method = "needRefresh", at = @At("RETURN"), cancellable = true, require = 1)
    private void neiguess$invalidateNames(int mode, CallbackInfoReturnable<Boolean> cir) {
        if (neiguess$nameRevision != ItemNameOverlayRenderer.INSTANCE.getCacheRevision()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
        method = "captureScreen",
        at = @At(value = "INVOKE", target = "Ljava/lang/Runnable;run()V", shift = At.Shift.AFTER),
        require = 1)
    private void neiguess$captureNames(Runnable callback, int mode, CallbackInfo ci) {
        neiguess$nameRevision = ItemNameOverlayRenderer.INSTANCE.getCacheRevision();
    }
}
