package com.asdflj.nech.integration.nei;

import static com.asdflj.nech.utils.Match.refreshInput;

import codechicken.lib.config.ConfigTagParent;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.config.OptionToggleButton;

public class BaseToggleButton extends OptionToggleButton {

    private static final ConfigTagParent TAG = NEIClientConfig.global.config;

    public BaseToggleButton(String name) {
        this(name, true);
    }

    public BaseToggleButton(String name, boolean defaultValue) {
        super(name, true);
        TAG.getTag(name)
            .getBooleanValue(defaultValue);
    }

    @Override
    public boolean onClick(int button) {
        boolean handled = super.onClick(button);
        if (handled) {
            NEIConfig.reloadSettings();
            onStateChanged();
            refreshInput();
        }
        return handled;
    }

    protected void onStateChanged() {}
}
