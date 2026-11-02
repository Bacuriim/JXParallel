package com.jxparallel.ui.native2d;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A control laid out smaller than its padding (a ProgressBar in a Scroll with fitToHeight gets no
 * height at all) must not hand the painter a negative width or height: Skia throws on every frame
 * and the window never shows one.
 */
class JXPaintTinySizeTest {
    /** Float arguments named w/h of each primitive, by position, to check. */
    private static final String[] SIZED = {"fillRect", "fillRoundRect", "fillRoundRectGradient", "strokeRoundRect",
        "fillOval", "strokeOval", "strokeArc", "clip", "image"};

    private static JXElement of(String type, JXProps.Builder props) {
        return JXElement.of(type, props.build());
    }

    private static JXElement[] controls() {
        JXProps.Builder p = JXProps.builder();
        return new JXElement[] {
            of("progress", JXProps.builder().set("progress", 0.5)),
            of("progress", JXProps.builder().set("progress", -1.0)),
            of("indicator", JXProps.builder().set("progress", 0.5)),
            of("indicator", JXProps.builder().set("progress", -1.0)),
            of("slider", JXProps.builder().set("value", 5.0).set("min", 0.0).set("max", 10.0)),
            of("button", JXProps.builder().set("label", "OK")),
            of("button", JXProps.builder().set("label", "OK").set("variant", "primary")),
            of("toggle", JXProps.builder().set("label", "OK").set("selected", true)),
            of("switch", JXProps.builder().set("selected", true)),
            of("checkbox", JXProps.builder().set("label", "c").set("selected", true)),
            of("radio", JXProps.builder().set("label", "r").set("selected", true)),
            of("input", JXProps.builder().set("value", "text").set("focused", true)),
            of("password", JXProps.builder().set("value", "text")),
            of("textarea", JXProps.builder().set("value", "a\nb").set("focused", true)),
            of("select", JXProps.builder().set("value", "x")),
            of("spinner", JXProps.builder().set("value", "7")),
            of("datepicker", JXProps.builder().set("value", "01/01/2026")),
            of("hyperlink", JXProps.builder().set("label", "l").set("underline", true)),
            of("chip", JXProps.builder().set("label", "chip")),
            of("avatar", JXProps.builder().set("label", "AB")),
            of("stepper", JXProps.builder().set("value", 1)),
            of("scrollbar", JXProps.builder().set("max", 10.0).set("visibleAmount", 2.0)),
            of("tabbar", JXProps.builder().set("titles", new String[] {"a", "b"}).set("selected", 0)),
            of("separator", p),
            of("titled", JXProps.builder().set("label", "t")),
            of("scroll", p),
            of("list", p),
            of("table", JXProps.builder().set("columns", new Object[] {"Obj", 42})),
            of("cell", JXProps.builder().set("value", "c").set("selected", true)),
            of("tablerow", JXProps.builder().set("selected", true)),
            of("tabs", JXProps.builder().set("titles", new String[] {"a", "b"}).set("selected", 0)),
            of("pagination", JXProps.builder().set("pageCount", 3).set("current", 0)),
            of("popup", p),
            of("tooltip", JXProps.builder().set("label", "tip")),
            of("timepicker", p),
            of("colorpicker", p),
            of("menubutton", JXProps.builder().set("label", "m")),
            of("menubar", p),
            of("#text", JXProps.builder().set("value", "text").set("background", 0xFFEEEEEE)),
        };
    }

    /** Paints with a painter that notes each primitive with a negative width or height. */
    private static List<String> negatives(JXElement element, int width, int height) {
        JXNativeNode node = JXNativeNode.createBackendNode(element);
        node.layout(0, 0, width, height);
        List<String> found = new ArrayList<>();
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            for (String sized : SIZED) {
                if (sized.equals(name)) {
                    // x, y, w, h come first except for line-like primitives, which are not listed
                    int w = "image".equals(name) ? 5 : 2;
                    float fw = (Float) args[w];
                    float fh = (Float) args[w + 1];
                    if (fw < 0 || fh < 0) {
                        found.add(element.getType() + " " + width + "x" + height + " " + name + " w=" + fw + " h=" + fh);
                    }
                }
            }
            return null;
        };
        JXPainter painter = (JXPainter) Proxy.newProxyInstance(JXPainter.class.getClassLoader(), new Class<?>[] {JXPainter.class}, handler);
        new JXPaint(painter).paint(node);
        return found;
    }

    @Test
    void aControlSmallerThanItsPaddingDrawsNoNegativeSize() {
        List<String> found = new ArrayList<>();
        for (JXElement control : controls()) {
            for (int[] size : new int[][] {{0, 0}, {1, 1}, {2, 2}, {3, 3}, {5, 5}, {40, 0}, {0, 20}, {40, 1}, {1, 20}}) {
                found.addAll(negatives(control, size[0], size[1]));
            }
        }
        assertEquals(new ArrayList<String>(), found);
    }

    @Test
    void theProgressBarOfAZeroHeightScrollDrawsNoNegativeSize() {
        assertTrue(negatives(of("progress", JXProps.builder().set("progress", 0.5)), 200, 0).isEmpty());
        assertTrue(negatives(of("progress", JXProps.builder().set("progress", -1.0)), 200, 0).isEmpty());
    }
}
