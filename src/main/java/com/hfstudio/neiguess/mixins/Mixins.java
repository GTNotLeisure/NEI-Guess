package com.hfstudio.neiguess.mixins;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;
import com.hfstudio.neiguess.Mods;

public enum Mixins implements IMixins {

    POPUP_INPUT(new MixinBuilder("Suggestion popup input capture").addClientMixins("input.MixinGuiScreenInput")
        .setPhase(Phase.EARLY)),
    FML_SEARCH(new MixinBuilder("Client configuration and mod list search").addClientMixins("search.MixinFmlSearch")
        .setPhase(Phase.EARLY)
        .addRequiredMod(Mods.HodgePodge)),
    WORLD_SEARCH(new MixinBuilder("Client world list search").addClientMixins("search.MixinWorldSearch")
        .setPhase(Phase.EARLY)
        .addRequiredMod(Mods.EtFuturumRequiem)),
    NEI_SEARCH(new MixinBuilder("NEI mod and ore search")
        .addClientMixins("search.MixinNeiSearchFilters", "search.MixinNeiTextSearch", "search.MixinNeiItemSearch")
        .setPhase(Phase.LATE)
        .addRequiredMod(Mods.NotEnoughItems)),
    AE_SEARCH(new MixinBuilder("Applied Energistics terminal search").addClientMixins("search.MixinAeSearch")
        .setPhase(Phase.LATE)
        .addRequiredMod(Mods.AppliedEnergistics)),
    KEYBIND_SEARCH(new MixinBuilder("Controlling key binding search").addClientMixins("search.MixinSearchType")
        .setPhase(Phase.LATE)
        .addRequiredMod(Mods.Controlling)),
    P2P_SEARCH(new MixinBuilder("Better P2P tunnel search").addClientMixins("search.MixinP2PSearch")
        .setPhase(Phase.LATE)
        .addRequiredMod(Mods.BetterP2P)),
    SHADER_SEARCH(new MixinBuilder("Angelica shader option prefix search").addClientMixins("search.MixinShaderSearch")
        .setPhase(Phase.LATE)
        .addRequiredMod(Mods.Angelica)),
    CHANNEL_SEARCH(new MixinBuilder("StructureLib channel suggestions").addClientMixins("search.MixinChannelSearch")
        .setPhase(Phase.LATE)
        .addRequiredMod(Mods.StructureLib)),
    FREQUENCY_SEARCH(
        new MixinBuilder("Wireless Redstone frequency name search").addClientMixins("search.MixinFrequencySearch")
            .setPhase(Phase.LATE)
            .addRequiredMod(Mods.WirelessRedstone)),
    GUIDE_SEARCH(new MixinBuilder("GuideNH document and title search")
        .addClientMixins(
            "search.MixinGuideSearch",
            "search.MixinGuideTitleSearch",
            "search.MixinGuidePreviewSearch",
            "search.MixinGuidePrefixSearch")
        .setPhase(Phase.LATE)
        .addRequiredMod(Mods.GuideNH)),
    ICON_NAMES(
        new MixinBuilder("NEI icon name overlay").addClientMixins("GuiContainerManagerMixin", "ScreenCaptureMixin")
            .setPhase(Phase.LATE)
            .addRequiredMod(Mods.NotEnoughItems));

    private final MixinBuilder builder;

    Mixins(MixinBuilder builder) {
        this.builder = builder;
    }

    @Override
    public MixinBuilder getBuilder() {
        return builder;
    }
}
