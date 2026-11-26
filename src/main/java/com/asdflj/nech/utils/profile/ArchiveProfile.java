package com.asdflj.nech.utils.profile;

import java.util.List;

public record ArchiveProfile(String file, List<ModMetadata> mods, List<String> methodsString,
    List<String> methodsRegExp, List<String> methodsStrsKt) {

    public ArchiveProfile {
        mods = List.copyOf(mods);
        methodsString = List.copyOf(methodsString);
        methodsRegExp = List.copyOf(methodsRegExp);
        methodsStrsKt = List.copyOf(methodsStrsKt);
    }
}
