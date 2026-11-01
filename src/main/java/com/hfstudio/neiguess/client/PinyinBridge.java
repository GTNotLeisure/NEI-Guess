package com.hfstudio.neiguess.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Locale;

import com.hfstudio.neiguess.NEIGuess;

public final class PinyinBridge {

    private static boolean resolved = false;
    private static Object pinIn;
    private static MethodHandle begins;
    private static MethodHandle contains;

    private PinyinBridge() {}

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            Object instance = null;
            try {
                Class<?> api = Class.forName("com.asdflj.nech.API");
                Object apiInstance = api.getField("INSTANCE")
                    .get(null);
                instance = api.getMethod("getPinIn")
                    .invoke(apiInstance);
            } catch (Throwable ignored) {}
            if (instance == null) {
                instance = Class.forName("me.towdium.pinin.PinIn")
                    .getConstructor()
                    .newInstance();
            }
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodType type = MethodType.methodType(boolean.class, String.class, String.class);
            begins = lookup.findVirtual(instance.getClass(), "begins", type);
            contains = lookup.findVirtual(instance.getClass(), "contains", type);
            pinIn = instance;
            NEIGuess.LOG.info(
                "Pinyin matching enabled ({})",
                instance.getClass()
                    .getName());
        } catch (Throwable t) {
            pinIn = null;
            NEIGuess.LOG.info("Pinyin matching unavailable, plain text only ({})", t.toString());
        }
    }

    public static boolean isReady() {
        resolve();
        return pinIn != null;
    }

    public static boolean begins(String text, String query) {
        if (!isReady() || text == null || query == null || query.isEmpty()) return false;
        try {
            return (boolean) begins.invoke(pinIn, text, query);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean contains(String text, String query) {
        if (!isReady() || text == null || query == null || query.isEmpty()) return false;
        try {
            return (boolean) contains.invoke(pinIn, text, query);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean matches(String text, String query, boolean prefixOnly) {
        if (text == null || query == null || query.isBlank()) return false;
        String t = text.toLowerCase(Locale.ROOT), q = query.toLowerCase(Locale.ROOT);
        if (t.startsWith(q) || !prefixOnly && t.contains(q)) return true;
        if (!isAscii(q) || isAscii(t)) return false;
        return begins(text, q) || !prefixOnly && contains(text, q);
    }

    public static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) >= 128) return false;
        return true;
    }
}
