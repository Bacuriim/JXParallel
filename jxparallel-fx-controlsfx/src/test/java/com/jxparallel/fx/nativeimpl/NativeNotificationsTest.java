package com.jxparallel.fx.nativeimpl;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.jxparallel.fx.Fx;
import com.jxparallel.fx.controlsfx.control.Notifications;
import com.jxparallel.fx.util.Duration;
import com.jxparallel.ui.native2d.JXInputEvent;
import com.jxparallel.ui.native2d.JXNativeNode;

import com.sun.javafx.application.PlatformImpl;
import javafx.application.Platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ControlsFX notifications in native mode: a small undecorated window that hides itself. */
class NativeNotificationsTest {
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

    static <T> T fx(Callable<T> work) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(work.call());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result.get(20, TimeUnit.SECONDS);
    }

    @Test
    void builderChainsAndShowOpensAWindowThatHidesAfterItsDelay() throws Exception {
        AtomicInteger actions = new AtomicInteger();
        Notifications[] built = new Notifications[2];
        fx(() -> {
            built[0] = Notifications.create();
            built[1] = built[0].title("Salvo").text("Ficha 7816 salva").darkStyle()
                    .hideAfter(Duration.millis(400)).onAction(e -> actions.incrementAndGet());
            built[1].showInformation();
            return null;
        });
        assertSame(built[0], built[1], "the builder returns itself");
        NativeScene shown = fx(() -> NativeRuntime.SCENES.get(NativeRuntime.SCENES.size() - 1));
        assertTrue(fx(() -> shown.showing));
        JXNativeNode root = fx(() -> {
            shown.render();
            return shown.node(shown.rootModel());
        });
        fx(() -> {
            double x = root.getX() + 5;
            double y = root.getY() + 5;
            NativeEvents.handle(shown, JXInputEvent.pointer(JXInputEvent.Kind.PRESS, x, y, 0, 0, 1, true, java.util.Collections.emptyList()));
            NativeEvents.handle(shown, JXInputEvent.pointer(JXInputEvent.Kind.RELEASE, x, y, 0, 0, 1, false, java.util.Collections.emptyList()));
            return null;
        });
        assertEquals(1, actions.get(), "clicking the notification runs its action");
        Thread.sleep(900);
        fx(() -> null);
        assertTrue(!fx(() -> shown.showing), "hidden after hideAfter");
    }
}
