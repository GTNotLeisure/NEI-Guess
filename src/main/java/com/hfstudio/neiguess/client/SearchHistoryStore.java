package com.hfstudio.neiguess.client;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hfstudio.neiguess.NEIGuess;
import com.hfstudio.neiguess.config.ModConfig;

import cpw.mods.fml.common.Loader;

public final class SearchHistoryStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static final SearchHistoryStore INSTANCE = new SearchHistoryStore();
    private static final long SAME_SEARCH_WINDOW_MS = 120_000L;
    private static final int MAX_CHAT = 80;
    private static final int MAX_PICK_QUERIES = 120;
    private static final int MAX_PICKS_PER_QUERY = 5;
    private static final int MAX_QUEST_VIEWS = 40;

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Deque<String> chatMentions = new ArrayDeque<>();
    private final LinkedHashSet<String> favorites = new LinkedHashSet<>();
    private final LinkedHashSet<String> banned = new LinkedHashSet<>();
    private final Map<String, Map<String, Integer>> picks = new LinkedHashMap<>();
    private final Map<String, String> itemNames = new LinkedHashMap<>();
    private final List<QuestView> questViews = new ArrayList<>();
    private volatile boolean dirty = false;
    private long dirtyAt = 0L;

    public static SearchHistoryStore get() {
        return INSTANCE;
    }

    public static final class Entry {

        public String word;
        public int count;
        public long lastUsed;
        public boolean fromChat;

        public Entry() {}

        public Entry(String word) {
            this.word = word;
        }
    }

    public static final class QuestView {

        public String id = "";
        public String title = "";
        public List<String> items = new ArrayList<>();
        public long lastViewed;
        public int views;

        public QuestView() {}

        public QuestView(String id, String title, List<String> items) {
            this.id = id;
            this.title = title == null ? "" : title;
            this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
        }
    }

    private static final class Data {

        int version = 1;
        Map<String, Entry> entries = new LinkedHashMap<>();
        List<String> chatMentions = new ArrayList<>();
        List<String> favorites = new ArrayList<>();
        List<String> banned = new ArrayList<>();
        Map<String, Map<String, Integer>> picks = new LinkedHashMap<>();
        Map<String, String> itemNames = new LinkedHashMap<>();
        List<QuestView> questViews = new ArrayList<>();
    }

    public static File dir() {
        return new File(
            Loader.instance()
                .getConfigDir(),
            "neiguess");
    }

    private static File file() {
        return new File(dir(), "history.json");
    }

    public synchronized void load() {
        File f = file();
        if (!f.isFile()) return;
        try {
            Data d = GSON.fromJson(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8), Data.class);
            if (d == null) return;
            entries.clear();
            if (d.entries != null) d.entries.forEach((k, v) -> { if (k != null && v != null) entries.put(k, v); });
            chatMentions.clear();
            if (d.chatMentions != null) chatMentions.addAll(d.chatMentions);
            favorites.clear();
            if (d.favorites != null) favorites.addAll(d.favorites);
            banned.clear();
            if (d.banned != null) banned.addAll(d.banned);
            picks.clear();
            if (d.picks != null) picks.putAll(d.picks);
            itemNames.clear();
            if (d.itemNames != null) itemNames.putAll(d.itemNames);
            questViews.clear();
            if (d.questViews != null) questViews.addAll(d.questViews);
            NEIGuess.LOG.info(
                "Loaded {} search words, {} favorites, {} banned, {} quest views",
                entries.size(),
                favorites.size(),
                banned.size(),
                questViews.size());
        } catch (Exception e) {
            NEIGuess.LOG.warn("Could not read {}: {}", f, e.toString());
        }
    }

    public void saveLater() {
        synchronized (this) {
            dirtyAt = System.currentTimeMillis();
        }
        dirty = true;
    }

    public void flushIfDirty() {
        if (!dirty) return;
        synchronized (this) {
            if (System.currentTimeMillis() - dirtyAt < 5000L) return;
        }
        save();
    }

    public synchronized void save() {
        dirty = false;
        Data d = new Data();
        d.entries = new LinkedHashMap<>(entries);
        d.chatMentions = new ArrayList<>(chatMentions);
        d.favorites = new ArrayList<>(favorites);
        d.banned = new ArrayList<>(banned);
        d.picks = new LinkedHashMap<>(picks);
        d.itemNames = new LinkedHashMap<>(itemNames);
        d.questViews = new ArrayList<>(questViews);
        File f = file();
        try {
            Files.createDirectories(
                f.getParentFile()
                    .toPath());
            File tmp = new File(f.getPath() + ".tmp");
            Files.write(
                tmp.toPath(),
                GSON.toJson(d)
                    .getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            NEIGuess.LOG.warn("Could not save {}: {}", f, e.toString());
        }
    }

    public synchronized void record(String word) {
        String w = clean(word, 64);
        if (w == null) return;
        Entry e = entries.computeIfAbsent(w, Entry::new);
        e.word = w;
        e.count++;
        e.lastUsed = System.currentTimeMillis();
        e.fromChat = false;
        trim();
        saveLater();
    }

    public synchronized void recordSearch(String word) {
        String w = clean(word, 64);
        if (w == null) return;
        Entry e = entries.computeIfAbsent(w, Entry::new);
        long now = System.currentTimeMillis();
        if (e.count == 0 || now - e.lastUsed > SAME_SEARCH_WINDOW_MS) e.count++;
        e.word = w;
        e.lastUsed = now;
        e.fromChat = false;
        trim();
        saveLater();
    }

    public synchronized void recordChatMention(String name) {
        String w = clean(name, 32);
        if (w == null) return;
        chatMentions.remove(w);
        chatMentions.addFirst(w);
        while (chatMentions.size() > MAX_CHAT) chatMentions.removeLast();
        Entry e = entries.get(w);
        if (e == null) {
            e = new Entry(w);
            e.fromChat = true;
            entries.put(w, e);
        }
        e.lastUsed = System.currentTimeMillis();
        trim();
        saveLater();
    }

    public synchronized void recordPick(String query, String itemKey, String itemName) {
        String q = clean(query, 64);
        if (q == null || itemKey == null) return;
        q = q.toLowerCase(Locale.ROOT);
        Map<String, Integer> m = picks.remove(q);
        if (m == null) m = new LinkedHashMap<>();
        picks.put(q, m);
        m.merge(itemKey, 1, Integer::sum);
        if (itemName != null && !itemName.isBlank()) itemNames.put(itemKey, itemName);
        if (m.size() > MAX_PICKS_PER_QUERY) {
            String weakest = null;
            int min = Integer.MAX_VALUE;
            for (Map.Entry<String, Integer> e : m.entrySet()) {
                if (e.getValue() < min) {
                    min = e.getValue();
                    weakest = e.getKey();
                }
            }
            m.remove(weakest);
        }
        Iterator<String> it = picks.keySet()
            .iterator();
        while (picks.size() > MAX_PICK_QUERIES && it.hasNext()) {
            it.next();
            it.remove();
        }
        itemNames.keySet()
            .removeIf(
                k -> picks.values()
                    .stream()
                    .noneMatch(p -> p.containsKey(k)));
        saveLater();
    }

    public synchronized List<String> pickedNames(String query, int limit) {
        List<String> out = new ArrayList<>();
        if (query == null) return out;
        Map<String, Integer> m = picks.get(
            query.trim()
                .toLowerCase(Locale.ROOT));
        if (m == null) return out;
        List<Map.Entry<String, Integer>> list = new ArrayList<>(m.entrySet());
        list.sort(
            Map.Entry.<String, Integer>comparingByValue()
                .reversed());
        for (Map.Entry<String, Integer> e : list) {
            if (out.size() >= limit) break;
            String name = itemNames.get(e.getKey());
            if (name != null && !out.contains(name)) out.add(name);
        }
        return out;
    }

    private void trim() {
        int max = ModConfig.panel.maxHistory;
        if (entries.size() <= max) return;
        List<Map.Entry<String, Entry>> list = new ArrayList<>(entries.entrySet());
        list.sort(Comparator.comparingLong(a -> a.getValue().lastUsed));
        for (int i = 0; entries.size() > max && i < list.size(); i++) {
            String w = list.get(i)
                .getKey();
            if (!favorites.contains(w)) entries.remove(w);
        }
    }

    public synchronized List<String> recent(int limit) {
        List<Entry> list = new ArrayList<>(entries.values());
        list.removeIf(e -> e.fromChat || e.count <= 0);
        list.sort(
            Comparator.comparingLong((Entry e) -> e.lastUsed)
                .reversed());
        List<String> out = new ArrayList<>();
        for (Entry e : list) {
            if (out.size() >= limit) break;
            out.add(e.word);
        }
        return out;
    }

    public synchronized List<String> frequent(int limit) {
        List<Entry> list = new ArrayList<>(entries.values());
        list.removeIf(e -> e.count <= 1);
        list.sort(
            Comparator.comparingInt((Entry e) -> e.count)
                .reversed()
                .thenComparing(
                    Comparator.comparingLong((Entry e) -> e.lastUsed)
                        .reversed()));
        List<String> out = new ArrayList<>();
        for (Entry e : list) {
            if (out.size() >= limit) break;
            out.add(e.word);
        }
        return out;
    }

    public synchronized List<String> chatRecent(int limit) {
        List<String> out = new ArrayList<>();
        for (String s : chatMentions) {
            if (out.size() >= limit) break;
            out.add(s);
        }
        return out;
    }

    public synchronized int countOf(String word) {
        Entry e = entries.get(word);
        return e == null ? 0 : e.count;
    }

    public synchronized List<String> allWords() {
        return new ArrayList<>(entries.keySet());
    }

    public synchronized boolean forget(String word) {
        if (word == null) return false;
        boolean any = entries.remove(word) != null;
        any |= chatMentions.remove(word);
        if (any) saveLater();
        return any;
    }

    public synchronized boolean isFavorite(String word) {
        return word != null && favorites.contains(word);
    }

    public synchronized boolean toggleFavorite(String word) {
        String w = clean(word, 64);
        if (w == null) return false;
        boolean now = !favorites.remove(w);
        if (now) favorites.add(w);
        saveLater();
        return now;
    }

    public synchronized List<String> favorites(int limit) {
        List<String> out = new ArrayList<>();
        for (String s : favorites) {
            if (out.size() >= limit) break;
            out.add(s);
        }
        return out;
    }

    public synchronized boolean isBanned(String word) {
        return word != null && banned.contains(word);
    }

    public synchronized void ban(String word) {
        String w = clean(word, 64);
        if (w == null) return;
        banned.add(w);
        saveLater();
    }

    public synchronized boolean unban(String word) {
        boolean any = banned.remove(word);
        if (any) saveLater();
        return any;
    }

    public synchronized int unbanAll() {
        int n = banned.size();
        banned.clear();
        if (n > 0) saveLater();
        return n;
    }

    public synchronized List<String> allBanned() {
        return new ArrayList<>(banned);
    }

    public synchronized int favoriteCount() {
        return favorites.size();
    }

    public synchronized void recordQuestView(String id, String title, List<String> items) {
        if (id == null) return;
        long now = System.currentTimeMillis();
        for (Iterator<QuestView> it = questViews.iterator(); it.hasNext();) {
            QuestView v = it.next();
            if (!id.equals(v.id)) continue;
            it.remove();
            v.lastViewed = now;
            v.views++;
            if (title != null && !title.isBlank()) v.title = title;
            if (items != null && !items.isEmpty()) v.items = new ArrayList<>(items);
            questViews.add(0, v);
            saveLater();
            return;
        }
        QuestView v = new QuestView(id, title, items);
        v.lastViewed = now;
        v.views = 1;
        questViews.add(0, v);
        while (questViews.size() > MAX_QUEST_VIEWS) questViews.remove(questViews.size() - 1);
        saveLater();
    }

    public synchronized List<QuestView> questViews() {
        return new ArrayList<>(questViews);
    }

    public synchronized File exportCsv() throws IOException {
        File d = dir();
        Files.createDirectories(d.toPath());
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now());
        File out = new File(d, "export-" + stamp + ".csv");
        StringBuilder sb = new StringBuilder("word,count,lastUsedISO,fromChat,favorite,banned,pickedItems\n");
        List<Entry> list = new ArrayList<>(entries.values());
        list.sort(
            Comparator.comparingLong((Entry e) -> e.lastUsed)
                .reversed());
        for (Entry e : list) {
            sb.append(csv(e.word))
                .append(',')
                .append(e.count)
                .append(',')
                .append(Instant.ofEpochMilli(e.lastUsed))
                .append(',')
                .append(e.fromChat)
                .append(',')
                .append(favorites.contains(e.word))
                .append(',')
                .append(banned.contains(e.word))
                .append(',')
                .append(csv(String.join(" | ", pickedNames(e.word, MAX_PICKS_PER_QUERY))))
                .append('\n');
        }
        Files.write(out.toPath(), ("﻿" + sb).getBytes(StandardCharsets.UTF_8));
        return out;
    }

    private static String csv(String s) {
        if (s == null) return "";
        String v = s.replace("\"", "\"\"");
        return v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 ? "\"" + v + "\"" : v;
    }

    private static String clean(String word, int max) {
        if (word == null) return null;
        String w = word.trim();
        return w.isEmpty() || w.length() > max ? null : w;
    }
}
