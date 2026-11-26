package com.hfstudio.neiguess.mixins.late.search;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.asdflj.nech.utils.Match;
import com.hfstudio.guidenh.guide.mediawiki.MediaWikiSpecialPageResolver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(value = MediaWikiSpecialPageResolver.class, remap = false)
public abstract class MixinGuidePrefixSearch {

    @WrapOperation(
        method = "matchesPrefix",
        at = @At(value = "INVOKE", target = "Ljava/lang/String;startsWith(Ljava/lang/String;)Z"),
        require = 1)
    private boolean neiguess$matchPagePrefix(String text, String query, Operation<Boolean> original) {
        return original.call(text, query) || Match.begins(text, query);
    }
}
