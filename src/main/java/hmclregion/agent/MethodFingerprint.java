// SPDX-License-Identifier: MIT OR Apache-2.0
package hmclregion.agent;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/// Computes a canonical digest of the target method's instructions and exception handlers
final class MethodFingerprint {
    /// The precise class selected for load-time transformation
    static final String TARGET_CLASS = "org/jackhuang/hmcl/util/RegionUtils";
    /// The upstream method name, including its original spelling
    static final String TARGET_METHOD = "isIsChinaMainlandNetwork";
    /// A static method accepting zero arguments and returning a boolean
    static final String TARGET_DESCRIPTOR = "()Z";

    /// Groups the static fingerprint operation
    private MethodFingerprint() {}

    /// Rebuilds just the selected method in a fixed class container before hashing it
    static String of(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        if (!TARGET_CLASS.equals(reader.getClassName())) {
            throw new IllegalArgumentException("Expected target class " + TARGET_CLASS);
        }
        ClassWriter canonical = new ClassWriter(0);
        canonical.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "MethodFingerprint", null,
                "java/lang/Object", null);
        int[] matches = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (TARGET_METHOD.equals(name) && TARGET_DESCRIPTOR.equals(descriptor)) {
                    if (access != (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) {
                        throw new IllegalArgumentException("Expected public static target method");
                    }
                    matches[0]++;
                    return canonical.visitMethod(access, name, descriptor, signature, exceptions);
                }
                return null;
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (matches[0] != 1) {
            throw new IllegalArgumentException("Expected exactly one target method");
        }
        canonical.visitEnd();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toByteArray()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 support is required", e);
        }
    }
}
