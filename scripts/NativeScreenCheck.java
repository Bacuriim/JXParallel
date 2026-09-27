import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import com.sun.javafx.application.PlatformImpl;
import javafx.application.Platform;

/**
 * Renders one DeviceConfig FXML to a PNG through the JX API, in the mode the JVM runs in:
 * with -Djx.backend=native the JX nodes are native models drawn by Skia (jxparallel-ui), otherwise
 * they are JavaFX nodes captured with Scene.snapshot. Running it twice gives the two images to
 * compare, plus (native mode) the API the screen needed and the native layer does not have yet.
 *
 *   java [-Djx.backend=native] -cp <jx build>;<src>;<deps>;<jxparallel-ui deps>;<this> NativeScreenCheck <fxml resource> <out.png> <width> <height>
 */
public class NativeScreenCheck {

    public static void main(String[] args) throws Exception {
        String fxml = args[0];
        File out = new File(args[1]);
        int width = Integer.parseInt(args[2]);
        int height = Integer.parseInt(args[3]);
        CountDownLatch started = new CountDownLatch(1);
        PlatformImpl.startup(started::countDown);
        started.await();
        setAdmin();
        boolean nativeMode = com.jxparallel.fx.Fx.NATIVE;
        URL url = Thread.currentThread().getContextClassLoader().getResource(fxml);
        CompletableFuture<Object> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                long t0 = System.nanoTime();
                Object root = new com.jxparallel.fx.fxml.FXMLLoader(url).load();
                long loaded = System.nanoTime();
                if (nativeMode) {
                    Object element = com.jxparallel.fx.nativeimpl.NativeElements.toElement(
                            (com.jxparallel.fx.nativeimpl.NativeModel) com.jxparallel.fx.Fx.fx(root));
                    System.out.printf("nativo: %s carregado em %d ms%n", fxml, (loaded - t0) / 1_000_000);
                    done.complete(element);
                    return;
                } else {
                    renderJavaFx(root, out, width, height);
                }
                System.out.printf("%s: %s carregado em %d ms, desenhado em %d ms -> %s%n", nativeMode ? "nativo" : "javafx", fxml,
                        (loaded - t0) / 1_000_000, (System.nanoTime() - loaded) / 1_000_000, out);
                done.complete(null);
            } catch (Throwable e) {
                done.completeExceptionally(e);
            }
        });
        try {
            Object element = done.get(2, TimeUnit.MINUTES);
            if (element != null) {
                long t0 = System.nanoTime();
                renderNative((com.jxparallel.ui.JXElement) element, out, width, height);
                System.out.printf("nativo: desenhado (NanoVG) em %d ms -> %s%n", (System.nanoTime() - t0) / 1_000_000, out);
            }
        } finally {
            if (nativeMode) {
                System.out.println("classes sem elemento nativo: " + com.jxparallel.fx.nativeimpl.NativeElements.unsupported());
                System.out.println("API que faltou no modo nativo (" + com.jxparallel.fx.nativeimpl.Native.missing().size() + "):");
                for (String m : com.jxparallel.fx.nativeimpl.Native.missing()) {
                    System.out.println("  " + m);
                }
            }
            System.exit(0);
        }
    }

    /** NanoVG in a hidden OpenGL window: the renderer DeviceConfig gets on Java 8 32-bit. */
    private static void renderNative(com.jxparallel.ui.JXElement element, File out, int width, int height) throws Exception {
        int[] argb = com.jxparallel.ui.native2d.JXWindow.captureNanoVG(element, width, height);
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, argb, 0, width);
        ImageIO.write(image, "png", out);
    }
    private static void renderJavaFx(Object root, File out, int width, int height) throws Exception {
        javafx.scene.Parent fx = (javafx.scene.Parent) com.jxparallel.fx.Fx.fx(root);
        javafx.scene.Scene scene = new javafx.scene.Scene(fx, width, height);
        javafx.scene.image.WritableImage image = scene.snapshot(null);
        ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(image, null), "png", out);
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
