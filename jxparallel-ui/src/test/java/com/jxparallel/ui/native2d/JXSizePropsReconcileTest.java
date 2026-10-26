package com.jxparallel.ui.native2d;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every prop that the size code reads (intrinsicSizes, JXControlLayout.sizes) must make a reconcile
 * lay out again: changing it in place must give what mounting the new element from scratch gives.
 * The random reconcile properties only change "value" and "gap" of a few leaf types, so a prop left
 * out of sizeAffected (tooltip "label", select "prompt", textarea "prefRowCount" were) went unseen.
 */
class JXSizePropsReconcileTest {

    @Test
    void everySizePropRelaysOutOnReconcile() {
        List<String> failures = new ArrayList<String>();
        check(failures, "#text", "value", "a", "a much longer text");
        check(failures, "#text", "fontSize", 12, 24);
        check(failures, "#text", "bold", false, true);
        for (String type : new String[] {"button", "toggle", "checkbox", "radio", "hyperlink", "tooltip"}) {
            check(failures, type, "label", "a", "a much longer label");
        }
        check(failures, "button", "fontSize", 12, 24);
        check(failures, "input", "prefColumnCount", 4, 30);
        check(failures, "password", "prefColumnCount", 4, 30);
        check(failures, "spinner", "prefColumnCount", 4, 30);
        check(failures, "textarea", "prefColumnCount", 4, 30);
        check(failures, "textarea", "prefRowCount", 2, 9);
        check(failures, "select", "value", "a", "a much longer value");
        check(failures, "select", "prompt", "x", "a much longer prompt text");
        check(failures, "select", "options", new String[] {"a"}, new String[] {"a", "a much longer option"});
        check(failures, "indicator", "progress", -1.0, 0.5);
        check(failures, "cell", "value", "a", "a much longer value");
        assertEquals(new ArrayList<String>(), failures);
    }

    private static void check(List<String> failures, String type, String key, Object before, Object after) {
        JXNativeNode updated = JXNativeNode.createBackendNode(screen(type, key, before));
        updated.layoutForBackend(900, 600);
        updated.reconcile(screen(type, key, after));
        updated.layoutForBackend(900, 600);

        JXNativeNode fresh = JXNativeNode.createBackendNode(screen(type, key, after));
        fresh.layoutForBackend(900, 600);

        String expected = describe(fresh);
        String actual = describe(updated);
        if (!expected.equals(actual)) {
            failures.add(type + "." + key + ": expected " + expected + " but was " + actual);
        }
    }

    /** The node beside a button in a row, so a stale width also moves the button. */
    private static JXElement screen(String type, String key, Object value) {
        return JXElement.of("row", JXProps.empty(),
                JXElement.of(type, JXProps.builder().set(key, value).build()),
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
