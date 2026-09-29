package com.jxparallel.fx.nativeimpl;

import java.util.function.Consumer;

import com.jxparallel.ui.native2d.JXInputEvent;
import com.jxparallel.ui.native2d.JXNativeNode;
import com.jxparallel.ui.native2d.JXWindow;

/**
 * Where a native scene is shown: a {@link JXWindow}, or nothing when {@code -Djx.headless=true}
 * (tests and screen checks run the whole runtime, layout and input included, without a display).
 */
interface NativeSurface {
    void setRoot(JXNativeNode tree, Object lock);

    void requestRender();

    void setTitle(String title);

    String getTitle();

    void setSize(int width, int height);

    void setResizable(boolean resizable);

    void setDecorated(boolean decorated);

    void setFloating(boolean floating);

    void setFocusOnShow(boolean focus);

    void setMinSize(int width, int height);

    void setPosition(int x, int y);

    void setInputListener(Consumer<JXInputEvent> listener);

    void open();

    void dispose();

    static NativeSurface create(String title) {
        if (Boolean.getBoolean("jx.headless")) {
            return new Headless(title);
        }
        JXWindow window = new JXWindow(title);
        window.setOnFrame(NativeFrames::presented);
        return new Window(window);
    }

    /** A real window on the display thread. */
    final class Window implements NativeSurface {
        private final JXWindow window;

        Window(JXWindow window) {
            this.window = window;
        }

        @Override
        public void setRoot(JXNativeNode tree, Object lock) {
            window.setRoot(tree, lock);
        }

        @Override
        public void requestRender() {
            window.requestRender();
        }

        @Override
        public void setTitle(String title) {
            window.setTitle(title);
        }

        @Override
        public String getTitle() {
            return window.getTitle();
        }

        @Override
        public void setSize(int width, int height) {
            window.setSize(width, height);
        }

        @Override
        public void setResizable(boolean resizable) {
            window.setResizable(resizable);
        }

        @Override
        public void setDecorated(boolean decorated) {
            window.setDecorated(decorated);
        }

        @Override
        public void setFloating(boolean floating) {
            window.setFloating(floating);
        }

        @Override
        public void setFocusOnShow(boolean focus) {
            window.setFocusOnShow(focus);
        }

        @Override
        public void setMinSize(int width, int height) {
            window.setMinSize(width, height);
        }

        @Override
        public void setPosition(int x, int y) {
            window.setPosition(x, y);
        }

        @Override
        public void setInputListener(Consumer<JXInputEvent> listener) {
            window.setInputListener(listener);
        }

        @Override
        public void open() {
            window.open();
        }

        @Override
        public void dispose() {
            window.dispose();
        }
    }

    /** No window: keeps what a window would know, for tests. */
    final class Headless implements NativeSurface {
        String title;
        int width;
        int height;
        boolean open;
        boolean disposed;
        JXNativeNode tree;
        int renders;

        Headless(String title) {
            this.title = title;
        }

        @Override
        public void setRoot(JXNativeNode tree, Object lock) {
            this.tree = tree;
        }

        @Override
        public void requestRender() {
            renders++;
        }

        @Override
        public void setTitle(String title) {
            this.title = title;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public void setSize(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public void setResizable(boolean resizable) {
        }

        @Override
        public void setDecorated(boolean decorated) {
        }

        @Override
        public void setFloating(boolean floating) {
        }

        @Override
        public void setFocusOnShow(boolean focus) {
        }

        @Override
        public void setMinSize(int width, int height) {
        }

        @Override
        public void setPosition(int x, int y) {
        }

        @Override
        public void setInputListener(Consumer<JXInputEvent> listener) {
        }

        @Override
        public void open() {
            open = true;
        }

        @Override
        public void dispose() {
            disposed = true;
            open = false;
        }
    }
}
