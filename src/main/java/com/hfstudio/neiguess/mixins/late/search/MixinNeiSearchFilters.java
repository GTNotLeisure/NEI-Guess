package com.hfstudio.neiguess.mixins.late.search;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.asdflj.nech.utils.Match;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import codechicken.nei.search.ModNameFilter;
import codechicken.nei.search.OreDictionaryFilter;

@Mixin(value = { ModNameFilter.class, OreDictionaryFilter.class }, remap = false)
public abstract class MixinNeiSearchFilters {

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
