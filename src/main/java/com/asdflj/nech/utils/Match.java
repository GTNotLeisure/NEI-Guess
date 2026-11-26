package com.asdflj.nech.utils;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.asdflj.nech.NechConfig;
import com.asdflj.nech.NechConfig.Spell;
import com.asdflj.nech.utils.matching.InputPlanCache;
import com.asdflj.nech.utils.matching.QueryParser;
import com.asdflj.nech.utils.matching.RegexSupport;

import me.towdium.pinin.DictLoader;
import me.towdium.pinin.PinIn;
import me.towdium.pinin.searchers.Searcher.Logic;
import me.towdium.pinin.searchers.TreeSearcher;

public class Match {

    public static final PinIn context = new PinIn(new Loader()).config()
        .accelerate(true)
        .commit();
    public static final List<ITextFunction<String, String>> textMiddleware = new CopyOnWriteArrayList<>();
    public static final List<IInputFunction<String, Set<String>>> inputMiddleware = new CopyOnWriteArrayList<>();
    private static final List<BiPredicate<String, String>> additionalMatchers = new CopyOnWriteArrayList<>();
    private static final Set<TreeSearcher<?>> searchers = Collections.newSetFromMap(new WeakHashMap<>());
    private static final InputPlanCache inputCache = new InputPlanCache(32);
    private static final AtomicLong revision = new AtomicLong();
    private static PhoneticSettings phoneticSettings;
    private static volatile boolean pinyinEnabled = true;

    public static boolean isPinyinEnabled() {
        return pinyinEnabled;
    }

    public static void setPinyinEnabled(boolean enabled) {
        if (pinyinEnabled == enabled) return;
        pinyinEnabled = enabled;
        refreshInput();
    }

    public static void addMatcher(BiPredicate<String, String> matcher) {
        additionalMatchers.add(Objects.requireNonNull(matcher, "matcher"));
        refreshInput();
    }

    public static long revision() {
        return revision.get();
    }

    public static <T> TreeSearcher<T> searcher(Logic logic) {
        synchronized (context) {
            TreeSearcher<T> searcher = new TreeSearcher<>(logic, context);
            searchers.add(searcher);
            return searcher;
        }
    }

    public static int rank(Object owner, String text, String query) {
        return contains(text, query) ? 1 : 0;
    }

    public static boolean contains(String text, CharSequence query) {
        return containsText(text, query) || query != null && additionalMatch(text, query.toString());
    }

    public static boolean containsText(String text, CharSequence query) {
        if (text == null || query == null) return false;
        String input = query.toString();
        if (text.contains(input)) return true;
        String processed = text;
        for (Function<String, String> middleware : textMiddleware) {
            try {
                String value = middleware.apply(processed);
                if (value != null) processed = value;
            } catch (RuntimeException ignored) {}
        }
        for (String term : inputCache.terms(input, revision.get(), inputMiddleware)) {
            if (!containsRaw(processed, term)) return false;
        }
        return true;
    }

    public static boolean containsRaw(String text, String query) {
        if (text == null || query == null) return false;
        if (text.contains(query)) return true;
        if (!pinyinEnabled) return false;
        synchronized (context) {
            return context.contains(text, query);
        }
    }

    public static boolean begins(String text, String query) {
        return beginsRaw(text, query) || additionalMatch(text, query);
    }

    public static boolean beginsRaw(String text, String query) {
        if (text == null || query == null) return false;
        if (text.startsWith(query)) return true;
        if (!pinyinEnabled) return false;
        synchronized (context) {
            return context.begins(text, query);
        }
    }

    public static boolean contains(CharSequence text, CharSequence query) {
        return text != null && contains(text.toString(), query);
    }

    public static boolean containsIgnoreCase(CharSequence text, CharSequence query) {
        return text != null && query != null
            && contains(
                text.toString()
                    .toLowerCase(Locale.ROOT),
                query.toString()
                    .toLowerCase(Locale.ROOT));
    }

    public static boolean containsIgnoreCase(String text, String query) {
        return containsIgnoreCase((CharSequence) text, query);
    }

    public static boolean contains(CharSequence text, CharSequence query, boolean ignoreCase) {
        return ignoreCase ? containsIgnoreCase(text, query) : contains(text, query);
    }

    public static boolean equals(String text, Object query) {
        if (text == null || !(query instanceof String input)) return false;
        if (text.equals(input)) return true;
        if (!pinyinEnabled) return false;
        synchronized (context) {
            return context.matches(text, input);
        }
    }

    public static Matcher matcher(Pattern pattern, CharSequence text) {
        return matcher(pattern, text, pattern.matcher(text));
    }

    public static boolean find(Pattern pattern, CharSequence text) {
        if (pattern.matcher(text)
            .find()) return true;
        String literal = literalPattern(pattern);
        if (literal == null) return false;
        return (pattern.flags() & Pattern.CASE_INSENSITIVE) != 0 ? containsIgnoreCase(text, literal)
            : contains(text, literal);
    }

    public static Matcher matcher(Pattern pattern, CharSequence text, Matcher original) {
        return matcher(pattern, text, original, true);
    }

    public static Matcher matcher(Pattern pattern, CharSequence text, Matcher original, boolean includeAliases) {
        String literal = literalPattern(pattern);
        if (literal == null) return original;
        if (original.regionStart() != 0 || original.regionEnd() != text.length() || original.pattern() != pattern)
            return original;
        boolean found = original.find();
        original.reset();
        if (found) return original;
        String value = text.toString();
        if ((pattern.flags() & Pattern.CASE_INSENSITIVE) != 0) {
            value = value.toLowerCase(Locale.ROOT);
            literal = literal.toLowerCase(Locale.ROOT);
        }
        if (includeAliases ? contains(value, literal) : containsText(value, literal)) {
            // Mixin callers consume find only; preserve native expressions and flags.
            return pattern.matcher(literal);
        }
        return original;
    }

    public static boolean matches(String text, String expression) {
        Pattern pattern = RegexSupport.compile(expression);
        if (pattern.matcher(text)
            .matches()) return true;
        String literal = literalPattern(pattern);
        if (literal == null) return false;
        String body = RegexSupport.stripFlags(expression);
        boolean leading = body.startsWith(".*");
        boolean trailing = body.endsWith(".*");
        if ((pattern.flags() & Pattern.CASE_INSENSITIVE) != 0) {
            text = text.toLowerCase(Locale.ROOT);
            literal = literal.toLowerCase(Locale.ROOT);
        }
        if (leading && trailing) return contains(text, literal);
        if (trailing) return begins(text, literal);
        if (leading) {
            if (!pinyinEnabled) return false;
            synchronized (context) {
                for (int index = 0; index <= text.length(); index++) {
                    if (context.matches(text.substring(index), literal)) return true;
                }
                return false;
            }
        }
        return equals(text, literal) || additionalMatch(text, literal);
    }

    public static String literalPattern(Pattern pattern) {
        return RegexSupport.literal(pattern);
    }

    public static List<String> queryTerms(String query) {
        return QueryParser.terms(query);
    }

    public static boolean matchesTerms(String text, List<String> terms) {
        if (text == null || terms.isEmpty()) return false;
        String foldedText = text.toLowerCase(Locale.ROOT);
        for (String term : terms) if (!contains(foldedText, term.toLowerCase(Locale.ROOT))) return false;
        return true;
    }

    public static boolean queryContains(String text, String query) {
        if (text == null || query == null) return false;
        List<String> terms = QueryParser.foldedTerms(query);
        if (terms.isEmpty()) return false;
        String foldedText = text.toLowerCase(Locale.ROOT);
        for (String term : terms) if (!contains(foldedText, term)) return false;
        return true;
    }

    public static boolean orQuery(boolean original, String text, String query) {
        return original || queryContains(text, query);
    }

    private static boolean additionalMatch(String text, String query) {
        if (text == null || query == null) return false;
        for (BiPredicate<String, String> matcher : additionalMatchers) {
            try {
                if (matcher.test(text, query)) return true;
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    public static void onConfigChange() {
        refresh();
    }

    public static void refreshInput() {
        revision.incrementAndGet();
        inputCache.clear();
    }

    public static void refresh() {
        synchronized (context) {
            PhoneticSettings current = new PhoneticSettings(
                NechConfig.EnableFZh2Z,
                NechConfig.EnableFSh2S,
                NechConfig.EnableFCh2C,
                NechConfig.EnableFAng2An,
                NechConfig.EnableFIng2In,
                NechConfig.EnableFEng2En,
                NechConfig.EnableFU2V,
                NechConfig.KeyboardType);
            if (!current.equals(phoneticSettings)) {
                context.config()
                    .fZh2Z(current.zhToZ)
                    .fSh2S(current.shToS)
                    .fCh2C(current.chToC)
                    .fAng2An(current.angToAn)
                    .fIng2In(current.ingToIn)
                    .fEng2En(current.engToEn)
                    .fU2V(current.uToV)
                    .keyboard(current.keyboard.get())
                    .commit();
                searchers.forEach(TreeSearcher::refresh);
                phoneticSettings = current;
            }
        }
        refreshInput();
    }

    private record PhoneticSettings(boolean zhToZ, boolean shToS, boolean chToC, boolean angToAn, boolean ingToIn,
        boolean engToEn, boolean uToV, Spell keyboard) {}

    public static class Loader extends DictLoader.Default {

        public Loader() {}

        @Override
        public void load(BiConsumer<Character, String[]> feed) {
            super.load(feed);
            feed.accept('鿏', new String[] { "mai4" });
            feed.accept('鿔', new String[] { "ge1" });
            feed.accept('鿭', new String[] { "ni3" });
            feed.accept('鿬', new String[] { "tian2" });
            feed.accept('鿫', new String[] { "ao4" });
            feed.accept('\ue900', new String[] { "lu2" });
            feed.accept('\ue901', new String[] { "du4" });
            feed.accept('\ue902', new String[] { "xi3" });
            feed.accept('\ue903', new String[] { "bo1" });
            feed.accept('\ue904', new String[] { "hei1" });
            feed.accept('\ue906', new String[] { "da2" });
            feed.accept('\ue907', new String[] { "lun2" });
            feed.accept('\ue910', new String[] { "fu1" });
            feed.accept('\ue912', new String[] { "li4" });
        }
    }
}
