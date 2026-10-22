package com.jxparallel.ui.native2d;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The controls sheet ({@link JXControlsSnapshotTest#controls}) painted by NanoVG, the renderer of 32-bit Java 8, in a
 * hidden GLFW window ({@link JXWindow#captureNanoVG}): the pixels are read back from the framebuffer
 * ({@code glReadPixels}), saved to {@code target/nanovg} and compared with what the software painter draws of the same
 * tree (which the reviewed Skia image is compared with by {@link JXSoftwareRendererTest}) and, in the light theme,
 * with that Skia image itself.
 *
 * <p>The test runs only on a 32-bit JVM (the case it is for; Skia has no natives there) or with
 * {@code -Djx.nanovg.sheet=true}, and is skipped, with the reason, when no OpenGL context can be made. It is also a
 * program: {@code java -cp ... com.jxparallel.ui.native2d.JXNanoVGSheetTest [outputDirectory]} prints the numbers.
 */
class JXNanoVGSheetTest {
    private static final int WIDTH = 760;
    private static final int HEIGHT = 620;
    private static final int CHANNEL_TOLERANCE = 48;
    /** Share of the pixels that may differ from the software painter (glyph edges; 0.6% measured) and from Skia (1.1%). */
    private static final double MAX_DIFFERENT = 0.01;
    private static final double MAX_DIFFERENT_SKIA = 0.015;

    /** The pixels of the sheet in the named theme by NanoVG, and the same by the software painter. */
    static final class Sheet {
        final int[] nano;
        final int[] software;
        final List<int[]> unlike = new ArrayList<int[]>();

        Sheet(String theme) {
            nano = JXWindow.captureNanoVG(JXControlsSnapshotTest.controls(theme), WIDTH, HEIGHT);
            JXNativeNode root = JXNativeNode.createBackendNode(JXControlsSnapshotTest.controls(theme));
            root.layoutForBackend(WIDTH, HEIGHT);
            JXSoftwareFrame frame = new JXSoftwareFrame(true);
            frame.paint(root, WIDTH, HEIGHT, 1f);
            software = java.util.Arrays.copyOf(frame.pixels(), WIDTH * HEIGHT);
            JXSoftwareRendererTest.unlikeSkia(root, unlike);
        }
    }

    static double different(int[] a, int[] b, List<int[]> unlike) {
        int different = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (JXSoftwareRendererTest.inside(unlike, x, y)) {
                    continue;
                }
                int p = a[y * WIDTH + x];
                int q = b[y * WIDTH + x];
                for (int shift = 0; shift <= 16; shift += 8) {
                    if (Math.abs(((p >> shift) & 0xFF) - ((q >> shift) & 0xFF)) > CHANNEL_TOLERANCE) {
                        different++;
                        break;
                    }
                }
            }
        }
        return different / (double) (WIDTH * HEIGHT);
    }

    static void write(Path file, int[] argb) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, WIDTH, HEIGHT, argb, 0, WIDTH);
        Files.createDirectories(file.getParent());
        ImageIO.write(image, "png", file.toFile());
    }

    private static int[] golden() throws IOException {
        BufferedImage image = ImageIO.read(Paths.get("src/test/resources/snapshots/controls-skia.png").toFile());
        int[] argb = new int[WIDTH * HEIGHT];
        image.getRGB(0, 0, WIDTH, HEIGHT, argb, 0, WIDTH);
        return argb;
    }

    @Test
    void nanoVGPaintsTheSheetLikeTheSoftwarePainterInBothThemes() throws IOException {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"), "the fonts of the comparison are the Windows ones");
        assumeTrue("32".equals(System.getProperty("sun.arch.data.model")) || Boolean.getBoolean("jx.nanovg.sheet"),
                "NanoVG is checked on the 32-bit JVM it is for (or with -Djx.nanovg.sheet=true)");
        Sheet light;
        Sheet dark;
        try {
            light = new Sheet(null);
            dark = new Sheet("dark");
        } catch (Throwable e) {
            assumeTrue(false, "no OpenGL context for NanoVG here: " + e);
            return;
        }
        Path out = Paths.get("target/nanovg");
        write(out.resolve("nanovg-light.png"), light.nano);
        write(out.resolve("nanovg-dark.png"), dark.nano);
        double lightRatio = different(light.nano, light.software, light.unlike);
        double darkRatio = different(dark.nano, dark.software, dark.unlike);
        double skiaRatio = different(light.nano, golden(), light.unlike);
        assertTrue(lightRatio <= MAX_DIFFERENT, String.format(Locale.ROOT, "light: %.2f%% differ from the software painter", lightRatio * 100));
        assertTrue(darkRatio <= MAX_DIFFERENT, String.format(Locale.ROOT, "dark: %.2f%% differ from the software painter", darkRatio * 100));
        assertTrue(skiaRatio <= MAX_DIFFERENT_SKIA, String.format(Locale.ROOT, "light: %.2f%% differ from the Skia image", skiaRatio * 100));
    }

    /** Captures both themes, writes the images and prints how far they are from the software painter and from Skia. */
    public static void main(String[] args) throws Exception {
        Path out = Paths.get(args.length > 0 ? args[0] : "target/nanovg");
        System.out.println("JVM " + System.getProperty("java.version") + " " + System.getProperty("sun.arch.data.model")
                + "-bit, " + System.getProperty("java.vm.name"));
        try {
            for (String theme : new String[] {null, "dark"}) {
                String name = theme == null ? "light" : "dark";
                Sheet sheet = new Sheet(theme);
                write(out.resolve("nanovg-" + name + ".png"), sheet.nano);
                write(out.resolve("software-" + name + ".png"), sheet.software);
                System.out.printf(Locale.ROOT, "%s: NanoVG vs software %.3f%% of the pixels differ%n", name,
                        100 * different(sheet.nano, sheet.software, sheet.unlike));
                if (theme == null) {
                    int[] skia = golden();
                    System.out.printf(Locale.ROOT, "light: NanoVG vs Skia image %.3f%%, software vs Skia image %.3f%%%n",
                            100 * different(sheet.nano, skia, sheet.unlike), 100 * different(sheet.software, skia, sheet.unlike));
                }
            }
        } finally {
            System.out.flush();
            System.exit(0); // the display thread of the windows lives until the JVM ends
        }
    }
}
