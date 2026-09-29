package com.jxparallel.fx.nativeimpl;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;

import com.jxparallel.fx.Fx;
import com.jxparallel.ui.native2d.JXInputEvent;
import com.jxparallel.ui.native2d.JXNativeNode;

import com.sun.javafx.application.PlatformImpl;
import javafx.application.Platform;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs native-mode code on the JavaFX application thread (the native event loop) and drives the
 * headless runtime with the same raw input a window delivers: clicks, keys, typed text, wheel.
 */
abstract class NativeTestSupport {
    @BeforeAll
    static void startToolkit() throws InterruptedException {
        assertTrue(Fx.NATIVE, "run with -Djx.backend=native");
        CountDownLatch started = new CountDownLatch(1);
        try {
            PlatformImpl.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        started.await();
        Platform.setImplicitExit(false);
    }

    /** Runs on the application thread and returns its result (rethrowing its failure). */
    static <T> T fx(Callable<T> work) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return work.call();
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(work.call());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        try {
            return result.get(20, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    static void fx(Runnable work) throws Exception {
        fx(() -> {
            work.run();
            return null;
        });
    }

    /** Waits until pending runLater tasks (renders included) ran. */
    static void settle() throws Exception {
        for (int i = 0; i < 3; i++) {
            fx(() -> null);
        }
    }

    /** Shows a scene with {@code root} in a headless native window of the given size. */
    static NativeScene show(Object root, int width, int height) throws Exception {
        return fx(() -> {
            com.jxparallel.fx.stage.Stage stage = new com.jxparallel.fx.stage.Stage();
            stage.setScene(new com.jxparallel.fx.scene.Scene((com.jxparallel.fx.scene.Parent) root, width, height));
            stage.show();
            NativeScene s = NativeRuntime.sceneOf(model(stage));
            assertNotNull(s);
            return s;
        });
    }

    static NativeModel model(Object jx) {
        return (NativeModel) ((Fx.Backed) jx).fxPeer();
    }

    /** The laid-out node of a JX node in a shown scene. */
    static JXNativeNode node(NativeScene s, Object jx) throws Exception {
        return fx(() -> {
            s.render();
            JXNativeNode n = s.node(model(jx));
            assertNotNull(n, "not rendered: " + jx);
            return n;
        });
    }

    static void press(NativeScene s, double x, double y, int clicks, int modifiers) throws Exception {
        fx(() -> NativeEvents.handle(s, JXInputEvent.pointer(JXInputEvent.Kind.PRESS, x, y, JXInputEvent.BUTTON_PRIMARY,
                modifiers, clicks, true, java.util.Collections.<JXNativeNode>emptyList())));
    }

    static void release(NativeScene s, double x, double y, int clicks, int modifiers) throws Exception {
        fx(() -> NativeEvents.handle(s, JXInputEvent.pointer(JXInputEvent.Kind.RELEASE, x, y, JXInputEvent.BUTTON_PRIMARY,
                modifiers, clicks, false, java.util.Collections.<JXNativeNode>emptyList())));
    }

    static void move(NativeScene s, double x, double y, boolean buttonDown) throws Exception {
        fx(() -> NativeEvents.handle(s, JXInputEvent.pointer(JXInputEvent.Kind.MOVE, x, y, JXInputEvent.BUTTON_PRIMARY,
                0, 0, buttonDown, java.util.Collections.<JXNativeNode>emptyList())));
    }

    static void click(NativeScene s, double x, double y) throws Exception {
        click(s, x, y, 1, 0);
    }

    static void click(NativeScene s, double x, double y, int clicks, int modifiers) throws Exception {
        press(s, x, y, clicks, modifiers);
        release(s, x, y, clicks, modifiers);
        settle();
    }

    /** Clicks the centre of a node. */
    static void click(NativeScene s, Object jx) throws Exception {
        JXNativeNode n = node(s, jx);
        click(s, n.getX() + n.getWidth() / 2.0, n.getY() + n.getHeight() / 2.0);
    }

    static void rightClick(NativeScene s, double x, double y) throws Exception {
        fx(() -> {
            NativeEvents.handle(s, JXInputEvent.pointer(JXInputEvent.Kind.PRESS, x, y, JXInputEvent.BUTTON_SECONDARY, 0, 1, true,
                    java.util.Collections.<JXNativeNode>emptyList()));
            NativeEvents.handle(s, JXInputEvent.pointer(JXInputEvent.Kind.RELEASE, x, y, JXInputEvent.BUTTON_SECONDARY, 0, 1, false,
                    java.util.Collections.<JXNativeNode>emptyList()));
        });
        settle();
    }

    static void key(NativeScene s, String key) throws Exception {
        key(s, key, 0);
    }

    static void key(NativeScene s, String key, int modifiers) throws Exception {
        fx(() -> {
            NativeEvents.handle(s, JXInputEvent.key(JXInputEvent.Kind.KEY_PRESS, key, modifiers));
            NativeEvents.handle(s, JXInputEvent.key(JXInputEvent.Kind.KEY_RELEASE, key, modifiers));
        });
        settle();
    }

    static void type(NativeScene s, String text) throws Exception {
        fx(() -> {
            text.codePoints().forEach(c -> NativeEvents.handle(s, JXInputEvent.character(c, 0)));
        });
        settle();
    }

    static void wheel(NativeScene s, double x, double y, double lines) throws Exception {
        fx(() -> NativeEvents.handle(s, JXInputEvent.scroll(x, y, 0, lines, 0, java.util.Collections.<JXNativeNode>emptyList())));
        settle();
    }

    static void close(NativeScene s) throws Exception {
        fx(() -> NativeRuntime.hide(s.stage));
    }

    static <T> List<T> list(List<T> items) {
        return items;
    }
}
