package com.hfstudio.neiguess.mixins.late.search;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.asdflj.nech.utils.Match;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Pseudo
@Mixin(
    targets = { "com.projecturanus.betterp2p.client.gui.Filter",
        "com.projecturanus.betterp2p.client.gui.InfoList$refilter$$inlined$sortedBy$1" },
    remap = false)
public abstract class MixinP2PSearch {

    @WrapOperation(
        method = { "_init_$lambda$4", "_init_$lambda$5" },
        at = @At(
            value = "INVOKE",
            target = "Lkotlin/text/StringsKt;contains$default(Ljava/lang/CharSequence;Ljava/lang/CharSequence;ZILjava/lang/Object;)Z"),
        require = 0)
    private static boolean neiguess$matchTunnel(CharSequence text, CharSequence query, boolean ignoreCase, int mask,
        Object marker, Operation<Boolean> original) {
        return original.call(text, query, ignoreCase, mask, marker)
            || Match.contains(text, query, (mask & 2) == 0 && ignoreCase);
    }

    @WrapOperation(
        method = "compare",
        at = @At(
            value = "INVOKE",
            target = "Lkotlin/text/StringsKt;contains(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z"),
        require = 0)
    private static boolean neiguess$rankTunnel(CharSequence text, CharSequence query, boolean ignoreCase,
        Operation<Boolean> original) {
        return original.call(text, query, ignoreCase) || Match.contains(text, query, ignoreCase);
    }
}
