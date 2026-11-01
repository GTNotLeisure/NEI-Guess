package com.hfstudio.neiguess.config;

import net.minecraft.client.gui.GuiScreen;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.SimpleGuiConfig;
import com.hfstudio.neiguess.NEIGuess;

public class NEIGuessGuiConfig extends SimpleGuiConfig {

    public NEIGuessGuiConfig(GuiScreen parentScreen) throws ConfigException {
        super(parentScreen, NEIGuess.MODID, NEIGuess.MODNAME, true, ModConfig.class);
    }
}
