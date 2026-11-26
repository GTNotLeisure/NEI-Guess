package com.asdflj.nech;

import com.asdflj.nech.integration.nei.NEIConfig;
import com.asdflj.nech.utils.LuaPlugin;
import com.asdflj.nech.utils.Match;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class NechClientEvents {

    public static final NechClientEvents INSTANCE = new NechClientEvents();

    private NechClientEvents() {}

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (NeverEnoughCharacters.MODID.equals(event.modID)) NechConfig.onConfigChanged();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        boolean settingsChanged = NEIConfig.reloadSettings();
        boolean scriptsChanged = LuaPlugin.reloadStates();
        if (settingsChanged || scriptsChanged) Match.refreshInput();
    }
}
