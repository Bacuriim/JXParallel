package com.jxparallel.ui.native2d;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every prop that the size code reads (intrinsicSizes, JXControlLayout.sizes, JXOverlays.rowSize and the layout
 * classes) must make a reconcile lay out again: changing it in place must give what mounting the new element
 * from scratch gives. The random reconcile properties only change "value" and "gap" of a few leaf types, so a prop
 * left out of sizeAffected (tooltip "label", select "prompt", textarea "prefRowCount" were) went unseen.
 */
class JXSizePropsReconcileTest {

    private static final Object[] NONE = new Object[0];
    private static final JXElement[] NO_KIDS = new JXElement[0];

    @Test
    void everySizePropRelaysOutOnReconcile() {
        List<String> failures = new ArrayList<String>();
        check(failures, "#text", "value", "a", "a much longer text");
        check(failures, "#text", "fontSize", 12, 24);
        check(failures, "#text", "bold", false, true, "value", "some text");
        check(failures, "#text", "padding", 0, 12);
        check(failures, "#text", "wrapText", false, true, "value", "a long text that wraps over a few lines", "prefWidth", 60);
        for (String type : new String[] {"button", "toggle", "checkbox", "radio", "hyperlink", "tooltip", "switch", "chip",
                "menubutton", "menu", "menuitem"}) {
            check(failures, type, "label", "a", "a much longer label");
        }
        check(failures, "titled", "label", "a", "a much longer label", "collapsible", false);
        for (String type : new String[] {"button", "checkbox", "radio", "switch", "menubutton"}) {
            check(failures, type, "fontSize", 12, 24, "label", "text");
        }
        for (String type : new String[] {"datepicker", "timepicker", "colorpicker", "progress", "slider"}) {
            check(failures, type, "fontSize", 12, 24);
        }
        check(failures, "button", "bold", false, true, "label", "text");
        check(failures, "button", "variant", "filled", "elevated", "label", "text");
        check(failures, "button", "padding", 2, 20, "label", "text");
        check(failures, "hyperlink", "padding", 2, 20, "label", "text");
        check(failures, "tooltip", "padding", 2, 20, "label", "text");
        check(failures, "switch", "label", "", "on");
        check(failures, "input", "prefColumnCount", 4, 30);
        check(failures, "password", "prefColumnCount", 4, 30);
        check(failures, "input", "padding", 2, 20);
        check(failures, "spinner", "prefColumnCount", 4, 30);
        check(failures, "textarea", "prefColumnCount", 4, 30);
        check(failures, "textarea", "prefRowCount", 2, 9);
        check(failures, "select", "value", "a", "a much longer value");
        check(failures, "select", "prompt", "x", "a much longer prompt text");
        check(failures, "select", "options", new String[] {"a"}, new String[] {"a", "a much longer option"});
        check(failures, "indicator", "progress", -1.0, 0.5);
        check(failures, "image", "imageWidth", 10, 50, "imageHeight", 10);
        check(failures, "image", "imageHeight", 10, 50, "imageWidth", 10);
        check(failures, "image", "fitWidth", 0, 30, "imageWidth", 100, "imageHeight", 50);
        check(failures, "image", "fitHeight", 0, 30, "imageWidth", 100, "imageHeight", 50);
        check(failures, "image", "preserveRatio", false, true, "imageWidth", 100, "imageHeight", 50, "fitWidth", 20);
        check(failures, "table", "columns", new String[] {"a"}, new String[] {"a", "b", "c", "d", "e", "f"});
        check(failures, "table", "columnWidths", new double[] {80}, new double[] {300}, "columns", new String[] {"a"});
        check(failures, "tabs", "titles", new String[] {"a"}, new String[] {"a", "a much longer title"});
        check(failures, "tabs", "closingPolicy", "UNAVAILABLE", "ALL_TABS", "titles", new String[] {"a", "b"});
        check(failures, "tabs", "selected", 0, 1, "titles", new String[] {"a", "b"}, "closingPolicy", "SELECTED_TAB",
                "closable", new boolean[] {true, false});
        check(failures, "tabs", "closable", new boolean[] {true, true}, new boolean[] {true, false},
                "titles", new String[] {"a", "b"}, "closingPolicy", "ALL_TABS");
        check(failures, "pagination", "pageCount", 3, 12);
        check(failures, "pagination", "maxPageIndicatorCount", 3, 8, "pageCount", 12);
        check(failures, "icon", "size", 24, 48);
        check(failures, "avatar", "size", 40, 80);
        check(failures, "chip", "icon", "", "star", "label", "tag");
        check(failures, "chip", "kind", "assist", "filter", "label", "tag", "selected", true);
        check(failures, "chip", "selected", false, true, "label", "tag", "kind", "filter");
        check(failures, "chip", "closable", false, true, "label", "tag");
        check(failures, "stepper", "steps", 2, 5);
        check(failures, "stepper", "titles", new String[] {"a", "b"}, new String[] {"a very long first title", "b"});
        check(failures, "scrollbar", "orientation", "horizontal", "vertical");
        check(failures, "separator", "orientation", "horizontal", "vertical");
        check(failures, "tabbar", "titles", new String[] {"a"}, new String[] {"a", "a much longer title"});
        check(failures, "tabbar", "closable", false, true, "titles", new String[] {"a long title", "another long one"});
        check(failures, "menuitem", "accelerator", "", "Ctrl+Shift+Alt+K", "label", "Open");
        check(failures, "menu", "accelerator", "", "Ctrl+Shift+Alt+K", "label", "Open");
        check(failures, "menuitem", "separator", false, true, "label", "Open");
        check(failures, "cell", "value", "a", "a much longer value");
        check(failures, "cell", "fixedHeight", -1, 60, "value", "a");
        check(failures, "cell", "padding", 2, 20, "value", "a");
        check(failures, "cell", "disclosure", "", "expanded", "value", "a");
        check(failures, "cell", "indent", 0, 4, "value", "a", "disclosure", "expanded");

        // nodes sized from their children, and parts of the layout classes
        JXElement[] two = {JXElement.of("button", JXProps.builder().set("label", "one").build()),
                JXElement.of("button", JXProps.builder().set("label", "two").build())};
        JXElement tiny = JXElement.of("icon", JXProps.builder().set("size", 4).build());
        check(failures, "badge", "count", 0, 7, new Object[0], new JXElement[] {tiny});
        check(failures, "badge", "text", "", "a long badge", NONE, new JXElement[] {tiny});
        check(failures, "badge", "dot", false, true, NONE, new JXElement[] {tiny});
        check(failures, "badge", "max", 99, 9, new Object[] {"count", 150}, new JXElement[] {tiny});
        check(failures, "splitpane", "orientation", "horizontal", "vertical", NONE, two);
        check(failures, "titled", "expanded", true, false, new Object[] {"label", "t"}, new JXElement[] {two[0]});
        check(failures, "titled", "collapsible", false, true, new Object[] {"label", "a long title"}, new JXElement[] {two[0]});
        check(failures, "column", "gap", 0, 20, NONE, two);
        check(failures, "row", "padding", 0, 20, NONE, two);
        check(failures, "flow", "hgap", 0, 30, new Object[] {"orientation", "vertical", "prefWrapLength", 30}, two);
        check(failures, "flow", "vgap", 0, 30, new Object[] {"prefWrapLength", 50}, two);
        check(failures, "flow", "prefWrapLength", 400, 10, NONE, two);
        check(failures, "flow", "orientation", "horizontal", "vertical", NONE, two);
        check(failures, "tile", "prefColumns", 1, 2, NONE, two);
        check(failures, "tile", "prefRows", 1, 2, new Object[] {"orientation", "vertical"}, two);
        check(failures, "tile", "hgap", 0, 30, NONE, two);
        check(failures, "tile", "vgap", 0, 30, new Object[] {"orientation", "vertical", "prefRows", 2}, two);
        check(failures, "tile", "prefTileWidth", 50, 120, NONE, two);
        check(failures, "tile", "prefTileHeight", 50, 120, NONE, two);

        // a child's own size change reaches the parent that is sized from it
        check(failures, "menubar (child label)",
                JXElement.of("menubar", JXProps.empty(), JXElement.of("menu", JXProps.builder().set("label", "File").build())),
                JXElement.of("menubar", JXProps.empty(), JXElement.of("menu", JXProps.builder().set("label", "File and more").build())));
        assertEquals(new ArrayList<String>(), failures);
    }

    /** Mounts the element before, reconciles to the one after, and compares with mounting that one from scratch. */
    private static void check(List<String> failures, String name, JXElement before, JXElement after) {
        JXNativeNode updated = JXNativeNode.createBackendNode(screen(before));
        updated.layoutForBackend(900, 600);
        updated.reconcile(screen(after));
        updated.layoutForBackend(900, 600);

        JXNativeNode fresh = JXNativeNode.createBackendNode(screen(after));
        fresh.layoutForBackend(900, 600);

        String expected = describe(fresh);
        String actual = describe(updated);
        if (!expected.equals(actual)) {
            failures.add(name + ": expected " + expected + " but was " + actual);
        }
        // the two values must really differ in size, or the check proves nothing
        String first = describe(freshOf(before));
        if (first.equals(expected)) {
            failures.add(name + ": the values do not change the size (" + expected + ")");
        }
    }

    /** One prop of one node; {@code base} are more props (key, value, ...) that stay the same. */
    private static void check(List<String> failures, String type, String key, Object before, Object after, Object... base) {
        check(failures, type, key, before, after, base, NO_KIDS);
    }

    private static void check(List<String> failures, String type, String key, Object before, Object after, Object[] base,
            JXElement[] children) {
        check(failures, type + "." + key, element(type, key, before, base, children), element(type, key, after, base, children));
    }

    private static JXElement element(String type, String key, Object value, Object[] base, JXElement[] children) {
        JXProps.Builder props = JXProps.builder();
        for (int i = 0; i + 1 < base.length; i += 2) {
            props.set((String) base[i], base[i + 1]);
        }
        return JXElement.of(type, props.set(key, value).build(), children);
    }

    private static JXNativeNode freshOf(JXElement element) {
        JXNativeNode node = JXNativeNode.createBackendNode(screen(element));
        node.layoutForBackend(900, 600);
        return node;
    }

    /** The node beside a button in a row, so a stale width also moves the button. */
    private static JXElement screen(JXElement node) {
        return JXElement.of("row", JXProps.empty(), node,
                JXElement.of("button", JXProps.builder().set("label", "next").build()));
    }

    private static String describe(JXNativeNode root) {
        StringBuilder out = new StringBuilder();
        for (JXNativeNode n : root.getChildren()) {
            out.append(n.getType()).append(" pref=").append(n.prefWidth()).append('x').append(n.prefHeight())
                    .append(" box=").append(n.getX()).append(',').append(n.getY()).append(' ')
                    .append(n.getWidth()).append('x').append(n.getHeight()).append("; ");
        }
        return out.toString();
    }
}
