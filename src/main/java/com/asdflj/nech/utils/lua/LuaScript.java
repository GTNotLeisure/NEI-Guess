package com.asdflj.nech.utils.lua;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

public class LuaScript {

    private static final Logger LOGGER = LogManager.getLogger("NECH Lua");
    private final Path path;
    private final String name;
    private final LuaValue textHandler;
    private final LuaValue inputHandler;
    private boolean textFailed;
    private boolean inputFailed;

    public LuaScript(Path path) {
        this.path = path;
        Globals globals = JsePlatform.standardGlobals();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            globals.load(reader, path.toString())
                .call();
        } catch (IOException failure) {
            throw new UncheckedIOException("Unable to read Lua script " + path, failure);
        }
        textHandler = handler(globals, "text_handler");
        inputHandler = handler(globals, "input_handler");
        LuaValue scriptName = globals.get("name");
        name = scriptName.isnil() ? null : (scriptName.isfunction() ? scriptName.call() : scriptName).checkjstring();
    }

    private static LuaValue handler(Globals globals, String key) {
        LuaValue value = globals.get(key);
        if (!value.isnil() && !value.isfunction())
            throw new IllegalArgumentException("Lua " + key + " must be a function");
        return value;
    }

    public String name() {
        return name;
    }

    public synchronized String text(String text) {
        if (textFailed || textHandler.isnil()) return text;
        try {
            return textHandler.call(LuaValue.valueOf(text))
                .checkjstring();
        } catch (RuntimeException failure) {
            textFailed = true;
            LOGGER.warn("Disabled text_handler in {} until reload", path, failure);
            return text;
        }
    }

    public synchronized Set<String> input(String input) {
        if (input.isEmpty() || inputFailed || inputHandler.isnil()) return Set.of();
        try {
            LuaTable table = inputHandler.call(LuaValue.valueOf(input))
                .checktable();
            Set<String> terms = new HashSet<>();
            for (LuaValue key : table.keys()) {
                LuaValue value = table.get(key);
                if (!value.isnil()) terms.add(value.checkjstring());
            }
            return terms;
        } catch (RuntimeException failure) {
            inputFailed = true;
            LOGGER.warn("Disabled input_handler in {} until reload", path, failure);
            return Set.of();
        }
    }
}
