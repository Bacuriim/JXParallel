package com.jxparallel.ui.native2d;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The HiDPI arithmetic of {@link JXWindow}: scale resolution and conversions between logical, window and physical units. */
class JXScaleTest {
    @Test
    void parsesTheOverride() {
        assertEquals(1.5f, JXScale.parse("1.5"));
        assertEquals(2f, JXScale.parse(" 2 "));
        assertEquals(1.25f, JXScale.parse("125%"));
        assertEquals(0f, JXScale.parse(null));
        assertEquals(0f, JXScale.parse(""));
        assertEquals(0f, JXScale.parse("abc"));
        assertEquals(0f, JXScale.parse("0"));
        assertEquals(0f, JXScale.parse("-2"));
        assertEquals(0f, JXScale.parse("NaN"));
        assertEquals(0f, JXScale.parse("Infinity"));
        assertEquals(JXScale.MAX, JXScale.parse("100"));
        assertEquals(JXScale.MIN, JXScale.parse("0.01"));
    }

    @Test
    void resolvePrefersTheOverrideThenTheMonitorThenOne() {
        assertEquals(1.5f, JXScale.resolve(1.5f, 2f, 2f));
        assertEquals(2f, JXScale.resolve(0f, 2f, 2f));
        assertEquals(1.25f, JXScale.resolve(0f, 1.25f, 1.5f), "x scale wins");
        assertEquals(1.5f, JXScale.resolve(0f, 0f, 1.5f), "y when x is unusable");
        assertEquals(1f, JXScale.resolve(0f, 0f, 0f));
        assertEquals(1f, JXScale.resolve(0f, Float.NaN, -1f));
        assertEquals(JXScale.MAX, JXScale.resolve(0f, 50f, 50f));
    }

    @Test
    void logicalExtentIsTheCeilingOfPhysicalOverScale() {
        assertEquals(640, JXScale.logicalExtent(640, 1f), "scale 1 is the identity");
        assertEquals(800, JXScale.logicalExtent(1200, 1.5f));
        assertEquals(801, JXScale.logicalExtent(1201, 1.5f), "covers the last physical pixel");
        assertEquals(500, JXScale.logicalExtent(1000, 2f));
        assertEquals(1000, JXScale.logicalExtent(1100, 1.1f), "float noise adds no pixel");
        assertEquals(1, JXScale.logicalExtent(1, 2f));
        assertEquals(0, JXScale.logicalExtent(0, 2f));
    }

    @Test
    void physicalExtentRounds() {
        assertEquals(300, JXScale.physicalExtent(200, 1.5f));
        assertEquals(500, JXScale.physicalExtent(333.33, 1.5f));
        assertEquals(480, JXScale.physicalExtent(480, 1f));
    }

    @Test
    void cursorOnWindowsAndX11WherePixelsAreWindowUnits() {
        // 150%: the window is 960 units = 960 physical pixels = 640 logical
        assertEquals(0.0, JXScale.toLogical(0, 960, 960, 1.5f));
        assertEquals(100.0, JXScale.toLogical(150, 960, 960, 1.5f), 1e-9);
        assertEquals(640.0, JXScale.toLogical(960, 960, 960, 1.5f), 1e-9);
        assertEquals(37.0, JXScale.toLogical(37, 800, 800, 1f), "scale 1 is the identity");
    }

    @Test
    void cursorOnMacOSAndWaylandWhereWindowUnitsAreLogical() {
        // 200%: 640 points, 1280 physical pixels
        assertEquals(100.0, JXScale.toLogical(100, 640, 1280, 2f), 1e-9);
        // the same screen forced to 1.5 with -Djx.scale: 1280 / 1.5 logical pixels span the 640 points
        assertEquals(100.0 * 2 / 1.5, JXScale.toLogical(100, 640, 1280, 1.5f), 1e-9);
    }

    @Test
    void windowUnitsOfALogicalSize() {
        assertEquals(960, JXScale.toWindowUnits(640, 800, 800, 1.5f), "Windows: physical pixels");
        assertEquals(640, JXScale.toWindowUnits(640, 800, 800, 1f));
        assertEquals(640, JXScale.toWindowUnits(640, 320, 640, 2f), "macOS: points, the framebuffer is 2x");
        assertEquals(1, JXScale.toWindowUnits(0, 800, 800, 2f));
        assertEquals(960, JXScale.toWindowUnits(640, 0, 0, 1.5f), "no framebuffer yet: ratio 1");
    }

    @Test
    void sizeAndCursorRoundTrip() {
        int logical = 640;
        float scale = 1.5f;
        int windowUnits = JXScale.toWindowUnits(logical, 1, 1, scale);
        int framebuffer = windowUnits; // Windows
        assertEquals(logical, JXScale.logicalExtent(framebuffer, scale));
        assertEquals(logical, JXScale.toLogical(windowUnits, windowUnits, framebuffer, scale), 1e-9);
    }

    @Test
    void ratioIsOneWithoutASize() {
        assertEquals(1.0, JXScale.ratio(0, 100));
        assertEquals(1.0, JXScale.ratio(100, 0));
        assertEquals(2.0, JXScale.ratio(100, 200));
    }
}
