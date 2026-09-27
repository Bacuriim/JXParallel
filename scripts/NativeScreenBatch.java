import java.io.File;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import com.sun.javafx.application.PlatformImpl;
import javafx.application.Platform;

/**
 * Native-mode check of every FXML under a folder, in one JVM (run with -Djx.backend=native): loads
 * each screen through the JX API with its real controller, draws it with NanoVG, and records per
 * screen the classes without a native element and the JX API the native layer did not have.
 * Writes screens.csv, a PNG per screen, and prints what blocks the most screens.
 *
 *   java -Djx.backend=native -cp <jx build>;<src>;<deps>;<jxparallel-ui deps>;<this> NativeScreenBatch <src dir> <out dir> [width] [height]
 */
public class NativeScreenBatch {

    public static void main(String[] args) throws Exception {
        Path src = Paths.get(args[0]);
        File out = new File(args[1]);
        int width = args.length > 2 ? Integer.parseInt(args[2]) : 1000;
        int height = args.length > 3 ? Integer.parseInt(args[3]) : 800;
        out.mkdirs();
        CountDownLatch started = new CountDownLatch(1);
        PlatformImpl.startup(started::countDown);
        started.await();
        setAdmin();
        List<String> screens;
        try (Stream<Path> files = Files.walk(src.resolve("main/resources/views"))) {
            screens = files.filter(p -> p.toString().endsWith(".fxml"))
                    .map(p -> src.relativize(p).toString().replace(File.separatorChar, '/')).sorted().collect(Collectors.toList());
        }
        Map<String, Integer> classScreens = new HashMap<>();
        Map<String, Integer> apiScreens = new HashMap<>();
        int clean = 0;
        int failed = 0;
        try (PrintWriter csv = new PrintWriter(new File(out, "screens.csv"), "UTF-8")) {
            csv.println("fxml;status;load_ms;draw_ms;classes_without_native;missing_api;error");
            for (String fxml : screens) {
                Set<String> missingBefore = new TreeSet<>(com.jxparallel.fx.nativeimpl.Native.missing());
                Set<String> unsupportedBefore = new TreeSet<>(com.jxparallel.fx.nativeimpl.NativeElements.unsupported());
                com.jxparallel.fx.nativeimpl.Native.missing().clear();
                com.jxparallel.fx.nativeimpl.NativeElements.unsupported().clear();
                String status = "OK";
                String error = "";
                long loadMs = -1;
                long drawMs = -1;
                try {
                    long t0 = System.nanoTime();
                    Object element = loadElement(Thread.currentThread().getContextClassLoader().getResource(fxml));
                    loadMs = (System.nanoTime() - t0) / 1_000_000;
                    long t1 = System.nanoTime();
                    int[] argb = com.jxparallel.ui.native2d.JXWindow.captureNanoVG((com.jxparallel.ui.JXElement) element, width, height);
                    drawMs = (System.nanoTime() - t1) / 1_000_000;
                    java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    image.setRGB(0, 0, width, height, argb, 0, width);
                    ImageIO.write(image, "png", new File(out, fxml.replace("main/resources/views/", "").replace('/', '_').replace(".fxml", ".png")));
                } catch (Throwable e) {
                    Throwable cause = e;
                    while (cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    status = "FAIL";
                    StringBuilder where = new StringBuilder();
                    for (StackTraceElement f : cause.getStackTrace()) {
                        if (where.length() < 400 && (f.getClassName().startsWith("main.java") || f.getClassName().startsWith("com.jxparallel"))) {
                            where.append(" <- ").append(f.getClassName().substring(f.getClassName().lastIndexOf('.') + 1)).append(':').append(f.getLineNumber());
                        }
                    }
                    error = cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage()).split("\n")[0] + where;
                    failed++;
                }
                Set<String> classes = new TreeSet<>(com.jxparallel.fx.nativeimpl.NativeElements.unsupported());
                Set<String> api = new TreeSet<>(com.jxparallel.fx.nativeimpl.Native.missing());
                for (String c : classes) {
                    classScreens.merge(c, 1, Integer::sum);
                }
                for (String a : api) {
                    apiScreens.merge(a, 1, Integer::sum);
                }
                if ("OK".equals(status) && classes.isEmpty() && api.isEmpty()) {
                    clean++;
                }
                csv.println(fxml + ";" + status + ";" + loadMs + ";" + drawMs + ";" + String.join(" ", shortNames(classes)) + ";"
                        + String.join(" ", api) + ";" + error.replace(';', ','));
                csv.flush();
                com.jxparallel.fx.nativeimpl.Native.missing().addAll(missingBefore);
                com.jxparallel.fx.nativeimpl.NativeElements.unsupported().addAll(unsupportedBefore);
                System.out.println(status + " " + fxml + " classes=" + classes.size() + " api=" + api.size());
            }
        }
        System.out.printf("%n%d telas: %d sem lacuna nenhuma, %d com erro%n", screens.size(), clean, failed);
        System.out.println("\nclasses sem elemento nativo, por telas:");
        classScreens.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(30)
                .forEach(e -> System.out.printf("%4d %s%n", e.getValue(), e.getKey()));
        System.out.println("\nAPI que faltou, por telas:");
        apiScreens.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(40)
                .forEach(e -> System.out.printf("%4d %s%n", e.getValue(), e.getKey()));
        System.exit(0);
    }

    private static List<String> shortNames(Set<String> classes) {
        List<String> out = new ArrayList<>();
        for (String c : classes) {
            out.add(c.substring(c.lastIndexOf('.') + 1));
        }
        return out;
    }

    /** Loads on the FX thread (controllers expect it) with a timeout, and builds the element tree there. */
    private static Object loadElement(URL url) throws Exception {
        CompletableFuture<Object> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                Object root = new com.jxparallel.fx.fxml.FXMLLoader(url).load();
                done.complete(com.jxparallel.fx.nativeimpl.NativeElements.toElement(
                        (com.jxparallel.fx.nativeimpl.NativeModel) com.jxparallel.fx.Fx.fx(root)));
            } catch (Throwable e) {
                done.completeExceptionally(e);
            }
        });
        return done.get(60, TimeUnit.SECONDS);
    }

    private static void setAdmin() throws Exception {
        Object repo = Class.forName("main.java.models.UserRepository").newInstance();
        List<?> users = (List<?>) repo.getClass().getMethod("listAllData").invoke(repo);
        Object chosen = users.get(0);
        for (Object u : users) {
            Object group = u.getClass().getMethod("getGroup").invoke(u);
            if (group != null && ((Number) group.getClass().getMethod("getId").invoke(group)).longValue() == 1) {
                chosen = u;
                break;
            }
        }
        Class.forName("main.java.Main").getField("user").set(null, chosen);
    }
}
