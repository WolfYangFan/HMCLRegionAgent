// SPDX-License-Identifier: MIT OR Apache-2.0
package hmclregion.agent;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/// Applies the reviewed network-region override during initial class loading
final class RegionTransformer implements ClassFileTransformer {
    /// Audited upstream source revision for the supported method implementation
    static final String UPSTREAM_COMMIT = "583e004d718102ae4489aa3f64bcde0c0161d000";
    /// Canonical method digest obtained from the audited upstream build
    static final String SUPPORTED_FINGERPRINT = "39696e47a67637e202e3565d7a8dcf1bbc3f84710fd71da4fc314c441ebde844";
    /// The accepted digest for this transformer instance
    private final String fingerprint;

    /// Selects the reviewed production fingerprint
    RegionTransformer() {
        this(SUPPORTED_FINGERPRINT);
    }

    /// Selects an explicit fingerprint for isolated bytecode tests
    RegionTransformer(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    /// Returns replacement bytes for a matching initial definition, or retains the input class
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!MethodFingerprint.TARGET_CLASS.equals(className) || classBeingRedefined != null) {
            return null;
        }
        try {
            String actual = MethodFingerprint.of(classfileBuffer);
            if (!fingerprint.equals(actual)) {
                RegionAgent.log("Original behavior retained; review method fingerprint " + actual);
                return null;
            }
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(reader, 0);
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (MethodFingerprint.TARGET_METHOD.equals(name)
                            && MethodFingerprint.TARGET_DESCRIPTOR.equals(descriptor)) {
                        method.visitCode();
                        method.visitInsn(Opcodes.ICONST_0);
                        method.visitInsn(Opcodes.IRETURN);
                        method.visitMaxs(1, 0);
                        method.visitEnd();
                        return null;
                    }
                    return method;
                }
            }, 0);
            byte[] transformed = writer.toByteArray();
            RegionAgent.log("Applied network-region override; upstream " + UPSTREAM_COMMIT);
            return transformed;
        } catch (RuntimeException | LinkageError e) {
            RegionAgent.log("Original behavior retained; transformation diagnostic: " + e);
            return null;
        }
    }
}
