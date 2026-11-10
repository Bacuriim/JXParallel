package com.jxparallel.ui.native2d;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The classic controls (fields, combo boxes, check boxes, sliders, scroll bars, lists, tables, popups) under
 * {@code theme="dark"}: a dark palette from the theme tokens that reads well, the light one untouched, and a
 * {@code background} on a Scroll, ListView or TableView that wins in both themes. The software painter's image of a
 * screen with all of them is compared with a reviewed golden.
 */
class JXDarkClassicTest {
    private static final Path GOLDEN = Paths.get("src/test/resources/snapshots");
    private static final Path ACTUAL = Paths.get("target/snapshots");
    private static final int WIDTH = 760;
    private static final int HEIGHT = 470;

    private static JXProps.Builder p() {
        return JXProps.builder();
    }

    private static JXElement e(String type, JXProps.Builder props, JXElement... children) {
        return JXElement.of(type, props.build(), children);
    }

    private static JXElement text(String value) {
        return e("#text", p().set("value", value));
    }

    /** A screen with the classic controls, under the given theme; null theme is the default, light one. */
    static JXElement screen(String theme) {
        JXProps.Builder root = p().set("gap", 10.0).set("padding", new double[] {10, 10, 10, 10});
        if (theme != null) {
            root.set("theme", theme);
        }
        JXElement buttons = e("row", p().set("gap", 8.0).set("alignment", "CENTER_LEFT"),
                e("button", p().set("label", "Normal")),
                e("button", p().set("label", "Hover").set("hover", true)),
                e("button", p().set("label", "Pressed").set("pressed", true)),
                e("button", p().set("label", "Focused").set("focused", true)),
                e("button", p().set("label", "Default").set("defaultButton", true)),
                e("button", p().set("label", "Disabled").set("disabled", true)),
                e("checkbox", p().set("label", "Checked").set("checked", true)),
                e("checkbox", p().set("label", "Off")),
                e("radio", p().set("label", "Radio").set("checked", true)),
                e("radio", p().set("label", "Focus").set("focused", true)),
                e("hyperlink", p().set("label", "Link")),
                e("hyperlink", p().set("label", "Visited").set("visited", true)));
        JXElement fields = e("row", p().set("gap", 8.0),
                e("input", p().set("value", "Ada Lovelace").set("prefColumnCount", 9.0)),
                e("input", p().set("value", "").set("prompt", "Prompt").set("prefColumnCount", 7.0)),
                e("input", p().set("value", "selected").set("caret", 3).set("anchor", 8).set("focused", true)
                        .set("prefColumnCount", 7.0)),
                e("password", p().set("value", "secret").set("prefColumnCount", 6.0)),
                e("input", p().set("value", "disabled").set("disabled", true).set("prefColumnCount", 6.0)),
                e("textarea", p().set("value", "Text area\nwith two lines").set("prefColumnCount", 12.0).set("prefRowCount", 2.0)));
        JXElement combos = e("row", p().set("gap", 8.0),
                e("select", p().set("value", "Fortaleza").set("options", new Object[] {"Fortaleza", "Recife"})),
                e("select", p().set("value", "").set("prompt", "Choose").set("options", new Object[] {"a"})),
                e("select", p().set("value", "Edit").set("editable", true).set("options", new Object[] {"Edit"})),
                e("spinner", p().set("value", "42").set("hoverPart", "up")),
                e("datepicker", p().set("value", "28/09/2026")),
                e("progress", p().set("progress", 0.6)),
                e("slider", p().set("value", 30.0)),
                e("slider", p().set("value", 70.0).set("focused", true)),
                e("indicator", p().set("progress", 0.35)));
        JXElement[] cells = new JXElement[5];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = e("cell", p().set("value", "Item " + i).set("odd", i % 2 == 1).set("selected", i == 1).set("listFocused", true));
        }
        JXElement list = e("list", p().set("itemCount", 12).set("prefWidth", 150.0).set("prefHeight", 110.0), cells);
        JXElement[] rows = new JXElement[3];
        for (int r = 0; r < rows.length; r++) {
            rows[r] = e("tablerow", p().set("odd", r % 2 == 1).set("selected", r == 0),
                    e("cell", p().set("value", "Row " + r).set("tableCell", true).set("paintBackground", false).set("selected", r == 0)),
                    e("cell", p().set("value", "Cidade").set("tableCell", true).set("paintBackground", false).set("selected", r == 0)));
        }
        JXElement table = e("table", p().set("columns", new String[] {"Nome", "Cidade"}).set("columnWidths", new double[] {70, 80})
                .set("itemCount", 3).set("prefWidth", 200.0).set("prefHeight", 110.0), rows);
        JXElement scroll = e("scroll", p().set("prefWidth", 120.0).set("prefHeight", 110.0).set("vvalue", 0.1),
                e("column", p().set("gap", 4.0), text("Scroll 1"), text("Scroll 2"), text("Scroll 3"),
                        e("pane", p().set("prefWidth", 200.0).set("prefHeight", 200.0))));
        JXElement tabs = e("tabs", p().set("titles", new String[] {"Dados", "Historico", "Log"}).set("selected", 1)
                .set("prefWidth", 200.0).set("prefHeight", 110.0), text("Tab content"));
        JXElement lists = e("row", p().set("gap", 10.0), list, table, scroll, tabs);
        JXElement titled = e("titled", p().set("label", "Titled").set("prefWidth", 160.0), e("column", p(), text("Inside"),
                e("separator", p())));
        JXElement calendar = e("calendar", p().set("year", 2026).set("month", 9).set("firstDayOfWeek", 7)
                .set("selectedYear", 2026).set("selectedMonth", 9).set("selectedDay", 28).set("title", "September 2026")
                .set("highlightYear", 2026).set("highlightMonth", 9).set("highlightDay", 15)
                .set("weekdays", new String[] {"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"}));
        JXElement popup = e("popup", p().set("padding", new double[] {1, 1, 1, 1}),
                e("cell", p().set("value", "Copy").set("padding", new double[] {4, 20, 4, 12})),
                e("cell", p().set("value", "Paste").set("selected", true).set("listFocused", true).set("padding", new double[] {4, 20, 4, 12})));
        JXElement pages = e("pagination", p().set("pageCount", 8).set("current", 3).set("maxPageIndicatorCount", 5)
                .set("prefWidth", 240.0), text("Page 4"));
        JXElement more = e("row", p().set("gap", 12.0).set("alignment", "TOP_LEFT"), titled, calendar, popup, pages);
        return e("column", root, buttons, fields, combos, lists, more);
    }

    // ---- the palette --------------------------------------------------------------------------------------

    private static double luminance(int argb) {
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = ((argb >> (16 - 8 * i)) & 0xFF) / 255.0;
            c[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    private static double contrast(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    @Test
    void theDarkPaletteIsReadableAndTheLightOneIsModena() {
        JXClassic d = JXClassic.DARK;
        assertTrue(contrast(d.text, d.base) >= 7, "text on a field or list");
        assertTrue(contrast(d.text, d.oddRow) >= 7, "text on a striped row");
        assertTrue(contrast(d.text, d.hoverRow) >= 7, "text on the row under the pointer");
        assertTrue(contrast(d.text, d.selectionUnfocused) >= 4.5, "text on a selection of a list without focus");
        assertTrue(contrast(d.selectionText, d.selection) >= 4.5, "text on the selection");
        assertTrue(contrast(d.prompt, d.base) >= 4.5, "the prompt of a field");
        assertTrue(contrast(d.text, d.bodyB) >= 7, "the label of a button");
        assertTrue(contrast(d.text, d.bodyHoverB) >= 7, "the label of a hovered button");
        assertTrue(contrast(d.text, d.pressedInnerB) >= 7, "the label of a pressed button");
        assertTrue(contrast(d.text, d.defInnerB) >= 4.5, "the label of a default button");
        assertTrue(contrast(d.text, d.defHoverA) >= 4.5, "the label of a hovered default button");
        assertTrue(contrast(d.text, d.background) >= 7, "text on a scroll pane");
        assertTrue(contrast(d.text, d.tabB) >= 7, "the title of a tab");
        assertTrue(contrast(d.accent, d.base) >= 4.5, "a link");
        assertTrue(contrast(d.visited, d.base) >= 4.5, "a visited link");
        assertTrue(contrast(d.arrow, d.ctlBottom) >= 4.5, "the arrows");
        assertTrue(contrast(d.barArrow, d.barLane) >= 3, "the arrows of a scroll bar");
        assertTrue(contrast(d.dim, d.base) >= 3, "the days of the other months");
        assertTrue(contrast(d.weekday, d.base) >= 4.5, "the weekday names");
        assertTrue(contrast(d.focus, d.base) >= 3, "the focus ring");
        assertTrue(contrast(d.boxBorder, d.base) >= 1.4, "the rim of a list");
        assertTrue(contrast(d.fieldEdgeA, d.base) >= 1.4, "the rim of a field");
        assertTrue(luminance(d.base) < 0.05 && luminance(d.background) < 0.05, "dark bodies");
        JXClassic l = JXClassic.LIGHT;
        assertEquals(0xFF333333, l.text);
        assertEquals(0xFFFFFFFF, l.base);
        assertEquals(0xFFF4F4F4, l.background);
        assertEquals(JXPaint.SELECTION, l.selection);
        assertEquals(JXClassic.LIGHT, JXClassic.of(JXTheme.LIGHT));
        assertEquals(JXClassic.DARK, JXClassic.of(JXTheme.DARK));
    }

    // ---- what is painted -------------------------------------------------------------------------------

    private static JXNativeNode mount(JXElement element, int w, int h) {
        JXNativeNode node = JXNativeNode.createBackendNode(element);
        node.layoutForBackend(w, h);
        return node;
    }

    private static String record(JXNativeNode node) {
        JXPaintRecordingTest.RecordingPainter painter = new JXPaintRecordingTest.RecordingPainter();
        new JXPaint(painter).paint(node);
        return painter.log.toString();
    }

    private static Set<String> colors(String log) {
        Set<String> out = new HashSet<String>();
        for (String line : log.split("\n")) {
            for (String token : line.split(" ")) {
                if (token.matches("[0-9a-f]{8}")) {
                    out.add(token);
                }
            }
        }
        return out;
    }

    private static String hex(int argb) {
        return String.format("%08x", argb);
    }

    @Test
    void nothingOfTheLightPaletteIsPaintedOnTheDarkTheme() {
        // white (the base of Modena) is left out: it is also the text on a selection and a swatch of the color picker
        Set<String> light = new HashSet<String>();
        JXClassic l = JXClassic.LIGHT;
        for (int c : new int[] {l.text, l.accent, l.focus, l.focusRing, l.outerBorder, l.boxBorder, l.highlight, l.background, l.prompt,
            l.arrow, l.selection, l.selectionUnfocused, l.oddRow, l.rowLine, l.hoverRow, l.ctlTop, l.ctlBottom, l.ctlBottomHover,
            l.bodyA, l.bodyB, l.bodyHoverA, l.bodyHoverB, l.pressedOuterA, l.pressedOuterB, l.pressedInnerA, l.pressedInnerB,
            l.defOuterA, l.defOuterB, l.defInnerA, l.defInnerB, l.defHoverA, l.defHoverB, l.fieldEdgeA, l.fieldEdgeB, l.sheenA,
            l.trackA, l.barA, l.barB, l.sliderTrack, l.thumbEdge, l.thumbBody, l.thumbHover, l.thumbPressed, l.sepLine,
            l.titledSheen, l.barTrack, l.barLane, l.barArrow, l.barCorner, l.tabA, l.tabB, l.dim, l.weekday, l.calHover,
            l.visited}) {
            light.add(hex(c));
        }
        String dark = record(mount(screen("dark"), WIDTH, HEIGHT));
        Set<String> used = colors(dark);
        used.retainAll(light);
        assertTrue(used.isEmpty(), "light colors on the dark theme: " + used);
        String plain = record(mount(screen(null), WIDTH, HEIGHT));
        assertTrue(colors(plain).contains(hex(l.base)) && colors(plain).contains(hex(l.text)), "the default theme is Modena");
    }

    @Test
    void thePopupsOfThePickersAreDarkToo() {
        JXElement popups = e("column", p().set("theme", "dark").set("gap", 4.0),
                e("select", p().set("value", "b").set("overlay", true).set("showing", true).set("options", new String[] {"a", "b", "c"})),
                e("datepicker", p().set("value", "28/09/2026").set("overlay", true).set("showing", true)),
                e("timepicker", p().set("overlay", true).set("showing", true)),
                e("colorpicker", p().set("value", "#2196F3").set("overlay", true).set("showing", true)));
        String log = record(mount(popups, 600, 500));
        Set<String> used = colors(log);
        assertFalse(used.contains(hex(JXClassic.LIGHT.boxBorder)), "no light calendar rim");
        assertFalse(used.contains(hex(JXClassic.LIGHT.outerBorder)), "no light popup rim");
        assertFalse(used.contains(hex(JXClassic.LIGHT.text)), "no dark text");
        assertFalse(used.contains(hex(JXClassic.LIGHT.selection)), "no light-theme selection");
        assertTrue(used.contains(hex(JXClassic.DARK.base)), "the dark body");
        assertTrue(used.contains(hex(JXClassic.DARK.selection)), "the dark selection");
    }

    // ---- background --------------------------------------------------------------------------------------

    private static int[] pixels(JXNativeNode root, int w, int h) {
        JXSoftwareFrame frame = new JXSoftwareFrame(false);
        frame.paint(root, w, h, 1f);
        return java.util.Arrays.copyOf(frame.pixels(), w * h);
    }

    @Test
    void theBackgroundOfAScrollAListAndATableIsHonouredInBothThemes() {
        int own = 0xFF3A2D6B;
        for (String theme : new String[] {"light", "dark"}) {
            JXElement list = e("list", p().set("itemCount", 1).set("background", own).set("prefWidth", 100.0).set("prefHeight", 100.0),
                    e("cell", p().set("value", "x").set("odd", true)));
            JXElement table = e("table", p().set("columns", new String[] {"A"}).set("columnWidths", new double[] {40})
                    .set("itemCount", 1).set("background", own).set("prefWidth", 100.0).set("prefHeight", 100.0),
                    e("tablerow", p().set("odd", true), e("cell", p().set("value", "x").set("tableCell", true).set("paintBackground", false))));
            JXElement scroll = e("scroll", p().set("background", own).set("prefWidth", 100.0).set("prefHeight", 100.0),
                    e("pane", p().set("prefWidth", 10.0).set("prefHeight", 10.0)));
            JXNativeNode root = mount(e("row", p().set("theme", theme).set("gap", 10.0), list, table, scroll), 340, 110);
            int[] px = pixels(root, 340, 110);
            for (JXNativeNode kid : root.getChildren()) {
                int x = (int) (kid.getX() + kid.getWidth() - 25);
                int y = (int) (kid.getY() + kid.getHeight() - 25);
                assertEquals(own, px[y * 340 + x], theme + " " + kid.getType() + ": the background written, below the rows");
                // the second row of a striped list would be the stripe: the rows do not paint over the background
                assertEquals(own, px[(int) (kid.getY() + kid.getHeight() - 45) * 340 + x - 10], theme + " " + kid.getType() + " stripe");
            }
        }
        // without a background of their own they are Modena light or the dark palette
        JXNativeNode light = mount(e("row", p().set("gap", 10.0),
                e("scroll", p().set("prefWidth", 100.0).set("prefHeight", 100.0), e("pane", p().set("prefWidth", 10.0).set("prefHeight", 10.0))),
                e("list", p().set("itemCount", 0).set("prefWidth", 100.0).set("prefHeight", 100.0))), 240, 110);
        int[] lightPx = pixels(light, 240, 110);
        JXNativeNode scrollNode = light.getChildren().get(0);
        JXNativeNode listNode = light.getChildren().get(1);
        assertEquals(0xFFF4F4F4, lightPx[(int) (scrollNode.getY() + 50) * 240 + (int) (scrollNode.getX() + 50)]);
        assertEquals(0xFFFFFFFF, lightPx[(int) (listNode.getY() + 20) * 240 + (int) (listNode.getX() + 50)]);
        JXNativeNode dark = mount(e("row", p().set("theme", "dark").set("gap", 10.0),
                e("scroll", p().set("prefWidth", 100.0).set("prefHeight", 100.0), e("pane", p().set("prefWidth", 10.0).set("prefHeight", 10.0))),
                e("list", p().set("itemCount", 0).set("prefWidth", 100.0).set("prefHeight", 100.0)),
                e("table", p().set("columns", new String[] {"A"}).set("itemCount", 0).set("prefWidth", 100.0).set("prefHeight", 100.0))), 340, 110);
        int[] darkPx = pixels(dark, 340, 110);
        JXNativeNode s = dark.getChildren().get(0);
        JXNativeNode l = dark.getChildren().get(1);
        JXNativeNode t = dark.getChildren().get(2);
        assertEquals(JXClassic.DARK.background, darkPx[(int) (s.getY() + 50) * 340 + (int) (s.getX() + 50)]);
        assertEquals(JXClassic.DARK.base, darkPx[(int) (l.getY() + 20) * 340 + (int) (l.getX() + 50)]);
        assertEquals(JXClassic.DARK.base, darkPx[(int) (t.getY() + 30) * 340 + (int) (t.getX() + 50)]);
    }

    // ---- golden ------------------------------------------------------------------------------------------

    @Test
    void theDarkControlsLookLikeTheReviewedImage() throws IOException {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"), "the golden image uses the Windows UI font");
        JXNativeNode root = mount(screen("dark"), WIDTH, HEIGHT);
        JXSoftwareFrame frame = new JXSoftwareFrame(true);
        frame.paint(root, WIDTH, HEIGHT, 1f);
        int[] actual = java.util.Arrays.copyOf(frame.pixels(), WIDTH * HEIGHT);
        BufferedImage out = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, WIDTH, HEIGHT, actual, 0, WIDTH);
        Path file = GOLDEN.resolve("controls-dark-software.png");
        if (!Files.exists(file)) {
            Files.createDirectories(GOLDEN);
            ImageIO.write(out, "png", file.toFile());
            fail("created snapshot " + file + ", review it and run again");
        }
        BufferedImage golden = ImageIO.read(file.toFile());
        assertEquals(WIDTH, golden.getWidth());
        assertEquals(HEIGHT, golden.getHeight());
        int different = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int a = actual[y * WIDTH + x];
                int b = golden.getRGB(x, y);
                for (int shift = 0; shift <= 16; shift += 8) {
                    if (Math.abs(((a >> shift) & 0xFF) - ((b >> shift) & 0xFF)) > 48) {
                        different++;
                        break;
                    }
                }
            }
        }
        double ratio = different / (double) (WIDTH * HEIGHT);
        if (ratio > 0.01) {
            Files.createDirectories(ACTUAL);
            ImageIO.write(out, "png", ACTUAL.resolve("controls-dark-software.png").toFile());
            fail(String.format("%.3f%% of the pixels differ from the golden image, render in %s", ratio * 100, ACTUAL));
        }
    }
}
