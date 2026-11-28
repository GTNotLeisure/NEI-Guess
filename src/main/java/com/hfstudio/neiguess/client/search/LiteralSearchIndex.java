package com.hfstudio.neiguess.client.search;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;

public class LiteralSearchIndex {

    private final Node[] nodes;
    private final boolean ignoreCase;

    private LiteralSearchIndex(Node[] nodes, boolean ignoreCase) {
        this.nodes = nodes;
        this.ignoreCase = ignoreCase;
    }

    public static LiteralSearchIndex of(Collection<String> words, boolean ignoreCase) {
        List<BuilderNode> builders = new ArrayList<>();
        builders.add(new BuilderNode());
        for (String word : words) {
            int state = 0;
            for (int index = 0; index < word.length(); index++) {
                char character = ignoreCase ? fold(word.charAt(index)) : word.charAt(index);
                BuilderNode parent = builders.get(state);
                Integer next = parent.edges.get(character);
                if (next == null) {
                    next = builders.size();
                    parent.edges.put(character, next);
                    builders.add(new BuilderNode());
                }
                state = next;
            }
            builders.get(state).terminal = true;
        }
        Queue<Integer> pending = new ArrayDeque<>(builders.get(0).edges.values());
        while (!pending.isEmpty()) {
            BuilderNode parent = builders.get(pending.remove());
            for (Map.Entry<Character, Integer> edge : parent.edges.entrySet()) {
                int failure = parent.failure;
                while (failure != 0 && !builders.get(failure).edges.containsKey(edge.getKey())) {
                    failure = builders.get(failure).failure;
                }
                Integer fallback = builders.get(failure).edges.get(edge.getKey());
                BuilderNode child = builders.get(edge.getValue());
                child.failure = fallback == null ? 0 : fallback;
                child.terminal |= builders.get(child.failure).terminal;
                pending.add(edge.getValue());
            }
        }
        Node[] nodes = new Node[builders.size()];
        for (int index = 0; index < nodes.length; index++) {
            BuilderNode builder = builders.get(index);
            char[] characters = new char[builder.edges.size()];
            int[] targets = new int[characters.length];
            int edgeIndex = 0;
            for (Map.Entry<Character, Integer> edge : builder.edges.entrySet()) {
                characters[edgeIndex] = edge.getKey();
                targets[edgeIndex++] = edge.getValue();
            }
            nodes[index] = new Node(characters, targets, builder.failure, builder.terminal);
        }
        return new LiteralSearchIndex(nodes, ignoreCase);
    }

    public boolean isEmpty() {
        return nodes[0].characters.length == 0 && !nodes[0].terminal;
    }

    public boolean contains(CharSequence text) {
        if (text == null || isEmpty()) return false;
        if (nodes[0].terminal) return true;
        int state = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = ignoreCase ? fold(text.charAt(index)) : text.charAt(index);
            int edge = Arrays.binarySearch(nodes[state].characters, character);
            while (edge < 0 && state != 0) {
                state = nodes[state].failure;
                edge = Arrays.binarySearch(nodes[state].characters, character);
            }
            state = edge < 0 ? 0 : nodes[state].targets[edge];
            if (nodes[state].terminal) return true;
        }
        return false;
    }

    private static char fold(char character) {
        return Character.toLowerCase(Character.toUpperCase(character));
    }

    private record Node(char[] characters, int[] targets, int failure, boolean terminal) {}

    private static class BuilderNode {

        private final Map<Character, Integer> edges = new TreeMap<>();
        private int failure;
        private boolean terminal;
    }
}
