package com.hfstudio.neiguess.client.quest;

import java.util.Collections;
import java.util.List;

import com.hfstudio.neiguess.NEIGuess;
import com.hfstudio.neiguess.api.Suggestion;

import cpw.mods.fml.common.Loader;

public final class QuestTracker {

    private static final boolean LOADED = Loader.isModLoaded("betterquesting");
    private static boolean dynamicFailed = false;
    private static boolean watchFailed = false;

    private QuestTracker() {}

    public static boolean available() {
        return LOADED;
    }

    public static List<Suggestion> suggestions(int limit) {
        if (!available() || limit <= 0) return Collections.emptyList();
        if (dynamicFailed) return BetterQuestingBridge.viewedSuggestions(limit);
        try {
            return BetterQuestingBridge.suggestions(limit);
        } catch (Throwable t) {
            dynamicFailed = true;
            disable("recommendation rebuild", t);
            return BetterQuestingBridge.viewedSuggestions(limit);
        }
    }

    public static void tick() {
        if (!available() || watchFailed) return;
        try {
            BetterQuestingBridge.watch();
        } catch (Throwable t) {
            watchFailed = true;
            disable("quest view tracking", t);
        }
    }

    public static void invalidate() {
        if (!available()) return;
        dynamicFailed = false;
        watchFailed = false;
        BetterQuestingBridge.invalidate();
    }

    private static void disable(String part, Throwable t) {
        NEIGuess.LOG.warn("BetterQuesting {} disabled: {}", part, t.toString());
    }
}
