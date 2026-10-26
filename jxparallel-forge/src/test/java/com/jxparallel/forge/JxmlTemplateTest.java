package com.jxparallel.forge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;

class JxmlTemplateTest {
    static JxmlTemplate feature() {
        return JxmlTemplate.read(JxmlTemplateTest.class.getResourceAsStream("/feature.jxml"), "feature.jxml");
    }

    private static JxmlTemplate read(String xml) {
        return JxmlTemplate.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "t.jxml");
    }

    @Test
    void propsGetTheTypesTheRendererReads() {
        FeatureController c = new FeatureController();
        JXElement root = feature().build(c);
        assertEquals("border", root.getType());
        JXElement top = root.getChildren().get(0);
        assertEquals("row", top.getType());
        assertEquals(Double.valueOf(4), top.getProps().get("gap"));
        assertArrayEquals(new double[] {4, 8, 4, 8}, (double[]) top.getProps().get("padding"));
        assertEquals("top", top.getProps().get("position"));
        assertEquals(Boolean.TRUE, top.getChildren().get(0).getProps().get("defaultButton"));
        assertEquals(Boolean.FALSE, top.getChildren().get(1).getProps().get("disabled"));
        assertEquals("Ready", top.getChildren().get(3).getProps().get("value"));
        assertEquals("always", top.getChildren().get(3).getProps().get("hgrow"));
    }

    @Test
    void tabsShowTheSelectedContentAndDeferTheOthers() {
        JXElement tabs = feature().build(new FeatureController()).getChildren().get(1);
        assertEquals("tabs", tabs.getType());
        assertEquals(Integer.valueOf(1), tabs.getProps().get("selected"));
        assertArrayEquals(new String[] {"Customer", "Notes", "Static"}, (String[]) tabs.getProps().get("titles"));
        assertTrue(Arrays.equals(new boolean[] {false, true, true}, (boolean[]) tabs.getProps().get("closable")));
        assertEquals(1, tabs.getChildren().size());
        JXElement shown = tabs.getChildren().get(0);
        assertEquals("column", shown.getType());
        assertSame("tab1", shown.getProps().get("key")); // the literal's instance: keys are compared with ==
        JXElement[] deferred = (JXElement[]) tabs.getProps().get("deferredTabs");
        assertEquals(2, deferred.length);
        assertSame("tab0", deferred[0].getProps().get("key"));
        assertEquals("#1 nothing bound here", deferred[1].getChildren().get(0).getProps().get("value"));
    }

    @Test
    void bindingsAndSlotsReadTheController() {
        JXElement tabs = feature().build(new FeatureController()).getChildren().get(1);
        JXElement grid = ((JXElement[]) tabs.getProps().get("deferredTabs"))[0].getChildren().get(0);
        assertEquals("Ada", grid.getChildren().get(1).getProps().get("value"));
        assertEquals(Integer.valueOf(1), grid.getChildren().get(1).getProps().get("column"));
        assertArrayEquals(new String[] {"Fortaleza", "Recife"}, (String[]) grid.getChildren().get(3).getProps().get("options"));
        assertEquals("Fortaleza", grid.getChildren().get(6).getProps().get("value")); // ${customer.city()}
        JXElement notes = tabs.getChildren().get(0);
        assertEquals(Double.valueOf(0.25), notes.getChildren().get(3).getProps().get("progress"));
        assertEquals("hand-written", notes.getChildren().get(4).getProps().get("value"));
    }

    @Test
    void handlersCallTheControllerMethod() {
        FeatureController c = new FeatureController();
        JXElement top = feature().build(c).getChildren().get(0);
        ((Runnable) top.getChildren().get(1).getProps().get("onAction")).run();
        ((Runnable) top.getChildren().get(0).getProps().get("onAction")).run();
        assertEquals(Arrays.asList("cancel", "save"), c.calls);
    }

    @Test
    void errorsNameTheFileAndLine() {
        assertLine(2, "unknown tag <Colum>", "<Column>\n<Colum/>\n</Column>");
        assertLine(1, "has no attribute spacing", "<Column spacing=\"4\"/>");
        assertLine(1, "is not a valid double", "<Column gap=\"four\"/>");
        assertLine(1, "cannot be bound", "<Grid hgap=\"${gap}\"/>");
        assertLine(1, "must name a controller method", "<Button onAction=\"save\"/>");
        assertLine(1, "takes no children", "<Label><Label/></Label>");
        assertLine(2, "text content is not allowed", "<Column>\n hello </Column>");
        assertLine(1, "only allowed inside <Tabs>", "<Tab title=\"x\"><Column/></Tab>");
        assertLine(2, "takes only <Tab> children", "<Tabs>\n<Column/></Tabs>");
        assertLine(1, "exactly one attribute", "<Slot/>");
        assertLine(1, "expected true or false", "<CheckBox selected=\"yes\"/>");
    }

    private static void assertLine(int line, String message, String xml) {
        JxmlException e = assertThrows(JxmlException.class, () -> read(xml));
        assertEquals(line, e.getLine(), e.getMessage());
        assertTrue(e.getMessage().startsWith("t.jxml:" + line + ": "), e.getMessage());
        assertTrue(e.getMessage().contains(message), e.getMessage());
    }

    @Test
    void aMissingGetterIsReportedWithItsLine() {
        JxmlTemplate t = read("<Label text=\"${nope}\"/>");
        JxmlException e = assertThrows(JxmlException.class, () -> t.build(new FeatureController()));
        assertTrue(e.getMessage().contains("t.jxml:1:") && e.getMessage().contains("getNope()"), e.getMessage());
    }
}
