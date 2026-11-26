package com.asdflj.nech.integration.nei;

import java.util.StringJoiner;

public enum Voltage {

    ulv,
    lv,
    mv,
    hv,
    ev,
    iv,
    luv,
    zpm,
    uv,
    uhv,
    uev,
    uiv,
    umv,
    uxv,
    max;

    private static final String PATTERN = buildPattern();

    private static String buildPattern() {
        StringJoiner pattern = new StringJoiner("|", "(", ")");
        for (Voltage voltage : values()) pattern.add(voltage.name());
        return pattern.toString();
    }

    public static String getPatternString() {
        return PATTERN;
    }
}
