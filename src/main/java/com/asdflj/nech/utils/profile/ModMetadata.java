package com.asdflj.nech.utils.profile;

import java.util.List;

public record ModMetadata(String modid, String name, String version, String mcversion, String url,
    List<String> authorList) {

    public ModMetadata {
        authorList = List.copyOf(authorList);
    }
}
