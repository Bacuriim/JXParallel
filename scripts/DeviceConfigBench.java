import java.io.File;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sun.javafx.application.PlatformImpl;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;

/**
 * Runs the same DeviceConfig workload against one build (JavaFX or JXParallel) and writes one CSV line
 * per step and iteration: wall time, process CPU, bytes allocated, GC time, the longest gap between UI
 * frames during the step, and heap after a GC. The workload repeats what the controllers do: load every
 * FXML, search records and build the rows, open the record with the most attributes.
 * App classes are called by reflection, so this one binary runs against both builds.
 *
 *   java -cp <build classes>;<build>/src;<dependencies>;<this> DeviceConfigBench <name> <FXMLLoader class> <src dir> <out.csv> [term] [iterations]
 */
public class DeviceConfigBench {
    private static final com.sun.management.OperatingSystemMXBean OS =
            (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final AtomicLong lastFrame = new AtomicLong();
    private static final AtomicLong maxGap = new AtomicLong();

    private static String name;
    private static final java.util.concurrent.ExecutorService LOADER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fxml-loader");
        t.setDaemon(true);
        return t;
    });
    private static PrintWriter csv;

    public static void main(String[] args) throws Exception {
        name = args[0];
        Class<?> loaderType = Class.forName(args[1]);
        Path src = Paths.get(args[2]);
        csv = new PrintWriter(new File(args[3]), "UTF-8");
        String term = args.length > 4 ? args[4] : "10";
        int iterations = args.length > 5 ? Integer.parseInt(args[5]) : 5;
        long recordId = args.length > 6 ? Long.parseLong(args[6]) : 0;
        csv.println("build,iteration,step,wall_ms,cpu_ms,alloc_mb,gc_ms,max_ui_gap_ms,heap_after_mb,items");

        CountDownLatch started = new CountDownLatch(1);
        PlatformImpl.startup(started::countDown);
        started.await();
        onFx(() -> {
            new AnimationTimer() {
                @Override
                public void handle(long now) {
                    long previous = lastFrame.getAndSet(now);
                    if (previous != 0) {
                        maxGap.accumulateAndGet(now - previous, Math::max);
                    }
                }
            }.start();
            return null;
        });

        // Startup: from JVM start to the database ready and the first screen built.
        measure(0, "startup", () -> {
            load(loaderType, url("main/resources/views/LoginView.fxml"));
            return 1;
        }, ManagementFactory.getRuntimeMXBean().getStartTime());
        Object admin = admin();
        Class.forName("main.java.Main").getField("user").set(null, admin);
        Object[] record = biggestRecord(recordId);
        List<String> fxml;
        try (Stream<Path> files = Files.walk(src.resolve("main/resources/views"))) {
            fxml = files.filter(p -> p.toString().endsWith(".fxml"))
                    .map(p -> src.relativize(p).toString().replace(File.separatorChar, '/')).sorted().collect(Collectors.toList());
        }
        System.out.println(name + ": " + fxml.size() + " FXML, record " + record[0] + " (" + record[1] + "), term '" + term + "'");

        for (int i = 1; i <= iterations; i++) {
            int iteration = i;
            measure(iteration, "fxml_all", () -> {
                int ok = 0;
                for (String file : fxml) {
                    if (loadWithTimeout(loaderType, url(file))) {
                        ok++;
                    }
                }
                return ok;
            }, 0);
            measure(iteration, "search", () -> onFx(() -> search(term)), 0);
            measure(iteration, "open_record", () -> onFx(() -> open(loaderType, record)), 0);
        }
        csv.close();
        System.exit(0);
    }

    // ------------------------------------------------------------------ workload

    private static int search(String term) throws Exception {
        Object records = Class.forName("main.java.models.RecordRepository").newInstance();
        List<?> entities = (List<?>) call(records, "listAllData", new Class<?>[]{String.class, boolean.class}, term, false);
        List<?> versions = (List<?>) call(Class.forName("main.java.models.VersionRepository").newInstance(), "listAllData", new Class<?>[0]);
        List<?> models = (List<?>) call(Class.forName("main.java.models.VersionModelRepository").newInstance(), "listAllData", new Class<?>[0]);
        Class<?> rowType = Class.forName("main.java.rows.RecordRow");
        Class<?> entityType = Class.forName("main.java.models.entities.RecordEntity");
        Method setUpdate = rowType.getMethod("setUpdate", List.class, List.class);
        for (Object entity : entities) {
            Object row = rowType.getConstructor(entityType).newInstance(entity);
            setUpdate.invoke(row, new ArrayList<>(versions), new ArrayList<>(models));
        }
        return entities.size();
    }

    /** What CommandsController.loadDevice does once the record is known. */
    private static int open(Class<?> loaderType, Object[] r) throws Exception {
        Class<?> manager = Class.forName("main.java.controllers.commands.ControllersManager");
        String view = (String) manager.getMethod("getViewPathByProductModelName", String.class).invoke(null, (String) r[1]);
        Object loader = loaderType.getConstructor(URL.class).newInstance(DeviceConfigBench.class.getResource(view));
        loaderType.getMethod("load").invoke(loader);
        Object controller = loaderType.getMethod("getController").invoke(loader);
        Class<?> device = Class.forName("main.java.controllers.commands.DeviceController");
        device.getMethod("setModel", String.class).invoke(controller, (String) r[1]);
        device.getMethod("setLine", String.class).invoke(controller, (String) r[5]);
        device.getMethod("setSoftware", String.class).invoke(controller, "DeviceConfig bench");
        device.getMethod("setProduct", String.class).invoke(controller, (String) r[2]);
        device.getMethod("setVersion", String.class).invoke(controller, String.valueOf(r[6]));
        device.getMethod("setDeviceInfo", long.class, String.class, String.class, String.class, String.class, String.class, String.class)
                .invoke(controller, (long) r[0], (String) r[3], (String) r[1], (String) r[2], (String) r[4], "", "bench");
        return 1;
    }

    /** The record whose categories have the most attributes, among models that have a screen. */
    private static Object[] biggestRecord(long wanted) throws Exception {
        Object session = session();
        Object query = call(session, "createQuery", new Class<?>[]{String.class},
                "SELECT c.recordId, count(a.id) FROM RecordCategoryAtributeEntity a, RecordCategoryEntity c "
                        + "WHERE a.categoryId = c.id GROUP BY c.recordId ORDER BY count(a.id) DESC");
        call(query, "setMaxResults", new Class<?>[]{int.class}, 100);
        List<?> ranked = (List<?>) call(query, "list", new Class<?>[0]);
        Object records = Class.forName("main.java.models.RecordRepository").newInstance();
        Class<?> manager = Class.forName("main.java.controllers.commands.ControllersManager");
        for (Object row : ranked) {
            long id = ((Number) ((Object[]) row)[0]).longValue();
            if (wanted != 0 && id != wanted) {
                continue;
            }
            Object r = call(records, "findById", new Class<?>[]{long.class}, id);
            if (r == null) {
                continue;
            }
            String model = (String) call(r, "getModel", new Class<?>[0]);
            if (manager.getMethod("getViewPathByProductModelName", String.class).invoke(null, model) != null) {
                call(session, "close", new Class<?>[0]);
                return new Object[]{id, model, call(r, "getProductCode", new Class<?>[0]), call(r, "getProductDesc", new Class<?>[0]),
                        call(r, "getClient", new Class<?>[0]), call(r, "getLine", new Class<?>[0]), call(r, "getVersion", new Class<?>[0]), ((Object[]) row)[1]};
            }
        }
        throw new IllegalStateException("no record with a screen");
    }

    private static Object admin() throws Exception {
        List<?> users = (List<?>) call(Class.forName("main.java.models.UserRepository").newInstance(), "listAllData", new Class<?>[0]);
        for (Object u : users) {
            Object group = call(u, "getGroup", new Class<?>[0]);
            if (group != null && ((Number) call(group, "getId", new Class<?>[0])).longValue() == 1) {
                return u;
            }
        }
        return users.get(0);
    }

    private static Object session() throws Exception {
        return Class.forName("main.java.HibernateConnection").getMethod("getInstance").invoke(null);
    }

    private static void load(Class<?> loaderType, URL url) throws Exception {
        Object loader = loaderType.getConstructor(URL.class).newInstance(url);
        loaderType.getMethod("load").invoke(loader);
    }

    /** Off the FX thread with a timeout, like FxmlLoadCheck: a screen waiting on a server cannot stall the run. */
    private static boolean loadWithTimeout(Class<?> loaderType, URL url) throws InterruptedException {
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        // One long-lived thread, so its allocations are still counted when the step ends.
        LOADER.execute(() -> {
            try {
                load(loaderType, url);
                done.complete(true);
            } catch (Throwable e) {
                done.complete(false);
            }
        });
        try {
            return done.get(30, TimeUnit.SECONDS);
        } catch (Exception timeout) {
            return false;
        }
    }

    // ------------------------------------------------------------------ measurement

    private static void measure(int iteration, String step, Callable<Integer> work, long startNanosOverride) throws Exception {
        long cpu0 = OS.getProcessCpuTime();
        long alloc0 = allocated();
        long gc0 = gcMillis();
        maxGap.set(0);
        lastFrame.set(0);
        long t0 = System.nanoTime();
        int items = work.call();
        long wallMs = startNanosOverride > 0
                ? System.currentTimeMillis() - startNanosOverride
                : (System.nanoTime() - t0) / 1_000_000;
        long cpuMs = (OS.getProcessCpuTime() - cpu0) / 1_000_000;
        double allocMb = (allocated() - alloc0) / 1048576.0;
        long gcMs = gcMillis() - gc0;
        long gapMs = maxGap.get() / 1_000_000;
        System.gc();
        Thread.sleep(200);
        double heapMb = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / 1048576.0;
        String line = String.format(java.util.Locale.ROOT, "%s,%d,%s,%d,%d,%.1f,%d,%d,%.1f,%d",
                name, iteration, step, wallMs, cpuMs, allocMb, gcMs, gapMs, heapMb, items);
        csv.println(line);
        csv.flush();
        System.out.println(line);
    }

    private static long allocated() {
        long sum = 0;
        for (long b : THREADS.getThreadAllocatedBytes(THREADS.getAllThreadIds())) {
            sum += Math.max(0, b);
        }
        return sum;
    }

    private static long gcMillis() {
        long sum = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            sum += Math.max(0, gc.getCollectionTime());
        }
        return sum;
    }

    // ------------------------------------------------------------------ helpers

    private static URL url(String resource) {
        return Thread.currentThread().getContextClassLoader().getResource(resource);
    }

    private static <T> T onFx(Callable<T> work) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(work.call());
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        });
        return result.get(10, TimeUnit.MINUTES);
    }

    private static Object call(Object target, String method, Class<?>[] types, Object... args) throws Exception {
        Method m = findMethod(target.getClass(), method, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private static Method findMethod(Class<?> type, String method, Class<?>[] types) throws NoSuchMethodException {
        try {
            return type.getMethod(method, types);
        } catch (NoSuchMethodException e) {
            for (Class<?> i : type.getInterfaces()) {
                try {
                    return i.getMethod(method, types);
                } catch (NoSuchMethodException ignored) {
                    // next
                }
            }
            throw e;
        }
    }
}
