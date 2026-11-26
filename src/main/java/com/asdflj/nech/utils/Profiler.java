package com.asdflj.nech.utils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.asdflj.nech.utils.profile.ArchiveProfile;
import com.asdflj.nech.utils.profile.SearchArchiveScanner;
import com.hfstudio.neiguess.Tags;

public class Profiler {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
        .withZone(ZoneOffset.UTC);

    public static Report run() {
        String specification = System.getProperty("java.specification.version", "1.8");
        int javaVersion = Integer.parseInt(specification.startsWith("1.") ? specification.substring(2) : specification);
        try {
            Report report = new Report();
            report.jars = new SearchArchiveScanner(javaVersion).scan(Paths.get("mods"));
            return report;
        } catch (IOException failure) {
            throw new UncheckedIOException("Unable to enumerate mod archives", failure);
        }
    }

    public static class Report {

        public final String version = Tags.VERSION;
        public final String mcversion = "1.7.10";
        public final String date = DATE_FORMAT.format(Instant.now());
        public List<ArchiveProfile> jars = List.of();
    }
}
