package com.hfstudio.neiguess.api;

import java.util.Locale;

import net.minecraft.util.StatCollector;

public record Suggestion(String display, String query, Source source, String label, int color, String badge,
    boolean badgeOnHover) {

    public Suggestion {
        display = display == null ? "" : display;
        query = query == null || query.isBlank() ? display : query;
        source = source == null ? Source.PLAIN : source;
        label = label == null ? source.label() : label;
        badge = badge == null ? "" : badge;
    }

    public static Suggestion of(String display, String query, Source source) {
        return of(display, query, source, "");
    }

    public static Suggestion of(String display, String query, Source source, String badge) {
        return new Suggestion(display, query, source, null, source.color, badge, false);
    }

    public static Suggestion hoverBadge(String display, String query, Source source, String badge) {
        return new Suggestion(display, query, source, null, source.color, badge, true);
    }

    public static Suggestion external(ISuggestionSource source, String display, String query, String badge) {
        return new Suggestion(display, query, Source.EXTERNAL, source.label(), source.color(), badge, false);
    }

    public enum Source {

        RECENT(0xFFC9A9B8),
        QUEST(0xFFFFD54F),
        FREQUENT(0xFF4DD0E1),
        CHAT(0xFFCE93D8),
        RANDOM(0xFF81C784),
        MOD(0xFFFFA726),
        ORE(0xFF9ACD32),
        FAVORITE(0xFFFFD700),
        PICK(0xFF80CBC4),
        EXTERNAL(0xFFB0BEC5),
        PLAIN(0xFFE0E0E0);

        public final int color;

        Source(int color) {
            this.color = color;
        }

        public String label() {
            if (this == PLAIN || this == EXTERNAL) return "";
            return StatCollector.translateToLocal("neiguess.source." + name().toLowerCase(Locale.ROOT));
        }

        public boolean isWordLike() {
            return switch (this) {
                case RECENT, FREQUENT, CHAT, RANDOM, PICK, FAVORITE -> true;
                default -> false;
            };
        }
    }
}
