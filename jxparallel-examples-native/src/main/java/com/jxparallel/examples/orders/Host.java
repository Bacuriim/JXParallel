package com.jxparallel.examples.orders;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import com.jxparallel.core.CancellationToken;

/**
 * What the controllers need from the program around them, so they run the same in the window and in a test: the UI
 * thread, the worker pool, the file dialogs. No controller touches a window or a static.
 */
public interface Host {
    /** Runs {@code action} on the UI thread (later, or at once when already there). Safe from any thread. */
    void runOnUi(Runnable action);

    /** Builds the screen again from the controllers' state and shows it. Coalesced; safe from any thread. */
    void requestRender();

    /** Runs {@code work} on the JXParallel worker pool; the token is the one the user's Cancel button trips. */
    <T> CompletableFuture<T> background(Callable<T> work, CancellationToken token);

    /** The CSV the user picked in an open dialog, or {@code null}. Blocks until the dialog closes: call it from a worker. */
    File chooseCsvToOpen();

    /** The file the user chose in a save dialog, or {@code null}. Blocks: call it from a worker. */
    File chooseCsvToSave(String suggestedName);

    /** Closes the window. */
    void exit();
}
