package com.hfstudio.neiguess.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.Config.Comment;
import com.gtnewhorizon.gtnhlib.config.Config.DefaultBoolean;
import com.gtnewhorizon.gtnhlib.config.Config.DefaultInt;
import com.gtnewhorizon.gtnhlib.config.Config.DefaultStringList;
import com.gtnewhorizon.gtnhlib.config.Config.RangeInt;
import com.gtnewhorizon.gtnhlib.config.Config.RequiresMcRestart;
import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.hfstudio.neiguess.NEIGuess;

@Config(modid = NEIGuess.MODID, filename = "neiguess", configSubDirectory = "neiguess")
@Config.LangKeyPattern(pattern = "neiguess.gui.config.%cat.%field", fullyQualified = true)
@Comment("NEI Guess configuration")
public class ModConfig {

    public static void registerConfig() throws ConfigException {
        ConfigurationManager.registerConfig(ModConfig.class);
    }

    public static final Panel panel = new Panel();
    public static final Quota quota = new Quota();
    public static final Sources sources = new Sources();
    public static final Chat chat = new Chat();
    public static final Words words = new Words();
    public static final Debug debug = new Debug();

    @Comment("Suggestion panel shown above the NEI search box")
    public static class Panel {

        @Comment("Master switch. false = the panel never opens (chat links are controlled separately)")
        @DefaultBoolean(true)
        public boolean enabled = true;

        @Comment("Rows per page; longer lists are split into pages")
        @DefaultInt(8)
        @RangeInt(min = 3, max = 20)
        public int rowsPerPage = 8;

        @Comment("Minimum panel width in pixels (grows with content)")
        @DefaultInt(160)
        @RangeInt(min = 80, max = 400)
        public int panelWidth = 160;

        @Comment("Hovering a row selects it, Enter then searches it")
        @DefaultBoolean(true)
        public boolean hoverSelects = true;

        @Comment("Show grey completion text inside the search box, Tab accepts it")
        @DefaultBoolean(true)
        public boolean ghostText = true;

        @Comment("Suggest a correction (edit distance <= 2) when nothing matches")
        @DefaultBoolean(true)
        public boolean correction = true;

        @Comment("Typing @ or $ lists mods / ore dictionary entries to teach the NEI prefix syntax")
        @DefaultBoolean(true)
        public boolean syntaxHints = true;

        @Comment("true = only prefix matches (browser-like), false = substring matches too")
        @DefaultBoolean(false)
        public boolean prefixOnly = false;

        @Comment({ "Left/Right only turn pages while the search box is empty,",
            "so they keep moving the text cursor while typing. PageUp/PageDown always work" })
        @DefaultBoolean(true)
        public boolean pagingNeedsEmptyInput = true;

        @Comment("Paging keys as LWJGL key names (PRIOR = PageUp, NEXT = PageDown)")
        @DefaultStringList({ "LEFT", "RIGHT", "PRIOR", "NEXT" })
        public String[] pagingKeys = { "LEFT", "RIGHT", "PRIOR", "NEXT" };

        @Comment({ "Also open the panel when NEI focuses the search box by itself on GUI open.",
            "false = it only opens after you click the box or start typing" })
        @DefaultBoolean(false)
        public boolean showOnAutoFocus = false;

        @Comment("Leave the search box after clicking a suggestion so the results are fully visible")
        @DefaultBoolean(true)
        public boolean closeAfterApply = true;

        @Comment("How many search words are kept locally")
        @DefaultInt(200)
        @RangeInt(min = 20, max = 2000)
        public int maxHistory = 200;
    }

    @Comment("How many rows each source gets on the first (mixed) page")
    public static class Quota {

        @DefaultInt(3)
        @RangeInt(min = 0, max = 30)
        public int recent = 3;

        @DefaultInt(3)
        @RangeInt(min = 0, max = 30)
        public int quest = 3;

        @DefaultInt(1)
        @RangeInt(min = 0, max = 30)
        public int frequent = 1;

        @Comment("0 = chat mentions only appear on their own page")
        @DefaultInt(0)
        @RangeInt(min = 0, max = 30)
        public int chat = 0;

        @DefaultInt(1)
        @RangeInt(min = 0, max = 30)
        public int random = 1;

        @Comment("Favorites are always listed first and do not count against the page")
        @DefaultInt(2)
        @RangeInt(min = 0, max = 30)
        public int favorite = 2;

        @Comment("Items you opened after searching the same word before (only while typing)")
        @DefaultInt(1)
        @RangeInt(min = 0, max = 30)
        public int pick = 1;

        @Comment({ "Source order from top to bottom: recent / quest / frequent / chat / random.",
            "Sources added by other mods can be listed by their id as well" })
        @DefaultStringList({ "recent", "quest", "random", "frequent", "chat" })
        public String[] sequence = { "recent", "quest", "random", "frequent", "chat" };

        @Comment("Quotas for sources added by other mods, format id=count")
        @DefaultStringList({})
        public String[] extra = {};
    }

    @Comment("Source switches")
    public static class Sources {

        @DefaultBoolean(true)
        public boolean recent = true;

        @Comment("Unfinished, unlocked BetterQuesting quests and the quest you last looked at")
        @DefaultBoolean(true)
        public boolean quest = true;

        @Comment("Skip quests without prerequisites (usually chapter entries and tutorials)")
        @DefaultBoolean(true)
        public boolean questSkipRoots = true;

        @DefaultBoolean(true)
        public boolean frequent = true;

        @DefaultBoolean(true)
        public boolean random = true;

        @Comment("Remember items mentioned in player chat")
        @DefaultBoolean(true)
        public boolean chat = true;
    }

    @Comment("Clickable item names in chat")
    public static class Chat {

        @Comment("Underline item names / ids / ore names / mod names in chat; click opens NEI")
        @DefaultBoolean(true)
        public boolean links = true;

        @Comment("Also process system messages (announcements, achievements)")
        @DefaultBoolean(true)
        public boolean linksOnSystemMessages = true;

        @Comment("Text that already carries a click action keeps it")
        @DefaultBoolean(true)
        public boolean keepExistingClicks = true;

        @Comment({ "Click = recipes, Ctrl+click = usages, Alt+click = copy id.",
            "Shift+click stays vanilla (insert the text into the chat box)" })
        @DefaultBoolean(true)
        public boolean modifierKeys = true;

        @Comment("Hovering a linked item shows its tooltip")
        @DefaultBoolean(true)
        public boolean hoverPreview = true;

        @Comment("English words must stand alone (Coal does not match inside Coalition)")
        @DefaultBoolean(true)
        public boolean asciiWordBoundary = true;
    }

    @Comment("Word lists. Larger lists live in config/neiguess/lexicon/*.json")
    public static class Words {

        @Comment({ "Recommended words, one is picked at random for the mixed page.",
            "Mod ids, mod names, item names or raw NEI queries ($circuit, @GregTech) all work" })
        @DefaultStringList({ "gregtech", "appliedenergistics2", "Thaumcraft", "Botania", "EnderIO", "IC2", "Avaritia",
            "$circuit" })
        public String[] recommendWords = { "gregtech", "appliedenergistics2", "Thaumcraft", "Botania", "EnderIO", "IC2",
            "Avaritia", "$circuit" };

        @Comment("Extra mod aliases, format alias=modid")
        @DefaultStringList({})
        public String[] modAliases = {};

        @Comment("Extra ore dictionary aliases, format alias=$oreName (regex fragments allowed)")
        @DefaultStringList({})
        public String[] oreAliases = {};
    }

    @Comment("Debug section")
    public static class Debug {

        @Comment("Enable Debug Print Log")
        @DefaultBoolean(false)
        @RequiresMcRestart
        public boolean enableDebugMode = false;
    }
}
