package com.asdflj.nech.utils;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.asdflj.nech.integration.nei.LuaToggleButton;
import com.asdflj.nech.proxy.ClientProxy;
import com.asdflj.nech.utils.lua.LuaScript;

import codechicken.nei.api.API;
import codechicken.nei.config.Option;
import codechicken.nei.config.OptionList;
import cpw.mods.fml.relauncher.FMLInjectionData;

public class LuaPlugin {

    public static volatile List<LuaObject> list = List.of();

    public static List<LuaObject> readLuaFiles(String directoryPath) {
        Path directory = Paths.get(directoryPath);
        List<Path> scripts;
        try {
            Files.createDirectories(directory);
            try (Stream<Path> files = Files.list(directory)) {
                scripts = files.filter(Files::isRegularFile)
                    .filter(
                        path -> path.getFileName()
                            .toString()
                            .endsWith(".lua"))
                    .sorted()
                    .toList();
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Unable to enumerate Lua scripts in " + directory, failure);
        }
        List<LuaObject> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Path path : scripts) {
            ClientProxy.LOGGER.info("Loading Lua script: {}", path.getFileName());
            try {
                LuaObject script = new LuaObject(path);
                String name = script.getName();
                if (name != null && !name.isEmpty() && !names.add(name)) {
                    throw new IllegalArgumentException("Duplicate Lua option name: " + name);
                }
                result.add(script);
            } catch (RuntimeException failure) {
                ClientProxy.LOGGER.warn("Invalid Lua script: {}", path.getFileName(), failure);
                ClientProxy.sendToPlayer(
                    "nech.message.lua.invalid",
                    path.getFileName()
                        .toString());
            }
        }
        return List.copyOf(result);
    }

    public static synchronized void loadLuaScript() {
        List<LuaObject> loaded;
        try {
            loaded = readLuaFiles(new File((File) FMLInjectionData.data()[6], "pinin").getPath());
        } catch (UncheckedIOException failure) {
            ClientProxy.LOGGER.warn("Keeping the previous Lua scripts after a directory read failure", failure);
            return;
        }
        for (LuaObject script : list) {
            LuaToggleButton button = script.getBtn();
            if (button != null && button.parent != null) {
                button.parent.optionList.remove(button);
                button.parent.options.values()
                    .removeIf(option -> option == button);
            }
        }
        for (LuaObject script : loaded) script.registerButton();
        list = loaded;
        Match.refreshInput();
    }

    public static OptionList getOptionList(String name, OptionList optionList) {
        if (optionList == null) return null;
        if (name.equals(optionList.name)) return optionList;
        for (Option option : optionList.optionList) {
            if (option instanceof OptionList child) {
                OptionList result = getOptionList(name, child);
                if (result != null) return result;
            }
        }
        return null;
    }

    public static boolean reloadStates() {
        boolean changed = false;
        for (LuaObject script : list) changed |= script.reloadState();
        return changed;
    }

    public static void reloadLuaScript() {
        loadLuaScript();
    }

    public static class LuaObject {

        private final LuaScript script;
        private LuaToggleButton button;
        private volatile boolean enabled = true;

        public LuaObject(Path path) {
            script = new LuaScript(path);
        }

        private void registerButton() {
            String name = getName();
            if (name == null || name.isEmpty()) return;
            button = new LuaToggleButton(name, this::reloadState);
            API.addOption(button);
            reloadState();
        }

        private boolean reloadState() {
            boolean current = button == null || button.state();
            boolean changed = enabled != current;
            enabled = current;
            return changed;
        }

        public LuaToggleButton getBtn() {
            return button;
        }

        public boolean getState() {
            return enabled;
        }

        public String textHandler(String text) {
            return script.text(text);
        }

        public String getName() {
            return script.name();
        }

        public Set<String> inputHandler(String input) {
            return script.input(input);
        }
    }
}
