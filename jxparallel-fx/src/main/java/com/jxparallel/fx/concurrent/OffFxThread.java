package com.jxparallel.fx.concurrent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.InputEvent;
import javafx.stage.Window;

/**
 * Runs blocking work (database, network) off the JavaFX thread without changing the calling code.
 * Called on the JavaFX thread, {@link #call} hands the work to a background thread and keeps the
 * event loop running until it finishes: windows keep repainting (Windows never marks them "Not
 * responding"), input is ignored and the cursor shows wait, so no handler can run in the middle of
 * the caller. Called on any other thread it just runs the work.
 */
public final class OffFxThread {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "jx-off-fx-thread");
        t.setDaemon(true);
        return t;
    });
    private static final EventHandler<InputEvent> SWALLOW = InputEvent::consume;

    private OffFxThread() {
    }

    public static <T> T call(Callable<T> work) throws Exception {
        if (!Platform.isFxApplicationThread()) {
            return work.call();
        }
        CompletableFuture<T> result = CompletableFuture.supplyAsync(() -> {
            try {
                return work.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, EXECUTOR);
        if (!result.isDone()) {
            Object key = new Object();
            result.whenComplete((value, error) -> Platform.runLater(() -> NestedLoop.finished(key)));
            List<Scene> blocked = blockInput();
            try {
                NestedLoop.run(key);
            } finally {
                unblock(blocked);
            }
        }
        try {
            return result.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw (Error) cause;
        }
    }

    /**
     * A proxy of {@code target} whose methods named in {@code blocking} run through {@link #call}.
     * Interfaces returned by any method (a query from a session, say) are wrapped the same way.
     */
    @SuppressWarnings("unchecked")
    public static <T> T wrap(T target, Class<T> type, Collection<String> blocking) {
        Set<String> names = new HashSet<>(blocking);
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Object value;
            try {
                value = names.contains(method.getName())
                        ? call(() -> invoke(method, target, args))
                        : invoke(method, target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
            Class<?> returned = method.getReturnType();
            if (value != null && returned.isInterface() && returned.isInstance(value) && sameLibrary(type, returned)) {
                return wrap(value, (Class<Object>) returned, names);
            }
            return value;
        });
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Exception {
        return method.invoke(target, args);
    }

    private static boolean sameLibrary(Class<?> a, Class<?> b) {
        String pa = a.getName();
        String pb = b.getName();
        int dot = pa.indexOf('.', pa.indexOf('.') + 1);
        return dot > 0 && pb.startsWith(pa.substring(0, dot + 1)) || pb.startsWith("javax.persistence.");
    }

    private static List<Scene> blockInput() {
        List<Scene> scenes = new ArrayList<>();
        for (Window window : NestedLoop.windows()) {
            Scene scene = window.getScene();
            if (scene != null && window.isShowing()) {
                scene.addEventFilter(InputEvent.ANY, SWALLOW);
                scene.setCursor(Cursor.WAIT);
                scenes.add(scene);
            }
        }
        return scenes;
    }

    private static void unblock(List<Scene> scenes) {
        for (Scene scene : scenes) {
            scene.removeEventFilter(InputEvent.ANY, SWALLOW);
            scene.setCursor(null);
        }
    }

    /**
     * Nested event loops, which must exit innermost first: work that finishes while a later call is
     * still waiting exits once that later loop has returned. JavaFX 9+ has Platform.enterNestedEventLoop
     * and Window.getWindows; JavaFX 8 only internals.
     */
    private static final class NestedLoop {
        private static final java.util.Deque<Object> OPEN = new java.util.ArrayDeque<>();
        private static final Set<Object> DONE = new HashSet<>();

        static void run(Object key) {
            OPEN.push(key);
            try {
                enter(key);
            } finally {
                OPEN.pop();
                Object outer = OPEN.peek();
                if (outer != null && DONE.remove(outer)) {
                    Platform.runLater(() -> exit(outer));
                }
            }
        }

        static void finished(Object key) {
            if (OPEN.peek() == key) {
                exit(key);
            } else {
                DONE.add(key);
            }
        }

        private static void enter(Object key) {
            try {
                Platform.class.getMethod("enterNestedEventLoop", Object.class).invoke(null, key);
            } catch (NoSuchMethodException java8) {
                com.sun.javafx.tk.Toolkit.getToolkit().enterNestedEventLoop(key);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        private static void exit(Object key) {
            try {
                Platform.class.getMethod("exitNestedEventLoop", Object.class, Object.class).invoke(null, key, null);
            } catch (NoSuchMethodException java8) {
                com.sun.javafx.tk.Toolkit.getToolkit().exitNestedEventLoop(key, null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        @SuppressWarnings("unchecked")
        static List<Window> windows() {
            List<Window> out = new ArrayList<>();
            try {
                out.addAll((List<Window>) Window.class.getMethod("getWindows").invoke(null));
            } catch (NoSuchMethodException java8) {
                try {
                    java.util.Iterator<Window> it = (java.util.Iterator<Window>) Window.class.getMethod("impl_getWindows").invoke(null);
                    it.forEachRemaining(out::add);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            return out;
        }
    }
}
