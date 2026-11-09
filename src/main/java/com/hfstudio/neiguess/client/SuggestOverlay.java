package com.hfstudio.neiguess.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.hfstudio.neiguess.NEIGuess;
import com.hfstudio.neiguess.api.Suggestion;
import com.hfstudio.neiguess.config.ModConfig;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.guihook.IContainerDrawHandler;
import codechicken.nei.guihook.IContainerInputHandler;
import codechicken.nei.guihook.IContainerTooltipHandler;

public final class SuggestOverlay implements IContainerDrawHandler, IContainerInputHandler, IContainerTooltipHandler {

    public static final SuggestOverlay INSTANCE = new SuggestOverlay();

    private static final int LINE_H = 12;
    private static final int PAD = 3;
    private static final int HEADER_H = 10;
    private static final int BG = 0xF0101010;
    private static final int BORDER = 0xFF4A90D9;
    private static final int SEL_BG = 0xC02B6CB0;
    private static final int HINT = 0xFF888888;
    private static final int GHOST = 0xFF707070;
    private static final int CHIP = 0xFFB07CE8;
    private static final int GOLD = 0xFFFFD700;
    private static final int HELP_BG = 0xF0181818;
    private static final int HELP_LINES = 8;
    private static final long AUTO_FOCUS_WINDOW_MS = 400L;
    private static final String NONE = "\u0000";

    private List<SuggestionEngine.Page> pages = new ArrayList<>();
    private List<Suggestion> searchItems = new ArrayList<>();
    private Suggestion.Source filter = null;
    private int page = 0;
    private String lastInput = NONE;
    private String completion = "";
    private int hovered = -1;
    private boolean visible = false;
    private int px, py, pw, ph, rowTop;
    private int chipX, chipW, helpX;
    private boolean helpHovered = false;

    private GuiScreen trackedGui;
    private long guiOpenedAt;
    private boolean wasFocused = false;
    private boolean activated = false;
    private boolean clickedSearch = false;
    private String focusText = "";

    private SuggestOverlay() {}

    private static int rows() {
        return ModConfig.panel.rowsPerPage;
    }

    private List<Suggestion> current() {
        if (pages.isEmpty()) return new ArrayList<>();
        return pages.get(Math.max(0, Math.min(page, pages.size() - 1)))
            .items();
    }

    private String currentLabel() {
        if (pages.isEmpty()) return "";
        return pages.get(Math.max(0, Math.min(page, pages.size() - 1)))
            .label();
    }

    private void hide() {
        visible = false;
        lastInput = NONE;
        completion = "";
        helpHovered = false;
        hovered = -1;
    }

    private void rebuild(String input, boolean force) {
        if (!force && input.equals(lastInput)) return;
        if (!input.equals(lastInput)) {
            page = 0;
            if (input.isBlank()) filter = null;
        }
        lastInput = input;
        try {
            if (input.isBlank()) {
                pages = SuggestionEngine.browsePages();
                searchItems = new ArrayList<>();
            } else {
                searchItems = SuggestionEngine.filter(input, 64);
                pages = chunkSearch();
            }
        } catch (Throwable t) {
            pages = new ArrayList<>();
            NEIGuess.LOG.warn("Building suggestions failed", t);
        }
        completion = computeCompletion(input);
        if (page >= pages.size()) page = 0;
        hovered = -1;
    }

    private List<SuggestionEngine.Page> chunkSearch() {
        List<Suggestion> list = new ArrayList<>();
        for (Suggestion s : searchItems) if (filter == null || s.source() == filter) list.add(s);
        List<SuggestionEngine.Page> out = new ArrayList<>();
        int rows = rows();
        int total = (list.size() + rows - 1) / rows;
        String label = NEIGuess.tr("page.search");
        for (int i = 0; i < total; i++) {
            List<Suggestion> chunk = new ArrayList<>(list.subList(i * rows, Math.min((i + 1) * rows, list.size())));
            out.add(new SuggestionEngine.Page(total > 1 ? label + " " + (i + 1) + "/" + total : label, chunk));
        }
        return out;
    }

    private void cycleFilter() {
        List<Suggestion.Source> present = SuggestionEngine.sourcesOf(searchItems);
        if (present.isEmpty()) {
            filter = null;
            return;
        }
        int idx = filter == null ? -1 : present.indexOf(filter);
        filter = idx + 1 >= present.size() ? null : present.get(idx + 1);
        page = 0;
        rebuild(lastInput, true);
    }

    private static String filterName(Suggestion.Source s) {
        if (s == null) return NEIGuess.tr("filter.all");
        return s.label()
            .isEmpty() ? NEIGuess.tr("filter.item") : s.label();
    }

    private void turnPage(int delta) {
        int n = pages.size();
        if (n <= 1) return;
        page = ((page + delta) % n + n) % n;
        hovered = -1;
    }

    private String computeCompletion(String input) {
        if (input == null || input.isBlank() || !ModConfig.panel.ghostText) return "";
        String key = input.toLowerCase(Locale.ROOT);
        for (Suggestion s : searchItems) {
            String d = s.display();
            if (d.length() > input.length() && d.toLowerCase(Locale.ROOT)
                .startsWith(key)) return d;
        }
        return "";
    }

    private static String fit(FontRenderer font, String text, int maxWidth) {
        if (text == null || maxWidth <= 0) return "";
        if (font.getStringWidth(text) <= maxWidth) return text;
        String ellipsis = "…";
        return font.trimStringToWidth(text, maxWidth - font.getStringWidth(ellipsis)) + ellipsis;
    }

    @Override
    public void onPreDraw(GuiContainer gui) {
        if (gui != trackedGui) {
            trackedGui = gui;
            guiOpenedAt = System.currentTimeMillis();
            activated = false;
            wasFocused = false;
        }
        boolean focused = NeiBridge.isSearchFocused();
        String text = NeiBridge.text();
        if (focused && !wasFocused) {
            boolean auto = System.currentTimeMillis() - guiOpenedAt < AUTO_FOCUS_WINDOW_MS;
            activated = clickedSearch || !auto || ModConfig.panel.showOnAutoFocus;
            focusText = text;
        } else if (focused && !activated && !text.equals(focusText)) {
            activated = true;
        } else if (!focused && wasFocused) {
            activated = false;
            if (!text.isBlank()) SearchHistoryStore.get()
                .recordSearch(text);
        }
        clickedSearch = false;
        wasFocused = focused;
    }

    @Override
    public void renderObjects(GuiContainer gui, int mousex, int mousey) {}

    @Override
    public void postRenderObjects(GuiContainer gui, int mx, int my) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!ModConfig.panel.enabled || mc.thePlayer == null || !activated || !NeiBridge.isSearchFocused()) {
            hide();
            return;
        }
        String input = NeiBridge.text();
        rebuild(input, false);
        FontRenderer font = mc.fontRenderer;

        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0, 250);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        try {
            if (!completion.isEmpty()) drawGhost(font, input);
            List<Suggestion> vis = current();
            if (vis.isEmpty()) {
                visible = false;
                return;
            }
            visible = true;
            drawPanel(gui, font, vis, input, mx, my);
        } finally {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glPopMatrix();
        }
    }

    private void drawPanel(GuiContainer gui, FontRenderer font, List<Suggestion> vis, String input, int mx, int my) {
        String head = EnumChatFormatting.AQUA + "NEI Guess"
            + (pages.size() > 1 ? EnumChatFormatting.YELLOW + " " + (page + 1) + "/" + pages.size() : "");
        String chip = "[" + (input.isBlank() ? currentLabel() : filterName(filter)) + "]";
        int w = Math.max(
            ModConfig.panel.panelWidth,
            PAD * 4 + font.getStringWidth(head) + 6 + font.getStringWidth(chip) + 6 + font.getStringWidth("?") + 6);
        for (Suggestion s : vis) {
            String tag = s.label()
                .isEmpty() ? "" : "[" + s.label() + "] ";
            int badge = s.badge()
                .isEmpty() || s.badgeOnHover() ? 0 : font.getStringWidth(s.badge()) + 4;
            w = Math.max(w, PAD * 2 + font.getStringWidth(tag) + font.getStringWidth(s.display()) + badge + 10);
        }
        w = Math.min(w, Math.max(120, gui.width - 8));
        int h = vis.size() * LINE_H + PAD * 2 + HEADER_H;

        GuiTextField inputBox = NeiBridge.innerBox();
        if (inputBox != null) {
            int searchLeft = inputBox.xPosition;
            px = Math.max(2, Math.min(searchLeft, gui.width - w - 2));
            py = inputBox.yPosition - h - 3;
            if (py < 2) py = inputBox.yPosition + inputBox.height + 3;
        } else {
            px = (gui.width - w) / 2;
            py = gui.height - h - 40;
        }
        pw = w;
        ph = h;
        rowTop = py + PAD + HEADER_H;

        hovered = -1;
        if (ModConfig.panel.hoverSelects && inside(mx, my)) {
            int local = Math.floorDiv(my - rowTop, LINE_H);
            if (local >= 0 && local < vis.size()) hovered = local;
        }

        Gui.drawRect(px - 1, py - 1, px + w + 1, py + h + 1, BORDER);
        Gui.drawRect(px, py, px + w, py + h, BG);
        int cy = py + PAD;
        int cx = px + PAD;
        font.drawString(head, cx, cy, HINT);
        cx += font.getStringWidth(head) + 6;
        chipX = cx;
        chipW = font.getStringWidth(chip);
        boolean chipHover = mx >= chipX - 2 && mx <= chipX + chipW + 2 && my >= cy - 2 && my <= cy + HEADER_H;
        font.drawString(chip, chipX, cy, chipHover ? GOLD : CHIP);
        helpX = px + w - PAD - font.getStringWidth("?");
        helpHovered = mx >= helpX - 3 && mx <= helpX + font.getStringWidth("?") + 3 && my >= cy - 3 && my <= cy + 11;
        font.drawString("?", helpX, cy, helpHovered ? GOLD : HINT);

        int y = rowTop;
        int nameRight = px + w - PAD - 2;
        for (int i = 0; i < vis.size(); i++) {
            Suggestion s = vis.get(i);
            boolean sel = i == hovered;
            if (sel) Gui.drawRect(px + 1, y - 1, px + w - 1, y + LINE_H - 1, SEL_BG);
            int textX = px + PAD;
            if (!s.label()
                .isEmpty()) {
                String tag = "[" + s.label() + "]";
                font.drawString(tag, textX, y + 2, s.color());
                textX += font.getStringWidth(tag) + 3;
            }
            boolean showBadge = !s.badge()
                .isEmpty() && (!s.badgeOnHover() || sel);
            int badgeW = showBadge ? font.getStringWidth(s.badge()) + 4 : 0;
            String name = fit(font, s.display(), nameRight - textX - badgeW);
            font.drawString(name, textX, y + 2, sel ? 0xFFFFFFFF : s.color());
            if (showBadge) font.drawString(s.badge(), nameRight - font.getStringWidth(s.badge()), y + 2, HINT);
            y += LINE_H;
        }
        if (helpHovered) drawHelp(gui, font, mx, my);
    }

    private void drawHelp(GuiContainer gui, FontRenderer font, int mx, int my) {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= HELP_LINES; i++) lines.add(NEIGuess.tr("help." + i));
        int w = 0;
        for (String s : lines) w = Math.max(w, font.getStringWidth(s));
        w += PAD * 2 + 2;
        int h = lines.size() * (LINE_H - 2) + PAD * 2 + 2;
        int x = Math.max(2, Math.min(mx + 8, gui.width - w - 2));
        int y = Math.max(2, Math.min(my + 8, gui.height - h - 2));
        Gui.drawRect(x - 1, y - 1, x + w + 1, y + h + 1, BORDER);
        Gui.drawRect(x, y, x + w, y + h, HELP_BG);
        int ty = y + PAD + 1;
        for (String s : lines) {
            font.drawString(s, x + PAD, ty, 0xFFDDDDDD);
            ty += LINE_H - 2;
        }
    }

    private void drawGhost(FontRenderer font, String input) {
        GuiTextField box = NeiBridge.innerBox();
        if (box == null || box.getCursorPosition() != input.length()) return;
        String rest = completion.substring(Math.min(input.length(), completion.length()));
        if (rest.isEmpty()) return;
        int x = box.xPosition + 4 + font.getStringWidth(input);
        if (x + font.getStringWidth(rest) > box.xPosition + box.width - 4) return;
        int y = box.yPosition + (box.height - 8) / 2;
        font.drawString(rest, x, y, GHOST);
    }

    @Override
    public void renderSlotUnderlay(GuiContainer gui, Slot slot) {}

    @Override
    public void renderSlotOverlay(GuiContainer gui, Slot slot) {}

    private boolean inside(int mx, int my) {
        return mx >= px && mx <= px + pw && my >= py && my <= py + ph;
    }

    private int rowAt(int mx, int my) {
        if (!visible || !inside(mx, my)) return -1;
        int local = Math.floorDiv(my - rowTop, LINE_H);
        return local >= 0 && local < current().size() ? local : -1;
    }

    private void apply(Suggestion s) {
        String q = SuggestionEngine.fixQuery(s.query());
        NeiBridge.setText(q);
        SearchHistoryStore.get()
            .record(q);
        page = 0;
        filter = null;
        hide();
        if (ModConfig.panel.closeAfterApply) {
            focusText = q;
            NeiBridge.setFocus(false);
        } else {
            NeiBridge.setFocus(true);
            focusText = q;
        }
    }

    @Override
    public boolean mouseClicked(GuiContainer gui, int mx, int my, int button) {
        if (!visible || !inside(mx, my)) return false;
        // NEI unfocused the search box when the click landed outside it; the panel belongs to the box.
        NeiBridge.setFocus(true);
        if (helpHovered) return true;
        if (mx >= chipX - 2 && mx <= chipX + chipW + 2 && my >= py + PAD - 2 && my <= py + PAD + HEADER_H) {
            if (NeiBridge.text()
                .isBlank()) turnPage(1);
            else cycleFilter();
            return true;
        }
        int idx = rowAt(mx, my);
        if (idx < 0) return true;
        Suggestion s = current().get(idx);
        if (button == 0) {
            apply(s);
        } else if (button == 1) {
            SearchHistoryStore store = SearchHistoryStore.get();
            if (GuiScreen.isShiftKeyDown()) store.forget(s.display());
            else store.toggleFavorite(s.display());
            rebuild(lastInput, true);
        }
        return true;
    }

    @Override
    public void onMouseClicked(GuiContainer gui, int mx, int my, int button) {
        if (NeiBridge.searchContains(mx, my)) {
            activated = true;
            clickedSearch = true;
            return;
        }
        if (visible && inside(mx, my)) return;
        if (button == 0 || button == 1) recordPick(mx, my);
    }

    private static void recordPick(int mx, int my) {
        String query = NeiBridge.text();
        if (query.isBlank() || !NeiBridge.isSearchVisible()) return;
        ItemStack stack = NeiBridge.panelStackAt(mx, my);
        String key = ItemIndexes.keyOf(stack);
        if (key == null) return;
        String name = EnumChatFormatting.getTextWithoutFormattingCodes(stack.getDisplayName());
        SearchHistoryStore.get()
            .recordPick(query, key, name);
    }

    @Override
    public void onMouseUp(GuiContainer gui, int mx, int my, int button) {}

    @Override
    public boolean mouseScrolled(GuiContainer gui, int mx, int my, int scrolled) {
        if (!visible || !inside(mx, my) || pages.size() <= 1) return false;
        turnPage(scrolled > 0 ? -1 : 1);
        return true;
    }

    @Override
    public void onMouseScrolled(GuiContainer gui, int mx, int my, int scrolled) {}

    @Override
    public void onMouseDragged(GuiContainer gui, int mx, int my, int button, long heldTime) {}

    private static int pagingDirection(int key) {
        for (String name : ModConfig.panel.pagingKeys) {
            if (name == null || Keyboard.getKeyIndex(
                name.trim()
                    .toUpperCase(Locale.ROOT))
                != key) continue;
            String n = name.toUpperCase(Locale.ROOT);
            if (n.contains("UP") || n.contains("LEFT") || n.contains("PRIOR")) return 0;
            if (n.contains("DOWN") || n.contains("RIGHT") || n.contains("NEXT")) return 1;
        }
        return -1;
    }

    @Override
    public boolean keyTyped(GuiContainer gui, char keyChar, int key) {
        if (!NeiBridge.isSearchFocused()) return false;
        String input = NeiBridge.text();
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            List<Suggestion> vis = current();
            if (visible && hovered >= 0 && hovered < vis.size()) {
                apply(vis.get(hovered));
                return true;
            }
            if (!input.isBlank()) SearchHistoryStore.get()
                .recordSearch(input);
            return false;
        }
        if (!visible) return false;
        if (key == Keyboard.KEY_TAB) {
            if (GuiScreen.isCtrlKeyDown()) {
                if (input.isBlank()) turnPage(1);
                else cycleFilter();
                return true;
            }
            if (!input.isBlank() && !completion.isEmpty()) {
                NeiBridge.setText(completion);
                focusText = completion;
                return true;
            }
            return false;
        }
        int dir = pagingDirection(key);
        if (dir >= 0) {
            boolean arrow = key == Keyboard.KEY_LEFT || key == Keyboard.KEY_RIGHT;
            if (arrow && !input.isBlank() && ModConfig.panel.pagingNeedsEmptyInput) return false;
            if (pages.size() <= 1) return false;
            turnPage(dir == 1 ? 1 : -1);
            return true;
        }
        return false;
    }

    @Override
    public void onKeyTyped(GuiContainer gui, char keyChar, int key) {
        if (NeiBridge.isSearchFocused()) return;
        try {
            if (key != NEIClientConfig.getKeyBinding("gui.recipe") && key != NEIClientConfig.getKeyBinding("gui.usage"))
                return;
            java.awt.Point p = GuiDraw.getMousePosition();
            recordPick(p.x, p.y);
        } catch (Throwable ignored) {}
    }

    @Override
    public List<String> handleTooltip(GuiContainer gui, int mx, int my, List<String> currenttip) {
        if (!visible || !inside(mx, my)) return currenttip;
        List<String> out = new ArrayList<>();
        int idx = helpHovered ? -1 : rowAt(mx, my);
        if (idx >= 0) {
            Suggestion s = current().get(idx);
            if (!s.query()
                .equals(s.display())) {
                out.add(EnumChatFormatting.GRAY + NEIGuess.tr("tooltip.query", EnumChatFormatting.WHITE + s.query()));
            }
        }
        return out;
    }

    @Override
    public List<String> handleItemTooltip(GuiContainer gui, ItemStack stack, int mx, int my, List<String> currenttip) {
        return visible && inside(mx, my) ? new ArrayList<>() : currenttip;
    }

    @Override
    public Map<String, String> handleHotkeys(GuiContainer gui, int mx, int my, Map<String, String> hotkeys) {
        return visible && inside(mx, my) ? new HashMap<>() : hotkeys;
    }

    @Override
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyID) {
        return false;
    }
}
