package com.hfstudio.neiguess.client.quest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.hfstudio.neiguess.NEIGuess;
import com.hfstudio.neiguess.api.Suggestion;
import com.hfstudio.neiguess.client.Lexicon;
import com.hfstudio.neiguess.client.NeiBridge;
import com.hfstudio.neiguess.client.SearchHistoryStore;
import com.hfstudio.neiguess.config.ModConfig;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestDatabase;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.client.gui2.GuiQuest;

final class BetterQuestingBridge {

    private static final long CACHE_MS = 15_000L;
    private static final int MAX_QUESTS = 300;
    private static final int MAX_ITEMS_PER_QUEST = 8;

    private record QuestItem(String display, String query) {}

    private static final Map<Class<?>, Field> REQUIRED_ITEMS = new HashMap<>();
    private static List<Suggestion> pool = new ArrayList<>();
    private static long loadedAt = 0L;
    private static Field questIdField;
    private static boolean questIdResolved = false;
    private static UUID lastViewed;

    private BetterQuestingBridge() {}

    static void invalidate() {
        loadedAt = 0L;
    }

    static List<Suggestion> suggestions(int limit) {
        long now = System.currentTimeMillis();
        if (now - loadedAt > CACHE_MS || pool.isEmpty() && now - loadedAt > 2000L) {
            loadedAt = now;
            pool = rebuild();
        }
        return new ArrayList<>(pool.subList(0, Math.min(limit, pool.size())));
    }

    private static List<Suggestion> rebuild() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return new ArrayList<>();
        IQuestDatabase db = QuestingAPI.getAPI(ApiReference.QUEST_DB);
        if (db == null) return new ArrayList<>();
        UUID me = QuestingAPI.getQuestingUUID(mc.thePlayer);
        boolean skipRoots = ModConfig.sources.questSkipRoots;

        Map<UUID, List<QuestItem>> items = new LinkedHashMap<>();
        Map<UUID, IQuest> quests = new HashMap<>();
        Set<UUID> started = new LinkedHashSet<>();
        try {
            for (Map.Entry<UUID, IQuest> e : db.entrySet()) {
                if (items.size() >= MAX_QUESTS) break;
                IQuest quest = e.getValue();
                try {
                    if (quest == null || quest.isComplete(me) || !quest.isUnlocked(me)) continue;
                    if (skipRoots && quest.getRequirements()
                        .isEmpty()) continue;
                    boolean[] anyDone = { false };
                    List<QuestItem> list = itemsOf(quest, me, anyDone);
                    if (list.isEmpty()) continue;
                    items.put(e.getKey(), list);
                    quests.put(e.getKey(), quest);
                    if (anyDone[0]) started.add(e.getKey());
                } catch (Throwable ignored) {}
            }
        } catch (ConcurrentModificationException e) {
            // The quest database is being synced right now; the next refresh picks up the rest.
            loadedAt = 0L;
        }

        List<UUID> order = new ArrayList<>();
        List<SearchHistoryStore.QuestView> views = SearchHistoryStore.get()
            .questViews();
        for (SearchHistoryStore.QuestView v : views) addId(order, v.id);
        views.sort(
            Comparator.comparingInt((SearchHistoryStore.QuestView v) -> v.views)
                .reversed());
        for (SearchHistoryStore.QuestView v : views) addId(order, v.id);
        order.addAll(started);
        order.addAll(items.keySet());

        List<Suggestion> out = new ArrayList<>();
        Set<String> seenQuests = new HashSet<>();
        Set<String> seenItems = new HashSet<>();
        for (UUID id : order) {
            List<QuestItem> list = items.get(id);
            if (list == null || !seenQuests.add(id.toString())) continue;
            String title = titleOf(id, quests.get(id));
            String badge = title.length() > 16 ? title.substring(0, 16) + "…" : title;
            for (QuestItem qi : list) {
                if (seenItems.add(qi.query())) {
                    out.add(Suggestion.hoverBadge(qi.display(), qi.query(), Suggestion.Source.QUEST, badge));
                }
            }
        }
        if (NEIGuess.debug()) NEIGuess.LOG.info("Quest pool: {} items from {} quests", out.size(), items.size());
        return out;
    }

    private static void addId(List<UUID> order, String id) {
        try {
            order.add(UUID.fromString(id));
        } catch (Throwable ignored) {}
    }

    private static String titleOf(UUID id, IQuest quest) {
        try {
            String t = QuestTranslation.translateQuestName(id, quest);
            return t == null ? "" : EnumChatFormatting.getTextWithoutFormattingCodes(t);
        } catch (Throwable t) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private static List<QuestItem> itemsOf(IQuest quest, UUID me, boolean[] anyDone) {
        List<QuestItem> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (DBEntry<ITask> entry : quest.getTasks()
            .getEntries()) {
            ITask task = entry.getValue();
            if (task == null) continue;
            if (me != null && task.isComplete(me)) {
                anyDone[0] = true;
                continue;
            }
            Field f = requiredItemsField(task.getClass());
            if (f == null) continue;
            List<BigItemStack> required;
            try {
                required = (List<BigItemStack>) f.get(task);
            } catch (Throwable t) {
                continue;
            }
            if (required == null) continue;
            for (BigItemStack big : required) {
                if (out.size() >= MAX_ITEMS_PER_QUEST) return out;
                QuestItem qi = toItem(big);
                if (qi != null && seen.add(qi.query())) out.add(qi);
            }
        }
        return out;
    }

    private static Field requiredItemsField(Class<?> cls) {
        return REQUIRED_ITEMS.computeIfAbsent(cls, c -> {
            try {
                Field f = c.getField("requiredItems");
                return List.class.isAssignableFrom(f.getType()) ? f : null;
            } catch (Throwable t) {
                return null;
            }
        });
    }

    private static QuestItem toItem(BigItemStack big) {
        if (big == null) return null;
        try {
            if (big.hasOreDict()) {
                String ore = big.getOreDict();
                return new QuestItem(Lexicon.oreDisplayName(ore), NeiBridge.prefixed(NeiBridge.ORE_PREFIX, ore));
            }
            ItemStack stack = big.getBaseStack();
            if (stack == null || stack.getItem() == null) return null;
            String name = EnumChatFormatting.getTextWithoutFormattingCodes(stack.getDisplayName());
            if (name == null || name.isBlank() || name.length() > 32) return null;
            return new QuestItem(name, NeiBridge.escape(name));
        } catch (Throwable t) {
            return null;
        }
    }

    static void watch() {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (!(screen instanceof GuiQuest)) {
            lastViewed = null;
            return;
        }
        UUID id = viewedQuest(screen);
        if (id == null || id.equals(lastViewed)) return;
        lastViewed = id;
        IQuestDatabase db = QuestingAPI.getAPI(ApiReference.QUEST_DB);
        IQuest quest = db == null ? null : db.get(id);
        if (quest == null) return;
        List<String> names = new ArrayList<>();
        for (QuestItem qi : itemsOf(quest, null, new boolean[1])) names.add(qi.display());
        if (names.isEmpty()) return;
        String title = titleOf(id, quest);
        SearchHistoryStore.get()
            .recordQuestView(id.toString(), title, names);
        loadedAt = 0L;
        if (NEIGuess.debug()) NEIGuess.LOG.info("Viewing quest '{}' ({} items)", title, names.size());
    }

    private static UUID viewedQuest(GuiScreen screen) {
        if (!questIdResolved) {
            questIdResolved = true;
            try {
                questIdField = GuiQuest.class.getDeclaredField("questID");
                questIdField.setAccessible(true);
            } catch (Throwable t) {
                NEIGuess.LOG.info("Quest book tracking unavailable: {}", t.toString());
            }
        }
        if (questIdField == null) return null;
        try {
            return (UUID) questIdField.get(screen);
        } catch (Throwable t) {
            return null;
        }
    }
}
