package com.jxparallel.examples;

import static com.jxparallel.examples.JvmMetrics.metric;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import com.jxparallel.core.JXParallel;
import com.jxparallel.fxml.FXMLLoaderService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;

/**
 * Loads the 20 generated FXML screens (form + controller each) in {@code PASSES} passes within
 * one JVM. Pass 1 is cold (class loading, JIT off), later passes are warm.
 * <ul>
 *   <li>{@code javafx}: plain {@link FXMLLoader}, sequential, on the FX thread (typical usage).</li>
 *   <li>{@code jxparallel}: {@link FXMLLoaderService#loadAsync} for all screens at once on the
 *       JXParallel worker pool, with its FXML cache.</li>
 * </ul>
 * Run each mode in a fresh JVM. Emits {@code JX_METRIC key=value} lines.
 */
public final class FxmlLoadBenchmark {
    private static final int SCREENS = Integer.getInteger("jx.fxml.screens", 20);
    private static final int PASSES = Integer.getInteger("jx.fxml.passes", 3);

    private FxmlLoadBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "javafx";
        JvmMetrics.startSampler();
        metric("screens", SCREENS);
        metric("processors", Runtime.getRuntime().availableProcessors());

        long toolkitStart = System.nanoTime();
        CountDownLatch toolkit = new CountDownLatch(1);
        Platform.startup(toolkit::countDown);
        toolkit.await();
        metric("toolkit_start_ns", System.nanoTime() - toolkitStart);

        FXMLLoaderService service = null;
        if ("jxparallel".equals(mode)) {
            JXParallel.start();
            service = new FXMLLoaderService();
        }
        for (int pass = 1; pass <= PASSES; pass++) {
            long cpu = JvmMetrics.cpuNanos();
            long allocated = JvmMetrics.allocatedBytes();
            long start = System.nanoTime();
            long[] firstReady = new long[1];
            List<Parent> views = service == null ? loadSequentialOnFxThread(firstReady) : loadParallel(service, firstReady);
            long end = System.nanoTime();
            metric("pass" + pass + "_total_ns", end - start);
            metric("pass" + pass + "_first_screen_ns", firstReady[0] - start);
            metric("pass" + pass + "_cpu_ns", JvmMetrics.cpuNanos() - cpu);
            metric("pass" + pass + "_allocated_bytes", JvmMetrics.allocatedBytes() - allocated);
            metric("pass" + pass + "_initialized_screens", initialized(views));
        }
        if (service != null) {
            metric("cache_hits", service.cache().getHits());
            metric("cache_misses", service.cache().getMisses());
        }
        JvmMetrics.emitProcessMetrics(); // before shutdown: allocation is summed over live threads
        if (service != null) {
            JXParallel.shutdown();
        }
        Platform.exit();
        System.exit(0);
    }

    private static List<Parent> loadSequentialOnFxThread(long[] firstReady) throws Exception {
        List<Parent> views = new ArrayList<Parent>(SCREENS);
        CompletableFuture<Void> done = new CompletableFuture<Void>();
        Platform.runLater(() -> {
            try {
                for (int i = 1; i <= SCREENS; i++) {
                    views.add(FXMLLoader.<Parent>load(screen(i)));
                    if (i == 1) {
                        firstReady[0] = System.nanoTime();
                    }
                }
                done.complete(null);
            } catch (Exception e) {
                done.completeExceptionally(e);
            }
        });
        done.get();
        return views;
    }

    private static List<Parent> loadParallel(FXMLLoaderService service, long[] firstReady) {
        List<CompletableFuture<Parent>> futures = new ArrayList<CompletableFuture<Parent>>(SCREENS);
        for (int i = 1; i <= SCREENS; i++) {
            futures.add(service.loadAsync(path(i)));
        }
        CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0])).join();
        firstReady[0] = System.nanoTime();
        List<Parent> views = new ArrayList<Parent>(SCREENS);
        for (CompletableFuture<Parent> future : futures) {
            views.add(future.join());
        }
        return views;
    }

    /** Proves the controller ran: initialize() sets the status label to "Loaded". */
    private static int initialized(List<Parent> views) {
        int count = 0;
        for (Parent view : views) {
            Object status = view.lookup("#status");
            if (status instanceof Label && "Loaded".equals(((Label) status).getText())) {
                count++;
            }
        }
        return count;
    }

    private static String path(int i) {
        return String.format("/fxmlbench/screen%02d.fxml", i);
    }

    private static URL screen(int i) {
        return FxmlLoadBenchmark.class.getResource(path(i));
    }
}
