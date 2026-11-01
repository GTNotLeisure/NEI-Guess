package com.hfstudio.neiguess.client.source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

import com.hfstudio.neiguess.api.ISuggestionSource;
import com.hfstudio.neiguess.api.NEIGuessAPI;
import com.hfstudio.neiguess.api.Suggestion;
import com.hfstudio.neiguess.api.Suggestion.Source;
import com.hfstudio.neiguess.client.Lexicon;
import com.hfstudio.neiguess.client.NeiBridge;
import com.hfstudio.neiguess.client.PinyinBridge;
import com.hfstudio.neiguess.client.SearchHistoryStore;
import com.hfstudio.neiguess.client.quest.QuestTracker;
import com.hfstudio.neiguess.config.ModConfig;

public final class BuiltinSources {

    public static final String RECENT = "recent";
    public static final String QUEST = "quest";
    public static final String FREQUENT = "frequent";
    public static final String CHAT = "chat";
    public static final String RANDOM = "random";

    private BuiltinSources() {}

    public static void registerAll() {
        NEIGuessAPI.registerSource(new Recent());
        NEIGuessAPI.registerSource(new Quest());
        NEIGuessAPI.registerSource(new Frequent());
        NEIGuessAPI.registerSource(new Chat());
        NEIGuessAPI.registerSource(new Recommend());
    }

    public static int builtinQuota(String id) {
        return switch (id) {
            case RECENT -> ModConfig.quota.recent;
            case QUEST -> ModConfig.quota.quest;
            case FREQUENT -> ModConfig.quota.frequent;
            case CHAT -> ModConfig.quota.chat;
            case RANDOM -> ModConfig.quota.random;
            default -> -1;
        };
    }

    private static List<String> recentWords(int limit) {
        LinkedHashSet<String> set = new LinkedHashSet<>(NeiBridge.neiHistory(limit));
        set.addAll(
            SearchHistoryStore.get()
                .recent(limit));
        List<String> out = new ArrayList<>(set);
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    private static Suggestion word(String w, Source source, String badge) {
        return Suggestion.of(Lexicon.displayOf(w), Lexicon.toQuery(w), source, badge);
    }

    private abstract static class Builtin implements ISuggestionSource {

        private final String id;
        private final Source kind;

        Builtin(String id, Source kind) {
            this.id = id;
            this.kind = kind;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String label() {
            return kind.label();
        }

        @Override
        public int color() {
            return kind.color;
        }
    }

    private static final class Recent extends Builtin {

        Recent() {
            super(RECENT, Source.RECENT);
        }

        @Override
        public boolean enabled() {
            return ModConfig.sources.recent;
        }

        @Override
        public List<Suggestion> browse(int limit) {
            List<Suggestion> out = new ArrayList<>();
            for (String w : recentWords(limit)) out.add(word(w, Source.RECENT, ""));
            return out;
        }

        @Override
        public List<Suggestion> match(String input, int limit) {
            List<Suggestion> out = new ArrayList<>();
            String typed = input.trim();
            for (String w : recentWords(40)) {
                if (out.size() >= limit) break;
                if (!w.equalsIgnoreCase(typed) && PinyinBridge.matches(w, typed, ModConfig.panel.prefixOnly))
                    out.add(word(w, Source.RECENT, ""));
            }
            return out;
        }
    }

    private static final class Quest extends Builtin {

        Quest() {
            super(QUEST, Source.QUEST);
        }

        @Override
        public boolean enabled() {
            return ModConfig.sources.quest && QuestTracker.available();
        }

        @Override
        public List<Suggestion> browse(int limit) {
            return QuestTracker.suggestions(limit);
        }

        @Override
        public List<Suggestion> match(String input, int limit) {
            List<Suggestion> out = new ArrayList<>();
            for (Suggestion s : QuestTracker.suggestions(60)) {
                if (out.size() >= limit) break;
                if (PinyinBridge.matches(s.display(), input, ModConfig.panel.prefixOnly)) out.add(s);
            }
            return out;
        }
    }

    private static final class Frequent extends Builtin {

        Frequent() {
            super(FREQUENT, Source.FREQUENT);
        }

        @Override
        public boolean enabled() {
            return ModConfig.sources.frequent;
        }

        @Override
        public List<Suggestion> browse(int limit) {
            SearchHistoryStore store = SearchHistoryStore.get();
            List<Suggestion> out = new ArrayList<>();
            for (String w : store.frequent(limit)) out.add(word(w, Source.FREQUENT, "x" + store.countOf(w)));
            return out;
        }
    }

    private static final class Chat extends Builtin {

        Chat() {
            super(CHAT, Source.CHAT);
        }

        @Override
        public boolean enabled() {
            return ModConfig.sources.chat;
        }

        @Override
        public List<Suggestion> browse(int limit) {
            List<Suggestion> out = new ArrayList<>();
            for (String w : SearchHistoryStore.get()
                .chatRecent(limit)) out.add(Suggestion.of(w, NeiBridge.escape(w), Source.CHAT));
            return out;
        }
    }

    private static final class Recommend extends Builtin {

        private final Random random = new Random();
        private List<String> shuffled = new ArrayList<>();
        private long shuffledAt = 0L;

        Recommend() {
            super(RANDOM, Source.RANDOM);
        }

        @Override
        public boolean enabled() {
            return ModConfig.sources.random;
        }

        @Override
        public List<Suggestion> browse(int limit) {
            List<String> words = Lexicon.recommendWords();
            List<Suggestion> out = new ArrayList<>();
            if (limit >= words.size()) {
                for (String w : words) out.add(word(w, Source.RANDOM, ""));
                return out;
            }
            long now = System.currentTimeMillis();
            if (now - shuffledAt > 60_000L || shuffled.size() != words.size()) {
                shuffledAt = now;
                shuffled = new ArrayList<>(words);
                Collections.shuffle(shuffled, random);
            }
            for (String w : shuffled.subList(0, limit)) out.add(word(w, Source.RANDOM, ""));
            return out;
        }
    }
}
