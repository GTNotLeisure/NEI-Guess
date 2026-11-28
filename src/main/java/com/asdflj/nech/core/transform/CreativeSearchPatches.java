package com.asdflj.nech.core.transform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.AnnotationNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.FieldInsnNode;
import org.spongepowered.asm.lib.tree.InsnList;
import org.spongepowered.asm.lib.tree.InsnNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.lib.tree.VarInsnNode;

public class CreativeSearchPatches {

    private static final String ASYNC_MIXIN = "org.embeddedt.archaicfix.mixins.client.core.MixinGuiContainerCreative";
    private static final String CONTAINER = "net/minecraft/client/gui/inventory/GuiContainerCreative$ContainerCreative";
    private static final String FILTER_DESCRIPTOR = "(L" + CONTAINER + ";)V";
    private static final Logger LOG = LogManager.getLogger("NECH");

    private CreativeSearchPatches() {}

    public static void completeCustomTabs(ClassNode target) {
        if (!target.name.equals("net/minecraft/client/gui/inventory/GuiContainerCreative")) return;
        for (Object value : target.methods) {
            MethodNode method = (MethodNode) value;
            if (!isAsyncSearch(method)) continue;
            MethodInsnNode scroll = null;
            FieldInsnNode scrollPosition = null;
            boolean completed = false;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode call) {
                    if (call.owner.equals(target.name) && call.name.equals("updateFilteredItems")
                        && call.desc.equals(FILTER_DESCRIPTOR)) completed = true;
                    if (call.owner.equals(CONTAINER) && call.desc.equals("(F)V")) scroll = call;
                } else if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD
                    && field.owner.equals(target.name)
                    && field.desc.equals("F")) scrollPosition = field;
            }
            if (completed || scroll == null) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)
                    || !call.owner.equals("net/minecraft/creativetab/CreativeTabs")
                    || !call.desc.equals("(Ljava/util/List;)V")
                    || !(call.name.equals("displayAllReleventItems") || call.name.equals("func_78018_a"))) continue;
                AbstractInsnNode previous = previousInstruction(call);
                if (!(previous instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETFIELD
                    || !field.owner.equals(CONTAINER)
                    || !field.desc.equals("Ljava/util/List;")) continue;
                if (!(previousInstruction(field) instanceof VarInsnNode container)
                    || container.getOpcode() != Opcodes.ALOAD) continue;

                InsnList clear = new InsnList();
                clear.add(new InsnNode(Opcodes.DUP));
                clear.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "clear", "()V", true));
                method.instructions.insertBefore(call, clear);

                InsnList filter = new InsnList();
                filter.add(new VarInsnNode(Opcodes.ALOAD, 0));
                filter.add(new VarInsnNode(Opcodes.ALOAD, container.var));
                filter.add(
                    new MethodInsnNode(
                        Opcodes.INVOKESPECIAL,
                        target.name,
                        "updateFilteredItems",
                        FILTER_DESCRIPTOR,
                        false));
                filter.add(new VarInsnNode(Opcodes.ALOAD, container.var));
                filter.add(new InsnNode(Opcodes.FCONST_0));
                filter.add(new MethodInsnNode(scroll.getOpcode(), scroll.owner, scroll.name, scroll.desc, scroll.itf));
                if (scrollPosition != null) {
                    filter.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    filter.add(new InsnNode(Opcodes.FCONST_0));
                    filter.add(new FieldInsnNode(Opcodes.PUTFIELD, target.name, scrollPosition.name, "F"));
                }
                method.instructions.insert(call, filter);
                method.maxStack = Math.max(method.maxStack, 3);
                LOG.info("Completed custom creative search filtering and slot refresh in {}", method.name);
            }
        }
    }

    private static boolean isAsyncSearch(MethodNode method) {
        if (method.visibleAnnotations == null) return false;
        for (Object value : method.visibleAnnotations) {
            AnnotationNode annotation = (AnnotationNode) value;
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
                || annotation.values == null) continue;
            for (int index = 0; index + 1 < annotation.values.size(); index += 2) {
                if (annotation.values.get(index)
                    .equals("mixin") && ASYNC_MIXIN.equals(annotation.values.get(index + 1))) return true;
            }
        }
        return false;
    }

    private static AbstractInsnNode previousInstruction(AbstractInsnNode instruction) {
        AbstractInsnNode previous = instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        return previous;
    }
}
