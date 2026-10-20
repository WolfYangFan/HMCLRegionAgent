// SPDX-License-Identifier: MIT OR Apache-2.0
package hmclregion.agent;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies class loading, the selected behavior change, and retention of other behavior
class RegionTransformerTest {
    /// Executes both original and transformed classes with the JVM verifier
    @Test
    void changesOnlyNetworkMethod() throws Exception {
        byte[] original = fixture(true, false, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "()Z");
        byte[] snapshot = original.clone();
        Class<?> baseline = define(original);
        assertEquals(true, baseline.getMethod(MethodFingerprint.TARGET_METHOD).invoke(null));
        assertEquals(1, baseline.getField("calls").get(null));

        RegionTransformer transformer = new RegionTransformer(MethodFingerprint.of(original));
        byte[] changed = transformer.transform(null, MethodFingerprint.TARGET_CLASS, null, null, original);
        assertNotNull(changed);
        Class<?> patched = define(changed);
        assertEquals(false, patched.getMethod(MethodFingerprint.TARGET_METHOD).invoke(null));
        assertEquals(0, patched.getField("calls").get(null));
        assertEquals(true, patched.getMethod("isIsChinaMainlandMachine").invoke(null));
        assertEquals(73, patched.getMethod("otherMethod").invoke(null));
        assertArrayEquals(snapshot, original);
    }

    /// Makes debug information independent of the accepted instruction fingerprint
    @Test
    void acceptsEquivalentDebugMetadata() {
        byte[] plain = fixture(true, false, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "()Z");
        byte[] debug = fixture(true, true, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "()Z");
        assertEquals(MethodFingerprint.of(plain), MethodFingerprint.of(debug));
    }

    /// Keeps a changed implementation available for a fresh compatibility review
    @Test
    void retainsChangedMethod() throws Exception {
        byte[] original = fixture(true, false, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "()Z");
        byte[] changed = fixture(false, false, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "()Z");
        RegionTransformer transformer = new RegionTransformer(MethodFingerprint.of(original));
        assertNull(transformer.transform(null, MethodFingerprint.TARGET_CLASS, null, null, changed));
        Class<?> retained = define(changed);
        assertEquals(false, retained.getMethod(MethodFingerprint.TARGET_METHOD).invoke(null));
        assertEquals(1, retained.getField("calls").get(null));
    }

    /// Preserves classes with alternate signatures and malformed input for the JVM to handle
    @Test
    void retainsAlternateShapes() {
        RegionTransformer transformer = new RegionTransformer();
        for (byte[] bytes : new byte[][] {
                fixture(true, false, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "()I"),
                fixture(true, false, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "()Z"),
                fixture(true, false, Opcodes.ACC_PUBLIC, "()Z"),
                new byte[] {1, 2, 3}
        }) {
            assertNull(transformer.transform(null, MethodFingerprint.TARGET_CLASS, null, null, bytes));
        }
    }

    /// Selects only the initial load of the named class
    @Test
    void preservesOtherClassesAndRedefinitions() throws Exception {
        byte[] bytes = fixture(true, false, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "()Z");
        RegionTransformer transformer = new RegionTransformer(MethodFingerprint.of(bytes));
        assertNull(transformer.transform(null, "example/Other", null, null, bytes));
        assertNull(transformer.transform(null, null, null, null, bytes));
        assertNull(transformer.transform(null, MethodFingerprint.TARGET_CLASS, define(bytes), null, bytes));
    }

    /// Defines a fixture with an isolated loader, forcing actual JVM verification
    private static Class<?> define(byte[] bytes) {
        return new ClassLoader(null) {
            Class<?> loadFixture() {
                return defineClass(MethodFingerprint.TARGET_CLASS.replace('/', '.'), bytes, 0, bytes.length);
            }
        }.loadFixture();
    }

    /// Produces an original synthetic class with observable network-method side effects
    private static byte[] fixture(boolean result, boolean debug, int access, String descriptor) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                MethodFingerprint.TARGET_CLASS, null, "java/lang/Object", null);
        if (debug) writer.visitSource("SyntheticRegion.java", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "calls", "I", null, null).visitEnd();
        MethodVisitor method = writer.visitMethod(access, MethodFingerprint.TARGET_METHOD, descriptor, null, null);
        method.visitCode();
        if (debug) {
            Label start = new Label();
            method.visitLabel(start);
            method.visitLineNumber(50, start);
        }
        method.visitFieldInsn(Opcodes.GETSTATIC, MethodFingerprint.TARGET_CLASS, "calls", "I");
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IADD);
        method.visitFieldInsn(Opcodes.PUTSTATIC, MethodFingerprint.TARGET_CLASS, "calls", "I");
        method.visitInsn(result ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        constantMethod(writer, "isIsChinaMainlandMachine", "()Z", 1);
        constantMethod(writer, "otherMethod", "()I", 73);
        writer.visitEnd();
        return writer.toByteArray();
    }

    /// Adds an independently observable method to the generated fixture
    private static void constantMethod(ClassWriter writer, String name, String descriptor, int value) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                name, descriptor, null, null);
        method.visitCode();
        method.visitLdcInsn(value);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }
}
