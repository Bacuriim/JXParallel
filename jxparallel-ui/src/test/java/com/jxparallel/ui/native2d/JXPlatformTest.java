package com.jxparallel.ui.native2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JXPlatformTest {
    @Test
    void skijaHasNoNativesForArm64OutsideMacOs() {
        assertTrue(JXWindow.skijaHasNatives("amd64"));
        assertTrue(JXWindow.skijaHasNatives("x86_64"));
        assertEquals(JXPlatform.isMac(), JXWindow.skijaHasNatives("aarch64"));
        assertEquals(JXPlatform.isMac(), JXWindow.skijaHasNatives("arm64"));
    }

    @Test
    void shortcutIsCmdOnMacAndCtrlElsewhere() {
        assertEquals(JXPlatform.isMac() ? JXInputEvent.META : JXInputEvent.CONTROL, JXInputEvent.SHORTCUT);
        JXAccelerator shortcut = JXAccelerator.parse("Shortcut+S");
        assertNotNull(shortcut);
        assertEquals(JXInputEvent.SHORTCUT, shortcut.modifiers);
        assertEquals(JXInputEvent.CONTROL, JXAccelerator.parse("Ctrl+S").modifiers);
        assertEquals(JXInputEvent.META, JXAccelerator.parse("Cmd+S").modifiers);
    }

    @Test
    void notStartedOnFirstThreadInATestRun() {
        assertFalse(JXPlatform.startedOnFirstThread());
    }
}
