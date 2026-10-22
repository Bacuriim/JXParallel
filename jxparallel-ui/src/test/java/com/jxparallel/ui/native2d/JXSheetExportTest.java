package com.jxparallel.ui.native2d;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Writes the controls sheet (the element tree of {@link JXControlsSnapshotTest}) in the light and the dark theme, painted
 * by Skia and by the software painter, to {@code target/sheets}, for people to look at and for the NanoVG comparison to
 * use as its reference. It asserts only that the images were written; nothing is compared.
 */
class JXSheetExportTest {
    static final int WIDTH = 760;
    static final int HEIGHT = 620;
    static final Path OUT = Paths.get("target/sheets");

    /** The sheet painted by Skia on a CPU raster, as ARGB ints. */
    static int[] skia(String theme) {
        JXNativeNode root = JXSkiaRenderer.mount(JXControlsSnapshotTest.controls(theme));
        try (Surface surface = Surface.makeRasterN32Premul(WIDTH, HEIGHT)) {
            JXSkiaRenderer.paint(root, surface.getCanvas(), WIDTH, HEIGHT);
            try (Image image = surface.makeImageSnapshot(); Bitmap bitmap = new Bitmap()) {
                assertTrue(bitmap.allocN32Pixels(WIDTH, HEIGHT));
                assertTrue(image.readPixels(bitmap));
                byte[] raw = bitmap.readPixels();
                int[] argb = new int[WIDTH * HEIGHT];
                for (int i = 0; i < argb.length; i++) { // BGRA bytes on Windows
                    argb[i] = 0xFF000000 | (raw[4 * i + 2] & 0xFF) << 16 | (raw[4 * i + 1] & 0xFF) << 8 | (raw[4 * i] & 0xFF);
                }
                return argb;
            }
        }
    }

    /** The sheet painted by the software painter, as ARGB ints. */
    static int[] software(String theme) {
        JXNativeNode root = JXNativeNode.createBackendNode(JXControlsSnapshotTest.controls(theme));
        root.layoutForBackend(WIDTH, HEIGHT);
        JXSoftwareFrame frame = new JXSoftwareFrame(true);
        frame.paint(root, WIDTH, HEIGHT, 1f);
        return java.util.Arrays.copyOf(frame.pixels(), WIDTH * HEIGHT);
    }

    static void write(String name, int[] argb, int x, int y, int w, int h, int scale) throws IOException {
        BufferedImage image = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < h * scale; j++) {
            for (int i = 0; i < w * scale; i++) {
                image.setRGB(i, j, argb[(y + j / scale) * WIDTH + x + i / scale]);
            }
        }
        Files.createDirectories(OUT);
        ImageIO.write(image, "png", OUT.resolve(name).toFile());
    }

    private static final int TILE_W = 300;
    private static final int TILE_H = 270;

    private static JXElement item(String label) {
        return JXElement.of("menuitem", JXProps.builder().set("label", label).build());
    }

    private static JXElement menu() {
        return JXElement.of("menubutton", JXProps.builder().set("label", "Actions").set("showing", true).build(),
                item("Open"), JXElement.of("menuitem", JXProps.builder().set("label", "Locked").set("disabled", true).build()),
                JXElement.of("menuitem", JXProps.builder().set("separator", true).set("label", "").build()),
                JXElement.of("menu", JXProps.builder().set("label", "Export").build(), item("PDF"), item("CSV")), item("Quit"));
    }

    /** A tile: the tree mounted in a small window, {@code act} done to it (pointer, keys), then painted by Skia. */
    private static int[] tile(JXElement tree, java.util.function.Consumer<JXNativeNode> act) {
        JXNativeNode root = JXNativeNode.createBackendNode(JXElement.of("column", JXProps.builder().set("padding", 10.0)
                .set("theme", "light").build(), tree));
        root.layoutForBackend(TILE_W, TILE_H);
        act.accept(root);
        root.layoutForBackend(TILE_W, TILE_H);
        try (Surface surface = Surface.makeRasterN32Premul(TILE_W, TILE_H)) {
            JXSkiaRenderer.paint(root, surface.getCanvas(), TILE_W, TILE_H);
            try (Image image = surface.makeImageSnapshot(); Bitmap bitmap = new Bitmap()) {
                assertTrue(bitmap.allocN32Pixels(TILE_W, TILE_H));
                assertTrue(image.readPixels(bitmap));
                byte[] raw = bitmap.readPixels();
                int[] argb = new int[TILE_W * TILE_H];
                for (int i = 0; i < argb.length; i++) {
                    argb[i] = 0xFF000000 | (raw[4 * i + 2] & 0xFF) << 16 | (raw[4 * i + 1] & 0xFF) << 8 | (raw[4 * i] & 0xFF);
                }
                return argb;
            }
        }
    }

    private static void key(JXNativeNode root, int code) {
        root.routeKey(new JXKeyEvent(code, '\0'), root.getChildren().get(0));
    }

    /** Four tiles in a row: a menu under the pointer, the same by the keys with a submenu, a select list and a date picker. */
    @Test
    void writesThePopupsWithAHighlight() throws IOException {
        assumeTrue(System.getProperty("os.name").startsWith("Windows") && "64".equals(System.getProperty("sun.arch.data.model")));
        JXElement select = JXElement.of("select", JXProps.builder().set("value", "Recife").set("overlay", true).set("showing", true)
                .set("options", new String[] {"Fortaleza", "Recife", "Salvador", "Natal", "Maceió"}).build());
        JXElement date = JXElement.of("datepicker", JXProps.builder().set("value", "2026-09-28").set("overlay", true).set("showing", true)
                .build());
        int[][] tiles = {
            tile(menu(), root -> {
                JXNativeNode row = root.getChildren().get(0).getChildren().get(4);
                root.routeHover(row.getX() + row.getWidth() / 2, row.getY() + row.getHeight() / 2);
            }),
            tile(menu(), root -> {
                key(root, 264);
                key(root, 264);
                key(root, 262);
                key(root, 264);
            }),
            tile(select, root -> {
                JXOverlays.Popup popup = JXOverlays.popup(root.getChildren().get(0));
                root.routeHover(popup.cells[3][0] + 10, popup.cells[3][1] + 8);
            }),
            tile(date, root -> {
                key(root, 262);
                key(root, 264);
            }),
        };
        int[] sheet = new int[tiles.length * TILE_W * TILE_H];
        for (int t = 0; t < tiles.length; t++) {
            for (int y = 0; y < TILE_H; y++) {
                System.arraycopy(tiles[t], y * TILE_W, sheet, y * tiles.length * TILE_W + t * TILE_W, TILE_W);
            }
        }
        BufferedImage image = new BufferedImage(tiles.length * TILE_W, TILE_H, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, tiles.length * TILE_W, TILE_H, sheet, 0, tiles.length * TILE_W);
        Files.createDirectories(OUT);
        ImageIO.write(image, "png", OUT.resolve("popups-highlight.png").toFile());
        assertTrue(Files.exists(OUT.resolve("popups-highlight.png")));
    }

    @Test
    void writesTheSheets() throws IOException {
        assumeTrue(System.getProperty("os.name").startsWith("Windows") && "64".equals(System.getProperty("sun.arch.data.model")));
        for (String theme : new String[] {null, "dark"}) {
            String suffix = theme == null ? "light" : "dark";
            write("skia-" + suffix + ".png", skia(theme), 0, 0, WIDTH, HEIGHT, 1);
            write("software-" + suffix + ".png", software(theme), 0, 0, WIDTH, HEIGHT, 1);
            write("buttons-" + suffix + ".png", skia(theme), 0, 0, 640, 56, 3);
        }
        assertTrue(Files.exists(OUT.resolve("skia-light.png")));
    }
}
