package com.hfstudio.neiguess.client;

import java.util.List;

import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.oredict.OreDictionary;

import com.hfstudio.neiguess.NEIGuess;
import com.hfstudio.neiguess.client.quest.QuestTracker;
import com.hfstudio.neiguess.config.ModConfig;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

public final class ClientEvents {

    public static final ClientEvents INSTANCE = new ClientEvents();
    private static final int MAX_LENGTH = 512;

    private volatile boolean disconnected = false;

    private ClientEvents() {}

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (disconnected) {
            disconnected = false;
            SearchHistoryStore.get()
                .save();
            QuestTracker.invalidate();
        }
        SearchHistoryStore.get()
            .flushIfDirty();
        QuestTracker.tick();
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        disconnected = true;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onChat(ClientChatReceivedEvent event) {
        IChatComponent msg = event.message;
        if (msg == null || !ItemIndexes.isBuilt()) return;
        String plain = msg.getUnformattedText();
        if (plain.length() < 2 || plain.length() > MAX_LENGTH) return;
        List<ItemIndexes.Hit> hits = ItemIndexes.findLinks(plain);
        if (hits.isEmpty()) return;
        boolean player = isPlayerChat(msg);
        if (player && ModConfig.sources.chat) recordMentions(hits);
        if (!ModConfig.chat.links || !player && !ModConfig.chat.linksOnSystemMessages) return;
        try {
            event.message = transform(msg);
        } catch (Throwable t) {
            NEIGuess.LOG.info("Could not add chat links: {}", t.toString());
        }
    }

    private static boolean isPlayerChat(IChatComponent msg) {
        if (!(msg instanceof ChatComponentTranslation t)) return false;
        String key = t.getKey();
        return key.startsWith("chat.type.text") || key.startsWith("chat.type.emote")
            || key.equals("commands.message.display.incoming");
    }

    private static void recordMentions(List<ItemIndexes.Hit> hits) {
        int n = 0;
        for (ItemIndexes.Hit h : hits) {
            ItemIndexes.Link link = h.link();
            if (link.kind() != ItemIndexes.LinkKind.ITEM || link.key()
                .indexOf(':') >= 0) continue;
            SearchHistoryStore.get()
                .recordChatMention(link.key());
            if (++n >= 3) break;
        }
    }

    private static IChatComponent transform(IChatComponent c) {
        IChatComponent out;
        if (c instanceof ChatComponentTranslation t) {
            Object[] args = t.getFormatArgs()
                .clone();
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof IChatComponent arg) args[i] = transform(arg);
                else if (args[i] instanceof String s) args[i] = transform(new ChatComponentText(s));
            }
            out = new ChatComponentTranslation(t.getKey(), args);
            out.setChatStyle(
                t.getChatStyle()
                    .createShallowCopy());
        } else if (c instanceof ChatComponentText text) {
            out = segment(
                text.getChatComponentText_TextValue(),
                c.getChatStyle()
                    .createShallowCopy());
        } else {
            out = c.createCopy();
            out.getSiblings()
                .clear();
        }
        for (Object sib : c.getSiblings()) out.appendSibling(transform((IChatComponent) sib));
        return out;
    }

    private static IChatComponent segment(String text, ChatStyle style) {
        IChatComponent root = new ChatComponentText("");
        root.setChatStyle(style);
        boolean keepExisting = ModConfig.chat.keepExistingClicks && style.getChatClickEvent() != null;
        List<ItemIndexes.Hit> hits = keepExisting ? List.of() : ItemIndexes.findLinks(text);
        int pos = 0;
        for (ItemIndexes.Hit h : hits) {
            if (h.start() > pos) root.appendSibling(new ChatComponentText(text.substring(pos, h.start())));
            root.appendSibling(link(text.substring(h.start(), h.end()), h.link()));
            pos = h.end();
        }
        if (pos < text.length()) root.appendSibling(new ChatComponentText(text.substring(pos)));
        return root;
    }

    private static IChatComponent link(String shown, ItemIndexes.Link link) {
        ChatStyle style = new ChatStyle().setUnderlined(true)
            .setColor(EnumChatFormatting.AQUA)
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, GuessCommand.linkCommand(link)));
        HoverEvent hover = hoverOf(link);
        if (hover != null) style.setChatHoverEvent(hover);
        ChatComponentText comp = new ChatComponentText(shown);
        comp.setChatStyle(style);
        return comp;
    }

    private static HoverEvent hoverOf(ItemIndexes.Link link) {
        if (!ModConfig.chat.hoverPreview) return null;
        ItemStack stack = switch (link.kind()) {
            case ITEM -> ItemIndexes.stackOfKey(link.target());
            case ORE -> {
                List<ItemStack> ores = OreDictionary.getOres(link.target());
                yield ores.isEmpty() ? null : ores.get(0);
            }
            case MOD -> null;
        };
        if (stack == null) {
            String text = link.kind() == ItemIndexes.LinkKind.MOD ? Lexicon.modDisplay(link.target()) : link.key();
            return new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ChatComponentText(text + "\n" + NEIGuess.tr("chat.hint")));
        }
        ItemStack copy = stack.copy();
        if (copy.getItemDamage() == OreDictionary.WILDCARD_VALUE) copy.setItemDamage(0);
        NBTTagCompound tag = new NBTTagCompound();
        copy.writeToNBT(tag);
        return new HoverEvent(HoverEvent.Action.SHOW_ITEM, new ChatComponentText(tag.toString()));
    }
}
