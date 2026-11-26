package com.asdflj.nech.utils.matching;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

public class RegexSupport {

    private static final int CACHE_SIZE = 32;
    private static final ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(
        () -> new Cache(new LinkedHashMap<>(CACHE_SIZE, 0.75f, true), new LinkedHashMap<>(CACHE_SIZE, 0.75f, true)));

    private RegexSupport() {}

    public static Pattern compile(String expression) {
        return cached(CACHE.get().expressions, expression, Pattern::compile);
    }

    public static String literal(Pattern pattern) {
        return cached(CACHE.get().literals, pattern, key -> Optional.ofNullable(decode(key))).orElse(null);
    }

    private static <K, V> V cached(Map<K, V> cache, K key, Function<K, V> factory) {
        V result = cache.computeIfAbsent(key, factory);
        if (cache.size() > CACHE_SIZE) {
            Iterator<K> oldest = cache.keySet()
                .iterator();
            oldest.next();
            oldest.remove();
        }
        return result;
    }

    public static String stripFlags(String expression) {
        while (expression.startsWith("(?")) {
            int end = expression.indexOf(')');
            if (end < 3) break;
            for (int index = 2; index < end; index++) {
                if ("idmsuxU-".indexOf(expression.charAt(index)) < 0) return expression;
            }
            expression = expression.substring(end + 1);
        }
        return expression;
    }

    private static String decode(Pattern pattern) {
        String expression = pattern.pattern();
        if ((pattern.flags() & Pattern.LITERAL) != 0) return expression;
        if ((pattern.flags() & (Pattern.COMMENTS | Pattern.CANON_EQ)) != 0) return null;
        expression = stripFlags(expression);
        if (expression.startsWith(".*")) expression = expression.substring(2);
        if (expression.endsWith(".*")) expression = expression.substring(0, expression.length() - 2);
        StringBuilder literal = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < expression.length(); index++) {
            char character = expression.charAt(index);
            if (quoted) {
                if (character == '\\' && index + 1 < expression.length() && expression.charAt(index + 1) == 'E') {
                    quoted = false;
                    index++;
                } else literal.append(character);
            } else if (character == '\\' && index + 1 < expression.length()) {
                char escaped = expression.charAt(++index);
                if (escaped == 'Q') quoted = true;
                else if (!Character.isLetterOrDigit(escaped)) literal.append(escaped);
                else return null;
            } else if (character != '\\' && ".^$|?*+()[]{}".indexOf(character) < 0) {
                literal.append(character);
            } else return null;
        }
        return quoted ? null : literal.toString();
    }

    private record Cache(Map<String, Pattern> expressions, Map<Pattern, Optional<String>> literals) {}
}
