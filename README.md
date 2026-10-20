# HMCL Region Agent

> An independent Java agent for studying HMCL class loading and runtime region detection

The project distributes source code, licenses and build instructions; users **should build the artifacts locally**

At HMCL startup, the agent makes `RegionUtils.isIsChinaMainlandNetwork()Z` return `false`, enabling the corresponding download page controls. The transformation applies to the target method inside the JVM.

## Build

The project root uses the standard Gradle layout: `src/main/java` contains the agent source and `src/test/java` contains the tests.

Run these commands from the project root:

```sh
git submodule update --init --recursive
mise trust
mise install
mise run agent:build
```

The build produces `build/libs/hmcl-region-agent.jar` locally.

## Inspect and launch

First inspect the target method in the HMCL JAR you intend to use:

```sh
mise exec -- java -jar build/libs/hmcl-region-agent.jar /absolute/path/HMCL.jar
```

Output beginning with `Compatible with reviewed upstream` indicates that the method fingerprint matches the reviewed implementation. Exit code `0` indicates compatibility; exit code `2` indicates that the diagnostics require a path correction or a review of a new upstream implementation.

Linux / macOS:

```sh
mise exec -- ./run-hmcl.sh "/absolute/path/HMCL.jar"
```

Windows PowerShell:

```powershell
mise exec -- powershell -File .\run-hmcl.ps1 -HmclJar "C:\Apps\HMCL.jar"
```

You can also add the agent argument directly to the JVM command that launches HMCL:

```sh
java "-javaagent:/absolute/path/hmcl-region-agent.jar" -jar "/absolute/path/HMCL.jar"
```

The launch scripts select Java from `HMCL_AGENT_JAVA`, then `JAVA_HOME`, then `java` on `PATH`

The startup log first shows `Transformer registered`, followed by `Applied network-region override` after the target class is transformed. If the agent encounters a new method fingerprint or a transformation error, it retains the original class and reports `Original behavior retained` with diagnostics.

## Compatibility

The current reviewed baseline is [HMCL commit 583e004d7](https://github.com/HMCL-dev/HMCL/commit/583e004d718102ae4489aa3f64bcde0c0161d000). Compatibility checks use the target method's instructions, exception handlers and signature; debug information and stack map frames are normalized when computing the fingerprint.

The reviewed method has this SHA-256:

```text
39696e47a67637e202e3565d7a8dcf1bbc3f84710fd71da4fc314c441ebde844
```

## HMCL build environment

The existing mise tasks provide entry points for upstream builds:

```sh
mise run build
mise run gradle -- --version
mise run gradle -- clean makeExecutables
```

Upstream artifacts are written to `HMCL-source/HMCL/build/libs/` and include JAR, EXE, SH and DEB files
