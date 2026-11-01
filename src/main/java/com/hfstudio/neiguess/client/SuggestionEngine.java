package com.hfstudio.neiguess.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;

import com.hfstudio.neiguess.NEIGuess;
import com.hfstudio.neiguess.api.ISuggestionSource;
import com.hfstudio.neiguess.api.NEIGuessAPI;
import com.hfstudio.neiguess.api.Suggestion;
import com.hfstudio.neiguess.api.Suggestion.Source;
import com.hfstudio.neiguess.client.source.BuiltinSources;
import com.hfstudio.neiguess.config.ModConfig;

import codechicken.nei.SearchField;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

public final class SuggestionEngine {

    private static final int FULL_PAGE_LIMIT = 60;
    private static final int MAX_PAGES_PER_SOURCE = 6;

    public record Page(String label, List<Suggestion> items) {}

    private SuggestionEngine() {}

    private static int rows() {
        return ModConfig.panel.rowsPerPage;
    }

    static int quotaFor(ISuggestionSource source) {
        int q = BuiltinSources.builtinQuota(source.id());
        if (q >= 0) return q;
        for (String rule : ModConfig.quota.extra) {
            int eq = rule == null ? -1 : rule.indexOf('=');
            if (eq <= 0 || !rule.substring(0, eq)
                .trim()
                .equalsIgnoreCase(source.id())) continue;
            try {
                return Math.max(
                    0,
                    Integer.parseInt(
                        rule.substring(eq + 1)
                            .trim()));
            } catch (NumberFormatException ignored) {}
        }
        return source.defaultQuota();
    }

    private static List<ISuggestionSource> mixedOrder() {
        List<ISuggestionSource> out = new ArrayList<>();
        for (String id : ModConfig.quota.sequence) {
            ISuggestionSource s = NEIGuessAPI.source(id);
            if (s != null && !out.contains(s)) out.add(s);
        }
        return out;
    }

    private static List<ISuggestionSource> pageOrder() {
        List<ISuggestionSource> out = mixedOrder();
        for (ISuggestionSource s : NEIGuessAPI.sources()) if (!out.contains(s)) out.add(s);
        return out;
    }

    private static List<Suggestion> safeBrowse(ISuggestionSource s, int limit) {
        if (limit <= 0) return new ArrayList<>();
        try {
            return s.enabled() ? s.browse(limit) : new ArrayList<>();
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    private static List<Suggestion> safeMatch(ISuggestionSource s, String input, int limit) {
        if (limit <= 0) return new ArrayList<>();
        try {
            return s.enabled() ? s.match(input, limit) : new ArrayList<>();
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    public static List<Suggestion> buildMixed() {
        List<Suggestion> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ISuggestionSource s : mixedOrder()) {
            for (Suggestion sug : safeBrowse(s, quotaFor(s))) add(out, seen, s.id() + ":" + sug.display(), sug);
        }
        List<Suggestion> all = finish(out);
        return all.size() <= rows() ? all : new ArrayList<>(all.subList(0, rows()));
    }

    public static List<Page> browsePages() {
        List<Page> out = new ArrayList<>();
        if (!ModConfig.panel.enabled) return out;
        List<Suggestion> mixed = buildMixed();
        if (!mixed.isEmpty()) out.add(new Page(NEIGuess.tr("page.mixed"), mixed));
        addChunks(out, Source.FAVORITE.label(), favoriteRows(FULL_PAGE_LIMIT));
        for (ISuggestionSource s : pageOrder()) {
            addChunks(out, s.label(), clean(safeBrowse(s, FULL_PAGE_LIMIT)));
        }
        return out;
    }

    private static void addChunks(List<Page> out, String label, List<Suggestion> all) {
        int rows = rows();
        if (all == null || all.isEmpty()) return;
        int total = Math.min(MAX_PAGES_PER_SOURCE, (all.size() + rows - 1) / rows);
        for (int i = 0; i < total; i++) {
            List<Suggestion> chunk = new ArrayList<>(all.subList(i * rows, Math.min((i + 1) * rows, all.size())));
            out.add(new Page(total > 1 ? label + " " + (i + 1) + "/" + total : label, chunk));
        }
    }

    public static List<Suggestion> filter(String input, int limit) {
        if (!ModConfig.panel.enabled) return new ArrayList<>();
        if (input == null || input.isBlank()) return buildMixed();
        String trimmed = input.trim();
        String key = trimmed.toLowerCase(Locale.ROOT);

        if (ModConfig.panel.syntaxHints && !key.isEmpty()) {
            char first = key.charAt(0);
            if (first == realPrefix(NeiBridge.MOD_PREFIX)) return finish(modHints(key.substring(1), limit));
            if (first == realPrefix(NeiBridge.ORE_PREFIX)) return finish(oreHints(key.substring(1), limit));
        }

        boolean prefixOnly = ModConfig.panel.prefixOnly;
        int itemQuota = Math.max(3, limit - 8);
        List<Suggestion> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String n : SearchHistoryStore.get()
            .pickedNames(trimmed, ModConfig.quota.pick)) {
            add(out, seen, "pick:" + n, Suggestion.of(n, NeiBridge.escape(n), Source.PICK, NEIGuess.tr("badge.pick")));
        }
        ISuggestionSource recent = NEIGuessAPI.source(BuiltinSources.RECENT);
        if (recent != null) {
            for (Suggestion s : safeMatch(recent, trimmed, 4)) add(out, seen, "hist:" + s.display(), s);
        }

        List<String> matchedItems = ItemIndexes.suggestNames(trimmed, itemQuota, prefixOnly);
        for (String n : matchedItems) {
            add(out, seen, "item:" + n, Suggestion.of(n, NeiBridge.escape(n), Source.PLAIN, NEIGuess.tr("badge.item")));
        }

        addModMatches(out, seen, key, 3);
        addOreAliasMatches(out, seen, key, 2);
        addDerivedOres(out, seen, key, matchedItems, 2);

        for (ISuggestionSource s : NEIGuessAPI.sources()) {
            if (s == recent) continue;
            int quota = s.id()
                .equals(BuiltinSources.QUEST) ? 2 : Math.max(1, quotaFor(s));
            for (Suggestion sug : safeMatch(s, trimmed, quota)) add(out, seen, s.id() + ":" + sug.display(), sug);
        }

        if (ModConfig.panel.correction && matchedItems.isEmpty()) {
            String fix = correction(trimmed);
            if (fix != null) {
                out.add(0, Suggestion.of(fix, Lexicon.toQuery(fix), Source.PLAIN, NEIGuess.tr("badge.didYouMean")));
            }
        }
        if (out.isEmpty()) return buildMixed();
        return finish(out);
    }

    private static char realPrefix(char prefix) {
        try {
            return SearchField.searchParser.getRedefinedPrefix(prefix);
        } catch (Throwable t) {
            return prefix;
        }
    }

    private static boolean aliasMatch(String alias, String input, boolean prefixOnly) {
        if (alias == null || input == null || alias.isEmpty() || input.isEmpty()) return false;
        return PinyinBridge.matches(alias, input, prefixOnly);
    }

    private static void addModMatches(List<Suggestion> out, Set<String> seen, String key, int limit) {
        Map<String, Lexicon.Alias> mods = Lexicon.mods();
        int added = 0;
        for (boolean prefix : new boolean[] { true, false }) {
            if (!prefix && added > 0) break;
            for (Map.Entry<String, Lexicon.Alias> e : mods.entrySet()) {
                if (added >= limit) return;
                Lexicon.Alias a = e.getValue();
                if (!aliasMatch(e.getKey(), key, prefix) || !seen.add("mod:" + a.target())) continue;
                out.add(Suggestion.of(a.display(), Lexicon.modQuery(a.target()), Source.MOD, "@" + a.target()));
                added++;
            }
        }
    }

    private static void addOreAliasMatches(List<Suggestion> out, Set<String> seen, String key, int limit) {
        int added = 0;
        for (Map.Entry<String, Lexicon.Alias> e : Lexicon.ores()
            .entrySet()) {
            if (added >= limit) return;
            String alias = e.getKey();
            boolean hit = aliasMatch(alias, key, true) || key.length() >= 2 && aliasMatch(alias, key, false);
            Lexicon.Alias a = e.getValue();
            if (hit && seen.add("ore:" + a.target())) {
                out.add(Suggestion.of(a.display(), a.target(), Source.ORE, a.target()));
                added++;
            }
        }
    }

    private static void addDerivedOres(List<Suggestion> out, Set<String> seen, String key, List<String> items,
        int limit) {
        Map<String, Integer> hits = new LinkedHashMap<>();
        for (String n : items) {
            ItemStack stack = ItemIndexes.stackOfName(n);
            for (String ore : Lexicon.oresOf(stack)) hits.merge(ore, 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(hits.entrySet());
        ranked.sort(
            Map.Entry.<String, Integer>comparingByValue()
                .reversed());
        int added = 0;
        for (Map.Entry<String, Integer> e : ranked) {
            if (added >= limit) return;
            if (addOre(out, seen, e.getKey())) added++;
        }
        if (key.length() < 3 || !PinyinBridge.isAscii(key)) return;
        for (String ore : Lexicon.oreNames()) {
            if (added >= limit) return;
            if (ore.toLowerCase(Locale.ROOT)
                .contains(key) && addOre(out, seen, ore)) added++;
        }
    }

    private static boolean addOre(List<Suggestion> out, Set<String> seen, String ore) {
        if (!seen.add("ore:$" + ore.toLowerCase(Locale.ROOT))) return false;
        out.add(
            Suggestion
                .of(Lexicon.oreDisplayName(ore), NeiBridge.prefixed(NeiBridge.ORE_PREFIX, ore), Source.ORE, "$" + ore));
        return true;
    }

    private static List<Suggestion> modHints(String rest, int limit) {
        List<Suggestion> out = new ArrayList<>();
        String r = rest.trim();
        Set<String> seenMods = new HashSet<>();
        for (Map.Entry<String, Lexicon.Alias> e : Lexicon.mods()
            .entrySet()) {
            if (out.size() >= limit) break;
            Lexicon.Alias a = e.getValue();
            if (!r.isEmpty() && !aliasMatch(e.getKey(), r, true)
                && !(r.length() >= 2 && aliasMatch(e.getKey(), r, false))) continue;
            if (!seenMods.add(a.target())) continue;
            String display = r.isEmpty() ? Lexicon.modDisplay(a.target()) : a.display();
            out.add(Suggestion.of(display, Lexicon.modQuery(a.target()), Source.MOD, "@" + a.target()));
        }
        if (out.isEmpty() && !r.isEmpty()) {
            out.add(
                Suggestion
                    .of("@" + r, NeiBridge.prefixed(NeiBridge.MOD_PREFIX, r), Source.MOD, NEIGuess.tr("badge.direct")));
        }
        return out;
    }

    private static List<Suggestion> oreHints(String rest, int limit) {
        List<Suggestion> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String r = rest.trim()
            .toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Lexicon.Alias> e : Lexicon.ores()
            .entrySet()) {
            if (out.size() >= limit) break;
            Lexicon.Alias a = e.getValue();
            boolean hit = r.isEmpty() || aliasMatch(e.getKey(), r, false)
                || a.target()
                    .toLowerCase(Locale.ROOT)
                    .contains(r);
            if (hit && seen.add(
                "ore:" + a.target()
                    .toLowerCase(Locale.ROOT))) {
                out.add(Suggestion.of(a.display(), a.target(), Source.ORE, a.target()));
            }
        }
        if (!r.isEmpty()) {
            for (String ore : Lexicon.oreNames()) {
                if (out.size() >= limit) break;
                if (ore.toLowerCase(Locale.ROOT)
                    .contains(r)) addOre(out, seen, ore);
            }
        }
        if (out.isEmpty()) {
            out.add(
                Suggestion.of(
                    "$" + rest.trim(),
                    NeiBridge.prefixed(NeiBridge.ORE_PREFIX, rest.trim()),
                    Source.ORE,
                    NEIGuess.tr("badge.direct")));
        }
        return out;
    }

    private static String correction(String input) {
        if (input.length() < 2 || input.length() > 16) return null;
        if (Lexicon.modAliasOf(input) != null || Lexicon.oreQueryOf(input) != null) return null;
        String key = input.toLowerCase(Locale.ROOT);
        String best = ItemIndexes.nearestName(input);
        int bestD = best == null ? 3 : ItemIndexes.editDistance(key, best.toLowerCase(Locale.ROOT), 2);
        for (String w : SearchHistoryStore.get()
            .allWords()) {
            int d = ItemIndexes.editDistance(key, w.toLowerCase(Locale.ROOT), 2);
            if (d < bestD) {
                bestD = d;
                best = w;
            }
        }
        return best == null || bestD == 0 || bestD > 2 ? null : best;
    }

    private static void add(List<Suggestion> out, Set<String> seen, String key, Suggestion s) {
        if (s != null && seen.add(key)) out.add(s);
    }

    private static List<Suggestion> clean(List<Suggestion> in) {
        List<Suggestion> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        SearchHistoryStore store = SearchHistoryStore.get();
        for (Suggestion s : in) {
            if (s == null || store.isBanned(s.display())) continue;
            if (s.source()
                .isWordLike() && !seen.add(s.display())) continue;
            out.add(s);
        }
        return out;
    }

    private static List<Suggestion> finish(List<Suggestion> in) {
        List<Suggestion> fixed = clean(in);
        List<Suggestion> fav = favoriteRows(ModConfig.quota.favorite);
        if (fav.isEmpty()) return fixed;
        List<Suggestion> all = new ArrayList<>(fav);
        Set<String> favWords = new HashSet<>();
        for (Suggestion f : fav) favWords.add(
            f.display()
                .toLowerCase(Locale.ROOT));
        for (Suggestion s : fixed) {
            if (!favWords.contains(
                s.display()
                    .toLowerCase(Locale.ROOT)))
                all.add(s);
        }
        return all;
    }

    private static List<Suggestion> favoriteRows(int limit) {
        List<Suggestion> out = new ArrayList<>();
        for (String w : SearchHistoryStore.get()
            .favorites(limit)) {
            out.add(Suggestion.of(Lexicon.displayOf(w), Lexicon.toQuery(w), Source.FAVORITE, "★"));
        }
        return out;
    }

    public static String fixQuery(String query) {
        if (query == null || query.isBlank()) return query;
        String q = query.trim();
        if ("@$#&-!|\"".indexOf(q.charAt(0)) >= 0 || NeiBridge.hasResult(q)) return q;
        String mod = Lexicon.modQueryOf(q);
        if (mod != null && NeiBridge.hasResult(mod)) return mod;
        String ore = Lexicon.oreQueryOf(q);
        if (ore != null && NeiBridge.hasResult(ore)) return ore;
        String norm = normalize(q);
        for (ModContainer mc : Loader.instance()
            .getActiveModList()) {
            if (norm.equals(normalize(mc.getModId()))) {
                String cand = Lexicon.modQuery(mc.getModId());
                if (NeiBridge.hasResult(cand)) return cand;
            }
        }
        List<String> names = ItemIndexes.suggestNames(q, 1, false);
        if (!names.isEmpty()) {
            String cand = NeiBridge.escape(names.get(0));
            if (NeiBridge.hasResult(cand)) return cand;
        }
        return q;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT)
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "");
    }

    public static List<Source> sourcesOf(List<Suggestion> rows) {
        LinkedHashSet<Source> set = new LinkedHashSet<>();
        for (Source s : Source.values()) for (Suggestion r : rows) if (r.source() == s) set.add(s);
        return new ArrayList<>(set);
    }
}
