package com.hfstudio.example.config;

import net.minecraft.client.gui.GuiScreen;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.SimpleGuiConfig;
import com.hfstudio.example.Example;

public class ExampleGuiConfig extends SimpleGuiConfig {

    public ExampleGuiConfig(GuiScreen parentScreen) throws ConfigException {
        super(parentScreen, Example.MODID, Example.MODNAME, true, ModConfig.class);
    }
}
