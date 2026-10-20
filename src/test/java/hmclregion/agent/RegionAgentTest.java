// SPDX-License-Identifier: MIT OR Apache-2.0
package hmclregion.agent;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/// Validates packaging and startup against the actual upstream JAR when supplied
class RegionAgentTest {
    /// Isolates process logs and launch paths for each test
    @TempDir Path temporary;

    /// Verifies self-contained packaging and included license material
    @Test
    void packagesAgentAndRelocatedAsm() throws Exception {
        try (JarFile jar = new JarFile(System.getProperty("agent.jar"))) {
            assertEquals("hmclregion.agent.RegionAgent",
                    jar.getManifest().getMainAttributes().getValue("Premain-Class"));
            assertNotNull(jar.getEntry("hmclregion/agent/internal/asm/ClassReader.class"));
            assertNull(jar.getEntry("org/objectweb/asm/ClassReader.class"));
            assertTrue(jar.stream().noneMatch(e -> e.getName().startsWith("org/jackhuang/")));
            for (String license : List.of("LICENSE-MIT", "LICENSE-APACHE", "LICENSE-ASM", "NOTICE")) {
                assertNotNull(jar.getEntry("META-INF/" + license), license);
            }
        }
    }

    /// Confirms the compatibility CLI reads the real build's target method
    @Test
    @EnabledIfSystemProperty(named = "hmcl.jar", matches = ".+")
    void inspectsUpstreamJar() throws Exception {
        String output = run(List.of(javaBinary(), "-jar", System.getProperty("agent.jar"),
                System.getProperty("hmcl.jar")));
        assertTrue(output.contains("Compatible with reviewed upstream " + RegionTransformer.UPSTREAM_COMMIT), output);
    }

    /// Exercises premain and restores baseline behavior by starting another fresh process
    @Test
    @EnabledIfSystemProperty(named = "hmcl.jar", matches = ".+")
    void appliesToRealUpstreamAndPreservesJar() throws Exception {
        Path upstream = Path.of(System.getProperty("hmcl.jar"));
        byte[] initial = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(upstream));
        Path agentWithSpaces = temporary.resolve("agent with spaces.jar");
        Files.copy(Path.of(System.getProperty("agent.jar")), agentWithSpaces);
        String patched = run(probeCommand(agentWithSpaces));
        assertTrue(patched.contains("Applied network-region override"), patched);
        assertTrue(patched.contains("PROBE_OK machine=true network=false"), patched);
        assertTrue(run(probeCommand(null)).contains("PROBE_OK machine=true network=true"));
        assertArrayEquals(initial, MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(upstream)));
    }

    /// Verifies the shell launcher keeps arguments and spaced paths intact
    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void shellLauncherPreservesArguments() throws Exception {
        Path root = temporary.resolve("project with spaces");
        Files.createDirectories(root.resolve("build/libs"));
        Path script = root.resolve("run-hmcl.sh");
        Files.copy(Path.of(System.getProperty("agent.project"), "run-hmcl.sh"), script);
        Files.copy(Path.of(System.getProperty("agent.jar")), root.resolve("build/libs/hmcl-region-agent.jar"));
        Path hmcl = Files.createFile(root.resolve("HMCL with spaces.jar"));
        Path recorder = root.resolve("java recorder.sh");
        Files.writeString(recorder, "#!/bin/sh\nprintf '%s\\n' \"$@\"\n");
        assertTrue(recorder.toFile().setExecutable(true));
        ProcessBuilder command = new ProcessBuilder("sh", script.toString(), hmcl.toString(), "--test", "value with spaces");
        command.environment().put("HMCL_AGENT_JAVA", recorder.toString());
        String output = run(command);
        assertEquals(List.of("-javaagent:" + root.resolve("build/libs/hmcl-region-agent.jar"),
                "-jar", hmcl.toString(), "--test", "value with spaces"), output.lines().toList());
    }

    /// Composes an isolated probe JVM with complete bytecode verification enabled
    private List<String> probeCommand(Path agent) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(javaBinary());
        command.add("-Xverify:all");
        command.add("-Duser.timezone=Asia/Shanghai");
        if (agent != null) command.add("-javaagent:" + agent);
        command.add("-cp");
        command.add(Path.of(AgentProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                + File.pathSeparator + System.getProperty("hmcl.jar"));
        command.add(AgentProbe.class.getName());
        command.add(agent == null ? "baseline" : "active");
        return command;
    }

    /// Selects the requested Java runtime or the Gradle test runtime
    private static String javaBinary() {
        return System.getProperty("test.java", Path.of(System.getProperty("java.home"), "bin", "java").toString());
    }

    /// Runs a command with the common isolated-process settings
    private String run(List<String> command) throws Exception {
        return run(new ProcessBuilder(command));
    }

    /// Captures subprocess output to a file while enforcing a bounded runtime
    private String run(ProcessBuilder command) throws Exception {
        Path log = Files.createTempFile(temporary, "process-", ".log");
        Process process = command.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Child process completes within 30 seconds");
            String output = Files.readString(log, StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), output);
            return output;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
