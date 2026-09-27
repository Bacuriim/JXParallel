import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.sun.javafx.application.PlatformImpl;
import javafx.scene.Node;
import javafx.scene.Parent;

/**
 * Loads every FXML of an application with a given FXMLLoader class and writes one line per file:
 * path, outcome (OK or the root cause) and how many of the created nodes are com.jxparallel.fx types.
 * Run once with the original classes and javafx.fxml.FXMLLoader, once with the migrated classes and
 * com.jxparallel.fx.fxml.FXMLLoader, then compare the two files.
 *
 *   java -cp <app classes and dependencies>;target/fx-mirrors FxmlLoadCheck <loader class> <resource root> <out>
 */
public class FxmlLoadCheck {

    public static void main(String[] args) throws Exception {
        Class<?> loaderType = Class.forName(args[0]);
        Path root = Paths.get(args[1]);
        CountDownLatch started = new CountDownLatch(1);
        PlatformImpl.startup(started::countDown);
        started.await();
        List<Path> files;
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(p -> p.toString().endsWith(".fxml")).sorted().collect(Collectors.toList());
        }
        try (PrintWriter out = new PrintWriter(new File(args[2]), "UTF-8")) {
            for (Path file : files) {
                String resource = root.relativize(file).toString().replace(File.separatorChar, '/');
                out.println(resource + "\t" + load(loaderType, Thread.currentThread().getContextClassLoader().getResource(resource)));
                out.flush();
            }
        }
        System.exit(0);
    }

    /** Loads off the FX thread with a timeout, so a controller waiting on a server cannot stall the run. */
    private static String load(Class<?> loaderType, URL url) throws InterruptedException {
        AtomicReference<String> result = new AtomicReference<>("TIMEOUT");
        Thread worker = new Thread(() -> {
            try {
                Object loader = loaderType.getConstructor(URL.class).newInstance(url);
                Object root = loaderType.getMethod("load").invoke(loader);
                int[] counts = new int[2];
                count(root, counts);
                result.set("OK\t" + counts[0] + "/" + counts[1] + " JX");
            } catch (Throwable e) {
                e.printStackTrace();
                Throwable cause = e instanceof InvocationTargetException ? e.getCause() : e;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                String message = String.valueOf(cause.getMessage()).split("\n")[0];
                result.set("FAIL\t" + cause.getClass().getName() + ": " + message.substring(0, Math.min(160, message.length())));
            }
        });
        worker.setDaemon(true);
        worker.start();
        worker.join(30_000);
        return result.get();
    }

    /** Counts nodes, and those created through the JX API (a JX node, or the JavaFX peer a JX node created). */
    private static void count(Object node, int[] counts) {
        try {
            node = node.getClass().getMethod("fxPeer").invoke(node);
        } catch (ReflectiveOperationException notJx) {
            // a JavaFX node
        }
        if (!(node instanceof Node)) {
            return;
        }
        counts[1]++;
        if (node.getClass().getName().startsWith("com.jxparallel.fx.")) {
            counts[0]++;
        }
        if (node instanceof Parent) {
            for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
                count(child, counts);
            }
        }
    }
}
