package com.hfstudio.neiguess.client;

import java.util.Locale;

import com.asdflj.nech.API;

public final class PinyinBridge {

    private PinyinBridge() {}

    public static boolean begins(String text, String query) {
        return text != null && query != null
            && !query.isEmpty()
            && API.INSTANCE.getPinIn()
                .begins(text, query);
    }

    public static boolean contains(String text, String query) {
        return text != null && query != null
            && !query.isEmpty()
            && API.INSTANCE.getPinIn()
                .contains(text, query);
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
