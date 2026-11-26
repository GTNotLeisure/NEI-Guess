package com.asdflj.nech.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.asdflj.nech.NechConfig;
import com.asdflj.nech.core.transform.MethodSelectors;
import com.asdflj.nech.core.transform.SearchPatches;
import com.asdflj.nech.core.transform.SearchPredicates;
import com.gtnewhorizon.gtnhlib.asm.SafeClassWriter;

public class NechClassTransformer implements IClassTransformer {

    private static final Logger LOGGER = LogManager.getLogger("NECH Search");
    private static final int STRINGS = 1;
    private static final int REGEX = 2;
    private static volatile Rules rules = new Rules(MethodSelectors.of(), MethodSelectors.of(), MethodSelectors.of());

    public static void configure() {
        rules = new Rules(
            MethodSelectors.of(NechConfig.defaultTransformerStringList, NechConfig.transformerStringAdditionalList),
            MethodSelectors.of(NechConfig.defaultTransformerRegExp, NechConfig.transformerRegExpAdditionalList),
            MethodSelectors.of(NechConfig.transformerMethodBlackList));
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        Rules current = rules;
        boolean strings = current.strings.containsClass(name) || current.strings.containsClass(transformedName);
        boolean regex = current.regex.containsClass(name) || current.regex.containsClass(transformedName);
        if (!strings && !regex) return bytes;
        try {
            if (!SearchPredicates.mightContain(bytes)) return bytes;
            ClassReader reader = new ClassReader(bytes);
            ClassNode node = new ClassNode();
            reader.accept(node, 0);
            if (!apply(node, current, name, transformedName)) return bytes;
            SafeClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            if (NechConfig.enableVerbose) LOGGER.info("Extended client search predicates in {}", transformedName);
            return writer.toByteArray();
        } catch (RuntimeException failure) {
            LOGGER.error("Unable to extend client search predicates in {}", transformedName, failure);
            return bytes;
        }
    }

    private static boolean apply(ClassNode node, Rules rules, String name, String transformedName) {
        Set<String> strings = rules.strings.methodsFor(name, transformedName, node.name);
        Set<String> regex = rules.regex.methodsFor(name, transformedName, node.name);
        Set<String> blocked = rules.blocked.methodsFor(name, transformedName, node.name);
        Map<String, MethodNode> implementations = new HashMap<>();
        Deque<PendingMethod> pending = new ArrayDeque<>();
        for (MethodNode method : node.methods) {
            implementations.put(method.name + method.desc, method);
            int kinds = (MethodSelectors.matches(strings, method.name, method.desc) ? STRINGS : 0)
                | (MethodSelectors.matches(regex, method.name, method.desc) ? REGEX : 0);
            if (kinds != 0) pending.addLast(new PendingMethod(method, kinds));
        }
        boolean changed = false;
        Map<MethodNode, Integer> visited = new HashMap<>();
        while (!pending.isEmpty()) {
            PendingMethod next = pending.removeFirst();
            MethodNode method = next.method;
            int kinds = next.kinds & ~visited.getOrDefault(method, 0);
            if (kinds == 0 || MethodSelectors.matches(blocked, method.name, method.desc)) continue;
            visited.merge(method, kinds, (left, right) -> left | right);
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof InvokeDynamicInsnNode dynamic) || !SearchPatches.isLambda(dynamic))
                    continue;
                for (Object argument : dynamic.bsmArgs) {
                    if (!(argument instanceof Handle handle) || !handle.getOwner()
                        .equals(node.name)) continue;
                    MethodNode implementation = implementations.get(handle.getName() + handle.getDesc());
                    if (implementation != null) pending.addLast(new PendingMethod(implementation, kinds));
                }
            }
            if ((kinds & STRINGS) != 0) changed |= SearchPatches.strings(method);
            if ((kinds & REGEX) != 0) changed |= SearchPatches.regex(method);
        }
        return changed;
    }

    private record Rules(MethodSelectors strings, MethodSelectors regex, MethodSelectors blocked) {}

    private record PendingMethod(MethodNode method, int kinds) {}
}
