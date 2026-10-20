// SPDX-License-Identifier: MIT OR Apache-2.0
package hmclregion.agent;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

/// Provides the startup agent entry point and a JAR compatibility inspection command
public final class RegionAgent {
    /// Holds only static entry points
    private RegionAgent() {}

    /// Registers the transformer before HMCL begins loading application classes
    public static void premain(String arguments, Instrumentation instrumentation) {
        if (arguments != null && !arguments.isBlank()) {
            log("Use -javaagent:/absolute/path/hmcl-region-agent.jar to activate the agent");
            return;
        }
        for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
            if (MethodFingerprint.TARGET_CLASS.replace('/', '.').equals(loaded.getName())) {
                log("Target already loaded; place this agent first in the JVM startup arguments");
                return;
            }
        }
        instrumentation.addTransformer(new RegionTransformer(), false);
        log("Transformer registered; awaiting the HMCL region class");
    }

    /// Prints compatibility information for the target class stored in an HMCL JAR
    public static void main(String[] args) {
        if (args.length != 1) {
            log("Usage: java -jar hmcl-region-agent.jar /absolute/path/HMCL.jar");
            System.exit(2);
        }
        try (JarFile jar = new JarFile(Path.of(args[0]).toFile(), true,
                ZipFile.OPEN_READ, Runtime.version())) {
            var entry = jar.getJarEntry(MethodFingerprint.TARGET_CLASS + ".class");
            if (entry == null) {
                throw new IOException("Provide an HMCL JAR containing " + MethodFingerprint.TARGET_CLASS);
            }
            String actual;
            try (var input = jar.getInputStream(entry)) {
                actual = MethodFingerprint.of(input.readAllBytes());
            }
            System.out.println("Method SHA-256: " + actual);
            if (RegionTransformer.SUPPORTED_FINGERPRINT.equals(actual)) {
                System.out.println("Compatible with reviewed upstream " + RegionTransformer.UPSTREAM_COMMIT);
            } else {
                log("Original behavior selected; review this fingerprint to extend compatibility");
                System.exit(2);
            }
        } catch (IOException | RuntimeException | LinkageError e) {
            log("Inspection diagnostic: " + e);
            System.exit(2);
        }
    }

    /// Labels process diagnostics with the independent agent's identity
    static void log(String message) {
        System.err.println("[HMCL Region Agent] " + message);
    }
}
