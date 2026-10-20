# SPDX-License-Identifier: MIT OR Apache-2.0
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string] $HmclJar,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $HmclArguments
)
$ErrorActionPreference = 'Stop'
$AgentJar = Join-Path $PSScriptRoot 'build/libs/hmcl-region-agent.jar'
if (!(Test-Path -LiteralPath $AgentJar -PathType Leaf)) {
    throw 'Build the agent first: mise run agent-build'
}
$ResolvedHmclJar = (Resolve-Path -LiteralPath $HmclJar).Path
$AgentJava = if ($env:HMCL_AGENT_JAVA) {
    $env:HMCL_AGENT_JAVA
} elseif ($env:JAVA_HOME) {
    Join-Path $env:JAVA_HOME 'bin/java.exe'
} else {
    'java'
}
& $AgentJava "-javaagent:$AgentJar" '-jar' $ResolvedHmclJar @HmclArguments
exit $LASTEXITCODE
