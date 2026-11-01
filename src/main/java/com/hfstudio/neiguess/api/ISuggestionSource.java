package com.hfstudio.neiguess.api;

import java.util.Collections;
import java.util.List;

/**
 * A provider of panel rows, registered with {@link NEIGuessAPI#registerSource}. It gets its own page while
 * browsing; its {@link #id()} can be put into the {@code quota.sequence} / {@code quota.extra} config entries.
 * Called on the client thread while the panel is open, so keep it cheap.
 */
public interface ISuggestionSource {

    String id();

    String label();

    default int color() {
        return Suggestion.Source.EXTERNAL.color;
    }

    /** Rows on the mixed page when {@code quota.extra} has no entry. */
    default int defaultQuota() {
        return 1;
    }

    default boolean enabled() {
        return true;
    }

    /** Rows while the search box is empty. */
    List<Suggestion> browse(int limit);

    /** Rows while the player types {@code input}. */
    default List<Suggestion> match(String input, int limit) {
        return Collections.emptyList();
    }
}
