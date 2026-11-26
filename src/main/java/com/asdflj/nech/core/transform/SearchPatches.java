package com.asdflj.nech.core.transform;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import com.asdflj.nech.core.transform.SearchPredicates.InvocationRule;

public class SearchPatches {

    private static final String MATCH_OWNER = "com/asdflj/nech/utils/Match";

    private SearchPatches() {}

    public static boolean strings(MethodNode method) {
        return replaceInvocations(method, SearchPredicates.STRINGS);
    }

    public static boolean regex(MethodNode method) {
        boolean changed = replaceInvocations(method, SearchPredicates.REGEX);
        Set<LabelNode> boundaries = controlFlowBoundaries(method);
        for (AbstractInsnNode node : method.instructions.toArray()) {
            if (!(node instanceof MethodInsnNode invocation) || !invocation.owner.equals("java/util/regex/Pattern")
                || !invocation.name.equals("matcher")
                || !invocation.desc.equals("(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;")) continue;
            AbstractInsnNode next = node.getNext();
            while (next instanceof LineNumberNode || next instanceof LabelNode label && !boundaries.contains(label)) {
                next = next.getNext();
            }
            if (!(next instanceof MethodInsnNode consumer) || !consumer.owner.equals("java/util/regex/Matcher")
                || !consumer.name.equals("find")
                || !consumer.desc.equals("()Z")) continue;
            // Cross debug labels only; control-flow targets and matcher state remain intact.
            method.instructions.set(
                invocation,
                new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    MATCH_OWNER,
                    "find",
                    "(Ljava/util/regex/Pattern;Ljava/lang/CharSequence;)Z",
                    false));
            method.instructions.remove(consumer);
            changed = true;
        }
        return changed;
    }

    public static boolean isLambda(InvokeDynamicInsnNode instruction) {
        return instruction.bsm.getOwner()
            .equals("java/lang/invoke/LambdaMetafactory");
    }

    private static boolean replaceInvocations(MethodNode method, List<InvocationRule> rules) {
        boolean changed = false;
        for (AbstractInsnNode node : method.instructions.toArray()) {
            if (node instanceof MethodInsnNode invocation) {
                for (InvocationRule rule : rules) {
                    if (!rule.matches(invocation.owner, invocation.name, invocation.desc)) continue;
                    method.instructions.set(
                        invocation,
                        new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            MATCH_OWNER,
                            rule.name(),
                            rule.replacementDescriptor(),
                            false));
                    changed = true;
                    break;
                }
            } else if (node instanceof InvokeDynamicInsnNode dynamic && isLambda(dynamic)) {
                for (int index = 0; index < dynamic.bsmArgs.length; index++) {
                    if (!(dynamic.bsmArgs[index] instanceof Handle handle)) continue;
                    for (InvocationRule rule : rules) {
                        if (!rule.matches(handle.getOwner(), handle.getName(), handle.getDesc())) continue;
                        // Preserve the SAM signature, captured arguments and altMetafactory flags.
                        dynamic.bsmArgs[index] = new Handle(
                            Opcodes.H_INVOKESTATIC,
                            MATCH_OWNER,
                            rule.name(),
                            rule.replacementDescriptor());
                        changed = true;
                        break;
                    }
                }
            }
        }
        return changed;
    }

    private static Set<LabelNode> controlFlowBoundaries(MethodNode method) {
        Set<LabelNode> boundaries = new HashSet<>();
        for (AbstractInsnNode node : method.instructions.toArray()) {
            if (node instanceof JumpInsnNode jump) boundaries.add(jump.label);
            else if (node instanceof LookupSwitchInsnNode lookup) {
                boundaries.add(lookup.dflt);
                boundaries.addAll(lookup.labels);
            } else if (node instanceof TableSwitchInsnNode table) {
                boundaries.add(table.dflt);
                boundaries.addAll(table.labels);
            }
        }
        for (TryCatchBlockNode block : method.tryCatchBlocks) {
            boundaries.add(block.start);
            boundaries.add(block.end);
            boundaries.add(block.handler);
        }
        return boundaries;
    }

}
