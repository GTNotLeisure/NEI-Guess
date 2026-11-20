package com.asdflj.nech.core;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

import cpw.mods.fml.relauncher.FMLLaunchHandler;

@LateMixin
public class NechLatePlugin implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.nech.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        if (FMLLaunchHandler.side()
            .isClient() && loadedMods.contains("controlling")) {
            return Collections.singletonList("MixinSearchType");
        }

        return Collections.emptyList();
    }

}
