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
@Mixin(targets = "appeng.client.me.ItemRepo", remap = false)
public abstract class MixinAeSearch {

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
