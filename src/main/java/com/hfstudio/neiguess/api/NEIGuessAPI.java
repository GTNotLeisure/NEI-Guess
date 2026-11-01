package com.hfstudio.neiguess.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.hfstudio.neiguess.NEIGuess;

public final class NEIGuessAPI {

    private static final List<ISuggestionSource> SOURCES = new CopyOnWriteArrayList<>();
    private static final Map<String, String> MOD_ALIASES = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Map<String, String> ORE_ALIASES = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final List<String> RECOMMEND = new CopyOnWriteArrayList<>();
    private static volatile int revision = 0;

    private NEIGuessAPI() {}

    public static void registerSource(ISuggestionSource source) {
        if (source == null || source.id() == null) return;
        SOURCES.removeIf(
            s -> s.id()
                .equalsIgnoreCase(source.id()));
        SOURCES.add(source);
        revision++;
    }

    public static List<ISuggestionSource> sources() {
        return Collections.unmodifiableList(SOURCES);
    }

    public static ISuggestionSource source(String id) {
        if (id == null) return null;
        for (ISuggestionSource s : SOURCES) {
            if (s.id()
                .equalsIgnoreCase(id.trim())) return s;
        }
        return null;
    }

    public static void addModAlias(String alias, String modid) {
        if (blank(alias) || blank(modid)) return;
        MOD_ALIASES.put(alias.trim(), modid.trim());
        revision++;
    }

    public static void addOreAlias(String alias, String oreQuery) {
        if (blank(alias) || blank(oreQuery)) return;
        String q = oreQuery.trim();
        ORE_ALIASES.put(alias.trim(), q.startsWith("$") ? q : "$" + q);
        revision++;
    }

    public static void addRecommendWord(String word) {
        if (blank(word) || RECOMMEND.contains(word.trim())) return;
        RECOMMEND.add(word.trim());
        revision++;
    }

    public static Map<String, String> modAliases() {
        synchronized (MOD_ALIASES) {
            return new LinkedHashMap<>(MOD_ALIASES);
        }
    }

    public static Map<String, String> oreAliases() {
        synchronized (ORE_ALIASES) {
            return new LinkedHashMap<>(ORE_ALIASES);
        }
    }

    public static List<String> recommendWords() {
        return new ArrayList<>(RECOMMEND);
    }

    public static int revision() {
        return revision;
    }

    public static void handleIMC(String sender, String key, String value) {
        if (key == null || value == null) return;
        String k = key.toLowerCase(Locale.ROOT);
        if (k.equals("addrecommendword")) {
            addRecommendWord(value);
            return;
        }
        int eq = value.indexOf('=');
        if (!k.equals("addmodalias") && !k.equals("addorealias") || eq <= 0) {
            NEIGuess.LOG.warn("Ignoring IMC {}='{}' from {}", key, value, sender);
            return;
        }
        String alias = value.substring(0, eq), target = value.substring(eq + 1);
        if (k.equals("addmodalias")) addModAlias(alias, target);
        else addOreAlias(alias, target);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
