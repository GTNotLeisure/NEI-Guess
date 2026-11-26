package com.asdflj.nech.proxy;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.client.ClientCommandHandler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.asdflj.nech.NechClientEvents;
import com.asdflj.nech.NechCommand;
import com.asdflj.nech.NechConfig;
import com.asdflj.nech.utils.PinInPlugin;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    public static final Logger LOGGER = LogManager.getLogger("Never Enough Characters");

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        NechConfig.registerConfig();
        FMLCommonHandler.instance()
            .bus()
            .register(NechClientEvents.INSTANCE);
        ClientCommandHandler.instance.registerCommand(new NechCommand());
        new PinInPlugin().run();
    }

    public static void sendToPlayer(String message, Object... args) {
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.func_152344_a(
            () -> {
                if (minecraft.thePlayer != null)
                    minecraft.thePlayer.addChatMessage(new ChatComponentTranslation(message, args));
            });
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
        NechConfig.onConfigChanged();
    }
}
