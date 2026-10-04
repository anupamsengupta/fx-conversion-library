package com.power.fx.testkit.arch;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped ASM bytecode scan for D-09 (S12.5), promoted from {@code
 * fx-core}'s {@code NoFloatingPointBytecodeShiftLeftTest} (Phase 2 Section
 * 2.1). Identical opcode list -- this is the only mechanically complete
 * check that D-09 holds, since ArchUnit cannot see local variables or
 * primitive opcodes.
 *
 * @see "Tech spec S12.5"
 */
class NoFloatingPointBytecodeTest {

    private static final Set<Integer> FORBIDDEN_OPCODES = Set.of(
            Opcodes.DADD, Opcodes.DSUB, Opcodes.DMUL, Opcodes.DDIV, Opcodes.DREM, Opcodes.DNEG,
            Opcodes.DCMPG, Opcodes.DCMPL, Opcodes.D2F, Opcodes.D2I, Opcodes.D2L,
            Opcodes.I2D, Opcodes.L2D, Opcodes.F2D,
            Opcodes.DCONST_0, Opcodes.DCONST_1,
            Opcodes.DLOAD, Opcodes.DSTORE, Opcodes.DRETURN,
            Opcodes.FADD, Opcodes.FSUB, Opcodes.FMUL, Opcodes.FDIV, Opcodes.FREM, Opcodes.FNEG,
            Opcodes.FCMPG, Opcodes.FCMPL, Opcodes.F2I, Opcodes.F2L,
            Opcodes.I2F, Opcodes.L2F,
            Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2,
            Opcodes.FLOAD, Opcodes.FSTORE, Opcodes.FRETURN);

    @Test
    void noDoubleOrFloatOpcodesOrDescriptorsInFxApiOrFxCore() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path classFile : classFilesOf("com.power.fx.api", "com.power.fx.core")) {
            scan(classFile, violations);
        }
        assertTrue(violations.isEmpty(), "D-09 bytecode violations found:\n" + String.join("\n", violations));
    }

    private static List<Path> classFilesOf(String... rootPackages) throws IOException {
        List<Path> result = new ArrayList<>();
        for (String cp : System.getProperty("java.class.path").split(File.pathSeparator)) {
            Path p = Path.of(cp);
            if (Files.isDirectory(p) && p.toString().replace('\\', '/').contains("/target/classes")) {
                try (Stream<Path> walk = Files.walk(p)) {
                    walk.filter(f -> f.toString().endsWith(".class"))
                            .filter(f -> {
                                String rel = p.relativize(f).toString().replace(File.separatorChar, '.');
                                for (String root : rootPackages) {
                                    if (rel.startsWith(root)) {
                                        return true;
                                    }
                                }
                                return false;
                            })
                            .forEach(result::add);
                }
            }
        }
        return result;
    }

    private static void scan(Path classFile, List<String> violations) throws IOException {
        try (InputStream in = Files.newInputStream(classFile)) {
            ClassReader reader = new ClassReader(in);
            ClassNode node = new ClassNode();
            reader.accept(node, 0);

            for (Object fieldObj : node.fields) {
                org.objectweb.asm.tree.FieldNode field = (org.objectweb.asm.tree.FieldNode) fieldObj;
                if (field.desc.contains("D") && isPrimitiveTypeDescriptor(field.desc, 'D')
                        || isPrimitiveTypeDescriptor(field.desc, 'F')) {
                    violations.add(node.name + "." + field.name + ": descriptor " + field.desc);
                }
            }

            for (Object methodObj : node.methods) {
                MethodNode method = (MethodNode) methodObj;
                if (isPrimitiveTypeDescriptor(method.desc, 'D') || isPrimitiveTypeDescriptor(method.desc, 'F')) {
                    violations.add(node.name + "#" + method.name + method.desc + ": double/float in signature");
                }
                for (AbstractInsnNode insn : method.instructions) {
                    if (FORBIDDEN_OPCODES.contains(insn.getOpcode())) {
                        violations.add(node.name + "#" + method.name + ": forbidden opcode " + insn.getOpcode());
                    }
                    if (insn instanceof LdcInsnNode ldc && (ldc.cst instanceof Double || ldc.cst instanceof Float)) {
                        violations.add(node.name + "#" + method.name + ": LDC of a Double/Float constant " + ldc.cst);
                    }
                }
            }
        }
    }

    private static boolean isPrimitiveTypeDescriptor(String desc, char typeChar) {
        for (int i = 0; i < desc.length(); i++) {
            char c = desc.charAt(i);
            if (c == typeChar) {
                if (!insideObjectDescriptor(desc, i)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean insideObjectDescriptor(String desc, int index) {
        int depth = 0;
        for (int i = 0; i < index; i++) {
            char c = desc.charAt(i);
            if (c == 'L') {
                depth++;
            } else if (c == ';') {
                depth--;
            }
        }
        return depth > 0;
    }
}
