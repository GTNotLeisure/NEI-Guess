package com.hfstudio.neiguess;

import static com.hfstudio.neiguess.NEIGuess.MODID;
import static com.hfstudio.neiguess.NEIGuess.MODNAME;

import net.minecraft.util.StatCollector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.hfstudio.neiguess.config.ModConfig;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = MODID,
    version = Tags.VERSION,
    name = MODNAME,
    guiFactory = "com.hfstudio.neiguess.config.NEIGuessGuiFactory",
    dependencies = "required-after:NotEnoughItems;required-after:gtnhlib;after:betterquesting;after:nech",
    acceptableRemoteVersions = "*",
    acceptedMinecraftVersions = "[1.7.10]")
public class NEIGuess {

    public static final String MODID = Tags.MODID;
    public static final String MODNAME = Tags.MODNAME;
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.hfstudio.neiguess.ClientProxy", serverSide = "com.hfstudio.neiguess.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) throws ConfigException {
        ModConfig.registerConfig();
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
    public void onIMC(FMLInterModComms.IMCEvent event) {
        proxy.handleIMC(event);
    }

    public static boolean debug() {
        return ModConfig.debug.enableDebugMode;
    }

    public static String tr(String key, Object... args) {
        String full = MODID + "." + key;
        return args.length == 0 ? StatCollector.translateToLocal(full)
            : StatCollector.translateToLocalFormatted(full, args);
    }
}
