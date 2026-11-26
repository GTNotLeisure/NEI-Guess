package com.asdflj.nech;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentTranslation;

import com.asdflj.nech.NechConfig.Spell;
import com.asdflj.nech.proxy.ClientProxy;
import com.asdflj.nech.utils.LuaPlugin;
import com.asdflj.nech.utils.Profiler;
import com.google.gson.GsonBuilder;

public class NechCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "nech";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/nech <reload|profile|verbose|keyboard>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 1 && "reload".equals(args[0])) {
            ClientProxy.sendToPlayer("nech.message.reload.started");
            NechConfig.reload();
            LuaPlugin.reloadLuaScript();
            ClientProxy.sendToPlayer("nech.message.reload.completed");
        } else if (args.length == 1 && "profile".equals(args[0])) {
            startProfiler(sender);
        } else if (args.length == 2 && "verbose".equals(args[0])) {
            if ("true".equalsIgnoreCase(args[1]) || "false".equalsIgnoreCase(args[1])) {
                NechConfig.setVerbose(Boolean.parseBoolean(args[1]));
            } else unknownCommand(sender);
        } else if (args.length == 2 && "keyboard".equals(args[0])) {
            String name = args[1].equalsIgnoreCase("pinyinjiajia") ? "PINYINPP" : args[1].toUpperCase(Locale.ROOT);
            try {
                NechConfig.setKeyboard(Spell.valueOf(name));
            } catch (IllegalArgumentException failure) {
                unknownCommand(sender);
            }
        } else unknownCommand(sender);
    }

    private static void startProfiler(ICommandSender sender) {
        Thread thread = new Thread(() -> {
            sendMessage(sender, "nech.message.profile.started");
            Path file = Paths.get("logs", "necharacters-profiler.txt");
            try {
                Profiler.Report report = Profiler.run();
                Files.createDirectories(file.getParent());
                try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    new GsonBuilder().setPrettyPrinting()
                        .create()
                        .toJson(report, writer);
                }
                sendMessage(sender, "nech.message.profile.saved");
            } catch (IOException | RuntimeException failure) {
                ClientProxy.LOGGER.error("Unable to write the NECH search profiler report to {}", file, failure);
                sendMessage(sender, "nech.message.profile.failed");
            }
        }, "NECH Search Profiler");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private static void sendMessage(ICommandSender sender, String key) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> sender.addChatMessage(new ChatComponentTranslation(key)));
    }

    private static void unknownCommand(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentTranslation("command.unknown"));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "profile", "verbose", "keyboard", "reload");
        if (args.length == 2 && "verbose".equals(args[0]))
            return getListOfStringsMatchingLastWord(args, "true", "false");
        if (args.length == 2 && "keyboard".equals(args[0])) return getListOfStringsMatchingLastWord(
            args,
            "quanpin",
            "daqian",
            "xiaohe",
            "ziranma",
            "sougou",
            "guobiao",
            "microsoft",
            "pinyinjiajia",
            "ziguang");
        return null;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 1;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }
}
