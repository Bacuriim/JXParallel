package com.jxparallel.ui.vulkan;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

public final class JXVulkanHelloApp {
    private JXVulkanHelloApp() {
    }

    public static void main(String[] args) {
        JXElement content = JXElement.of("column",
                JXProps.builder().set("gap", 12).build(),
                JXElement.of("button", JXProps.builder().set("label", "Vulkan").build()),
                JXElement.of("input", JXProps.builder().set("value", "JXParallel").build()),
                JXElement.of("progress", JXProps.builder().set("progress", 0.65).build()),
                JXElement.of("slider", JXProps.builder().set("min", 0).set("max", 100)
                        .set("value", 65).build()));
        JXVulkanWindow window = new JXVulkanWindow("JXParallel LWJGL + Vulkan");
        window.setContent(content);
        window.show();
    }
}
