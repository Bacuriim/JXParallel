package com.jxparallel.examples.orders;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.jxparallel.core.CancellationToken;
import com.jxparallel.core.JXParallel;
import com.jxparallel.core.Task;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.native2d.JXFileDialogs;
import com.jxparallel.ui.native2d.JXWindow;

/** The {@link Host} of the real program: a {@link JXWindow} for the UI thread, JXParallel for the workers. */
final class WindowHost implements Host {
    private static final List<String> CSV = Collections.singletonList("*.csv");

    private final JXWindow window;
    private final AtomicBoolean renderPending = new AtomicBoolean();
    private volatile Supplier<JXElement> screen;
    private volatile boolean ready;

    WindowHost(JXWindow window) {
        this.window = window;
    }

    /** What to show: {@code view.build(shell)}. */
    void setScreen(Supplier<JXElement> screen) {
        this.screen = screen;
    }

    /** The window painted once: from now on a change of state renders. Called on the display thread. */
    void markReady() {
        ready = true;
    }

    @Override
    public void runOnUi(Runnable action) {
        window.invokeLater(action);
    }

    @Override
    public void requestRender() {
        if (!ready || !renderPending.compareAndSet(false, true)) {
            return; // one rebuild covers every change made before it runs
        }
        window.invokeLater(() -> {
            renderPending.set(false);
            window.setContent(screen.get());
        });
    }

    @Override
    public <T> CompletableFuture<T> background(Callable<T> work, CancellationToken token) {
        return JXParallel.background(Task.of(work).withCancellationToken(token));
    }

    @Override
    public File chooseCsvToOpen() {
        List<File> files = JXFileDialogs.open("Open orders (CSV)", null, null, CSV, "CSV files", false);
        return files.isEmpty() ? null : files.get(0);
    }

    @Override
    public File chooseCsvToSave(String suggestedName) {
        return JXFileDialogs.save("Export orders (CSV)", null, suggestedName, CSV, "CSV files");
    }

    @Override
    public void exit() {
        window.close();
    }
}
