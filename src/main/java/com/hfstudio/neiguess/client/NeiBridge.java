package com.hfstudio.neiguess.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.item.ItemStack;

import com.hfstudio.neiguess.NEIGuess;

import codechicken.nei.ItemList;
import codechicken.nei.ItemPanels;
import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.SearchField;
import codechicken.nei.SearchTokenParser;
import codechicken.nei.TextField;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import codechicken.nei.util.TextHistory;

public final class NeiBridge {

    public static final char MOD_PREFIX = '@';
    public static final char ORE_PREFIX = '$';

    private static final Map<String, Boolean> RESULT_CACHE = new LinkedHashMap<>(64, 0.75f, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > 256;
        }
    };

    private static int cacheGeneration = -1;
    private static Field innerField;
    private static Field historyField;
    private static Field historyListField;
    private static boolean reflected = false;

    private NeiBridge() {}

    private static void reflect() {
        if (reflected) return;
        reflected = true;
        try {
            innerField = TextField.class.getDeclaredField("field");
            innerField.setAccessible(true);
        } catch (Throwable t) {
            NEIGuess.LOG.info("NEI text box not reachable, ghost text disabled: {}", t.toString());
        }
        try {
            historyField = SearchField.class.getDeclaredField("history");
            historyField.setAccessible(true);
            historyListField = TextHistory.class.getDeclaredField("history");
            historyListField.setAccessible(true);
        } catch (Throwable t) {
            NEIGuess.LOG.info("NEI search history not reachable: {}", t.toString());
        }
    }

    public static SearchField field() {
        return LayoutManager.searchField;
    }

    public static boolean isSearchVisible() {
        SearchField f = field();
        return f != null && Minecraft.getMinecraft().currentScreen instanceof GuiContainer
            && NEIClientConfig.isEnabled()
            && !NEIClientConfig.isHidden()
            && f.isVisible();
    }

    public static boolean isSearchFocused() {
        return isSearchVisible() && field().focused();
    }

    public static String text() {
        SearchField f = field();
        return f == null ? "" : f.text();
    }

    public static void setText(String text) {
        SearchField f = field();
        if (f == null) return;
        String t = text == null ? "" : text;
        f.setText(t);
        GuiTextField box = innerBox();
        if (box != null) box.setCursorPositionEnd();
        addNeiHistory(t);
    }

    public static void setFocus(boolean focus) {
        SearchField f = field();
        if (f != null) f.setFocus(focus);
    }

    public static GuiTextField innerBox() {
        reflect();
        SearchField f = field();
        if (innerField == null || f == null) return null;
        try {
            return (GuiTextField) innerField.get(f);
        } catch (Throwable t) {
            return null;
        }
    }

    public static int[] bounds() {
        SearchField f = field();
        return f == null ? null : new int[] { f.x, f.y, f.w, f.h };
    }

    public static boolean searchContains(int mx, int my) {
        SearchField f = field();
        return f != null && f.contains(mx, my);
    }

    @SuppressWarnings("unchecked")
    public static List<String> neiHistory(int limit) {
        reflect();
        List<String> out = new ArrayList<>();
        if (historyField == null || historyListField == null || limit <= 0) return out;
        try {
            List<String> list = (List<String>) historyListField.get(historyField.get(null));
            for (int i = list.size() - 1; i >= 0 && out.size() < limit; i--) {
                String s = list.get(i);
                if (s != null && !s.isBlank()) out.add(s);
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private static void addNeiHistory(String text) {
        reflect();
        if (historyField == null || text == null || text.isBlank()) return;
        try {
            ((TextHistory) historyField.get(null)).add(text);
        } catch (Throwable ignored) {}
    }

    public static boolean hasResult(String query) {
        if (query == null || query.isBlank()) return true;
        List<ItemStack> items = ItemList.items;
        if (items == null || items.isEmpty()) return true;
        if (cacheGeneration != ItemIndexes.generation()) {
            RESULT_CACHE.clear();
            cacheGeneration = ItemIndexes.generation();
        }
        String key = query.trim();
        Boolean cached = RESULT_CACHE.get(key);
        if (cached != null) return cached;
        boolean found = false;
        try {
            ItemFilter filter = SearchField.getFilter(key);
            for (ItemStack stack : items) {
                if (filter.matches(stack)) {
                    found = true;
                    break;
                }
            }
        } catch (Throwable t) {
            found = true;
        }
        RESULT_CACHE.put(key, found);
        return found;
    }

    public static void clearResultCache() {
        RESULT_CACHE.clear();
    }

    public static ItemStack panelStackAt(int mx, int my) {
        try {
            if (!ItemPanels.itemPanel.contains(mx, my)) return null;
            return ItemPanels.itemPanel.getStackMouseOver(mx, my);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean openRecipes(ItemStack stack, boolean usage) {
        if (stack == null) return false;
        try {
            return usage ? GuiUsageRecipe.openRecipeGui("item", stack.copy())
                : GuiCraftingRecipe.openRecipeGui("item", stack.copy());
        } catch (Throwable t) {
            NEIGuess.LOG.info("Could not open NEI for {}: {}", stack, t.toString());
            return false;
        }
    }

    public static void openInventorySearch(String query) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        setText(query);
        if (!(mc.currentScreen instanceof GuiContainer)) mc.displayGuiScreen(new GuiInventory(mc.thePlayer));
    }

    public static boolean prefixEnabled(char prefix) {
        try {
            SearchTokenParser parser = SearchField.searchParser;
            return parser.getProvider(parser.getRedefinedPrefix(prefix)) != null;
        } catch (Throwable t) {
            return true;
        }
    }

    public static String prefixed(char prefix, String term) {
        String body = escape(term);
        try {
            SearchTokenParser parser = SearchField.searchParser;
            char real = parser.getRedefinedPrefix(prefix);
            if (parser.getProvider(real) == null) return body;
            return real + body;
        } catch (Throwable t) {
            return prefix + body;
        }
    }

    public static String escape(String term) {
        if (term == null) return "";
        String t = term.trim();
        int mode;
        try {
            mode = NEIClientConfig.getIntSetting("inventory.search.patternMode");
        } catch (Throwable e) {
            mode = 1;
        }
        boolean quote = t.indexOf(' ') >= 0;
        t = switch (mode) {
            case 1 -> t.replaceAll("[?*]", "\\\\$0");
            case 2 -> t.replaceAll("[{}()\\[\\].+*?^$\\\\|]", "\\\\$0");
            case 3 -> quote ? t : t.replaceAll("[-<>^{}|/\\\\\"]", "\\\\$0");
            default -> t;
        };
        if (quote) t = "\"" + t.replace("\"", "") + "\"";
        return t;
    }
}
