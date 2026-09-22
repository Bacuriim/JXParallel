package com.jxparallel.ui.native2d;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.controls.JXControls;
import com.jxparallel.ui.layout.JXLayouts;
import com.jxparallel.ui.vulkan.JXVulkanRenderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JXVulkanRendererTest {
    @Test
    void shouldMountAndLayoutNativeTreeWithoutJavaFx() {
        JXElement element = JXLayouts.column(8,
                JXElement.text("Customers"),
                JXControls.button("Load", null));

        JXNativeNode node = JXVulkanRenderer.mount(element);

        JXVulkanRenderer.layout(node, 320, 200);

        assertNotNull(node);
        assertEquals("column", node.getType());
        assertEquals(2, node.getChildren().size());
        assertEquals(320, node.getBounds().width);
        assertEquals(200, node.getBounds().height);
    }

    @Test
    void shouldMountAllNativeControlShapesForVulkan() {
        JXNativeNode node = JXVulkanRenderer.mount(JXControls.button("Load", null));

        JXVulkanRenderer.layout(node, 160, 40);

        assertEquals("button", node.getType());
        assertEquals(160, node.getBounds().width);
        assertEquals(40, node.getBounds().height);
        assertNotNull(node.getProperty("label"));
    }
}
