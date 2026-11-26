package com.hfstudio.neiguess.mixins.late.search;

import java.util.Locale;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.asdflj.nech.utils.Match;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

@Pseudo
@Mixin(targets = "codechicken.wirelessredstone.core.GuiNameSlot", remap = false)
public abstract class MixinFrequencySearch {

    @ModifyReturnValue(method = "doesNameMatch", at = @At("RETURN"), require = 1)
    private static boolean neiguess$matchName(boolean original, String name, String query) {
        return original || Match.begins(name.toLowerCase(Locale.ROOT), query.toLowerCase(Locale.ROOT));
    }
}
