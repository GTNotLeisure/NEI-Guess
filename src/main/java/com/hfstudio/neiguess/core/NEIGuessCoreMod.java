package com.hfstudio.neiguess.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.asdflj.nech.NechConfig;
import com.asdflj.nech.core.NechClassTransformer;
import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import com.gtnewhorizon.gtnhmixins.builders.IBaseTransformer.Phase;
import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.hfstudio.neiguess.mixins.MixinConfiguration;
import com.hfstudio.neiguess.mixins.Mixins;

import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.IFMLCallHook;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.MCVersion;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.Name;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions;

@TransformerExclusions({ "com.asdflj.nech.core", "com.hfstudio.neiguess.core" })
@MCVersion("1.7.10")
@Name("NEIGuessCoreMod")
public class NEIGuessCoreMod implements IFMLLoadingPlugin, IFMLCallHook, IEarlyMixinLoader {

    public static final Logger LOGGER = LogManager.getLogger("NEIGuessCoreMod");
    public static volatile boolean INITIALIZED = false;

    public NEIGuessCoreMod() {}

    public String[] getASMTransformerClass() {
        return FMLLaunchHandler.side()
            .isClient() ? new String[] { "com.asdflj.nech.core.NechClassTransformer" } : new String[0];
    }

    public String getModContainerClass() {
        return null;
    }

    public String getSetupClass() {
        return FMLLaunchHandler.side()
            .isClient() ? NEIGuessCoreMod.class.getName() : null;
    }

    public void injectData(Map<String, Object> data) {}

    public String getAccessTransformerClass() {
        return null;
    }

    public Void call() {
        NechConfig.registerConfig();
        NechClassTransformer.configure();
        INITIALIZED = true;
        return null;
    }

    @Override
    public String getMixinConfig() {
        return MixinConfiguration.forPhase(Phase.EARLY);
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        return IMixins.getEarlyMixins(Mixins.class, loadedCoreMods);
    }
}
