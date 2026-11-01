package com.hfstudio.neiguess.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.oredict.OreDictionary;

import com.hfstudio.neiguess.NEIGuess;
import com.hfstudio.neiguess.config.ModConfig;

import codechicken.nei.ItemList;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.GameRegistry;

public final class ItemIndexes {

    private static final int MIN_NAME = 2;
    private static final int MAX_NAME = 32;
    private static final int MAX_LINK_HITS = 8;

    private record NameIndex(String[] names, String[] lower, boolean[] ascii, ItemStack[] stacks, int[] lengthStart,
        Map<String, Integer> byName) {

        static final NameIndex EMPTY = new NameIndex(
            new String[0],
            new String[0],
            new boolean[0],
            new ItemStack[0],
            new int[MAX_NAME + 2],
            new HashMap<>());
    }

    public enum LinkKind {
        ITEM,
        ORE,
        MOD
    }

    public record Link(String key, LinkKind kind, String target) {}

    public record Hit(int start, int end, Link link) {}

    private record NarrowCache(NameIndex index, String key, boolean prefixOnly, int[] matches) {}

    private static volatile NameIndex names = NameIndex.EMPTY;
    private static volatile Map<String, Link[]> links = new HashMap<>();
    private static volatile int generation = 0;
    private static NarrowCache narrow;

    private ItemIndexes() {}

    public static int generation() {
        return generation;
    }

    public static boolean isBuilt() {
        return names.names().length > 0;
    }

    public static int nameCount() {
        return names.names().length;
    }

    public static void rebuild() {
        long start = System.currentTimeMillis();
        try {
            List<ItemStack> items = new ArrayList<>(ItemList.items);
            Map<String, ItemStack> byName = new LinkedHashMap<>();
            Map<String, ItemStack> byId = new HashMap<>();
            Map<String, ItemStack> byMod = new HashMap<>();
            for (ItemStack stack : items) {
                if (stack == null || stack.getItem() == null) continue;
                String name = cleanName(stack);
                if (name != null) byName.putIfAbsent(name, stack);
                String key = keyOf(stack);
                if (key != null) {
                    int at = key.indexOf('@');
                    byId.putIfAbsent(at > 0 ? key.substring(0, at) : key, stack);
                }
                String mod = modOf(stack);
                if (mod != null) byMod.putIfAbsent(mod, stack);
            }
            NameIndex index = buildNames(byName);
            Map<String, Link[]> linkIndex = buildLinks(index, byId, byMod);
            names = index;
            links = linkIndex;
            generation++;
            NEIGuess.LOG.info(
                "Indexed {} item names and {} chat link buckets in {} ms",
                index.names().length,
                linkIndex.size(),
                System.currentTimeMillis() - start);
        } catch (Throwable t) {
            NEIGuess.LOG.warn("Building the item indexes failed: {}", t.toString());
        }
    }

    private static String cleanName(ItemStack stack) {
        try {
            String raw = stack.getDisplayName();
            if (raw == null) return null;
            String name = EnumChatFormatting.getTextWithoutFormattingCodes(raw)
                .trim();
            if (name.length() < MIN_NAME || name.length() > MAX_NAME || name.indexOf('§') >= 0) return null;
            return isRawKey(name) ? null : name;
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean isRawKey(String n) {
        if (n.indexOf(' ') >= 0) return false;
        if (n.endsWith(".name")) return true;
        String[] parts = n.split("\\.");
        if (parts.length < 3) return false;
        for (String p : parts) if (p.isEmpty() || !p.equals(p.toLowerCase(Locale.ROOT))) return false;
        return switch (parts[0]) {
            case "item", "tile", "block", "entity", "fluid", "gui", "gt" -> true;
            default -> false;
        };
    }

    private static NameIndex buildNames(Map<String, ItemStack> byName) {
        String[] sorted = byName.keySet()
            .toArray(new String[0]);
        Arrays.sort(
            sorted,
            Comparator.comparingInt(String::length)
                .thenComparing(Comparator.naturalOrder()));
        int n = sorted.length;
        String[] lower = new String[n];
        boolean[] ascii = new boolean[n];
        ItemStack[] stacks = new ItemStack[n];
        Map<String, Integer> pos = new HashMap<>(n * 2);
        int[] lengthStart = new int[MAX_NAME + 2];
        Arrays.fill(lengthStart, n);
        for (int i = n - 1; i >= 0; i--) {
            String s = sorted[i];
            lower[i] = s.toLowerCase(Locale.ROOT);
            ascii[i] = PinyinBridge.isAscii(s);
            stacks[i] = byName.get(s);
            pos.put(s, i);
            lengthStart[s.length()] = i;
        }
        for (int l = MAX_NAME; l >= 0; l--) lengthStart[l] = Math.min(lengthStart[l], lengthStart[l + 1]);
        return new NameIndex(sorted, lower, ascii, stacks, lengthStart, pos);
    }

    private static Map<String, Link[]> buildLinks(NameIndex index, Map<String, ItemStack> byId,
        Map<String, ItemStack> byMod) {
        Map<String, Link> all = new HashMap<>();
        for (int i = 0; i < index.names().length; i++) {
            String key = keyOf(index.stacks()[i]);
            if (key != null) all.putIfAbsent(index.names()[i], new Link(index.names()[i], LinkKind.ITEM, key));
        }
        for (Map.Entry<String, ItemStack> e : byId.entrySet()) {
            all.putIfAbsent(e.getKey(), new Link(e.getKey(), LinkKind.ITEM, keyOf(e.getValue())));
        }
        for (String ore : OreDictionary.getOreNames()) {
            if (ore == null || ore.length() < 3
                || OreDictionary.getOres(ore)
                    .isEmpty())
                continue;
            all.putIfAbsent("$" + ore, new Link("$" + ore, LinkKind.ORE, ore));
        }
        Map<String, ModContainer> mods = Loader.instance()
            .getIndexedModList();
        for (String modid : byMod.keySet()) {
            all.putIfAbsent("@" + modid, new Link("@" + modid, LinkKind.MOD, modid));
            ModContainer mc = mods.get(modid);
            String display = mc == null ? null : mc.getName();
            if (display != null && display.length() >= 3 && display.length() <= MAX_NAME) {
                all.putIfAbsent(display, new Link(display, LinkKind.MOD, modid));
            }
        }
        Map<String, List<Link>> buckets = new HashMap<>();
        for (Link link : all.values()) {
            if (link.key()
                .length() < MIN_NAME) continue;
            buckets.computeIfAbsent(bucketKey(link.key(), 0), k -> new ArrayList<>())
                .add(link);
        }
        Map<String, Link[]> out = new HashMap<>(buckets.size() * 2);
        for (Map.Entry<String, List<Link>> e : buckets.entrySet()) {
            Link[] arr = e.getValue()
                .toArray(new Link[0]);
            Arrays.sort(
                arr,
                Comparator.comparingInt(
                    (Link l) -> l.key()
                        .length())
                    .reversed());
            out.put(e.getKey(), arr);
        }
        return out;
    }

    private static String bucketKey(String s, int at) {
        return s.substring(at, at + 2)
            .toLowerCase(Locale.ROOT);
    }

    public static List<Hit> findLinks(String text) {
        List<Hit> result = new ArrayList<>();
        Map<String, Link[]> index = links;
        if (text == null || text.length() < MIN_NAME || index.isEmpty()) return result;
        boolean boundary = ModConfig.chat.asciiWordBoundary;
        List<Hit> all = new ArrayList<>();
        for (int i = 0; i + MIN_NAME <= text.length(); i++) {
            Link[] bucket = index.get(bucketKey(text, i));
            if (bucket == null) continue;
            for (Link link : bucket) {
                String k = link.key();
                int end = i + k.length();
                if (end > text.length() || !text.regionMatches(true, i, k, 0, k.length())) continue;
                if (boundary && !boundaryOk(text, i, end)) continue;
                all.add(new Hit(i, end, link));
            }
        }
        all.sort(
            Comparator.comparingInt((Hit h) -> h.end() - h.start())
                .reversed()
                .thenComparingInt(Hit::start));
        for (Hit h : all) {
            if (result.size() >= MAX_LINK_HITS) break;
            boolean overlap = false;
            for (Hit x : result) {
                if (h.start() < x.end() && h.end() > x.start()) {
                    overlap = true;
                    break;
                }
            }
            if (!overlap) result.add(h);
        }
        result.sort(Comparator.comparingInt(Hit::start));
        return result;
    }

    private static boolean boundaryOk(String text, int start, int end) {
        if (isWordChar(text.charAt(start)) && start > 0 && isWordChar(text.charAt(start - 1))) return false;
        return !(isWordChar(text.charAt(end - 1)) && end < text.length() && isWordChar(text.charAt(end)));
    }

    private static boolean isWordChar(char c) {
        return c < 128 && (Character.isLetterOrDigit(c) || c == '_');
    }

    public static List<String> suggestNames(String input, int limit, boolean prefixOnly) {
        List<String> out = new ArrayList<>();
        NameIndex idx = names;
        if (input == null || input.isBlank() || limit <= 0 || idx.names().length == 0) return out;
        String key = input.trim()
            .toLowerCase(Locale.ROOT);
        if (key.length() > MAX_NAME) return out;
        int[] matches = matches(idx, key, prefixOnly);
        boolean pinyin = PinyinBridge.isAscii(key) && PinyinBridge.isReady();
        for (int pass = 0; pass < 2 && out.size() < limit; pass++) {
            for (int i : matches) {
                if (out.size() >= limit) break;
                boolean prefix = idx.lower()[i].startsWith(key)
                    || pinyin && !idx.ascii()[i] && PinyinBridge.begins(idx.names()[i], key);
                if (prefix == (pass == 0)) out.add(idx.names()[i]);
            }
        }
        return out;
    }

    private static int[] matches(NameIndex idx, String key, boolean prefixOnly) {
        boolean pinyin = PinyinBridge.isAscii(key) && PinyinBridge.isReady();
        NarrowCache c = narrow;
        int[] candidates = null;
        if (c != null && c.index() == idx && c.prefixOnly() == prefixOnly) {
            if (c.key()
                .equals(key)) return c.matches();
            // A one-letter pinyin query only checked prefixes, so it cannot narrow a substring search.
            boolean narrowable = prefixOnly || !pinyin
                || c.key()
                    .length() >= 2;
            if (narrowable && key.startsWith(c.key())) candidates = c.matches();
        }
        int total = candidates == null ? idx.names().length : candidates.length;
        int[] found = new int[Math.min(total, 1024)];
        int count = 0;
        for (int n = 0; n < total; n++) {
            int i = candidates == null ? n : candidates[n];
            String lower = idx.lower()[i];
            boolean hit = prefixOnly ? lower.startsWith(key) : lower.contains(key);
            if (!hit && pinyin && !idx.ascii()[i]) {
                String name = idx.names()[i];
                hit = PinyinBridge.begins(name, key)
                    || !prefixOnly && key.length() >= 2 && PinyinBridge.contains(name, key);
            }
            if (!hit) continue;
            if (count == found.length) found = Arrays.copyOf(found, found.length * 2);
            found[count++] = i;
        }
        int[] result = Arrays.copyOf(found, count);
        narrow = new NarrowCache(idx, key, prefixOnly, result);
        return result;
    }

    public static String nearestName(String input) {
        NameIndex idx = names;
        if (input == null || idx.names().length == 0) return null;
        String key = input.trim()
            .toLowerCase(Locale.ROOT);
        int len = key.length();
        if (len < MIN_NAME || len > MAX_NAME) return null;
        int from = idx.lengthStart()[Math.max(0, len - 2)];
        int to = idx.lengthStart()[Math.min(MAX_NAME + 1, len + 3)];
        String best = null;
        int bestD = 3;
        for (int i = from; i < to && i - from < 20000; i++) {
            int d = editDistance(key, idx.lower()[i], 2);
            if (d < bestD) {
                bestD = d;
                best = idx.names()[i];
                if (d == 0) break;
            }
        }
        return best;
    }

    public static int editDistance(String a, String b, int limit) {
        if (Math.abs(a.length() - b.length()) > limit) return limit + 1;
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            int rowMin = cur[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, cur[j]);
            }
            if (rowMin > limit) return limit + 1;
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    public static ItemStack stackOfName(String name) {
        NameIndex idx = names;
        Integer i = name == null ? null
            : idx.byName()
                .get(name);
        return i == null ? null : idx.stacks()[i];
    }

    public static String keyOf(ItemStack stack) {
        String id = idOf(stack);
        if (id == null) return null;
        int meta = stack.getItemDamage();
        return meta == 0 ? id : id + "@" + meta;
    }

    public static String modOf(ItemStack stack) {
        String id = idOf(stack);
        return id == null ? null : id.substring(0, id.indexOf(':'));
    }

    private static String idOf(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        try {
            GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(stack.getItem());
            return id == null ? null : id.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    public static ItemStack stackOfKey(String key) {
        if (key == null || key.isBlank()) return null;
        String k = key.trim();
        int meta = 0;
        int at = k.lastIndexOf('@');
        if (at > 0) {
            try {
                meta = Integer.parseInt(k.substring(at + 1));
                k = k.substring(0, at);
            } catch (NumberFormatException ignored) {}
        }
        int colon = k.indexOf(':');
        String modid = colon > 0 ? k.substring(0, colon) : "minecraft";
        String name = colon > 0 ? k.substring(colon + 1) : k;
        Item item = GameRegistry.findItem(modid, name);
        if (item == null) {
            Block block = GameRegistry.findBlock(modid, name);
            if (block != null) item = Item.getItemFromBlock(block);
        }
        return item == null ? null : new ItemStack(item, 1, meta);
    }
}
