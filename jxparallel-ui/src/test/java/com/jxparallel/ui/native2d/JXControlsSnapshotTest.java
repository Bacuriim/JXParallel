package com.jxparallel.ui.native2d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.EncoderPNG;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Every element type in its states (focus, hover, pressed, selected, disabled, collapsed, open
 * popups) painted by Skia on a CPU raster and compared with a reviewed golden image, like
 * {@link JXSnapshotTest}: a change in how anything is drawn shows up here.
 */
class JXControlsSnapshotTest {
    private static final Path GOLDEN = Paths.get("src/test/resources/snapshots");
    private static final Path ACTUAL = Paths.get("target/snapshots");
    private static final int WIDTH = 760;
    private static final int HEIGHT = 620;
    private static final int CHANNEL_TOLERANCE = 8;
    private static final double MAX_DIFFERENT_PIXELS = 0.002;

    private static JXProps.Builder p() {
        return JXProps.builder();
    }

    private static JXElement e(String type, JXProps.Builder props, JXElement... children) {
        return JXElement.of(type, props.build(), children);
    }

    private static JXElement text(String value) {
        return e("#text", p().set("value", value));
    }

    static JXElement controls() {
        JXElement buttons = e("row", p().set("gap", 8.0),
                e("button", p().set("label", "Normal")),
                e("button", p().set("label", "Hover").set("hover", true)),
                e("button", p().set("label", "Pressed").set("pressed", true)),
                e("button", p().set("label", "Focused").set("focused", true)),
                e("button", p().set("label", "Default").set("defaultButton", true)),
                e("button", p().set("label", "Disabled").set("disabled", true)),
                e("toggle", p().set("label", "Toggle").set("selected", true)),
                e("button", p().set("label", "Graphic"), e("pane", p().set("prefWidth", 12.0).set("prefHeight", 12.0)
                        .set("background", 0xFF2D6CDF).set("backgroundRadius", 6.0))));
        JXElement checks = e("row", p().set("gap", 12.0).set("alignment", "CENTER_LEFT"),
                e("checkbox", p().set("label", "Checked").set("checked", true)),
                e("checkbox", p().set("label", "Mixed").set("indeterminate", true)),
                e("checkbox", p().set("label", "Off")),
                e("radio", p().set("label", "Radio on").set("checked", true)),
                e("radio", p().set("label", "Radio off").set("focused", true)),
                e("hyperlink", p().set("label", "Link")),
                e("hyperlink", p().set("label", "Visited").set("visited", true).set("hover", true)));
        JXElement fields = e("row", p().set("gap", 8.0),
                e("input", p().set("value", "Ada Lovelace").set("prefColumnCount", 9.0)),
                e("input", p().set("value", "").set("prompt", "Prompt").set("prefColumnCount", 7.0)),
                e("input", p().set("value", "selected").set("caret", 3).set("anchor", 8).set("focused", true)
                        .set("prefColumnCount", 7.0)),
                e("password", p().set("value", "secret").set("prefColumnCount", 6.0)),
                e("input", p().set("value", "right").set("textAlignment", "RIGHT").set("prefColumnCount", 6.0)));
        JXElement combos = e("row", p().set("gap", 8.0),
                e("select", p().set("value", "Fortaleza").set("options", new Object[]{"Fortaleza", "Recife"})),
                e("select", p().set("value", "").set("prompt", "Escolha").set("options", new Object[]{"a"})),
                e("select", p().set("value", "Edit").set("editable", true).set("options", new Object[]{"Edit"})),
                e("spinner", p().set("value", "42").set("hoverPart", "up")),
                e("datepicker", p().set("value", "28/09/2026")));
        JXElement progress = e("row", p().set("gap", 12.0).set("alignment", "CENTER_LEFT"),
                e("progress", p().set("progress", 0.6)),
                e("indicator", p().set("progress", 0.35)),
                e("slider", p().set("value", 30.0)),
                e("slider", p().set("value", 70.0).set("orientation", "vertical").set("prefHeight", 40.0)),
                e("separator", p().set("orientation", "vertical").set("prefHeight", 40.0)),
                e("#text", p().set("value", "Bold 14").set("bold", true).set("fontSize", 14.0).set("textFill", 0xFFC0392B)),
                e("#text", p().set("value", "underline").set("underline", true)),
                e("#text", p().set("value", "styled").set("background", 0xFFFFF3CD).set("borderColor", 0xFFE0A800)
                        .set("backgroundRadius", 4.0).set("borderRadius", 4.0).set("padding", new double[]{2, 6, 2, 6})));
        JXElement[] cells = new JXElement[5];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = e("cell", p().set("value", "Item " + i).set("odd", i % 2 == 1).set("selected", i == 1).set("listFocused", true));
        }
        JXElement list = e("list", p().set("itemCount", 12).set("prefWidth", 150.0).set("prefHeight", 100.0), cells);
        JXElement[] rows = new JXElement[3];
        for (int r = 0; r < rows.length; r++) {
            rows[r] = e("tablerow", p().set("odd", r % 2 == 1).set("selected", r == 0),
                    e("cell", p().set("value", "Row " + r).set("tableCell", true).set("paintBackground", false).set("selected", r == 0)),
                    e("cell", p().set("value", "Cidade").set("tableCell", true).set("paintBackground", false).set("selected", r == 0)));
        }
        JXElement table = e("table", p().set("columns", new String[]{"Nome", "Cidade"}).set("columnWidths", new double[]{70, 80})
                .set("itemCount", 3).set("prefWidth", 200.0).set("prefHeight", 100.0), rows);
        JXElement scroll = e("scroll", p().set("prefWidth", 120.0).set("prefHeight", 100.0).set("vvalue", 0.1),
                e("column", p().set("gap", 4.0), text("Scroll 1"), text("Scroll 2"), text("Scroll 3"),
                        e("pane", p().set("prefWidth", 200.0).set("prefHeight", 200.0))));
        JXElement tabs = e("tabs", p().set("titles", new String[]{"Dados", "Histórico", "Log"}).set("selected", 1)
                .set("prefWidth", 220.0).set("prefHeight", 100.0), text("Tab content"));
        JXElement lists = e("row", p().set("gap", 10.0), list, table, scroll, tabs);
        JXElement titled = e("titled", p().set("label", "Expanded").set("prefWidth", 200.0), e("column", p(), text("Inside")));
        JXElement collapsed = e("titled", p().set("label", "Collapsed").set("expanded", false).set("prefWidth", 200.0), text("hidden"));
        JXElement pages = e("pagination", p().set("pageCount", 8).set("current", 3).set("maxPageIndicatorCount", 5)
                .set("prefWidth", 240.0), text("Page 4"));
        int[] pixels = new int[16 * 16];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (i / 16 + i % 16) % 2 == 0 ? 0xFF2D6CDF : 0x00000000;
        }
        JXElement image = e("image", p().set("pixels", pixels).set("imageWidth", 16.0).set("imageHeight", 16.0)
                .set("fitWidth", 32.0).set("fitHeight", 32.0));
        JXElement blurred = e("#text", p().set("value", "Blurred").set("blur", 3.0));
        JXElement faded = e("#text", p().set("value", "Faded").set("opacity", 0.4));
        JXElement flow = e("textflow", p().set("prefWidth", 120.0), text("Text "), e("#text", p().set("value", "flow").set("bold", true)));
        JXElement misc = e("row", p().set("gap", 12.0), titled, collapsed, pages,
                e("column", p().set("gap", 6.0), image, blurred, faded, flow));
        JXElement calendar = e("calendar", p().set("year", 2026).set("month", 9).set("firstDayOfWeek", 7)
                .set("selectedYear", 2026).set("selectedMonth", 9).set("selectedDay", 28).set("title", "Setembro 2026")
                .set("weekdays", new String[]{"dom", "seg", "ter", "qua", "qui", "sex", "sáb"}));
        JXElement popup = e("popup", p().set("padding", new double[]{1, 1, 1, 1}),
                e("cell", p().set("value", "Copiar").set("padding", new double[]{4, 20, 4, 12})),
                e("separator", p()),
                e("cell", p().set("value", "Colar").set("selected", true).set("listFocused", true).set("padding", new double[]{4, 20, 4, 12})));
        JXElement tooltip = e("tooltip", p().set("label", "Dica do campo"));
        JXElement bar = e("buttonbar", p().set("prefWidth", 260.0), e("button", p().set("label", "OK").set("defaultButton", true)),
                e("button", p().set("label", "Cancelar")));
        JXElement overlays = e("row", p().set("gap", 16.0).set("alignment", "TOP_LEFT"), calendar,
                e("column", p().set("gap", 10.0), popup, tooltip, bar));
        return e("column", p().set("gap", 10.0).set("padding", new double[]{10, 10, 10, 10}),
                buttons, checks, fields, combos, progress, lists, misc, overlays);
    }

    @Test
    void everyControlPaintsLikeTheReviewedImage() throws IOException {
        assumeTrue(System.getProperty("os.name").startsWith("Windows")
                && "64".equals(System.getProperty("sun.arch.data.model")), "golden image is for 64-bit Windows");
        JXNativeNode root = JXSkiaRenderer.mount(controls());
        byte[] png;
        byte[] pixels;
        try (Surface surface = Surface.makeRasterN32Premul(WIDTH, HEIGHT)) {
            JXSkiaRenderer.paint(root, surface.getCanvas(), WIDTH, HEIGHT);
            try (Image image = surface.makeImageSnapshot()) {
                png = EncoderPNG.encode(image).getBytes();
                pixels = pixels(image);
            }
        }
        Path file = GOLDEN.resolve("controls-skia.png");
        if (!Files.exists(file)) {
            Files.createDirectories(GOLDEN);
            Files.write(file, png);
            fail("created snapshot " + file + ", review it and run again");
        }
        byte[] expected;
        try (Image image = Image.makeFromEncoded(Files.readAllBytes(file))) {
            assertEquals(WIDTH, image.getWidth());
            expected = pixels(image);
        }
        int different = 0;
        for (int i = 0; i < pixels.length; i += 4) {
            for (int c = 0; c < 4; c++) {
                if (Math.abs((pixels[i + c] & 0xFF) - (expected[i + c] & 0xFF)) > CHANNEL_TOLERANCE) {
                    different++;
                    break;
                }
            }
        }
        double ratio = different / (double) (WIDTH * HEIGHT);
        if (ratio > MAX_DIFFERENT_PIXELS) {
            Files.createDirectories(ACTUAL);
            Files.write(ACTUAL.resolve("controls-skia.png"), png);
            fail(String.format("%d pixels (%.3f%%) differ from the golden image, new render in %s", different, ratio * 100, ACTUAL));
        }
        assertTrue(ratio <= MAX_DIFFERENT_PIXELS);
    }

    private static byte[] pixels(Image image) {
        try (Bitmap bitmap = new Bitmap()) {
            assertTrue(bitmap.allocN32Pixels(image.getWidth(), image.getHeight()));
            assertTrue(image.readPixels(bitmap));
            return bitmap.readPixels();
        }
    }
}
