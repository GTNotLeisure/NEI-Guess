package com.hfstudio.neiguess.mixins.late.search;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.asdflj.nech.utils.Match;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Pseudo
@Mixin(
    targets = { "codechicken.nei.search.TooltipFilter", "codechicken.nei.filter.RecipeTextFilter",
        "codechicken.nei.SubsetWidget$DefaultParserProvider" },
    remap = false)
public abstract class MixinNeiTextSearch {

    @WrapOperation(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/regex/Pattern;matcher(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;"),
        require = 0)
    private static Matcher neiguess$matchText(Pattern pattern, CharSequence text, Operation<Matcher> original) {
        return Match.matcher(pattern, text, original.call(pattern, text));
    }

    @WrapOperation(
        method = "matches",
        at = @At(value = "INVOKE", target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"),
        require = 0)
    private static boolean neiguess$matchSubset(String text, CharSequence query, Operation<Boolean> original) {
        return original.call(text, query) || Match.contains(text, query);
    }
}
