// SPDX-License-Identifier: MIT OR Apache-2.0
plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "hmclregion"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.ow2.asm:asm:9.9")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.jar { enabled = false }

tasks.shadowJar {
    archiveFileName.set("hmcl-region-agent.jar")
    relocate("org.objectweb.asm", "hmclregion.agent.internal.asm")
    manifest.attributes(
        "Premain-Class" to "hmclregion.agent.RegionAgent",
        "Main-Class" to "hmclregion.agent.RegionAgent",
        "Implementation-Title" to "HMCL Region Agent (independent community project)",
        "Implementation-Version" to project.version,
        "Bundle-License" to "MIT OR Apache-2.0",
    )
    from(listOf("LICENSE-MIT", "LICENSE-APACHE", "NOTICE", "LICENSE-ASM")) { into("META-INF") }
}

tasks.assemble { dependsOn(tasks.shadowJar) }

tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.shadowJar)
    systemProperty("agent.jar", tasks.shadowJar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("agent.project", projectDir.absolutePath)
    // Supplying an upstream JAR enables the real HMCL process checks
    providers.gradleProperty("hmclJar").orNull?.let {
        val upstreamJar = file(it)
        require(upstreamJar.isFile) { "Provide the HMCL JAR at ${upstreamJar.absolutePath}" }
        inputs.file(upstreamJar)
        systemProperty("hmcl.jar", upstreamJar.absolutePath)
    }
    providers.gradleProperty("testJava").orNull?.let {
        inputs.property("testJava", it)
        systemProperty("test.java", it)
    }
    testLogging { events("passed", "skipped", "failed") }
}
