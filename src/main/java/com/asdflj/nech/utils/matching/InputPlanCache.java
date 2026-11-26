package com.asdflj.nech.utils.matching;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class InputPlanCache {

    private final int capacity;
    private final ThreadLocal<Snapshot> plans = new ThreadLocal<>();

    public InputPlanCache(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Input cache capacity must be positive");
        this.capacity = capacity;
    }

    public Set<String> terms(String input, long revision,
        Iterable<? extends Function<String, Set<String>>> middleware) {
        Snapshot snapshot = plans.get();
        if (snapshot == null || snapshot.revision != revision) {
            snapshot = new Snapshot(revision, new LinkedHashMap<>(capacity, 0.75f, true));
            plans.set(snapshot);
        }
        Set<String> cached = snapshot.terms.get(input);
        if (cached != null) return cached;
        Set<String> terms = new HashSet<>();
        for (Function<String, Set<String>> function : middleware) {
            try {
                Set<String> result = function.apply(input);
                if (result != null) terms.addAll(result);
            } catch (RuntimeException ignored) {
                // A failing extension must not prevent native phonetic matching.
            }
        }
        terms.remove(null);
        if (terms.isEmpty()) terms.add(input);
        cached = Set.copyOf(terms);
        snapshot.terms.put(input, cached);
        if (snapshot.terms.size() > capacity) {
            Iterator<String> oldest = snapshot.terms.keySet()
                .iterator();
            oldest.next();
            oldest.remove();
        }
        return cached;
    }

    public void clear() {
        plans.remove();
    }

    private record Snapshot(long revision, Map<String, Set<String>> terms) {}
}
