package com.hfstudio.neiguess.mixins;

import java.util.Locale;

import com.gtnewhorizon.gtnhmixins.builders.IBaseTransformer.Phase;

public class MixinConfiguration {

    private MixinConfiguration() {}

    public static String forPhase(Phase phase) {
        String specification = System.getProperty("java.specification.version", "1.8");
        int version = Integer.parseInt(specification.startsWith("1.") ? specification.substring(2) : specification);
        String suffix = version >= 21 ? ".j21" : version >= 17 ? ".j17" : "";
        return "mixins.neiguess." + phase.name()
            .toLowerCase(Locale.ROOT) + suffix + ".json";
    }
}
