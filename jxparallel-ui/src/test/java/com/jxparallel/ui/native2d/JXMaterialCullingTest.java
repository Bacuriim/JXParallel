package com.jxparallel.ui.native2d;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

/**
 * The Material look keeps what a control paints outside its box (shadows, focus rings, state layers) within the margin
 * of the culling: a tall column of every kind of control, in the light and the dark theme, is painted into windows
 * that cut it at many heights with the culling on and off, and the pixels are the same.
 */
class JXMaterialCullingTest {
    private static final int WIDTH = 420;

    private final java.util.function.LongSupplier clock = JXPaint.clock;

    @BeforeEach
    void fixedClock() {
        JXPaint.clock = () -> 700L; // the indeterminate bars and spinners move with the clock; the two frames must be of one instant
    }

    @AfterEach
    void cullingBackOn() {
        JXPaint.cull = true;
        JXPaint.clock = clock;
    }

    private static JXProps.Builder p() {
        return JXProps.builder();
    }

    private static JXElement e(String type, JXProps.Builder props, JXElement... children) {
        return JXElement.of(type, props.build(), children);
    }

    private static JXElement text(String value) {
        return e("#text", p().set("value", value));
    }

    /** One of every control, in the states that paint something beyond their box. */
    private static List<JXElement> oneOfEach(int n) {
        List<JXElement> all = new ArrayList<JXElement>();
        String[] variants = {"filled", "tonal", "outlined", "elevated", "fab", "text"};
        for (String variant : variants) {
            all.add(e("button", p().set("label", variant + n).set("variant", variant)));
            all.add(e("button", p().set("label", variant + " hover").set("variant", variant).set("hover", true).set("focused", true)));
            all.add(e("button", p().set("label", variant + " off").set("variant", variant).set("disabled", true)));
        }
        all.add(e("button", p().set("label", "Classic")));
        all.add(e("button", p().set("label", "Default").set("defaultButton", true).set("focused", true)));
        all.add(e("button", p().set("label", "Pressed").set("pressed", true).set("hover", true)));
        all.add(e("toggle", p().set("label", "Toggle").set("selected", true)));
        all.add(e("toggle", p().set("label", "Toggle off").set("hover", true)));
        all.add(e("checkbox", p().set("label", "Checked").set("checked", true).set("hover", true)));
        all.add(e("checkbox", p().set("label", "Mixed").set("indeterminate", true).set("focused", true)));
        all.add(e("checkbox", p().set("label", "Off").set("pressed", true)));
        all.add(e("radio", p().set("label", "On").set("checked", true).set("hover", true)));
        all.add(e("radio", p().set("label", "Off").set("focused", true)));
        all.add(e("switch", p().set("label", "Switch").set("checked", true).set("hover", true)));
        all.add(e("switch", p().set("label", "Switch off").set("focused", true)));
        all.add(e("chip", p().set("label", "Chip").set("kind", "filter").set("selected", true).set("hover", true)));
        all.add(e("chip", p().set("label", "Assist").set("kind", "assist").set("focused", true)));
        all.add(e("input", p().set("value", "Ada").set("hover", true)));
        all.add(e("input", p().set("value", "selected").set("caret", 2).set("anchor", 6).set("focused", true)));
        all.add(e("input", p().set("prompt", "Prompt")));
        all.add(e("password", p().set("value", "secret").set("focused", true)));
        all.add(e("textarea", p().set("value", "one\ntwo\nthree").set("focused", true).set("prefRowCount", 3.0)));
        all.add(e("select", p().set("value", "A").set("options", new Object[] {"A", "B"}).set("showing", true)));
        all.add(e("select", p().set("value", "A").set("editable", true).set("options", new Object[] {"A"}).set("focused", true)));
        all.add(e("spinner", p().set("value", "3").set("hoverPart", "down").set("focused", true)));
        all.add(e("datepicker", p().set("value", "28/09/2026").set("showing", false)));
        all.add(e("timepicker", p().set("value", "10:30")));
        all.add(e("colorpicker", p().set("value", "#1565C0")));
        all.add(e("menubutton", p().set("label", "Menu").set("hover", true)));
        all.add(e("progress", p().set("progress", 0.4)));
        all.add(e("progress", p().set("progress", -1.0)));
        all.add(e("indicator", p().set("progress", 0.7)));
        all.add(e("indicator", p().set("progress", -1.0)));
        all.add(e("slider", p().set("value", 40.0).set("hover", true).set("focused", true)));
        all.add(e("slider", p().set("value", 70.0).set("orientation", "vertical").set("pressed", true).set("prefHeight", 60.0)));
        all.add(e("separator", p()));
        all.add(e("hyperlink", p().set("label", "Link").set("focused", true)));
        all.add(e("tooltip", p().set("label", "Tooltip")));
        JXElement[] cells = new JXElement[4];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = e("cell", p().set("value", "Item " + i).set("selected", i == 1).set("listFocused", i == 1).set("hover", i == 2));
        }
        all.add(e("list", p().set("itemCount", 12).set("prefWidth", 150.0).set("prefHeight", 90.0).set("focused", true), cells));
        JXElement[] rows = new JXElement[3];
        for (int r = 0; r < rows.length; r++) {
            rows[r] = e("tablerow", p().set("selected", r == 0).set("hover", r == 1),
                    e("cell", p().set("value", "Row " + r).set("tableCell", true).set("paintBackground", false)));
        }
        all.add(e("table", p().set("columns", new String[] {"Name"}).set("columnWidths", new double[] {120})
                .set("itemCount", 3).set("prefWidth", 160.0).set("prefHeight", 90.0), rows));
        all.add(e("scroll", p().set("prefWidth", 120.0).set("prefHeight", 60.0),
                e("column", p(), text("Scroll"), e("pane", p().set("prefWidth", 200.0).set("prefHeight", 200.0)))));
        all.add(e("tabs", p().set("titles", new String[] {"One", "Two"}).set("selected", 1).set("prefWidth", 200.0)
                .set("prefHeight", 70.0).set("closable", true), text("content")));
        all.add(e("tabbar", p().set("titles", new String[] {"One", "Two"}).set("selected", 0)));
        all.add(e("titled", p().set("label", "Titled").set("prefWidth", 160.0).set("hover", true), text("inside")));
        all.add(e("titled", p().set("label", "Collapsed").set("expanded", false).set("prefWidth", 160.0).set("focused", true),
                text("hidden")));
        all.add(e("pagination", p().set("pageCount", 6).set("current", 2).set("prefWidth", 200.0), text("page")));
        all.add(e("calendar", p().set("year", 2026).set("month", 9).set("selectedYear", 2026).set("selectedMonth", 9)
                .set("selectedDay", 12).set("todayYear", 2026).set("todayMonth", 9).set("todayDay", 3)));
        all.add(e("badge", p().set("text", "3"), e("icon", p().set("name", "check"))));
        all.add(e("avatar", p().set("initials", "AL")));
        all.add(e("stepper", p().set("steps", new String[] {"One", "Two", "Three"}).set("current", 1)));
        all.add(e("scrollbar", p().set("pressed", true).set("prefHeight", 14.0).set("prefWidth", 120.0)));
        all.add(e("column", p().set("disabled", true), e("button", p().set("label", "Faded").set("variant", "elevated"))));
        return all;
    }

    private static JXElement column(boolean dark, int copies) {
        List<JXElement> rows = new ArrayList<JXElement>();
        for (int i = 0; i < copies; i++) {
            rows.addAll(oneOfEach(i));
        }
        JXProps.Builder props = p().set("gap", 14.0).set("padding", new double[] {8, 8, 8, 8});
        if (dark) {
            props.set("theme", "dark");
        }
        return e("column", props, rows.toArray(new JXElement[0]));
    }

    private static int[] pixels(JXNativeNode root, boolean cull, int height) {
        JXPaint.cull = cull;
        int[] argb = new int[WIDTH * height];
        JXWindow.paintSoftware(root, argb, WIDTH, height);
        return argb;
    }

    @Test
    void everyControlPaintsTheSameWithTheCullingOnAndOffInBothThemes() {
        for (boolean dark : new boolean[] {false, true}) {
            JXNativeNode root = JXNativeNode.createBackendNode(column(dark, 3));
            root.layoutForBackend(WIDTH, 5000);
            assertTrue(root.getChildren().get(root.getChildren().size() - 1).getY() > 4000, "the column is far taller than the windows");
            for (int height : new int[] {60, 97, 143, 211, 280, 333}) {
                // the same tall column seen through windows of other heights, so the edge cuts through other controls
                root.layoutForBackend(WIDTH, height);
                int[] off = pixels(root, false, height);
                int[] on = pixels(root, true, height);
                assertArrayEquals(off, on, (dark ? "dark" : "light") + " theme, window of " + height + " px");
            }
        }
    }

    @Test
    void theWindowFollowsTheColumnAtManyOffsets() {
        // a window over the middle of the column: the column is laid out at several vertical offsets
        for (boolean dark : new boolean[] {false, true}) {
            for (int shift = 0; shift < 12; shift++) {
                JXProps.Builder wrap = p().set("layoutY", -(shift * 137.0 + 500));
                JXNativeNode root = JXNativeNode.createBackendNode(e("pane", p(), e("pane", wrap, column(dark, 2))));
                root.layoutForBackend(WIDTH, 240);
                assertArrayEquals(pixels(root, false, 240), pixels(root, true, 240),
                        (dark ? "dark" : "light") + " theme, shifted " + shift);
            }
        }
    }
}
