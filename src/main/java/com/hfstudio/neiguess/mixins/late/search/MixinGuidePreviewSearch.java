package com.hfstudio.neiguess.mixins.late.search;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.asdflj.nech.utils.Match;
import com.hfstudio.guidenh.bridge.preview.ItemPreviewSearchService;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

@Mixin(value = ItemPreviewSearchService.class, remap = false)
public abstract class MixinGuidePreviewSearch {

    @ModifyReturnValue(method = "scoreEntry", at = @At("RETURN"), require = 1)
    private int neiguess$matchPreview(int original, String id, String label, String detail, String query) {
        if (original != Integer.MAX_VALUE) return original;
        if (Match.containsIgnoreCase(label, query)) return 17;
        if (Match.containsIgnoreCase(detail, query)) return 18;
        return original;
    }
}
