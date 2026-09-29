package com.jxparallel.fx.nativeimpl;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jxparallel.fx.scene.control.Button;
import com.jxparallel.fx.scene.control.Label;
import com.jxparallel.fx.scene.layout.HBox;
import com.jxparallel.fx.scene.layout.VBox;
import com.jxparallel.ui.JXProps;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The CSS subset: parsing, values, selectors, specificity and priority over code. */
class NativeCssTest extends NativeTestSupport {

    private static Map<String, Object> declared(String css) {
        JXProps.Builder p = JXProps.builder();
        NativeCss.parseDeclarations(css).forEach((k, v) -> NativeCss.applyDeclaration(k, v, p));
        return p.build().asMap();
    }

    @Test
    void colorsInEveryForm() {
        assertEquals(0xFFFF0000, (int) NativeCss.color("red"));
        assertEquals(0xFF112233, (int) NativeCss.color("#123"));
        assertEquals(0xFF102030, (int) NativeCss.color("#102030"));
        assertEquals(0x80102030, (int) NativeCss.color("rgba(16, 32, 48, 0.5)"));
        assertEquals(0, (int) NativeCss.color("transparent"));
        assertNull(NativeCss.color("null"));
        assertNull(NativeCss.color("no-such-color"));
        assertEquals(0xFFAABBCC, (int) NativeCss.color("linear-gradient(to bottom, #aabbcc 0%, #000000 100%)"));
        int lighter = NativeCss.color("derive(#000000, 50%)");
        assertEquals(0x80, lighter & 0xFF, "halfway to white");
        int darker = NativeCss.color("derive(#ffffff, -50%)");
        assertEquals(0x80, darker & 0xFF, "halfway to black");
    }

    @Test
    void lengthsInPixelsPointsAndEms() {
        assertEquals(10.0, NativeCss.length("10px"));
        assertEquals(16.0, NativeCss.length("12pt"));
        assertEquals(18.0, NativeCss.length("1.5em"));
        assertEquals(6.0, NativeCss.length("50%"));
        assertEquals(7.0, NativeCss.length("7"));
    }

    @Test
    void declarationsBecomeElementProps() {
        Map<String, Object> p = declared("-fx-background-color: #eee, #fff; -fx-background-radius: 4 4 0 0;"
                + " -fx-border-color: red transparent; -fx-border-width: 2; -fx-text-fill: #333;"
                + " -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 1 2; -fx-pref-width: 80;"
                + " -fx-max-height: -fx-use-pref-size; -fx-opacity: 0.5; -fx-underline: true; -fx-alignment: center-left;"
                + " -fx-spacing: 3; -fx-hgap: 4; -fx-vgap: 5; -fx-wrap-text: true; -fx-text-alignment: right");
        assertEquals(0xFFFFFFFF, p.get("background"), "the top layer");
        assertEquals(4.0, p.get("backgroundRadius"));
        assertEquals(0xFFFF0000, p.get("borderColor"));
        assertEquals(2.0, p.get("borderWidth"));
        assertEquals(0xFF333333, p.get("textFill"));
        assertEquals(14.0, p.get("fontSize"));
        assertEquals(true, p.get("bold"));
        assertArrayEquals(new double[]{1, 2, 1, 2}, (double[]) p.get("padding"));
        assertEquals(80.0, p.get("prefWidth"));
        assertEquals(Double.NEGATIVE_INFINITY, p.get("maxHeight"));
        assertEquals(0.5, p.get("opacity"));
        assertEquals(true, p.get("underline"));
        assertEquals("CENTER_LEFT", p.get("alignment"));
        assertEquals(3.0, p.get("gap"));
        assertEquals(4.0, p.get("hgap"));
        assertEquals(5.0, p.get("vgap"));
        assertEquals(true, p.get("wrapText"));
        assertEquals("RIGHT", p.get("textAlignment"));
    }

    @Test
    void fontShorthandAndNumericWeights() {
        Map<String, Object> p = declared("-fx-font: bold 13px System");
        assertEquals(true, p.get("bold"));
        assertEquals(13.0, p.get("fontSize"));
        assertEquals(true, declared("-fx-font-weight: 700").get("bold"));
        assertEquals(false, declared("-fx-font-weight: 400").get("bold"));
    }

    @Test
    void invalidValuesAreSkipped() {
        Map<String, Object> p = declared("-fx-opacity: nope; -fx-font-size: 11");
        assertFalse(p.containsKey("opacity"));
        assertEquals(11.0, p.get("fontSize"));
    }

    @Test
    void selectorsSpecificityAndPseudoClasses() throws Exception {
        File sheet = File.createTempFile("jxcss", ".css");
        sheet.deleteOnExit();
        Files.write(sheet.toPath(), (".root { -accent: #00ff00; }\n"
                + "/* comment */ .label { -fx-text-fill: blue; }\n"
                + "#titulo { -fx-text-fill: -accent; }\n"
                + "VBox > .label { -fx-font-size: 20; }\n"
                + ".destaque .label { -fx-font-weight: bold; }\n"
                + ".button:hover { -fx-background-color: yellow; }\n"
                + "@media screen { .x { } }\n").getBytes(StandardCharsets.UTF_8));
        Label[] labels = new Label[3];
        Button[] button = new Button[1];
        NativeScene s = show(fx(() -> {
            labels[0] = new Label("a");
            labels[1] = new Label("b");
            labels[1].setId("titulo");
            labels[2] = new Label("c");
            labels[2].setStyle("-fx-text-fill: #abcdef");
            HBox inner = new HBox(labels[2]);
            inner.getStyleClass().add("destaque");
            button[0] = new Button("btn");
            VBox root = new VBox(labels[0], labels[1], inner, button[0]);
            root.getStylesheets().add(sheet.toURI().toString());
            return root;
        }), 300, 200);

        assertEquals(0xFF0000FF, node(s, labels[0]).getProperty("textFill"));
        assertEquals(20.0, node(s, labels[0]).getProperty("fontSize"), "child combinator");
        assertEquals(0xFF00FF00, node(s, labels[1]).getProperty("textFill"), "id beats class; looked-up color");
        assertEquals(0xFFABCDEF, node(s, labels[2]).getProperty("textFill"), "inline style beats stylesheets");
        assertEquals(true, node(s, labels[2]).getProperty("bold"), "descendant combinator");
        assertNull(node(s, labels[2]).getProperty("fontSize"), "not a direct child of the VBox");
        assertNull(node(s, button[0]).getProperty("background"));
        JXNativeNodeHover(s, button[0]);
        assertEquals(0xFFFFFF00, node(s, button[0]).getProperty("background"), ":hover");
        close(s);
    }

    private static void JXNativeNodeHover(NativeScene s, Object jx) throws Exception {
        com.jxparallel.ui.native2d.JXNativeNode n = node(s, jx);
        move(s, n.getX() + 2, n.getY() + 2, false);
        settle();
    }

    @Test
    void stylesheetBeatsAValueSetInCode() throws Exception {
        Label label = fx(() -> {
            Label l = new Label("x");
            l.setTextFill(com.jxparallel.fx.scene.paint.Color.RED);
            l.setStyle("-fx-text-fill: #0000ff");
            return l;
        });
        assertEquals(0xFF0000FF, fx(() -> NativeElements.toElement(model(label))).getProps().get("textFill"));
    }

    @Test
    void defaultStyleClassesOfControls() {
        List<String> classes = NativeCss.styleClasses(new NativeModel(javafx.scene.control.PasswordField.class), null);
        assertEquals(java.util.Arrays.asList("text-input", "text-field", "password-field"), classes);
        assertTrue(NativeCss.styleClasses(new NativeModel(javafx.scene.layout.VBox.class), null).isEmpty());
    }

    @Test
    void parseKeepsEverySelectorOfARule() {
        List<NativeCss.Rule> rules = NativeCss.parseSheet(".a, .b:focused, #c { -fx-opacity: 1 }");
        assertEquals(3, rules.size());
        assertTrue(rules.get(2).selector.specificity > rules.get(1).selector.specificity);
        assertTrue(rules.get(1).selector.specificity > rules.get(0).selector.specificity);
    }
}
