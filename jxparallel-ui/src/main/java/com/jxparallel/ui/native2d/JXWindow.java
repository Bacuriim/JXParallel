package com.jxparallel.ui.native2d;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.vulkan.JXVulkanWindow;

/**
 * Compatibility facade for the native window API. Rendering is performed by Vulkan.
 */
public final class JXWindow implements AutoCloseable {
    private final JXVulkanWindow delegate;

    public JXWindow(String title) {
        delegate = new JXVulkanWindow(title);
    }

    public void setContent(JXElement element) {
        delegate.setContent(element);
    }

    public void show() {
        delegate.show();
    }

    public void setOnFirstPaint(Runnable callback) {
        delegate.setOnFirstPaint(callback);
    }

    public void invokeLater(Runnable action) {
        delegate.invokeLater(action);
    }

    public void renderNow() {
        delegate.requestRender();
    }

    public void dispose() {
        delegate.close();
    }

    @Override
    public void close() {
        dispose();
    }
}
