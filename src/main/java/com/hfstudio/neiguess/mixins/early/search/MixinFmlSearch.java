package com.hfstudio.neiguess.mixins.early.search;

import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.asdflj.nech.utils.Match;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import cpw.mods.fml.client.GuiModList;
import cpw.mods.fml.client.config.GuiConfig;

@Mixin(value = { GuiConfig.class, GuiModList.class }, remap = false, priority = 1100)
public abstract class MixinFmlSearch {

    @Dynamic("Merged by Hodgepodge's FML search mixins.")
    @WrapOperation(
        method = { "hodgepodge$doesElementMatchSearch", "hodgepodge$reloadMods" },
        at = @At(value = "INVOKE", target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"),
        require = 0)
    private static boolean neiguess$matchSearch(String text, CharSequence query, Operation<Boolean> original) {
        return original.call(text, query) || Match.contains(text, query);
    }
}
