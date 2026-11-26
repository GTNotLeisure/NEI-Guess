package com.hfstudio.neiguess.client.input;

import java.util.Locale;

import org.lwjgl.input.Keyboard;

public class OverlayKeyBindings {

    private OverlayKeyBindings() {}

    public static int pagingOffset(int key, String[] bindings) {
        if (key == Keyboard.KEY_NONE || bindings == null) return 0;
        for (String binding : bindings) {
            if (binding == null) continue;
            String name = binding.trim()
                .toUpperCase(Locale.ROOT);
            int configuredKey = resolve(name);
            if (configuredKey == Keyboard.KEY_NONE || configuredKey != key) continue;
            if (name.contains("UP") || name.contains("LEFT") || name.equals("PRIOR")) return -1;
            if (name.contains("DOWN") || name.contains("RIGHT") || name.equals("NEXT")) return 1;
        }
        return 0;
    }

    private static int resolve(String name) {
        return switch (name) {
            case "LEFT" -> Keyboard.KEY_LEFT;
            case "RIGHT" -> Keyboard.KEY_RIGHT;
            case "UP" -> Keyboard.KEY_UP;
            case "DOWN" -> Keyboard.KEY_DOWN;
            case "PRIOR", "PAGEUP", "PAGE_UP", "PAGE UP" -> Keyboard.KEY_PRIOR;
            case "NEXT", "PAGEDOWN", "PAGE_DOWN", "PAGE DOWN" -> Keyboard.KEY_NEXT;
            default -> Keyboard.getKeyIndex(name);
        };
    }
}
