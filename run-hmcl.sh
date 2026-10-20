#!/bin/sh
# SPDX-License-Identifier: MIT OR Apache-2.0
set -eu

agent_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
agent_jar="$agent_root/build/libs/hmcl-region-agent.jar"
if [ "$#" -lt 1 ]; then
    printf '%s\n' 'Usage: ./run-hmcl.sh /path/to/HMCL.jar [HMCL arguments...]' >&2
    exit 2
fi
if [ ! -f "$agent_jar" ]; then
    printf '%s\n' 'Build the agent first: mise run agent-build' >&2
    exit 2
fi
hmcl_jar=$1
shift
if [ ! -f "$hmcl_jar" ]; then
    printf 'Provide the HMCL JAR at: %s\n' "$hmcl_jar" >&2
    exit 2
fi
agent_java=${HMCL_AGENT_JAVA:-${JAVA_HOME:+$JAVA_HOME/bin/java}}
agent_java=${agent_java:-java}
exec "$agent_java" "-javaagent:$agent_jar" -jar "$hmcl_jar" "$@"
