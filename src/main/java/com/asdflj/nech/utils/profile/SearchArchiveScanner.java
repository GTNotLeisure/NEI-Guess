package com.asdflj.nech.utils.profile;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.asdflj.nech.core.transform.SearchPatches;
import com.asdflj.nech.core.transform.SearchPredicates;
import com.asdflj.nech.core.transform.SearchPredicates.Kind;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class SearchArchiveScanner {

    private static final Logger LOGGER = LogManager.getLogger("NECH Search Profiler");
    private static final String VERSION_PREFIX = "META-INF/versions/";
    private final int javaVersion;

    public SearchArchiveScanner(int javaVersion) {
        this.javaVersion = javaVersion;
    }

    public List<ArchiveProfile> scan(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        List<Path> archives;
        try (Stream<Path> files = Files.walk(directory)) {
            archives = files.filter(Files::isRegularFile)
                .filter(
                    path -> path.getFileName()
                        .toString()
                        .endsWith(".jar"))
                .sorted()
                .toList();
        }
        List<ArchiveProfile> results = new ArrayList<>();
        for (Path archive : archives) {
            try (JarFile jar = new JarFile(archive.toFile())) {
                ArchiveProfile result = scan(jar);
                if (result != null) results.add(result);
            } catch (IOException | RuntimeException failure) {
                LOGGER.warn("Unable to scan search predicates in {}", archive, failure);
            }
        }
        return List.copyOf(results);
    }

    private ArchiveProfile scan(JarFile jar) throws IOException {
        Map<Kind, List<String>> methods = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) methods.put(kind, new ArrayList<>());
        Manifest manifest = jar.getManifest();
        boolean multiRelease = manifest != null && "true".equalsIgnoreCase(
            manifest.getMainAttributes()
                .getValue("Multi-Release"));
        for (ZipEntry entry : effectiveClasses(jar, multiRelease).values()) {
            if (entry.getSize() > Integer.MAX_VALUE - 8L) {
                LOGGER.warn("Skipping oversized class {} in {}", entry.getName(), jar.getName());
                continue;
            }
            try (InputStream input = jar.getInputStream(entry)) {
                scanClass(input, methods);
            } catch (IOException | RuntimeException failure) {
                LOGGER.warn("Unable to scan class {} in {}", entry.getName(), jar.getName(), failure);
            }
        }
        if (methods.values()
            .stream()
            .allMatch(List::isEmpty)) return null;
        return new ArchiveProfile(
            jar.getName(),
            readMetadata(jar),
            methods.get(Kind.STRING),
            methods.get(Kind.REGEX),
            methods.get(Kind.KOTLIN));
    }

    private Map<String, ZipEntry> effectiveClasses(JarFile jar, boolean multiRelease) {
        Map<String, ZipEntry> selected = new TreeMap<>();
        Map<String, Integer> versions = new TreeMap<>();
        Enumeration<? extends ZipEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() || !name.endsWith(".class")) continue;
            int version = 0;
            if (name.startsWith(VERSION_PREFIX)) {
                if (!multiRelease) continue;
                int separator = name.indexOf('/', VERSION_PREFIX.length());
                if (separator < 0) continue;
                try {
                    version = Integer.parseInt(name.substring(VERSION_PREFIX.length(), separator));
                } catch (NumberFormatException ignored) {
                    continue;
                }
                if (version < 9 || version > javaVersion) continue;
                name = name.substring(separator + 1);
            }
            if (name.startsWith("META-INF/") || name.equals("module-info.class")) continue;
            if (version > versions.getOrDefault(name, -1)) {
                versions.put(name, version);
                selected.put(name, entry);
            }
        }
        return selected;
    }

    private static void scanClass(InputStream input, Map<Kind, List<String>> methods) throws IOException {
        byte[] bytes = input.readAllBytes();
        if (!SearchPredicates.mightContain(bytes)) return;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        String owner = node.name.replace('/', '.');
        for (MethodNode method : node.methods) {
            String selector = owner + ":" + method.name + method.desc;
            Set<Kind> recorded = EnumSet.noneOf(Kind.class);
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode invocation) {
                    record(invocation.owner, invocation.name, invocation.desc, selector, recorded, methods);
                } else if (instruction instanceof InvokeDynamicInsnNode dynamic && SearchPatches.isLambda(dynamic)) {
                    for (Object argument : dynamic.bsmArgs) {
                        if (argument instanceof Handle handle) {
                            record(handle.getOwner(), handle.getName(), handle.getDesc(), selector, recorded, methods);
                        }
                    }
                }
            }
        }
    }

    private static void record(String owner, String name, String descriptor, String selector, Set<Kind> recorded,
        Map<Kind, List<String>> methods) {
        Kind kind = SearchPredicates.classify(owner, name, descriptor);
        if (kind != null && recorded.add(kind)) methods.get(kind)
            .add(selector);
    }

    private static List<ModMetadata> readMetadata(JarFile jar) {
        ZipEntry entry = jar.getEntry("mcmod.info");
        if (entry == null) return List.of();
        try (InputStreamReader reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
            JsonElement metadata = new JsonParser().parse(reader);
            if (metadata.isJsonObject() && metadata.getAsJsonObject()
                .has("modList")) {
                metadata = metadata.getAsJsonObject()
                    .get("modList");
            }
            List<ModMetadata> mods = new ArrayList<>();
            if (metadata.isJsonArray()) {
                for (JsonElement element : metadata.getAsJsonArray()) addMetadata(element, mods);
            } else addMetadata(metadata, mods);
            return mods;
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("Unable to read mod metadata in {}", jar.getName(), failure);
            return List.of();
        }
    }

    private static void addMetadata(JsonElement element, List<ModMetadata> mods) {
        if (!element.isJsonObject()) return;
        JsonObject metadata = element.getAsJsonObject();
        List<String> authors = new ArrayList<>();
        JsonElement authorList = metadata.get("authorList");
        if (authorList != null && authorList.isJsonArray()) {
            for (JsonElement author : authorList.getAsJsonArray()) {
                if (author.isJsonPrimitive()) authors.add(author.getAsString());
            }
        }
        mods.add(
            new ModMetadata(
                string(metadata, "modid"),
                string(metadata, "name"),
                string(metadata, "version"),
                string(metadata, "mcversion"),
                string(metadata, "url"),
                authors));
    }

    private static String string(JsonObject metadata, String key) {
        JsonElement value = metadata.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }
}
