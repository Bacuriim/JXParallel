package com.jxparallel.ui.accessibility;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.controls.JXControls;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JXAccessibilityTreeTest {
    @Test
    void shouldExposeRolesAndNames() {
        JXAccessibleNode root = JXAccessibilityTree.from(
                JXElement.of("column", null, JXControls.button("Save", null)));
        assertEquals(JXAccessibilityRole.GROUP, root.getRole());
        assertEquals(JXAccessibilityRole.BUTTON, root.getChildren().get(0).getRole());
        assertEquals("Save", root.getChildren().get(0).getName());
    }
}
