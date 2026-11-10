package com.hfstudio.neiguess.client;

import com.cleanroommc.modularui.screen.RichTooltipEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class ModularUI2Compat {

    public static final ModularUI2Compat INSTANCE = new ModularUI2Compat();

    private ModularUI2Compat() {}

    // ChromaticTooltips bypasses RichTooltip.draw for item stacks; ChromaticTooltipCompat covers that path.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRichTooltipPre(RichTooltipEvent.Pre event) {
        if (SuggestOverlay.INSTANCE.shouldHideTooltipAt(event.getX(), event.getY())) event.setCanceled(true);
    }
}
