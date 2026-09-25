package com.jxparallel.ui.native2d;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;


import static org.junit.jupiter.api.Assertions.assertEquals;

class JXNativeTreeScalabilityTest {
    @Test
    void shouldLayoutLargeSiblingTree() {
        JXElement[] children = new JXElement[10000];
        for (int index = 0; index < children.length; index++) {
            children[index] = JXElement.of("button",
                    JXProps.builder().set("label", String.valueOf(index)).build());
        }
        JXNativeNode root = JXSkiaRenderer.mount(
                JXElement.of("column", JXProps.builder().set("gap", 1).build(), children));

        JXSkiaRenderer.layout(root, 800, 10000);

        assertEquals(10000, root.getChildren().size());
        assertEquals(800, root.getWidth());
    }
}
