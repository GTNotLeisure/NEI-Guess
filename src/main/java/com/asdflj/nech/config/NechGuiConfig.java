package com.asdflj.nech.config;

import net.minecraft.client.gui.GuiScreen;

import com.asdflj.nech.NechConfig;
import com.asdflj.nech.NeverEnoughCharacters;
import com.gtnewhorizon.gtnhlib.config.SimpleGuiConfig;

public class NechGuiConfig extends SimpleGuiConfig {

    public NechGuiConfig(GuiScreen parent) {
        super(parent, NechConfig.class, NeverEnoughCharacters.MODID, NeverEnoughCharacters.MOD_NAME);
    }
}
