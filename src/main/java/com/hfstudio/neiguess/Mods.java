package com.hfstudio.neiguess;

import java.util.Locale;

import net.minecraft.util.ResourceLocation;

import com.gtnewhorizon.gtnhlib.util.data.IMod;
import com.gtnewhorizon.gtnhmixins.builders.ITargetMod;
import com.gtnewhorizon.gtnhmixins.builders.TargetModBuilder;

import cpw.mods.fml.common.Loader;

public enum Mods implements IMod, ITargetMod {

    NotEnoughItems("NotEnoughItems", "Not Enough Items", "codechicken.nei.asm.NEICorePlugin"),
    AppliedEnergistics("appliedenergistics2", "Applied Energistics 2", "appeng.transformer.AppEngCore"),
    AE2FluidCraft("ae2fc", "AE2 Fluid Crafting", "appeng.transformer.AppEngCore"),
    Controlling("controlling", "Controlling", "com.blamejared.controlling.mixins.EarlyMixinsLoader"),
    HodgePodge("hodgepodge", "Hodgepodge", "com.mitchej123.hodgepodge.core.HodgepodgeCore"),
    EtFuturumRequiem("etfuturum", "Et Futurum Requiem", "ganymedes01.etfuturum.mixinplugin.EtFuturumEarlyMixins"),
    GuideNH("guidenh", "GuideNH", "com.hfstudio.guidenh.coremod.GuideNHCoreMod"),
    BetterP2P("betterp2p", "Better P2P"),
    Angelica("angelica", "Angelica"),
    StructureLib("structurelib", "StructureLib"),
    WirelessRedstone("WR-CBE|Core", "WR-CBE Core"),
    BetterQuesting("betterquesting", "BetterQuesting"),
    BinnieCore("BinnieCore", "Binnie Core"),
    Botania("Botania", "Botania"),
    GregTech("gregtech", "GregTech", "gregtech.asm.GTCorePlugin"),
    VisualProspecting("visualprospecting", "VisualProspecting",
        "com.sinthoras.visualprospecting.mixinplugin.VisualProspectingEarlyMixinLoader"),
    TaskNH("tasknh", "TaskNH");

    public final String ID;
    public final String resourceDomain;
    public final String displayName;
    private final TargetModBuilder targetBuilder;

    Mods(String id, String displayName) {
        this(id, displayName, null);
    }

    Mods(String id, String displayName, String coreModClass) {
        this.ID = id;
        this.resourceDomain = id.toLowerCase(Locale.ROOT);
        this.displayName = displayName;
        this.targetBuilder = new TargetModBuilder().setModId(id)
            .setCoreModClass(coreModClass);
    }

    @Override
    public TargetModBuilder getBuilder() {
        return targetBuilder;
    }

    @Override
    public boolean isModLoaded() {
        return Loader.isModLoaded(ID);
    }

    @Override
    public String getID() {
        return ID;
    }

    @Override
    public String getResourceLocation() {
        return resourceDomain;
    }

    public ResourceLocation getResourceLocation(String path) {
        return new ResourceLocation(resourceDomain, path);
    }
}
