// SPDX-License-Identifier: MIT OR Apache-2.0
package hmclregion.agent;

/// Exercises the real upstream class inside a fresh JVM using reflection
public final class AgentProbe {
    /// Uses static process entry points
    private AgentProbe() {}

    /// Checks the machine result, network result and cache effects in the selected mode
    public static void main(String[] args) throws Exception {
        boolean active = "active".equals(args[0]);
        Class<?> region = Class.forName("org.jackhuang.hmcl.util.RegionUtils");
        var cache = region.getDeclaredField("isChinaMainlandNetwork");
        cache.setAccessible(true);
        if (!active) cache.set(null, Boolean.TRUE);
        Object machine = region.getMethod("isIsChinaMainlandMachine").invoke(null);
        Object network = region.getMethod("isIsChinaMainlandNetwork").invoke(null);
        if (!Boolean.TRUE.equals(machine) || !Boolean.valueOf(!active).equals(network)) {
            throw new AssertionError("Observed machine=" + machine + ", network=" + network);
        }
        if (active && cache.get(null) != null) {
            throw new AssertionError("Expected the initial network cache state");
        }
        System.out.println("PROBE_OK machine=" + machine + " network=" + network);
    }
}
