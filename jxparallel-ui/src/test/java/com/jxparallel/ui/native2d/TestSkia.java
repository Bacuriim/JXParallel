package com.jxparallel.ui.native2d;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Whether Skia (Skija) can run in this JVM. Skija 0.116.4 has natives for 64-bit Windows and Linux on x64 and for macOS
 * only: a 32-bit JVM, or Linux and Windows on ARM64, use NanoVG ({@link JXWindow#selectRenderer}), so the Skia tests skip
 * there instead of failing to load the library.
 */
final class TestSkia {
    private static Boolean available;

    private TestSkia() {
    }

    static synchronized boolean available() {
        if (available == null) {
            boolean candidate = "64".equals(System.getProperty("sun.arch.data.model"))
                    && JXWindow.skijaHasNatives(System.getProperty("os.arch", ""));
            boolean loaded = false;
            if (candidate) {
                try {
                    io.github.humbleui.skija.impl.Library.staticLoad();
                    loaded = true;
                } catch (Throwable e) {
                    loaded = false; // no natives for this platform in the jar
                }
            }
            available = loaded;
        }
        return available;
    }

    /** Skips the calling test where Skia cannot run. */
    static void assume() {
        assumeTrue(available(), "Skia has no natives for " + System.getProperty("os.name") + " "
                + System.getProperty("os.arch") + " (" + System.getProperty("sun.arch.data.model") + "-bit)");
    }
}
