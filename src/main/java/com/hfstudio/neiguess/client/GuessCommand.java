package com.hfstudio.neiguess.client;

import static com.hfstudio.neiguess.NEIGuess.tr;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;

import com.hfstudio.neiguess.NEIGuess;
import com.hfstudio.neiguess.client.quest.QuestTracker;
import com.hfstudio.neiguess.config.ModConfig;

public final class GuessCommand extends CommandBase {

    public static final String NAME = "neiguess";
    private static final String LINK = "link";
    private static final int HELP_LINES = 8;
    private static final List<String> SUBCOMMANDS = Arrays
        .asList("show", "quest", "fav", "ban", "unban", "stats", "export", "reload", "help");

    static String linkCommand(ItemIndexes.Link link) {
        return "/" + NAME
            + " "
            + LINK
            + " "
            + link.kind()
                .name()
                .toLowerCase(Locale.ROOT)
            + " "
            + link.target();
    }

    @Override
    public String getCommandName() {
        return NAME;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/" + NAME + " <" + String.join("|", SUBCOMMANDS) + ">";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsFromIterableMatchingLastWord(args, SUBCOMMANDS);
        if (args.length == 2 && args[0].equalsIgnoreCase("unban")) {
            List<String> words = SearchHistoryStore.get()
                .allBanned();
            words.add(0, "all");
            return getListOfStringsFromIterableMatchingLastWord(args, words);
        }
        return null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        String rest = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "";
        SearchHistoryStore store = SearchHistoryStore.get();
        switch (sub) {
            case LINK -> {
                forgetSentLink();
                if (args.length >= 3) runLink(args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
            }
            case "show" -> {
                if (rest.isBlank()) message(EnumChatFormatting.RED + getCommandUsage(sender));
                else runLink("item", rest);
            }
            case "quest" -> showQuest(store);
            case "fav" -> {
                if (rest.isBlank()) return;
                boolean fav = store.toggleFavorite(rest);
                message(
                    (fav ? EnumChatFormatting.GOLD : EnumChatFormatting.GRAY)
                        + tr(fav ? "cmd.favOn" : "cmd.favOff", rest));
            }
            case "ban" -> {
                if (rest.isBlank()) return;
                store.ban(rest);
                message(EnumChatFormatting.GRAY + tr("cmd.banned", rest));
            }
            case "unban" -> {
                if (rest.equalsIgnoreCase("all"))
                    message(EnumChatFormatting.GRAY + tr("cmd.unbannedAll", store.unbanAll()));
                else if (store.unban(rest)) message(EnumChatFormatting.GRAY + tr("cmd.unbanned", rest));
                else message(EnumChatFormatting.RED + tr("cmd.notBanned", rest));
            }
            case "stats" -> message(
                tr(
                    "cmd.stats",
                    store.allWords()
                        .size(),
                    store.favoriteCount(),
                    store.allBanned()
                        .size(),
                    store.questViews()
                        .size(),
                    ItemIndexes.nameCount()));
            case "export" -> {
                try {
                    File f = store.exportCsv();
                    message(
                        EnumChatFormatting.GREEN + tr("cmd.exported", EnumChatFormatting.WHITE + f.getAbsolutePath()));
                } catch (Throwable t) {
                    message(EnumChatFormatting.RED + tr("cmd.exportFailed", t.toString()));
                }
            }
            case "reload" -> {
                Lexicon.invalidate();
                QuestTracker.invalidate();
                message(EnumChatFormatting.GREEN + tr("cmd.reloaded", Lexicon.userDir()));
            }
            default -> {
                for (int i = 1; i <= HELP_LINES; i++) message(EnumChatFormatting.GRAY + tr("cmd.help." + i));
            }
        }
    }

    private static void showQuest(SearchHistoryStore store) {
        List<SearchHistoryStore.QuestView> views = store.questViews();
        if (views.isEmpty()) {
            message(EnumChatFormatting.RED + tr("cmd.questNone"));
            return;
        }
        SearchHistoryStore.QuestView v = views.get(0);
        String title = v.title.isBlank() ? v.id : v.title;
        message(
            EnumChatFormatting.AQUA + tr("cmd.quest", EnumChatFormatting.WHITE + title, String.join(", ", v.items)));
        if (!v.items.isEmpty()) {
            String query = NeiBridge.escape(v.items.get(0));
            store.record(query);
            NeiBridge.openInventorySearch(query);
        }
    }

    private static void runLink(String kind, String target) {
        ItemIndexes.LinkKind k;
        try {
            k = ItemIndexes.LinkKind.valueOf(kind.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            message(EnumChatFormatting.RED + tr("chat.unknownItem", target));
            return;
        }
        try {
            openLink(k, target.trim());
        } catch (Throwable t) {
            NEIGuess.LOG.info("Chat link {} {} failed: {}", kind, target, t.toString());
        }
    }

    private static void openLink(ItemIndexes.LinkKind kind, String target) {
        boolean modifiers = ModConfig.chat.modifierKeys;
        boolean ctrl = modifiers && GuiScreen.isCtrlKeyDown();
        boolean alt = modifiers && (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU));
        if (alt) {
            String copied = switch (kind) {
                case ITEM -> target;
                case ORE -> "$" + target;
                case MOD -> "@" + target;
            };
            GuiScreen.setClipboardString(copied);
            message(EnumChatFormatting.GRAY + tr("chat.copied", EnumChatFormatting.WHITE + copied));
            return;
        }
        switch (kind) {
            case ITEM -> {
                ItemStack stack = ItemIndexes.stackOfKey(target);
                if (stack == null) message(EnumChatFormatting.RED + tr("chat.unknownItem", target));
                else if (!NeiBridge.openRecipes(stack, ctrl) && !NeiBridge.openRecipes(stack, !ctrl))
                    message(EnumChatFormatting.GRAY + tr("chat.noRecipe", stack.getDisplayName()));
            }
            case ORE -> NeiBridge.openInventorySearch(NeiBridge.prefixed(NeiBridge.ORE_PREFIX, target));
            case MOD -> NeiBridge.openInventorySearch(Lexicon.modQuery(target));
        }
        Minecraft.getMinecraft().ingameGUI.getChatGUI()
            .resetScroll();
    }

    private static void forgetSentLink() {
        try {
            List<String> sent = Minecraft.getMinecraft().ingameGUI.getChatGUI()
                .getSentMessages();
            if (!sent.isEmpty() && sent.get(sent.size() - 1)
                .startsWith("/" + NAME + " " + LINK + " ")) sent.remove(sent.size() - 1);
        } catch (Throwable ignored) {}
    }

    static void message(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) mc.thePlayer.addChatMessage(
            new ChatComponentText(EnumChatFormatting.AQUA + "[NEI Guess] " + EnumChatFormatting.RESET + text));
    }
}
