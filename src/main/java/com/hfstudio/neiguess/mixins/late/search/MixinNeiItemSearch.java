package com.hfstudio.neiguess.mixins.late.search;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.asdflj.nech.utils.Match;
import com.hfstudio.neiguess.client.search.ClientSearchAliases;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import codechicken.nei.ItemList.PatternItemFilter;

@Mixin(value = PatternItemFilter.class, remap = false)
public abstract class MixinNeiItemSearch {

    @Shadow
    public Pattern pattern;

    @WrapOperation(
        method = "matches",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/regex/Pattern;matcher(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;"),
        require = 1)
    private Matcher neiguess$matchItemName(Pattern pattern, CharSequence text, Operation<Matcher> original) {
        return Match.matcher(pattern, text, original.call(pattern, text), false);
    }

    @ModifyReturnValue(method = "matches", at = @At("RETURN"), require = 1)
    private boolean neiguess$matchItemAlias(boolean original, ItemStack stack) {
        if (original) return true;
        String query = Match.literalPattern(pattern);
        return query != null && ClientSearchAliases.INSTANCE.matchesItem(stack, query);
    }
}
