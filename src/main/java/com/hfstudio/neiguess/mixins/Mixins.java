package com.hfstudio.neiguess.mixins;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;
import com.hfstudio.neiguess.Mods;

public enum Mixins implements IMixins {

    MC(new MixinBuilder("Minecraft client input and search")
        .addClientMixins("input.MixinGuiScreenInput", "search.MixinCreativeSearch")
        .setPhase(Phase.EARLY)),
    FORGE(new MixinBuilder("Forge client configuration and mod list search").addClientMixins("search.MixinFmlSearch")
        .setPhase(Phase.EARLY)
        .addRequiredMod(Mods.HodgePodge)),
    ET_FUTURUM(new MixinBuilder("Et Futurum Requiem world list search").addClientMixins("search.MixinWorldSearch")
        .setPhase(Phase.EARLY)
        .addRequiredMod(Mods.EtFuturumRequiem)),
    NEI(new MixinBuilder("NEI search and item name overlay")
        .addClientMixins(
            "search.MixinNeiSearchFilters",
            "search.MixinNeiTextSearch",
            "search.MixinNeiItemSearch",
            "GuiContainerManagerMixin",
            "ScreenCaptureMixin")
        .setPhase(Phase.LATE)
        .addRequiredMod(Mods.NotEnoughItems)),
    AE2(new MixinBuilder("Applied Energistics terminal search").addClientMixins("search.MixinAeSearch")
        .setPhase(Phase.LATE)
        .addRequiredMod(Mods.AppliedEnergistics)),
    CONTROLLING(new MixinBuilder("Controlling key binding search").addClientMixins("search.MixinSearchType")
        .setPhase(Phase.LATE)
        .addRequiredMod(Mods.Controlling)),
    BETTER_P2P(new MixinBuilder("Better P2P tunnel search").addClientMixins("search.MixinP2PSearch")
        .setPhase(Phase.LATE)
        .addRequiredMod(Mods.BetterP2P)),
    ANGELICA(new MixinBuilder("Angelica shader option prefix search").addClientMixins("search.MixinShaderSearch")
        .setPhase(Phase.LATE)
        .addRequiredMod(Mods.Angelica)),
    STRUCTURE_LIB(new MixinBuilder("StructureLib channel suggestions").addClientMixins("search.MixinChannelSearch")
        .setPhase(Phase.LATE)
        .addRequiredMod(Mods.StructureLib)),
    WR_CBE(new MixinBuilder("Wireless Redstone frequency name search").addClientMixins("search.MixinFrequencySearch")
        .setPhase(Phase.LATE)
        .addRequiredMod(Mods.WirelessRedstone)),
    GUIDE_NH(new MixinBuilder("GuideNH document and title search")
        .addClientMixins(
            "search.MixinGuideSearch",
            "search.MixinGuideTitleSearch",
            "search.MixinGuidePreviewSearch",
            "search.MixinGuidePrefixSearch")
        .setPhase(Phase.LATE)
        .addRequiredMod(Mods.GuideNH));

    private final MixinBuilder builder;

    Mixins(MixinBuilder builder) {
        this.builder = builder;
    }

    @Override
    public MixinBuilder getBuilder() {
        return builder;
    }
}
