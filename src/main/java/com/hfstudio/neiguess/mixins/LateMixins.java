package com.hfstudio.neiguess.mixins;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

import cpw.mods.fml.relauncher.FMLLaunchHandler;

@LateMixin
public class LateMixins implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.neiguess.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        if (!FMLLaunchHandler.side()
            .isClient() || !loadedMods.contains("NotEnoughItems")) {
            return Collections.emptyList();
        }
        // Avoid double rendering if a newer NEI already contains the original feature.
        if (LateMixins.class.getClassLoader()
            .getResource("codechicken/nei/item/ItemNameOverlayRenderer.class") != null) {
            return Collections.emptyList();
        }
        return List.of("GuiContainerManagerMixin", "ScreenCaptureMixin");
    }
}
