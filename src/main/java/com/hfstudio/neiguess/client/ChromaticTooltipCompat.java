package com.hfstudio.neiguess.client;

import java.awt.Point;

import com.slprime.chromatictooltips.TooltipRegistry;
import com.slprime.chromatictooltips.api.ITargetSanitizer;
import com.slprime.chromatictooltips.api.TooltipTarget;

import codechicken.lib.gui.GuiDraw;

public final class ChromaticTooltipCompat implements ITargetSanitizer {

    private static final ChromaticTooltipCompat INSTANCE = new ChromaticTooltipCompat();

    private ChromaticTooltipCompat() {}

    public static void register() {
        TooltipRegistry.addTargetSanitizer(INSTANCE);
    }

    @Override
    public TooltipTarget sanitize(TooltipTarget target) {
        if (target == null || !target.isItem() && !target.isFluid()) return target;
        Point mouse = GuiDraw.getMousePosition();
        return SuggestOverlay.INSTANCE.shouldHideTooltipAt(mouse.x, mouse.y) ? TooltipTarget.EMPTY : target;
    }
}
