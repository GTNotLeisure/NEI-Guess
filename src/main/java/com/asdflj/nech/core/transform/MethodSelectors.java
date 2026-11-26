package com.asdflj.nech.core.transform;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MethodSelectors {

    private static final Logger LOGGER = LogManager.getLogger("NECH Search");
    private final Map<String, Set<String>> methods;

    private MethodSelectors(Map<String, Set<String>> methods) {
        this.methods = Map.copyOf(methods);
    }

    public static MethodSelectors of(String[]... sources) {
        Map<String, Set<String>> methods = new HashMap<>();
        for (String[] source : sources) {
            if (source == null) continue;
            for (String selector : source) {
                if (selector == null) continue;
                selector = selector.trim();
                int separator = selector.indexOf(':');
                if (separator <= 0 || separator == selector.length() - 1) {
                    LOGGER.warn("Invalid search method selector: {}", selector);
                    continue;
                }
                String owner = selector.substring(0, separator)
                    .trim()
                    .replace('/', '.');
                String method = selector.substring(separator + 1)
                    .trim();
                if (owner.isEmpty() || method.isEmpty()) {
                    LOGGER.warn("Invalid search method selector: {}", selector);
                    continue;
                }
                methods.computeIfAbsent(owner, ignored -> new HashSet<>())
                    .add(method);
            }
        }
        methods.replaceAll((owner, selectors) -> Set.copyOf(selectors));
        return new MethodSelectors(methods);
    }

    public boolean containsClass(String name) {
        return name != null && methods.containsKey(name.replace('/', '.'));
    }

    public Set<String> methodsFor(String... names) {
        Set<String> result = new HashSet<>();
        for (String name : names) {
            if (name != null) result.addAll(methods.getOrDefault(name.replace('/', '.'), Set.of()));
        }
        return result;
    }

    public static boolean matches(Set<String> selectors, String name, String descriptor) {
        return selectors.contains("*") || selectors.contains(name) || selectors.contains(name + descriptor);
    }
}
