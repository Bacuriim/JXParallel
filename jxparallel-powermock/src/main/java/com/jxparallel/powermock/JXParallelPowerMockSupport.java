package com.jxparallel.powermock;

public final class JXParallelPowerMockSupport {
    private JXParallelPowerMockSupport() {
    }

    public static boolean supportsLegacyInstrumentation() {
        return false;
    }

    public static String limitation() {
        return "PowerMock instrumentation is adapter-only and must be validated against the target JDK and test runner.";
    }
}
