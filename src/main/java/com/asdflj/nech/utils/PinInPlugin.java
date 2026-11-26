package com.asdflj.nech.utils;

import static com.asdflj.nech.integration.nei.NEIConfig.getConfigValue;
import static com.asdflj.nech.utils.Match.inputMiddleware;
import static com.asdflj.nech.utils.Match.textMiddleware;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.asdflj.nech.integration.nei.ButtonConstants;
import com.asdflj.nech.integration.nei.Voltage;

public class PinInPlugin implements Runnable {

    public static final String voltagePatternString = Voltage.getPatternString();
    private static final Pattern VOLTAGE_PREFIX = Pattern.compile("^" + voltagePatternString + "\\w+");
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    @Override
    public void run() {
        if (!INSTALLED.compareAndSet(false, true)) return;
        LuaPlugin.loadLuaScript();
        textMiddleware.add(text -> {
            if (getConfigValue(ButtonConstants.COMMA)) {
                return text.replace(",", "");
            }
            return text;
        });
        textMiddleware.add(text -> {
            if (getConfigValue(ButtonConstants.PARENTHESES)) {
                return text.replace("(", "")
                    .replace(")", "");
            }
            return text;
        });
        textMiddleware.add(new ITextFunction<>() {

            @Override
            public String apply(String text) {
                if (getConfigValue(ButtonConstants.LUA_SCRIPT)) {
                    for (LuaPlugin.LuaObject script : LuaPlugin.list) {
                        if (script.getState()) {
                            text = script.textHandler(text);
                        }
                    }
                }
                return text;
            }

            @Override
            public int index() {
                return 90;
            }
        });

        inputMiddleware.add(input -> {
            if (!getConfigValue(ButtonConstants.VOLTAGE)) return null;
            Matcher prefix = VOLTAGE_PREFIX.matcher(input);
            if (!prefix.matches()) return null;
            String voltage = prefix.group(1);
            String remainder = input.substring(prefix.end(1));
            if (!voltage.equals(remainder)) return Set.of(voltage, remainder);
            return null;
        });

        inputMiddleware.add(new IInputFunction<>() {

            @Override
            public Set<String> apply(String input) {
                if (getConfigValue(ButtonConstants.LUA_SCRIPT)) {
                    Set<String> terms = new HashSet<>();
                    for (LuaPlugin.LuaObject script : LuaPlugin.list) {
                        if (script.getState()) {
                            terms.addAll(script.inputHandler(input));
                        }
                    }
                    return terms;
                }
                return null;
            }

            @Override
            public int index() {
                return 90;
            }
        });
        textMiddleware.sort(Comparator.comparing(IIndexFunction::index));
        inputMiddleware.sort(Comparator.comparing(IIndexFunction::index));
        Match.refreshInput();
    }
}
