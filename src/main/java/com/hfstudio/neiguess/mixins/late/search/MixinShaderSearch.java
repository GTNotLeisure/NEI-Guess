package com.hfstudio.neiguess.mixins.late.search;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.asdflj.nech.utils.Match;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Pseudo
@Mixin(
    targets = { "net.coderbot.iris.shaderpack.option.menu.ShaderSearchEngine",
        "net.coderbot.iris.shaderpack.option.menu.ShaderSearchEngine$ScoredOptionElement" },
    remap = false)
public abstract class MixinShaderSearch {

    @WrapOperation(
        method = { "computeMatchTier", "compareByPrefixBoost", "findMatchingWord" },
        at = @At(value = "INVOKE", target = "Ljava/lang/String;startsWith(Ljava/lang/String;)Z"),
        require = 0)
    private static boolean neiguess$matchPrefix(String text, String query, Operation<Boolean> original) {
        return original.call(text, query) || Match.begins(text, query);
    }
}
