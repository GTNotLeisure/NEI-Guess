package com.hfstudio.neiguess.client.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.oredict.OreDictionary;

import com.asdflj.nech.utils.Match;
import com.hfstudio.neiguess.NEIGuess;
import com.hfstudio.neiguess.api.NEIGuessAPI;
import com.hfstudio.neiguess.client.ItemIndexes;
import com.hfstudio.neiguess.client.Lexicon;

import codechicken.nei.ItemList;
import codechicken.nei.LRUCache;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.SearchField;
import codechicken.nei.search.ModNameFilter;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import me.towdium.pinin.searchers.Searcher.Logic;
import me.towdium.pinin.searchers.TreeSearcher;

public class ClientSearchAliases implements BiPredicate<String, String> {

    public static final ClientSearchAliases INSTANCE = new ClientSearchAliases();
    private final LRUCache<String, QueryPlan> plans = new LRUCache<>(128);
    private volatile Snapshot snapshot = new Snapshot(Map.of(), null, Map.of());
    private Snapshot plansSnapshot;
    private long plansRevision = -1;
    private int apiRevision = -1;
    private int itemGeneration = -1;
    private long lexiconRevision = -1;
    private int patternMode = -1;
    private long phoneticRevision = -1;
    private boolean initialized;
    private volatile long snapshotRevision;
    private char modPrefix;
    private char orePrefix;

    private ClientSearchAliases() {}

    public void initialize() {
        if (initialized) return;
        initialized = true;
        Match.addMatcher(this);
        if (Minecraft.getMinecraft()
            .getResourceManager() instanceof IReloadableResourceManager manager) {
            manager.registerReloadListener(resources -> Lexicon.invalidate());
        }
    }

    public void tick() {
        if (!initialized) return;
        char currentModPrefix = SearchField.searchParser.getRedefinedPrefix('@');
        char currentOrePrefix = SearchField.searchParser.getRedefinedPrefix('$');
        long currentPhoneticRevision = Match.revision();
        boolean queryChanged = modPrefix != currentModPrefix || orePrefix != currentOrePrefix
            || phoneticRevision != currentPhoneticRevision;
        if (queryChanged) {
            synchronized (plans) {
                plans.clear();
            }
            modPrefix = currentModPrefix;
            orePrefix = currentOrePrefix;
            phoneticRevision = currentPhoneticRevision;
        }
        int currentApiRevision = NEIGuessAPI.revision();
        int currentItemGeneration = ItemIndexes.generation();
        long currentLexiconRevision = Lexicon.revision();
        int currentPatternMode = NEIClientConfig.getIntSetting("inventory.search.patternMode");
        boolean aliasesChanged = apiRevision != currentApiRevision || itemGeneration != currentItemGeneration
            || lexiconRevision != currentLexiconRevision
            || patternMode != currentPatternMode;
        if (!queryChanged && !aliasesChanged) return;
        if (aliasesChanged) {
            boolean namesChanged = itemGeneration != currentItemGeneration || lexiconRevision != currentLexiconRevision;
            snapshot = buildSnapshot(namesChanged ? buildNames() : snapshot.names);
        }
        snapshotRevision++;
        apiRevision = currentApiRevision;
        itemGeneration = currentItemGeneration;
        lexiconRevision = currentLexiconRevision;
        patternMode = currentPatternMode;
        SearchField.searchParser.clearCache();
        ItemList.updateFilter.restart();
    }

    public long revision() {
        return snapshotRevision;
    }

    @Override
    public boolean test(String text, String query) {
        if (text == null || query == null || query.isBlank()) return false;
        QueryPlan plan = plan(query);
        if (plan.isEmpty()) return false;
        String normalized = normalize(text);
        Metadata metadata = plan.snapshot.names.get(normalized);
        if (metadata != null && plan.matches(metadata)) return true;
        if (plan.modNames.contains(normalized)) return true;
        for (Pattern ore : plan.ores) {
            if (ore.matcher(text)
                .find()) return true;
        }
        for (String alias : plan.aliases) {
            if (normalized.contains(alias)) return true;
        }
        return false;
    }

    public boolean matchesItem(ItemStack stack, String query) {
        if (stack == null || stack.getItem() == null || query == null) return false;
        QueryPlan plan = plan(query);
        if (plan.isEmpty()) return false;
        if (plan.modNames.contains(normalize(NativeModAccess.name(stack)))) return true;
        for (int id : OreDictionary.getOreIDs(stack)) {
            String ore = OreDictionary.getOreName(id);
            for (Pattern pattern : plan.ores) if (pattern.matcher(ore)
                .find()) return true;
        }
        return false;
    }

    private QueryPlan plan(String query) {
        synchronized (plans) {
            Snapshot current = snapshot;
            long revision = Match.revision();
            if (plansSnapshot != current || plansRevision != revision) {
                plans.clear();
                plansSnapshot = current;
                plansRevision = revision;
            }
            String key = normalize(query);
            QueryPlan plan = plans.get(key);
            if (plan == null) {
                plan = compile(current, key);
                plans.put(key, plan);
            }
            return plan;
        }
    }

    private static QueryPlan compile(Snapshot snapshot, String query) {
        char kind = query.isEmpty() ? 0 : query.charAt(0);
        char modPrefix = SearchField.searchParser.getRedefinedPrefix('@');
        char orePrefix = SearchField.searchParser.getRedefinedPrefix('$');
        boolean modOnly = kind == '@' || kind == modPrefix;
        boolean oreOnly = kind == '$' || kind == orePrefix;
        String term = modOnly || oreOnly ? query.substring(1) : query;
        if (term.startsWith("\"") && term.endsWith("\"") && term.length() >= 2) {
            term = term.substring(1, term.length() - 1);
        }
        Set<String> modNames = new HashSet<>();
        List<Pattern> ores = new ArrayList<>();
        Set<String> aliases = new HashSet<>();
        if (!term.isEmpty()) {
            List<AliasRule> candidates;
            synchronized (Match.context) {
                candidates = snapshot.index == null ? List.of() : snapshot.index.search(term);
            }
            for (AliasRule rule : candidates) {
                if (modOnly && rule.ore || oreOnly && !rule.ore) continue;
                if (!rule.alias.equals(term) && term.length() < 2) continue;
                if (rule.ore) ores.add(rule.pattern);
                else modNames.add(rule.target);
                aliases.addAll(snapshot.aliases.getOrDefault((rule.ore ? "$" : "@") + rule.target, List.of()));
            }
        }
        return new QueryPlan(snapshot, Set.copyOf(modNames), List.copyOf(ores), List.copyOf(aliases));
    }

    private static Snapshot buildSnapshot(Map<String, Metadata> names) {
        Map<String, String> modNames = new HashMap<>();
        for (ModContainer mod : Loader.instance()
            .getActiveModList()) {
            modNames.put(mod.getModId(), normalize(mod.getName()));
        }
        modNames.putIfAbsent("minecraft", "minecraft");
        List<AliasRule> rules = new ArrayList<>();
        for (Lexicon.Alias alias : Lexicon.mods()
            .values()) {
            String name = modNames.get(alias.target());
            if (name != null) {
                String word = normalize(alias.display());
                rules.add(new AliasRule(word, name, false, null));
                String id = normalize(alias.target());
                if (!id.equals(name)) rules.add(new AliasRule(word, id, false, null));
            }
        }
        Set<String> oreRules = new HashSet<>();
        for (Lexicon.Alias alias : Lexicon.ores()
            .values()) {
            addOreRule(rules, oreRules, alias.display(), alias.target());
        }
        for (String ore : Lexicon.oreNames()) {
            addOreRule(rules, oreRules, ore, "$" + ore);
            addOreRule(rules, oreRules, Lexicon.oreDisplayName(ore), "$" + ore);
        }
        NEIGuess.LOG.info("Published {} shared search aliases for {} item names", rules.size(), names.size());
        Map<String, Set<String>> grouped = new HashMap<>();
        for (AliasRule rule : rules) {
            grouped.computeIfAbsent((rule.ore ? "$" : "@") + rule.target, ignored -> new HashSet<>())
                .add(rule.alias);
        }
        Map<String, List<String>> aliases = new HashMap<>();
        grouped.forEach((target, words) -> aliases.put(target, List.copyOf(words)));
        TreeSearcher<AliasRule> index = Match.searcher(Logic.CONTAIN);
        synchronized (Match.context) {
            for (AliasRule rule : rules) index.put(rule.alias, rule);
        }
        return new Snapshot(names, index, Map.copyOf(aliases));
    }

    private static Map<String, Metadata> buildNames() {
        Map<String, MutableMetadata> names = new HashMap<>();
        for (ItemStack stack : new ArrayList<>(ItemList.items)) {
            if (stack == null || stack.getItem() == null) continue;
            try {
                String name = normalize(stack.getDisplayName());
                MutableMetadata metadata = names.computeIfAbsent(name, ignored -> new MutableMetadata());
                metadata.modNames.add(normalize(NativeModAccess.name(stack)));
                for (int id : OreDictionary.getOreIDs(stack)) metadata.ores.add(OreDictionary.getOreName(id));
            } catch (RuntimeException ignored) {}
        }
        Map<String, Metadata> immutableNames = new HashMap<>(names.size());
        names.forEach(
            (name, data) -> immutableNames.put(name, new Metadata(Set.copyOf(data.modNames), Set.copyOf(data.ores))));
        return Map.copyOf(immutableNames);
    }

    private static void addOreRule(List<AliasRule> rules, Set<String> seen, String alias, String target) {
        if (alias == null || alias.isBlank() || !seen.add(alias + '\n' + target)) return;
        String body = target.startsWith("$") ? target.substring(1) : target;
        Pattern pattern = SearchField.getPattern(body);
        if (pattern != null) rules.add(new AliasRule(normalize(alias), body, true, pattern));
    }

    private static String normalize(String text) {
        String plain = EnumChatFormatting.getTextWithoutFormattingCodes(text);
        return plain == null ? ""
            : plain.trim()
                .toLowerCase(Locale.ROOT);
    }

    private record AliasRule(String alias, String target, boolean ore, Pattern pattern) {}

    private record Metadata(Set<String> modNames, Set<String> ores) {}

    private record Snapshot(Map<String, Metadata> names, TreeSearcher<AliasRule> index,
        Map<String, List<String>> aliases) {}

    private record QueryPlan(Snapshot snapshot, Set<String> modNames, List<Pattern> ores, List<String> aliases) {

        private boolean isEmpty() {
            return modNames.isEmpty() && ores.isEmpty();
        }

        private boolean matches(Metadata metadata) {
            for (String name : metadata.modNames) if (modNames.contains(name)) return true;
            for (String name : metadata.ores) {
                for (Pattern pattern : ores) if (pattern.matcher(name)
                    .find()) return true;
            }
            return false;
        }
    }

    private static class MutableMetadata {

        private final Set<String> modNames = new HashSet<>();
        private final Set<String> ores = new HashSet<>();
    }

    private static class NativeModAccess extends ModNameFilter {

        private NativeModAccess() {
            super(Pattern.compile(""));
        }

        private static String name(ItemStack stack) {
            synchronized (NativeModAccess.class) {
                return nameFromStack(stack);
            }
        }
    }
}
