package com.asdflj.nech.core.transform;

import java.util.List;

import com.gtnewhorizon.gtnhlib.asm.ClassConstantPoolParser;

public class SearchPredicates {

    private static final ClassConstantPoolParser CANDIDATES = new ClassConstantPoolParser(
        "contains",
        "containsIgnoreCase",
        "matches",
        "matcher");

    public static final List<InvocationRule> STRINGS = List.of(
        new InvocationRule(
            "java/lang/String",
            "contains",
            "(Ljava/lang/CharSequence;)Z",
            "(Ljava/lang/String;Ljava/lang/CharSequence;)Z"),
        new InvocationRule(
            "org/apache/commons/lang3/StringUtils",
            "containsIgnoreCase",
            "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z",
            "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z"),
        new InvocationRule(
            "org/apache/commons/lang/StringUtils",
            "containsIgnoreCase",
            "(Ljava/lang/String;Ljava/lang/String;)Z",
            "(Ljava/lang/String;Ljava/lang/String;)Z"),
        new InvocationRule(
            "kotlin/text/StringsKt",
            "contains",
            "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z",
            "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z"));
    public static final List<InvocationRule> REGEX = List.of(
        new InvocationRule(
            "java/lang/String",
            "matches",
            "(Ljava/lang/String;)Z",
            "(Ljava/lang/String;Ljava/lang/String;)Z"));

    private SearchPredicates() {}

    public static boolean mightContain(byte[] bytes) {
        return CANDIDATES.find(bytes);
    }

    public static Kind classify(String owner, String name, String descriptor) {
        for (InvocationRule rule : STRINGS) {
            if (rule.matches(owner, name, descriptor)) return owner.startsWith("kotlin/") ? Kind.KOTLIN : Kind.STRING;
        }
        for (InvocationRule rule : REGEX) {
            if (rule.matches(owner, name, descriptor)) return Kind.REGEX;
        }
        return owner.equals("java/util/regex/Pattern") && name.equals("matcher")
            && descriptor.equals("(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;") ? Kind.REGEX : null;
    }

    public enum Kind {
        STRING,
        REGEX,
        KOTLIN
    }

    public record InvocationRule(String owner, String name, String sourceDescriptor, String replacementDescriptor) {

        public boolean matches(String owner, String name, String descriptor) {
            return this.owner.equals(owner) && this.name.equals(name) && sourceDescriptor.equals(descriptor);
        }
    }
}
