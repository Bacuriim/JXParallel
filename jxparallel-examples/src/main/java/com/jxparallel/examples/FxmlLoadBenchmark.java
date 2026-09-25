package com.jxparallel.examples;

import static com.jxparallel.examples.JvmMetrics.metric;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import com.jxparallel.core.JXParallel;
import com.jxparallel.fxml.FXMLLoaderService;
import com.jxparallel.fxml.FxmlTemplate;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;

/**
 * Loads the 20 generated FXML screens (form + controller each) in {@code PASSES} passes within
 * one JVM. Pass 1 is cold (class loading, JIT off), later passes are warm.
 * <ul>
 *   <li>{@code javafx}: plain {@link FXMLLoader}, sequential, on the FX thread (typical usage).</li>
 *   <li>{@code jxparallel}: {@link FXMLLoaderService#loadAsync} on the JXParallel worker pool,
 *       with its template cache: the first screen alone, then the other screens in parallel.</li>
 *   <li>{@code jxparallel-fxmlloader}: the same, but with FXMLLoader inside the service
 *       ({@code -Djx.fxml.template=false}), to isolate what the template adds.</li>
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
        if ("jxparallel-fxmlloader".equals(mode)) {
            System.setProperty("jx.fxml.template", "false"); // same pool, FXMLLoader inside
        }
        JvmMetrics.startSampler();
        metric("screens", SCREENS);
        metric("processors", Runtime.getRuntime().availableProcessors());

        long toolkitStart = System.nanoTime();
        CountDownLatch toolkit = new CountDownLatch(1);
        Platform.startup(toolkit::countDown);
        toolkit.await();
        metric("toolkit_start_ns", System.nanoTime() - toolkitStart);

        FXMLLoaderService service = null;
        if (mode.startsWith("jxparallel")) {
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
            long[] shape = new long[3];
            for (Parent view : views) {
                shape(view, shape);
            }
            metric("pass" + pass + "_nodes", shape[0]);
            metric("pass" + pass + "_spinner_value_sum", shape[1]);
            metric("pass" + pass + "_grid_row_index_sum", shape[2]);
        }
        if (service != null) {
            metric("cache_hits", service.cache().getHits());
            metric("cache_misses", service.cache().getMisses());
            int templates = 0;
            for (int i = 1; i <= SCREENS; i++) {
                if (service.cache().get(path(i)) instanceof FxmlTemplate) {
                    templates++;
                }
            }
            metric("templates_cached", templates);
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

    /**
     * The screen the user opened is loaded alone first, so it does not compete for cores with
     * the others; the remaining screens are then preloaded in parallel.
     */
    private static List<Parent> loadParallel(FXMLLoaderService service, long[] firstReady) {
        List<Parent> views = new ArrayList<Parent>(SCREENS);
        views.add(service.loadAsync(path(1)).join());
        firstReady[0] = System.nanoTime();
        List<CompletableFuture<Parent>> rest = new ArrayList<CompletableFuture<Parent>>(SCREENS - 1);
        for (int i = 2; i <= SCREENS; i++) {
            rest.add(service.loadAsync(path(i)));
        }
        for (CompletableFuture<Parent> future : rest) {
            views.add(future.join());
        }
        return views;
    }

    /**
     * Structural fingerprint, so both loaders can be checked to build the same graph: counts every
     * reachable node (children, tab content, scroll pane content, toolbar items) and sums spinner
     * values and GridPane row indexes.
     */
    private static void shape(Object object, long[] shape) {
        if (object == null) {
            return;
        }
        shape[0]++;
        if (object instanceof javafx.scene.Node) {
            Integer row = javafx.scene.layout.GridPane.getRowIndex((javafx.scene.Node) object);
            shape[2] += row == null ? 0 : row;
        }
        if (object instanceof javafx.scene.control.Spinner) {
            Object value = ((javafx.scene.control.Spinner<?>) object).getValue();
            shape[1] += value instanceof Number ? ((Number) value).longValue() : 0;
        }
        if (object instanceof javafx.scene.control.TabPane) {
            for (javafx.scene.control.Tab tab : ((javafx.scene.control.TabPane) object).getTabs()) {
                shape[0]++;
                shape(tab.getContent(), shape);
            }
        } else if (object instanceof javafx.scene.control.ScrollPane) {
            shape(((javafx.scene.control.ScrollPane) object).getContent(), shape);
        } else if (object instanceof javafx.scene.control.ToolBar) {
            for (javafx.scene.Node item : ((javafx.scene.control.ToolBar) object).getItems()) {
                shape(item, shape);
            }
        } else if (object instanceof javafx.scene.layout.BorderPane) {
            javafx.scene.layout.BorderPane pane = (javafx.scene.layout.BorderPane) object;
            shape(pane.getTop(), shape);
            shape(pane.getCenter(), shape);
            shape(pane.getBottom(), shape);
        } else if (object instanceof javafx.scene.layout.Pane) {
            for (javafx.scene.Node child : ((javafx.scene.layout.Pane) object).getChildren()) {
                shape(child, shape);
            }
        }
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
