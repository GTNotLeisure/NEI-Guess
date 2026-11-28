package com.hfstudio.neiguess.client.search;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.asdflj.nech.utils.matching.RegexSupport;

public class AliasPatternMatcher {

    private static final int SUPPORTED_FLAGS = Pattern.LITERAL | Pattern.MULTILINE
        | Pattern.CASE_INSENSITIVE
        | Pattern.UNICODE_CASE;
    private final LiteralSearchIndex exact;
    private final LiteralSearchIndex folded;
    private final List<Pattern> expressions;

    private AliasPatternMatcher(LiteralSearchIndex exact, LiteralSearchIndex folded, List<Pattern> expressions) {
        this.exact = exact;
        this.folded = folded;
        this.expressions = expressions;
    }

    public static AliasPatternMatcher of(List<Pattern> patterns) {
        List<String> exact = new ArrayList<>();
        List<String> folded = new ArrayList<>();
        List<Pattern> expressions = new ArrayList<>();
        for (Pattern pattern : patterns) {
            String literal = RegexSupport.literal(pattern);
            int flags = pattern.flags();
            boolean ignoreCase = (flags & Pattern.CASE_INSENSITIVE) != 0;
            boolean unicode = (flags & Pattern.UNICODE_CASE) != 0;
            if (literal == null || (flags & ~SUPPORTED_FLAGS) != 0 || ignoreCase && (!unicode || !isAscii(literal))) {
                expressions.add(pattern);
            } else {
                (ignoreCase ? folded : exact).add(literal);
            }
        }
        return new AliasPatternMatcher(
            LiteralSearchIndex.of(exact, false),
            LiteralSearchIndex.of(folded, true),
            List.copyOf(expressions));
    }

    public boolean isEmpty() {
        return exact.isEmpty() && folded.isEmpty() && expressions.isEmpty();
    }

    public boolean matches(String text) {
        if (exact.contains(text) || folded.contains(text)) return true;
        for (Pattern pattern : expressions) if (pattern.matcher(text)
            .find()) return true;
        return false;
    }

    private static boolean isAscii(String text) {
        for (int index = 0; index < text.length(); index++) if (text.charAt(index) >= 128) return false;
        return true;
    }
}
