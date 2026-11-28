package com.hfstudio.neiguess.mixins.early.search;

import net.minecraft.client.gui.GuiSelectWorld;

import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.asdflj.nech.utils.Match;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(value = GuiSelectWorld.class, priority = 1100)
public abstract class MixinWorldSearch {

    @Dynamic("Merged by Et Futurum Requiem's world-search mixin.")
    @WrapOperation(
        method = "etfu$updateFilter(Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"),
        remap = false,
        require = 0)
    private static boolean neiguess$matchSearch(String text, CharSequence query, Operation<Boolean> original) {
        return original.call(text, query) || Match.contains(text, query);
    }
}
