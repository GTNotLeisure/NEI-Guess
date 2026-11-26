package com.hfstudio.neiguess.mixins.late.search;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.asdflj.nech.utils.Match;
import com.hfstudio.guidenh.guide.internal.search.GuideTitleQuery;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

@Mixin(value = GuideTitleQuery.class, remap = false)
public abstract class MixinGuideTitleSearch {

    @Unique
    private String neiguess$query;

    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void neiguess$captureQuery(String query, CallbackInfo ci) {
        neiguess$query = query;
    }

    @ModifyReturnValue(method = "test(Ljava/lang/String;)Z", at = @At("RETURN"), require = 1)
    private boolean neiguess$matchTitle(boolean original, String title) {
        return Match.orQuery(original, title, neiguess$query);
    }
}
