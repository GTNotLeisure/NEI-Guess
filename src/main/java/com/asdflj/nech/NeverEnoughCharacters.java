package com.asdflj.nech;

import com.asdflj.nech.proxy.CommonProxy;
import com.hfstudio.neiguess.Tags;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(
    modid = NeverEnoughCharacters.MODID,
    version = Tags.VERSION,
    name = NeverEnoughCharacters.MOD_NAME,
    dependencies = "required-after:NotEnoughItems;required-after:gtnhlib;required-after:unimixins",
    guiFactory = "com.asdflj.nech.config.NechGuiFactory",
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*")
public class NeverEnoughCharacters {

    public static final String MODID = "nech";
    public static final String MOD_NAME = "NeverEnoughCharacters-Rework";

    @SidedProxy(clientSide = "com.asdflj.nech.proxy.ClientProxy", serverSide = "com.asdflj.nech.proxy.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
