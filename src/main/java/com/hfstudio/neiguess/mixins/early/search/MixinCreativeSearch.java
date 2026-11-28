package com.hfstudio.neiguess.mixins.early.search;

import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.asdflj.nech.utils.Match;
import com.hfstudio.neiguess.client.search.ClientSearchAliases;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(value = GuiContainerCreative.class, priority = 1100)
public abstract class MixinCreativeSearch {

    @WrapOperation(
        method = "updateFilteredItems(Lnet/minecraft/client/gui/inventory/GuiContainerCreative$ContainerCreative;)V",
        at = @At(value = "INVOKE", target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"),
        remap = false,
        require = 1)
    private static boolean neiguess$matchSearch(String text, CharSequence query, Operation<Boolean> original,
        @Local ItemStack stack) {
        return original.call(text, query) || Match.containsText(text, query)
            || ClientSearchAliases.INSTANCE.matchesItem(stack, query.toString());
    }

    @Dynamic("Merged by ArchaicFix's asynchronous creative-search mixin; its lambda name is generated.")
    @WrapOperation(
        method = "(Ljava/util/function/Function;Ljava/lang/String;Lnet/minecraft/item/ItemStack;)Z",
        at = @At(value = "INVOKE", target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"),
        remap = false,
        require = 0,
        expect = 0)
    private static boolean neiguess$matchAsyncSearch(String text, CharSequence query, Operation<Boolean> original,
        @Local(argsOnly = true) ItemStack stack) {
        return original.call(text, query) || Match.containsText(text, query)
            || ClientSearchAliases.INSTANCE.matchesItem(stack, query.toString());
    }
}
