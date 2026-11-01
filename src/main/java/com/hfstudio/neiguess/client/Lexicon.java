package com.hfstudio.neiguess.client;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.oredict.OreDictionary;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hfstudio.neiguess.NEIGuess;
import com.hfstudio.neiguess.api.NEIGuessAPI;
import com.hfstudio.neiguess.config.ModConfig;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

public final class Lexicon {

    public record Alias(String display, String target) {}

    private static final String[] VANILLA_TABS = { "buildingBlocks", "decorations", "redstone", "transportation",
        "misc", "search", "food", "tools", "combat", "brewing", "materials", "inventory" };

    private static Map<String, Alias> mods;
    private static Map<String, Alias> ores;
    private static Map<String, String> oreDisplayPrefix;
    private static List<String> recommend;
    private static final Map<String, String> modDisplay = new HashMap<>();
    private static final Map<String, String> oreNameCache = new HashMap<>();
    private static String[] oreNames;
    private static int builtRevision = -1;
    private static int builtGeneration = -1;

    private Lexicon() {}

    public static void invalidate() {
        mods = null;
    }

    private static void ensure() {
        if (mods != null && builtRevision == NEIGuessAPI.revision()) {
            if (builtGeneration != ItemIndexes.generation()) {
                oreNames = null;
                oreNameCache.clear();
                builtGeneration = ItemIndexes.generation();
            }
            return;
        }
        long start = System.currentTimeMillis();
        builtRevision = NEIGuessAPI.revision();
        builtGeneration = ItemIndexes.generation();
        oreNames = null;
        oreNameCache.clear();
        modDisplay.clear();
        Map<String, Alias> m = new LinkedHashMap<>();
        Map<String, Alias> o = new LinkedHashMap<>();
        Map<String, String> d = new HashMap<>();
        Set<String> r = new LinkedHashSet<>();

        collectFromCreativeTabs(m);
        collectFromMods(m);
        int bundled = readModTable(readResource("mod_names.json"), m);
        readOreTable(readResource("ore_aliases.json"), o);
        readStringMap(readResource("ore_display.json"), d);
        for (JsonObject user : readUserFiles()) {
            if (user.has("mods") && user.get("mods")
                .isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : user.getAsJsonObject("mods")
                    .entrySet()) putMod(m, e.getKey(), str(e.getValue()), true);
            }
            if (user.has("ores")) readOreTable(user.get("ores"), o);
            if (user.has("oreDisplay")) readStringMap(user.get("oreDisplay"), d);
            if (user.has("recommend") && user.get("recommend")
                .isJsonArray()) {
                for (JsonElement e : user.getAsJsonArray("recommend")) r.add(str(e));
            }
        }
        for (String rule : ModConfig.words.modAliases) {
            String[] kv = split(rule);
            if (kv != null) putMod(m, kv[0], kv[1].startsWith("@") ? kv[1].substring(1) : kv[1], true);
        }
        for (String rule : ModConfig.words.oreAliases) {
            String[] kv = split(rule);
            if (kv != null) putOre(o, kv[0], kv[1]);
        }
        NEIGuessAPI.modAliases()
            .forEach((alias, modid) -> putMod(m, alias, modid, true));
        NEIGuessAPI.oreAliases()
            .forEach((alias, q) -> putOre(o, alias, q));
        r.addAll(Arrays.asList(ModConfig.words.recommendWords));
        r.addAll(NEIGuessAPI.recommendWords());
        r.removeIf(s -> s == null || s.isBlank());

        mods = m;
        ores = o;
        oreDisplayPrefix = d;
        recommend = new ArrayList<>(r);
        NEIGuess.LOG.info(
            "Lexicon ready: {} mod aliases ({} bundled), {} ore aliases, {} recommended words in {} ms",
            m.size(),
            bundled,
            o.size(),
            recommend.size(),
            System.currentTimeMillis() - start);
    }

    private static void collectFromCreativeTabs(Map<String, Alias> m) {
        Map<CreativeTabs, Map<String, Integer>> counts = new HashMap<>();
        try {
            for (Object o : Item.itemRegistry) {
                if (!(o instanceof Item item)) continue;
                CreativeTabs tab = item.getCreativeTab();
                if (tab == null || tab.getTabIndex() < VANILLA_TABS.length || isVanillaTab(tab)) continue;
                String mod = ItemIndexes.modOf(new ItemStack(item));
                if (mod == null || mod.equals("minecraft")) continue;
                counts.computeIfAbsent(tab, k -> new HashMap<>())
                    .merge(mod, 1, Integer::sum);
            }
        } catch (Throwable t) {
            NEIGuess.LOG.info("Reading creative tabs failed: {}", t.toString());
        }
        for (Map.Entry<CreativeTabs, Map<String, Integer>> e : counts.entrySet()) {
            int total = 0, best = 0;
            String bestMod = null;
            for (Map.Entry<String, Integer> c : e.getValue()
                .entrySet()) {
                total += c.getValue();
                if (c.getValue() > best) {
                    best = c.getValue();
                    bestMod = c.getKey();
                }
            }
            if (bestMod == null || total < 3 || best * 2 < total) continue;
            String name;
            try {
                name = e.getKey()
                    .getTranslatedTabLabel()
                    .trim();
            } catch (Throwable t) {
                continue;
            }
            if (name.length() < 2 || name.length() > 24 || name.startsWith("itemGroup.")) continue;
            putMod(m, name, bestMod, false);
        }
    }

    private static boolean isVanillaTab(CreativeTabs tab) {
        String label = tab.getTabLabel();
        for (String v : VANILLA_TABS) if (v.equals(label)) return true;
        return false;
    }

    private static void collectFromMods(Map<String, Alias> m) {
        for (ModContainer mc : Loader.instance()
            .getActiveModList()) {
            String id = mc.getModId();
            putMod(m, id, id, false);
            if (mc.getName() != null && !mc.getName()
                .isBlank()) putMod(m, mc.getName(), id, false);
            for (String key : new String[] { "itemGroup." + id, "itemGroup." + id.toLowerCase(Locale.ROOT) }) {
                if (StatCollector.canTranslate(key)) putMod(m, StatCollector.translateToLocal(key), id, false);
            }
        }
    }

    private static void putMod(Map<String, Alias> m, String alias, String modid, boolean override) {
        if (alias == null || modid == null) return;
        String a = alias.trim(), id = modid.trim();
        if (a.length() < 2 || id.isEmpty() || !Loader.isModLoaded(id) && !id.equals("minecraft")) return;
        String key = a.toLowerCase(Locale.ROOT);
        if (override) m.put(key, new Alias(a, id));
        else m.putIfAbsent(key, new Alias(a, id));
        if (!PinyinBridge.isAscii(a)) modDisplay.putIfAbsent(id, a);
    }

    private static void putOre(Map<String, Alias> o, String alias, String query) {
        if (alias == null || query == null || alias.isBlank() || query.isBlank()) return;
        String q = query.trim();
        o.put(
            alias.trim()
                .toLowerCase(Locale.ROOT),
            new Alias(alias.trim(), q.startsWith("$") ? q : "$" + q));
    }

    /** Bundled table: {@code "Mod Name[domain]"} -> tab names, resolved by mod id = domain, else by mod name. */
    private static int readModTable(JsonElement json, Map<String, Alias> m) {
        if (json == null || !json.isJsonObject()) return 0;
        Map<String, String> byId = new HashMap<>(), byName = new HashMap<>();
        for (ModContainer mc : Loader.instance()
            .getActiveModList()) {
            byId.put(
                mc.getModId()
                    .toLowerCase(Locale.ROOT),
                mc.getModId());
            if (mc.getName() != null) byName.putIfAbsent(folderName(mc.getName()), mc.getModId());
        }
        int n = 0;
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject()
            .entrySet()) {
            String key = e.getKey();
            int open = key.lastIndexOf('[');
            if (open <= 0 || !key.endsWith("]")) continue;
            String modid = byId.get(
                key.substring(open + 1, key.length() - 1)
                    .toLowerCase(Locale.ROOT));
            if (modid == null) modid = byName.get(folderName(key.substring(0, open)));
            if (modid == null || !e.getValue()
                .isJsonArray()) continue;
            for (JsonElement a : e.getValue()
                .getAsJsonArray()) putMod(m, str(a), modid, false);
            n++;
        }
        return n;
    }

    /** Translation folders replace characters that are illegal in file names. */
    private static String folderName(String name) {
        return name.replaceAll("[\\/:*?\"<>|]", "_")
            .toLowerCase(Locale.ROOT);
    }

    private static void readOreTable(JsonElement json, Map<String, Alias> o) {
        if (json == null || !json.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject()
            .entrySet()) putOre(o, e.getKey(), str(e.getValue()));
    }

    private static void readStringMap(JsonElement json, Map<String, String> d) {
        if (json == null || !json.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject()
            .entrySet()) {
            String v = str(e.getValue());
            if (v != null) d.put(e.getKey(), v);
        }
    }

    private static String str(JsonElement e) {
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static String[] split(String rule) {
        if (rule == null) return null;
        int eq = rule.indexOf('=');
        if (eq <= 0 || eq == rule.length() - 1) return null;
        return new String[] { rule.substring(0, eq)
            .trim(),
            rule.substring(eq + 1)
                .trim() };
    }

    private static JsonElement readResource(String name) {
        try (InputStream in = Lexicon.class.getResourceAsStream("/assets/neiguess/lexicon/" + name)) {
            if (in == null) return null;
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return new JsonParser().parse(reader);
            }
        } catch (Throwable t) {
            NEIGuess.LOG.info("Could not read bundled {}: {}", name, t.toString());
            return null;
        }
    }

    public static File userDir() {
        return new File(
            Loader.instance()
                .getConfigDir(),
            "neiguess/lexicon");
    }

    private static List<JsonObject> readUserFiles() {
        List<JsonObject> out = new ArrayList<>();
        File dir = userDir();
        try {
            if (!dir.isDirectory() && dir.mkdirs()) writeReadme(dir);
            File[] files = dir.listFiles(
                (f, n) -> n.toLowerCase(Locale.ROOT)
                    .endsWith(".json"));
            if (files == null) return out;
            Arrays.sort(files);
            for (File f : files) {
                try {
                    JsonElement e = new JsonParser()
                        .parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
                    if (e.isJsonObject()) out.add(e.getAsJsonObject());
                } catch (Throwable t) {
                    NEIGuess.LOG.warn("Skipping lexicon file {}: {}", f.getName(), t.toString());
                }
            }
        } catch (Throwable t) {
            NEIGuess.LOG.info("Could not read {}: {}", dir, t.toString());
        }
        return out;
    }

    private static void writeReadme(File dir) {
        String text = """
            NEI Guess word lists. Every *.json file here is merged in alphabetical order, later files win.
            Reload in game with /neiguess reload.

            {
              "mods": { "应用能源2": "appliedenergistics2" },
              "ores": { "锭": "$ingot", "小撮粉": "$dustTiny" },
              "oreDisplay": { "ingot": "%s锭", "dustTiny": "小撮%s粉" },
              "recommend": [ "appliedenergistics2", "$ingot" ]
            }

            mods       alias -> mod id (the search becomes @"Mod Name")
            ores       alias -> NEI ore dictionary query
            oreDisplay ore dictionary prefix -> name template (%s = rest of the ore name), used when an entry has no items
            recommend  extra words for the recommended source
            """;
        try {
            Files.write(new File(dir, "README.txt").toPath(), text.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {}
    }

    public static Map<String, Alias> mods() {
        ensure();
        return mods;
    }

    public static Map<String, Alias> ores() {
        ensure();
        return ores;
    }

    public static List<String> recommendWords() {
        ensure();
        return recommend;
    }

    public static String modQuery(String modid) {
        ModContainer mc = Loader.instance()
            .getIndexedModList()
            .get(modid);
        String name = mc == null ? "Minecraft" : mc.getName();
        return NeiBridge.prefixed(NeiBridge.MOD_PREFIX, name);
    }

    public static String modDisplay(String modid) {
        ensure();
        String d = modDisplay.get(modid);
        if (d != null) return d;
        ModContainer mc = Loader.instance()
            .getIndexedModList()
            .get(modid);
        return mc == null ? modid : mc.getName();
    }

    public static String modQueryOf(String text) {
        Alias a = modAliasOf(text);
        return a == null ? null : modQuery(a.target());
    }

    public static Alias modAliasOf(String text) {
        if (text == null || text.isBlank()) return null;
        String key = text.trim()
            .toLowerCase(Locale.ROOT);
        Map<String, Alias> map = mods();
        Alias exact = map.get(key);
        if (exact != null || key.length() < 3) return exact;
        Alias best = null;
        int bestLen = Integer.MAX_VALUE;
        for (Map.Entry<String, Alias> e : map.entrySet()) {
            String name = e.getKey();
            if (name.length() > key.length() && name.length() < bestLen && name.startsWith(key)) {
                bestLen = name.length();
                best = e.getValue();
            }
        }
        return best;
    }

    public static String oreQueryOf(String text) {
        if (text == null || text.isBlank()) return null;
        String key = text.trim()
            .toLowerCase(Locale.ROOT);
        if (key.startsWith("$")) return text.trim();
        Alias exact = ores().get(key);
        if (exact != null) return exact.target();
        if (key.length() >= 2) {
            for (Map.Entry<String, Alias> e : ores().entrySet()) {
                if (e.getKey()
                    .startsWith(key))
                    return e.getValue()
                        .target();
            }
        }
        return null;
    }

    public static String toQuery(String text) {
        if (text == null || text.isBlank()) return text;
        String t = text.trim();
        if ("@$#&-!|\"".indexOf(t.charAt(0)) >= 0) return t;
        Alias a = mods().get(t.toLowerCase(Locale.ROOT));
        return a != null ? modQuery(a.target()) : t;
    }

    public static String displayOf(String word) {
        if (word == null) return "";
        Alias a = mods().get(
            word.trim()
                .toLowerCase(Locale.ROOT));
        if (a != null && a.target()
            .equalsIgnoreCase(word.trim())) return modDisplay(a.target());
        return word;
    }

    public static String[] oreNames() {
        ensure();
        if (oreNames == null) {
            List<String> list = new ArrayList<>();
            for (String ore : OreDictionary.getOreNames()) {
                if (ore != null && !ore.isEmpty()
                    && !OreDictionary.getOres(ore)
                        .isEmpty())
                    list.add(ore);
            }
            oreNames = list.toArray(new String[0]);
        }
        return oreNames;
    }

    public static List<String> oresOf(ItemStack stack) {
        List<String> out = new ArrayList<>();
        if (stack == null || stack.getItem() == null) return out;
        try {
            for (int id : OreDictionary.getOreIDs(stack)) out.add(OreDictionary.getOreName(id));
        } catch (Throwable ignored) {}
        return out;
    }

    /** First item name of the ore entry, else a GregTech prefix template, else the raw name. */
    public static String oreDisplayName(String ore) {
        ensure();
        String o = ore.startsWith("$") ? ore.substring(1) : ore;
        return oreNameCache.computeIfAbsent(o, Lexicon::computeOreDisplay);
    }

    private static String computeOreDisplay(String ore) {
        try {
            List<ItemStack> stacks = OreDictionary.getOres(ore);
            for (ItemStack s : stacks) {
                if (s == null || s.getItem() == null) continue;
                ItemStack probe = s.copy();
                if (probe.getItemDamage() == OreDictionary.WILDCARD_VALUE) probe.setItemDamage(0);
                String name = probe.getDisplayName();
                if (name != null && !name.isBlank() && !ItemIndexes.isRawKey(name)) return name;
            }
        } catch (Throwable ignored) {}
        String best = null;
        for (String prefix : oreDisplayPrefix.keySet()) {
            if (ore.startsWith(prefix) && ore.length() > prefix.length()
                && Character.isUpperCase(ore.charAt(prefix.length()))
                && (best == null || prefix.length() > best.length())) best = prefix;
        }
        return best == null ? ore
            : oreDisplayPrefix.get(best)
                .replace("%s", ore.substring(best.length()));
    }
}
