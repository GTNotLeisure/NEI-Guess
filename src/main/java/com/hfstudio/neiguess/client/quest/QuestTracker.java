package com.hfstudio.neiguess.client.quest;

import java.util.Collections;
import java.util.List;

import com.hfstudio.neiguess.NEIGuess;
import com.hfstudio.neiguess.api.Suggestion;

import cpw.mods.fml.common.Loader;

public final class QuestTracker {

    private static final boolean LOADED = Loader.isModLoaded("betterquesting");
    private static boolean failed = false;

    private QuestTracker() {}

    public static boolean available() {
        return LOADED && !failed;
    }

    public static List<Suggestion> suggestions(int limit) {
        if (!available() || limit <= 0) return Collections.emptyList();
        try {
            return BetterQuestingBridge.suggestions(limit);
        } catch (Throwable t) {
            disable(t);
            return Collections.emptyList();
        }
    }

    public static void tick() {
        if (!available()) return;
        try {
            BetterQuestingBridge.watch();
        } catch (Throwable t) {
            disable(t);
        }
    }

    public static void invalidate() {
        if (available()) BetterQuestingBridge.invalidate();
    }

    private static void disable(Throwable t) {
        failed = true;
        NEIGuess.LOG.warn("BetterQuesting integration disabled: {}", t.toString());
    }
}
