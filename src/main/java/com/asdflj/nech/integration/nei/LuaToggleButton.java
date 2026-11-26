package com.asdflj.nech.integration.nei;

import static com.asdflj.nech.integration.nei.ButtonConstants.LUA_SCRIPTS;

import java.util.Objects;

import net.minecraft.client.resources.I18n;

public class LuaToggleButton extends BaseToggleButton {

    private final String displayName;
    private final Runnable stateChanged;

    public LuaToggleButton(String name) {
        this(name, () -> {});
    }

    public LuaToggleButton(String name, Runnable stateChanged) {
        super(LUA_SCRIPTS + "." + name, true);
        this.displayName = name;
        this.stateChanged = Objects.requireNonNull(stateChanged, "stateChanged");
    }

    @Override
    protected void onStateChanged() {
        stateChanged.run();
    }

    @Override
    public String getButtonText() {
        return I18n.format(state() ? "nech.state.enabled" : "nech.state.disabled");
    }

    @Override
    public String translateN(String key, Object... args) {
        return key.equals(tooltip) ? I18n.format("nei.options.nech.lua_scripts.tip") : displayName;
    }
}
