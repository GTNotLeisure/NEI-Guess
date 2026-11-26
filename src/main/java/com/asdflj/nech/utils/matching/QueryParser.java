package com.asdflj.nech.utils.matching;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class QueryParser {

    private static final ThreadLocal<Plan> CACHE = new ThreadLocal<>();

    private QueryParser() {}

    public static List<String> terms(String query) {
        return plan(query).terms;
    }

    public static List<String> foldedTerms(String query) {
        return plan(query).foldedTerms;
    }

    private static Plan plan(String query) {
        Plan cached = CACHE.get();
        if (cached == null || !cached.query.equals(query)) {
            List<String> terms = parse(query);
            cached = new Plan(
                query,
                terms,
                terms.stream()
                    .map(term -> term.toLowerCase(Locale.ROOT))
                    .toList());
            CACHE.set(cached);
        }
        return cached;
    }

    private static List<String> parse(String query) {
        List<String> terms = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index <= query.length(); index++) {
            char character = index == query.length() ? ' ' : query.charAt(index);
            if (character == '"') {
                quoted = !quoted;
            } else if (index == query.length() || Character.isWhitespace(character) && !quoted) {
                int start = 0;
                int end = token.length();
                while (start < end && token.charAt(start) == '*') start++;
                while (end > start && token.charAt(end - 1) == '*') end--;
                String term = token.substring(start, end);
                token.setLength(0);
                // Leave complex wildcard expressions to the caller's native parser.
                if (term.indexOf('*') >= 0) return List.of();
                if (!term.isEmpty()) terms.add(term);
            } else token.append(character);
        }
        return List.copyOf(terms);
    }

    private record Plan(String query, List<String> terms, List<String> foldedTerms) {}
}
