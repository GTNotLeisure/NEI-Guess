package com.asdflj.nech;

import java.util.Objects;

import com.asdflj.nech.core.transform.SearchTargets;
import com.asdflj.nech.utils.Match;
import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

import me.towdium.pinin.Keyboard;

@Config(modid = NeverEnoughCharacters.MODID, filename = "NeverEnoughCharacters", category = "general")
@Config.LangKeyPattern(pattern = "nech.config.%field")
@Config.LangKey("nech.config.general")
@Config.Comment("Client-side phonetic search and ASM integration")
public class NechConfig {

    @Config.DefaultBoolean(false)
    @Config.Reloadable("runtime")
    @Config.Comment("Log every class changed by the client search transformer.")
    @Config.LangKey("nech.config.debug.verbose")
    public static boolean enableVerbose = false;

    @Config.DefaultBoolean(true)
    @Config.Reloadable("runtime")
    @Config.Comment("Match Zh and Z interchangeably.")
    @Config.LangKey("nech.config.fuzzy.zh_z")
    public static boolean EnableFZh2Z = true;

    @Config.DefaultBoolean(true)
    @Config.Reloadable("runtime")
    @Config.Comment("Match Sh and S interchangeably.")
    @Config.LangKey("nech.config.fuzzy.sh_s")
    public static boolean EnableFSh2S = true;

    @Config.DefaultBoolean(true)
    @Config.Reloadable("runtime")
    @Config.Comment("Match Ch and C interchangeably.")
    @Config.LangKey("nech.config.fuzzy.ch_c")
    public static boolean EnableFCh2C = true;

    @Config.DefaultBoolean(true)
    @Config.Reloadable("runtime")
    @Config.Comment("Match Ang and An interchangeably.")
    @Config.LangKey("nech.config.fuzzy.ang_an")
    public static boolean EnableFAng2An = true;

    @Config.DefaultBoolean(true)
    @Config.Reloadable("runtime")
    @Config.Comment("Match Ing and In interchangeably.")
    @Config.LangKey("nech.config.fuzzy.ing_in")
    public static boolean EnableFIng2In = true;

    @Config.DefaultBoolean(true)
    @Config.Reloadable("runtime")
    @Config.Comment("Match Eng and En interchangeably.")
    @Config.LangKey("nech.config.fuzzy.eng_en")
    public static boolean EnableFEng2En = true;

    @Config.DefaultBoolean(true)
    @Config.Reloadable("runtime")
    @Config.Comment("Match U and V interchangeably.")
    @Config.LangKey("nech.config.fuzzy.u_v")
    public static boolean EnableFU2V = true;

    @Config.DefaultEnum("QUANPIN")
    @Config.Reloadable("runtime")
    @Config.Comment("Keyboard layout used for phonetic input.")
    @Config.LangKey("nech.config.keyboard.type")
    public static Spell KeyboardType = Spell.QUANPIN;

    @Config.Name("AdditionalTransformerRegExpList")
    @Config.DefaultStringList({})
    @Config.RequiresMcRestart
    @Config.Comment("Additional regex predicates. Use class:method or class:method(JVM descriptor).")
    @Config.LangKey("nech.config.transformers.additional.regex")
    public static String[] transformerRegExpAdditionalList = new String[0];

    @Config.Name("AdditionalTransformerStringList")
    @Config.DefaultStringList({})
    @Config.RequiresMcRestart
    @Config.Comment("Additional text predicates. Use class:method or class:method(JVM descriptor).")
    @Config.LangKey("nech.config.transformers.additional.string")
    public static String[] transformerStringAdditionalList = new String[0];

    @Config.DefaultStringList({})
    @Config.RequiresMcRestart
    @Config.Comment("Excluded ASM roots and lambda implementations. Use class:method or class:*.")
    @Config.LangKey("nech.config.transformers.blacklist")
    public static String[] transformerMethodBlackList = new String[0];

    @Config.Ignore
    public static String[] defaultTransformerRegExp = SearchTargets.regexMethods();

    @Config.Ignore
    public static String[] defaultTransformerStringList = SearchTargets.stringMethods();

    @Config.Ignore
    private static boolean registered;

    private NechConfig() {}

    public static synchronized void registerConfig() {
        if (registered) return;
        ConfigurationManager.registerConfig(NechConfig.class);
        registered = true;
    }

    public static void reload() {
        ConfigurationManager.reloadConfig(NechConfig.class, "runtime");
        onConfigChanged();
    }

    public static void onConfigChanged() {
        Match.onConfigChange();
    }

    public static void setKeyboard(Spell keyboard) {
        KeyboardType = Objects.requireNonNull(keyboard, "keyboard");
        save();
        onConfigChanged();
    }

    public static void setVerbose(boolean enabled) {
        enableVerbose = enabled;
        save();
    }

    private static void save() {
        if (registered) ConfigurationManager.save(NechConfig.class);
    }

    public enum Spell {

        QUANPIN(Keyboard.QUANPIN),
        DAQIAN(Keyboard.DAQIAN),
        XIAOHE(Keyboard.XIAOHE),
        ZIRANMA(Keyboard.ZIRANMA),
        SOUGOU(Keyboard.SOUGOU),
        GUOBIAO(Keyboard.GUOBIAO),
        MICROSOFT(Keyboard.MICROSOFT),
        PINYINPP(Keyboard.PINYINPP),
        ZIGUANG(Keyboard.ZIGUANG);

        public final Keyboard keyboard;

        Spell(Keyboard keyboard) {
            this.keyboard = keyboard;
        }

        public Keyboard get() {
            return keyboard;
        }
    }
}
