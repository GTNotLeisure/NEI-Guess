package com.hfstudio.neiguess.mixins.late.search;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.asdflj.nech.utils.Match;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Pseudo
@Mixin(targets = "com.blamejared.controlling.client.gui.SearchType", remap = false)
public abstract class MixinSearchType {

    @WrapOperation(
        method = "*",
        at = @At(value = "INVOKE", target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"),
        require = 0)
    private static boolean neiguess$matchSearch(String text, CharSequence query, Operation<Boolean> original) {
        return original.call(text, query) || Match.contains(text, query);
    }
}
