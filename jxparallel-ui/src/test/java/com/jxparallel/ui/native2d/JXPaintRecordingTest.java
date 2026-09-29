package com.jxparallel.ui.native2d;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Every drawing primitive {@link JXPaint} issues for the controls of {@link JXControlsSnapshotTest}
 * and for more states, recorded as text and compared with a reviewed golden file. The raster
 * golden tolerates antialiasing noise; this one pins every coordinate and color exactly, so a
 * one-pixel slip in any control is caught (and the diff says which primitive moved).
 */
class JXPaintRecordingTest {
    private static final Path GOLDEN = Paths.get("src/test/resources/snapshots");
    private static final Path ACTUAL = Paths.get("target/snapshots");

    /** Writes each primitive call as one line, numbers with two decimals. */
    static final class RecordingPainter implements JXPainter {
        final StringBuilder log = new StringBuilder();

        private void add(String name, Object... args) {
            log.append(name);
            for (Object a : args) {
                log.append(' ');
                if (a instanceof Float) {
                    log.append(String.format(Locale.ROOT, "%.2f", (Float) a));
                } else if (a instanceof Integer) {
                    log.append(Integer.toHexString((Integer) a));
                } else {
                    log.append(a);
                }
            }
            log.append('\n');
        }

        @Override
        public void fillRect(float x, float y, float w, float h, int argb) {
            add("fillRect", x, y, w, h, argb);
        }

        @Override
        public void fillRoundRect(float x, float y, float w, float h, float radius, int argb) {
            add("fillRoundRect", x, y, w, h, radius, argb);
        }

        @Override
        public void fillRoundRectGradient(float x, float y, float w, float h, float radius, int top, int bottom) {
            add("gradient", x, y, w, h, radius, top, bottom);
        }

        @Override
        public void strokeRoundRect(float x, float y, float w, float h, float radius, float width, int argb) {
            add("strokeRoundRect", x, y, w, h, radius, width, argb);
        }

        @Override
        public void fillOval(float x, float y, float w, float h, int argb) {
            add("fillOval", x, y, w, h, argb);
        }

        @Override
        public void strokeOval(float x, float y, float w, float h, float width, int argb) {
            add("strokeOval", x, y, w, h, width, argb);
        }

        @Override
        public void strokeArc(float x, float y, float w, float h, float startDegrees, float sweepDegrees, float width, int argb) {
            add("strokeArc", x, y, w, h, startDegrees, sweepDegrees, width, argb);
        }

        @Override
        public void line(float x1, float y1, float x2, float y2, float width, int argb) {
            add("line", x1, y1, x2, y2, width, argb);
        }

        @Override
        public void fillTriangle(float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
            add("triangle", x0, y0, x1, y1, x2, y2, argb);
        }

        @Override
        public void text(String value, float x, float baseline, float size, boolean bold, int argb) {
            add("text", "\"" + value + "\"", x, baseline, size, bold, argb);
        }

        @Override
        public void image(int[] pixels, int imageWidth, int imageHeight, float x, float y, float w, float h) {
            add("image", pixels.length, imageWidth, imageHeight, x, y, w, h);
        }

        @Override
        public void clip(float x, float y, float w, float h) {
            add("clip", x, y, w, h);
        }

        @Override
        public void layer(float opacity, float blurRadius) {
            add("layer", opacity, blurRadius);
        }

        @Override
        public void restore() {
            add("restore");
        }
    }

    private static JXProps.Builder p() {
        return JXProps.builder();
    }

    private static JXElement e(String type, JXProps.Builder props, JXElement... children) {
        return JXElement.of(type, props.build(), children);
    }

    /** States the controls image does not show: open popups, hover parts, today, clipping, text areas. */
    static JXElement states() {
        JXElement area = e("textarea", p().set("value", "first line\nsecond line wraps here\nthird").set("wrapText", true)
                .set("caret", 3).set("anchor", 17).set("focused", true).set("scrollTop", 4.0)
                .set("prefWidth", 120.0).set("prefHeight", 60.0));
        JXElement fields = e("row", p().set("gap", 8.0), area,
                e("select", p().set("value", "Aberto").set("showing", true).set("hover", true)),
                e("datepicker", p().set("value", "01/01/2026").set("showing", true).set("promptShown", true)),
                e("spinner", p().set("value", "7").set("hoverPart", "down").set("focused", true)),
                e("input", p().set("value", "centro").set("textAlignment", "CENTER").set("prefColumnCount", 8.0)),
                e("textarea", p().set("value", "").set("prompt", "Observações").set("prefWidth", 90.0).set("prefHeight", 40.0)));
        JXElement[] cells = new JXElement[3];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = e("cell", p().set("value", "Opção " + i).set("selected", i == 2).set("hover", i == 0).set("odd", i % 2 == 1));
        }
        JXElement unfocused = e("list", p().set("itemCount", 3).set("prefWidth", 110.0).set("prefHeight", 80.0), cells);
        JXElement popupList = e("list", p().set("itemCount", 2).set("popupOf", "combo").set("prefWidth", 110.0).set("prefHeight", 50.0),
                e("cell", p().set("value", "Um").set("hover", true)), e("cell", p().set("value", "Dois")));
        JXElement toggles = e("column", p().set("gap", 6.0),
                e("checkbox", p().set("label", "Hover").set("hover", true)),
                e("checkbox", p().set("label", "Off disabled").set("disabled", true)),
                e("radio", p().set("label", "Hover").set("hover", true)),
                e("hyperlink", p().set("label", "Underline").set("underline", true).set("textFill", 0xFF008000)),
                e("progress", p().set("progress", -1.0)),
                e("indicator", p().set("progress", -1.0)),
                e("slider", p().set("value", 5.0).set("min", 0.0).set("max", 10.0).set("hover", true).set("pressed", true)));
        JXElement clipped = e("pane", p().set("clip", true).set("prefWidth", 60.0).set("prefHeight", 30.0)
                .set("background", 0xFFEEEEFF).set("borderColor", 0xFF3366CC).set("borderWidth", 2.0).set("borderRadius", 6.0),
                e("#text", p().set("value", "clipped text that overflows").set("wrapText", false)));
        JXElement wrapped = e("#text", p().set("value", "a label that wraps onto more lines").set("wrapText", true)
                .set("prefWidth", 80.0).set("textAlignment", "CENTER"));
        JXElement lists = e("row", p().set("gap", 10.0), unfocused, popupList, toggles, e("column", p().set("gap", 6.0), clipped, wrapped));
        JXElement calendar = e("calendar", p().set("year", 2026).set("month", 2).set("firstDayOfWeek", 1)
                .set("selectedYear", 2026).set("selectedMonth", 2).set("selectedDay", 14)
                .set("todayYear", 2026).set("todayMonth", 2).set("todayDay", 3).set("title", "Fevereiro 2026")
                .set("weekdays", new String[]{"seg", "ter", "qua", "qui", "sex", "sáb", "dom"}));
        JXElement tabs = e("tabs", p().set("titles", new String[]{"Um", "Dois"}).set("selected", 0).set("hover", true)
                .set("prefWidth", 160.0).set("prefHeight", 60.0), e("#text", p().set("value", "conteúdo")));
        JXElement pages = e("pagination", p().set("pageCount", 3).set("current", 0).set("prefWidth", 160.0),
                e("#text", p().set("value", "primeira")));
        JXElement titled = e("titled", p().set("label", "Hover").set("hover", true).set("prefWidth", 150.0),
                e("#text", p().set("value", "corpo")));
        return e("column", p().set("gap", 10.0).set("padding", new double[]{10, 10, 10, 10}),
                fields, lists, e("row", p().set("gap", 10.0), calendar, e("column", p().set("gap", 8.0), tabs, pages, titled)));
    }

    /** Text paths: selections, scrolled fields and areas, side nodes, alignments, cells with graphics. */
    static JXElement texts() {
        String longValue = "a long value that scrolls to keep the caret visible";
        JXElement fields = e("column", p().set("gap", 6.0),
                e("input", p().set("value", "left side").set("caret", 2).set("anchor", 6).set("focused", true).set("prefWidth", 180.0),
                        e("pane", p().set("prefWidth", 12.0).set("prefHeight", 12.0).set("background", 0xFF808080)),
                        e("pane", p().set("prefWidth", 10.0).set("prefHeight", 10.0).set("side", "right").set("background", 0xFF404040))),
                e("input", p().set("value", longValue).set("caret", longValue.length()).set("anchor", 3).set("focused", true)
                        .set("prefWidth", 140.0)),
                e("input", p().set("value", longValue).set("caret", 5).set("prefWidth", 140.0)),
                e("input", p().set("value", "").set("prompt", "focused prompt hidden").set("focused", true).set("prefWidth", 140.0)),
                e("input", p().set("value", "ctr").set("textAlignment", "CENTER").set("caret", 1).set("focused", true).set("prefWidth", 140.0)),
                e("password", p().set("value", "pw").set("caret", 2).set("anchor", 0).set("focused", true).set("prefWidth", 140.0)),
                e("input", p().set("value", "bold big").set("bold", true).set("fontSize", 16.0).set("textFill", 0xFF1060A0)
                        .set("padding", new double[]{2, 3, 2, 3}).set("prefWidth", 140.0)));
        JXElement areas = e("column", p().set("gap", 6.0),
                e("textarea", p().set("value", "one\r\ntwo\nthree\nfour\nfive\nsix").set("caret", 12).set("anchor", 2).set("focused", true)
                        .set("scrollTop", 20.0).set("prefWidth", 150.0).set("prefHeight", 60.0)),
                e("textarea", p().set("value", "wrap this text inside a narrow area please").set("wrapText", true)
                        .set("caret", 9).set("anchor", 30).set("focused", true).set("prefWidth", 110.0).set("prefHeight", 90.0)),
                e("textarea", p().set("value", "").set("prompt", "prompt").set("focused", true).set("prefWidth", 110.0).set("prefHeight", 30.0)),
                e("textarea", p().set("value", "end").set("caret", 3).set("anchor", 0).set("focused", true)
                        .set("prefWidth", 110.0).set("prefHeight", 30.0)));
        JXElement cells = e("column", p().set("gap", 2.0).set("prefWidth", 160.0),
                e("cell", p().set("value", "centre").set("textAlignment", "CENTER").set("prefWidth", 160.0)),
                e("cell", p().set("value", "right").set("alignment", "CENTER_RIGHT").set("prefWidth", 160.0)),
                e("cell", p().set("value", "left").set("alignment", "CENTER_LEFT").set("prefWidth", 160.0)),
                e("cell", p().set("value", "icon right").set("alignment", "BASELINE_RIGHT").set("prefWidth", 160.0),
                        e("pane", p().set("prefWidth", 14.0).set("prefHeight", 14.0).set("background", 0xFF2D6CDF))),
                e("cell", p().set("value", "icon centre").set("alignment", "BASELINE_CENTER").set("prefWidth", 160.0),
                        e("pane", p().set("prefWidth", 14.0).set("prefHeight", 14.0).set("background", 0xFF2D6CDF))),
                e("cell", p().set("value", "selected unfocused").set("selected", true).set("prefWidth", 160.0)),
                e("cell", p().set("value", "underlined").set("underline", true).set("prefWidth", 160.0)));
        JXElement labels = e("column", p().set("gap", 2.0),
                e("#text", p().set("value", "top left").set("alignment", "TOP_LEFT").set("prefWidth", 120.0).set("prefHeight", 30.0)
                        .set("background", 0xFFEEEEEE)),
                e("#text", p().set("value", "bottom right").set("alignment", "BOTTOM_RIGHT").set("prefWidth", 120.0).set("prefHeight", 30.0)
                        .set("background", 0xFFEEEEEE)),
                e("#text", p().set("value", "centre").set("alignment", "CENTER").set("prefWidth", 120.0).set("background", 0xFFEEEEEE)),
                e("#text", p().set("value", "text right").set("textAlignment", "RIGHT").set("prefWidth", 120.0).set("background", 0xFFEEEEEE)),
                e("#text", p().set("value", "ellipsis for a label that is too long").set("prefWidth", 90.0).set("maxWidth", 90.0)),
                e("#text", p().set("value", "wrapped right aligned text").set("wrapText", true).set("textAlignment", "RIGHT")
                        .set("prefWidth", 90.0).set("background", 0xFFEEEEEE)),
                e("button", p().set("label", "a button label that is cut").set("prefWidth", 90.0).set("maxWidth", 90.0)),
                e("table", p().set("columns", new Object[]{"Obj", 42}).set("prefWidth", 120.0).set("prefHeight", 60.0)));
        return e("row", p().set("gap", 12.0).set("padding", new double[]{8, 8, 8, 8}), fields, areas, cells, labels);
    }

    private static String record(JXElement element, int width, int height) {
        JXNativeNode root = JXNativeNode.createBackendNode(element);
        root.layoutForBackend(width, height);
        RecordingPainter painter = new RecordingPainter();
        java.util.function.LongSupplier clock = JXPaint.clock;
        JXPaint.clock = () -> 1000L; // indeterminate progress frames at a fixed time
        try {
            new JXPaint(painter).paint(root);
        } finally {
            JXPaint.clock = clock;
        }
        return painter.log.toString();
    }

    private static void assertGolden(String name, String actual) throws IOException {
        Path file = GOLDEN.resolve(name);
        if (!Files.exists(file)) {
            Files.createDirectories(GOLDEN);
            Files.write(file, actual.getBytes(StandardCharsets.UTF_8));
            fail("created snapshot " + file + ", review it and run again");
        }
        String expected = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
        if (!expected.equals(actual)) {
            Files.createDirectories(ACTUAL);
            Files.write(ACTUAL.resolve(name), actual.getBytes(StandardCharsets.UTF_8));
            String[] e = expected.split("\n");
            String[] a = actual.split("\n");
            for (int i = 0; i < Math.min(e.length, a.length); i++) {
                assertEquals(e[i], a[i], name + " line " + (i + 1) + ", new recording in " + ACTUAL);
            }
            assertEquals(e.length, a.length, name + " primitive count, new recording in " + ACTUAL);
        }
    }

    private static void assumeReferenceFonts() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"), "text widths come from Segoe UI");
    }

    @Test
    void controlsDrawTheReviewedPrimitives() throws IOException {
        assumeReferenceFonts();
        assertGolden("controls-paint.txt", record(JXControlsSnapshotTest.controls(), 760, 620));
    }

    @Test
    void textsDrawTheReviewedPrimitives() throws IOException {
        assumeReferenceFonts();
        assertGolden("texts-paint.txt", record(texts(), 760, 360));
    }

    @Test
    void statesDrawTheReviewedPrimitives() throws IOException {
        assumeReferenceFonts();
        assertGolden("states-paint.txt", record(states(), 760, 560));
    }
}
