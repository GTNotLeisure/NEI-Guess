package com.hfstudio.neiguess.mixins.late.search;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.asdflj.nech.utils.Match;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Pseudo
@Mixin(targets = "com.gtnewhorizon.structurelib.gui.GuiScreenConfigureChannels$GuiAutoCompleteList", remap = false)
public abstract class MixinChannelSearch {

    @WrapOperation(
        method = "search",
        at = @At(value = "INVOKE", target = "Ljava/lang/String;startsWith(Ljava/lang/String;)Z"),
        require = 1)
    private static boolean neiguess$matchChannel(String text, String query, Operation<Boolean> original) {
        return original.call(text, query) || Match.begins(text, query);
    }
}
