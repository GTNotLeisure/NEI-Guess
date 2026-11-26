package com.asdflj.nech.integration.nei;

import com.asdflj.nech.NeverEnoughCharacters;
import com.asdflj.nech.utils.Match;
import com.hfstudio.neiguess.Tags;

import codechicken.lib.config.ConfigTagParent;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;

public class NEIConfig implements IConfigureNEI {

    private static final ConfigTagParent TAG = NEIClientConfig.global.config;
    private static volatile Settings settings = new Settings(true, true, true, true);
    private static boolean registered;

    public NEIConfig() {}

    @Override
    public void loadConfig() {
        if (registered) return;
        API.addOption(new BaseToggleButton(ButtonConstants.PINYIN));
        API.addOption(new BaseToggleButton(ButtonConstants.COMMA));
        API.addOption(new BaseToggleButton(ButtonConstants.PARENTHESES));
        API.addOption(new BaseToggleButton(ButtonConstants.VOLTAGE));
        API.addOption(new BaseToggleButton(ButtonConstants.LUA_SCRIPT));
        registered = true;
        reloadSettings();
    }

    public static boolean getConfigValue(String identifier) {
        Settings current = settings;
        return switch (identifier) {
            case ButtonConstants.PINYIN -> current.pinyin;
            case ButtonConstants.COMMA -> current.ignoreComma;
            case ButtonConstants.PARENTHESES -> current.ignoreParentheses;
            case ButtonConstants.VOLTAGE -> current.splitVoltage;
            case ButtonConstants.LUA_SCRIPT -> current.luaScript;
            default -> TAG.getTag(identifier)
                .getBooleanValue(true);
        };
    }

    public static boolean reloadSettings() {
        if (!registered) return false;
        Settings current = new Settings(
            readValue(ButtonConstants.COMMA),
            readValue(ButtonConstants.PARENTHESES),
            readValue(ButtonConstants.VOLTAGE),
            readValue(ButtonConstants.LUA_SCRIPT),
            readValue(ButtonConstants.PINYIN));
        if (settings.equals(current)) return false;
        settings = current;
        Match.setPinyinEnabled(current.pinyin);
        return true;
    }

    private static boolean readValue(String identifier) {
        String value = NEIClientConfig.getSetting(identifier)
            .getValue("true");
        return !"false".equalsIgnoreCase(value) && !"no".equalsIgnoreCase(value);
    }

    @Override
    public String getName() {
        return NeverEnoughCharacters.MOD_NAME;
    }

    @Override
    public String getVersion() {
        return Tags.VERSION;
    }

    public record Settings(boolean ignoreComma, boolean ignoreParentheses, boolean splitVoltage, boolean luaScript,
        boolean pinyin) {

        public Settings(boolean ignoreComma, boolean ignoreParentheses, boolean splitVoltage, boolean luaScript) {
            this(ignoreComma, ignoreParentheses, splitVoltage, luaScript, true);
        }
    }
}
