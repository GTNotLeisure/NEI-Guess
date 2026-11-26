package com.hfstudio.neiguess.mixins.late.search;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.asdflj.nech.utils.Match;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Pseudo
@Mixin(targets = "appeng.client.me.ItemRepo", remap = false)
public abstract class MixinAeSearch {

    @Shadow
    private Map<?, ?> searchCache;

    @Shadow
    public abstract void updateView();

    @Unique
    private long neiguess$matchRevision = -1;

    @Unique
    private boolean neiguess$invalidateSearch() {
        long revision = Match.revision();
        if (neiguess$matchRevision == revision) return false;
        searchCache.clear();
        neiguess$matchRevision = revision;
        return true;
    }

    @Inject(method = "updateView", at = @At("HEAD"), require = 1)
    private void neiguess$refreshSearch(CallbackInfo callback) {
        neiguess$invalidateSearch();
    }

    @Inject(method = "updateViewIfChanged", at = @At("HEAD"), cancellable = true, require = 0)
    private void neiguess$refreshChangedView(CallbackInfoReturnable<Boolean> callback) {
        if (neiguess$invalidateSearch()) {
            updateView();
            callback.setReturnValue(true);
        }
    }

    @WrapOperation(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/regex/Pattern;matcher(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;"),
        require = 0)
    private static Matcher neiguess$matchSearch(Pattern pattern, CharSequence text, Operation<Matcher> original) {
        return Match.matcher(pattern, text, original.call(pattern, text));
    }
}
