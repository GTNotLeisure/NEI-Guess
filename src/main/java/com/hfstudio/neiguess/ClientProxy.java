package com.hfstudio.neiguess;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;

import com.hfstudio.neiguess.api.NEIGuessAPI;
import com.hfstudio.neiguess.client.ChromaticTooltipCompat;
import com.hfstudio.neiguess.client.ClientEvents;
import com.hfstudio.neiguess.client.GuessCommand;
import com.hfstudio.neiguess.client.ItemIndexes;
import com.hfstudio.neiguess.client.ModularUI2Compat;
import com.hfstudio.neiguess.client.SearchHistoryStore;
import com.hfstudio.neiguess.client.SuggestOverlay;
import com.hfstudio.neiguess.client.source.BuiltinSources;

import codechicken.nei.ItemList;
import codechicken.nei.guihook.GuiContainerManager;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        SearchHistoryStore.get()
            .load();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        BuiltinSources.registerAll();
        MinecraftForge.EVENT_BUS.register(ClientEvents.INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(ClientEvents.INSTANCE);
        if (Loader.isModLoaded("chromatictooltips")) ChromaticTooltipCompat.register();
        if (Loader.isModLoaded("modularui2")) MinecraftForge.EVENT_BUS.register(ModularUI2Compat.INSTANCE);
        ClientCommandHandler.instance.registerCommand(new GuessCommand());
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        GuiContainerManager.inputHandlers.addFirst(SuggestOverlay.INSTANCE);
        GuiContainerManager.addDrawHandler(SuggestOverlay.INSTANCE);
        GuiContainerManager.addObjectHandler(SuggestOverlay.INSTANCE);
        GuiContainerManager.addTooltipHandler(SuggestOverlay.INSTANCE);
        ItemList.loadCallbacks.add(ItemIndexes::rebuild);
    }

    @Override
    public void handleIMC(FMLInterModComms.IMCEvent event) {
        for (FMLInterModComms.IMCMessage message : event.getMessages()) {
            if (message.isStringMessage())
                NEIGuessAPI.handleIMC(message.getSender(), message.key, message.getStringValue());
        }
    }
}
