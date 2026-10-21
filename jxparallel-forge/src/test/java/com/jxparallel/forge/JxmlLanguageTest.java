package com.jxparallel.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jxparallel.ui.JXElement;

/** For, If / Else, change events and two-way binding: the interpreter and the generated code must agree. */
class JxmlLanguageTest {
    private static final String CTRL = "com.jxparallel.forge.LanguageController";
    private static final String ORDER = CTRL + ".Order";
    private static final String LINE = CTRL + ".Line";

    @TempDir
    Path dir;
    private int classes;

    /** Line 1 is the root; the body starts on line 2. */
    private static String screen(String body) {
        return "<Column controller=\"" + CTRL + "\">\n" + body + "\n</Column>";
    }

    private static JxmlTemplate read(String xml) {
        return JxmlTemplate.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "t.jxml");
    }

    /** The interpreter, then the generated class with hoisting and without: all must build the same tree. */
    @SuppressWarnings("unchecked")
    private List<JxmlView<Object>> views(String xml) throws Exception {
        JxmlTemplate template = read(xml);
        List<JxmlView<Object>> out = new ArrayList<JxmlView<Object>>();
        out.add(template);
        for (boolean hoist : new boolean[] {true, false}) {
            String name = "Gen" + (classes++);
            out.add((JxmlView<Object>) (JxmlView<?>) javac(JxmlCompiler.compile(template,
                    new JxmlCompiler.Options("gen", name, hoist)), name).getDeclaredConstructor().newInstance());
        }
        return out;
    }

    /** Builds with each view and a controller from {@code setup}; asserts the trees agree; returns the interpreter's. */
    private JXElement agree(String xml, Consumer<LanguageController> setup) throws Exception {
        List<JxmlView<Object>> views = views(xml);
        LanguageController c = new LanguageController();
        setup.accept(c);
        JXElement first = views.get(0).build(c);
        for (int i = 1; i < views.size(); i++) {
            assertNull(JxmlTrees.diff(first, views.get(i).build(c)), "view " + i);
        }
        return first;
    }

    private JXElement agree(String xml) throws Exception {
        return agree(xml, c -> { });
    }

    private Class<?> javac(String java, String className) throws Exception {
        Path src = dir.resolve("gen/" + className + ".java");
        Files.createDirectories(src.getParent());
        Files.write(src, java.getBytes(StandardCharsets.UTF_8));
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertNotNull(javac, "tests need a JDK");
        String classpath = location(LanguageController.class) + File.pathSeparator + location(JxmlView.class)
                + File.pathSeparator + location(JXElement.class);
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int status = javac.run(null, null, errors, "-encoding", "UTF-8", "-source", "8", "-target", "8",
                "-nowarn", "-classpath", classpath, "-d", dir.toString(), src.toString());
        if (status != 0) {
            throw new AssertionError(new String(errors.toByteArray(), StandardCharsets.UTF_8) + "\n" + java);
        }
        return new URLClassLoader(new URL[] {dir.toUri().toURL()}, getClass().getClassLoader()).loadClass("gen." + className);
    }

    private static String location(Class<?> type) {
        try {
            return new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> values(JXElement e) {
        List<String> out = new ArrayList<String>();
        for (JXElement child : e.getChildren()) {
            out.add(String.valueOf(child.getProps().get("value")));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void change(JXElement input, Object value) {
        ((Consumer<Object>) input.getProps().get("onChange")).accept(value);
    }

    // ---- For ---------------------------------------------------------------------------------------

    @Test
    void forPlacesItsElementsInPlaceAmongTheSiblingsWithTheirKeys() throws Exception {
        String xml = screen("<Label text=\"head\"/>\n"
                + "<For items=\"${orders}\" var=\"o\" type=\"" + ORDER + "\" key=\"${o.id}\">\n"
                + "  <Row><Label text=\"${o.name}\"/><Button text=\"Open\" onAction=\"#open(o)\"/></Row>\n"
                + "</For>\n<Label text=\"foot\"/>");
        JXElement column = agree(xml);
        assertEquals(5, column.getChildren().size());
        assertEquals("head", column.getChildren().get(0).getProps().get("value"));
        assertEquals("foot", column.getChildren().get(4).getProps().get("value"));
        for (int i = 1; i <= 3; i++) {
            JXElement row = column.getChildren().get(i);
            assertEquals("row", row.getType());
            assertEquals(Integer.valueOf(i), row.getProps().get("key")); // the Integer the getter returned
            assertEquals(String.valueOf((char) ('A' + i - 1)), row.getChildren().get(0).getProps().get("value"));
        }
    }

    @Test
    void forHandlersGetTheItem() throws Exception {
        String xml = screen("<For items=\"${orders}\" var=\"o\" type=\"" + ORDER + "\">"
                + "<Button text=\"${o.name}\" onAction=\"#open(o)\"/></For>");
        for (JxmlView<Object> view : views(xml)) {
            LanguageController c = new LanguageController();
            JXElement column = view.build(c);
            ((Runnable) column.getChildren().get(2).getProps().get("onAction")).run();
            ((Runnable) column.getChildren().get(0).getProps().get("onAction")).run();
            assertEquals(Arrays.asList("open C", "open A"), c.calls);
        }
    }

    @Test
    void forWithoutKeyLeavesNoKeyProp() throws Exception {
        JXElement column = agree(screen("<For items=\"${names}\" var=\"n\"><Label text=\"${n}\"/></For>"));
        assertEquals(Arrays.asList("n1", "n2"), values(column));
        assertFalse(column.getChildren().get(0).getProps().asMap().containsKey("key"));
    }

    @Test
    void forTakesIterablesAndArraysOfObjectsOrPrimitives() throws Exception {
        JXElement column = agree(screen("<For items=\"${numbers}\" var=\"n\"><Label text=\"${n}\"/></For>"
                + "<For items=\"${names}\" var=\"n\"><Label text=\"${n}\"/></For>"));
        assertEquals(Arrays.asList("7", "8", "9", "n1", "n2"), values(column));
    }

    @Test
    void anEmptyOrNullCollectionAddsNothing() throws Exception {
        JXElement column = agree(screen("<Label text=\"x\"/><For items=\"${orders}\" var=\"o\"><Label text=\"y\"/></For>"),
                c -> c.orders.clear());
        assertEquals(1, column.getChildren().size());
    }

    @Test
    void nestedLoopsAndShadowedVariables() throws Exception {
        String xml = screen("<For items=\"${orders}\" var=\"x\" type=\"" + ORDER + "\">\n"
                + "  <Label text=\"${x.name}\"/>\n"
                + "  <For items=\"${x.lines}\" var=\"x\" type=\"" + LINE + "\"><Label text=\"${x.text}\"/></For>\n"
                + "  <Label text=\"${x.id}\"/>\n"
                + "</For>");
        JXElement column = agree(xml);
        assertEquals(Arrays.asList("A", "x", "y", "1", "B", "2", "C", "z", "3"), values(column));
    }

    @Test
    void loopsNestInsideContainersThatAreInsideLoops() throws Exception {
        String xml = screen("<For items=\"${orders}\" var=\"o\" type=\"" + ORDER + "\">\n"
                + "  <Row><For items=\"${o.lines}\" var=\"l\" type=\"" + LINE + "\"><Label text=\"${l.text}\"/></For></Row>\n"
                + "</For>");
        JXElement column = agree(xml);
        assertEquals(3, column.getChildren().size());
        assertEquals(Arrays.asList("x", "y"), values(column.getChildren().get(0)));
        assertEquals(0, column.getChildren().get(1).getChildren().size());
    }

    @Test
    void constantElementsInsideALoopAreSharedWhenHoisted() throws Exception {
        String xml = screen("<For items=\"${names}\" var=\"n\"><Label text=\"static\"/></For>");
        List<JxmlView<Object>> views = views(xml);
        LanguageController c = new LanguageController();
        JXElement hoisted = views.get(1).build(c);
        assertSame(hoisted.getChildren().get(0), hoisted.getChildren().get(1));
        JXElement plain = views.get(2).build(c);
        assertTrue(plain.getChildren().get(0) != plain.getChildren().get(1));
    }

    // ---- If / Else ---------------------------------------------------------------------------------

    @Test
    void ifIncludesItsChildrenWhenTrueAndElseOtherwise() throws Exception {
        String xml = screen("<Label text=\"a\"/>\n<If test=\"${admin}\"><Label text=\"yes1\"/><Label text=\"yes2\"/></If>\n"
                + "<Else><Label text=\"no\"/></Else>\n<Label text=\"z\"/>");
        assertEquals(Arrays.asList("a", "yes1", "yes2", "z"), values(agree(xml, c -> c.admin = true)));
        assertEquals(Arrays.asList("a", "no", "z"), values(agree(xml, c -> c.admin = false)));
    }

    @Test
    void ifWithoutElseAndNegation() throws Exception {
        String xml = screen("<If test=\"${!admin}\"><Label text=\"user\"/></If>");
        assertEquals(Arrays.asList("user"), values(agree(xml, c -> c.admin = false)));
        assertEquals(0, agree(xml, c -> c.admin = true).getChildren().size());
    }

    @Test
    void ifInsideLoopsReadsTheItemAndLoopsInsideElse() throws Exception {
        String xml = screen("<For items=\"${orders}\" var=\"o\" type=\"" + ORDER + "\">\n"
                + "  <If test=\"${o.paid}\"><Label text=\"${o.name}\"/></If>\n"
                + "  <Else><For items=\"${names}\" var=\"n\"><Label text=\"${n}\"/></For></Else>\n"
                + "</For>");
        assertEquals(Arrays.asList("A", "n1", "n2", "C"), values(agree(xml)));
    }

    // ---- change events and two-way binding ---------------------------------------------------------

    @Test
    void onChangeCallsTheControllerWithTheTypedValue() throws Exception {
        String xml = screen("<TextField onChange=\"#setNote\"/><CheckBox onChange=\"#setAgree\"/>"
                + "<Spinner onChange=\"#setAge\"/><ComboBox onChange=\"#setCity\"/><TextArea onChange=\"#setNote\"/>"
                + "<PasswordField onChange=\"#setNote\"/><DatePicker onChange=\"#setCity\"/>");
        for (JxmlView<Object> view : views(xml)) {
            LanguageController c = new LanguageController();
            List<JXElement> inputs = view.build(c).getChildren();
            change(inputs.get(0), "hello");
            assertEquals("hello", c.note);
            change(inputs.get(1), Boolean.TRUE);
            assertTrue(c.agree);
            change(inputs.get(2), Double.valueOf(4.5));
            assertEquals(4.5, c.age);
            change(inputs.get(3), "Recife");
            assertEquals("Recife", c.city);
            change(inputs.get(4), "area");
            assertEquals("area", c.note);
            change(inputs.get(5), "pin");
            assertEquals("pin", c.note);
            change(inputs.get(6), "2026-10-21");
            assertEquals("2026-10-21", c.city);
        }
    }

    @Test
    void onChangeWithALoopItemPassesItFirst() throws Exception {
        String xml = screen("<For items=\"${orders}\" var=\"o\" type=\"" + ORDER + "\">"
                + "<TextField text=\"${o.name}\" onChange=\"#rename(o)\"/></For>");
        for (JxmlView<Object> view : views(xml)) {
            LanguageController c = new LanguageController();
            change(view.build(c).getChildren().get(1), "Bee");
            assertEquals(Arrays.asList("rename B Bee"), c.calls);
        }
    }

    @Test
    void twoWayBindingReadsTheGetterAndCallsTheSetter() throws Exception {
        String xml = screen("<TextField text=\"@{customer.name}\"/><CheckBox selected=\"@{customer.active}\"/>"
                + "<Slider value=\"@{customer.score}\"/><TextField text=\"@{note}\"/><CheckBox selected=\"@{agree}\"/>");
        assertEquals("Ada", agree(xml).getChildren().get(0).getProps().get("value"));
        for (JxmlView<Object> view : views(xml)) {
            LanguageController c = new LanguageController();
            List<JXElement> inputs = view.build(c).getChildren();
            assertEquals(Boolean.FALSE, inputs.get(1).getProps().get("checked"));
            change(inputs.get(0), "Grace");
            change(inputs.get(1), Boolean.TRUE);
            change(inputs.get(2), Double.valueOf(0.5));
            change(inputs.get(3), "n");
            change(inputs.get(4), Boolean.TRUE);
            assertEquals("Grace", c.customer.name);
            assertTrue(c.customer.active);
            assertEquals(0.5, c.customer.score);
            assertEquals("n", c.note);
            assertTrue(c.agree);
            assertEquals("Grace", view.build(c).getChildren().get(0).getProps().get("value")); // read back
        }
    }

    @Test
    void twoWayBindingOnALoopItem() throws Exception {
        String xml = screen("<For items=\"${orders}\" var=\"o\" type=\"" + ORDER + "\">"
                + "<TextField text=\"@{o.name}\"/></For>");
        for (JxmlView<Object> view : views(xml)) {
            LanguageController c = new LanguageController();
            change(view.build(c).getChildren().get(1), "Bee");
            assertEquals("Bee", c.orders.get(1).getName());
            assertEquals("Bee", view.build(c).getChildren().get(1).getProps().get("value"));
        }
    }

    // ---- generated code is checked by javac --------------------------------------------------------

    @Test
    void aMisspelledItemMethodFailsInJavacWithTheJxmlLine() throws Exception {
        String xml = screen("<For items=\"${orders}\" var=\"o\" type=\"" + ORDER + "\">\n<Label text=\"${o.nmae}\"/>\n</For>");
        String java = JxmlCompiler.compile(read(xml), new JxmlCompiler.Options("gen", "TypoItem", true));
        AssertionError e = assertThrows(AssertionError.class, () -> javac(java, "TypoItem"));
        assertTrue(e.getMessage().contains("getNmae"), e.getMessage());
        assertTrue(java.contains("t.jxml:3"), java);
    }

    @Test
    void aSetterOfTheWrongTypeFailsInJavac() throws Exception {
        String xml = screen("<Slider value=\"@{customer.rating}\"/>");
        String java = JxmlCompiler.compile(read(xml), new JxmlCompiler.Options("gen", "WrongSetter", true));
        assertThrows(AssertionError.class, () -> javac(java, "WrongSetter"));
    }

    @Test
    void aMisspelledChangeHandlerFailsInJavac() throws Exception {
        String xml = screen("<TextField onChange=\"#setNot\"/>");
        String java = JxmlCompiler.compile(read(xml), new JxmlCompiler.Options("gen", "TypoChange", true));
        AssertionError e = assertThrows(AssertionError.class, () -> javac(java, "TypoChange"));
        assertTrue(e.getMessage().contains("setNot"), e.getMessage());
    }

    @Test
    void generatedLoopsAreTyped() {
        String xml = screen("<For items=\"${orders}\" var=\"o\" type=\"" + ORDER + "\"><Label text=\"${o.name}\"/></For>");
        String java = JxmlCompiler.compile(read(xml), new JxmlCompiler.Options("gen", "Typed", true));
        assertTrue(java.contains("for (" + ORDER + " o_0 : JxmlRuntime.<" + ORDER + ">items(c.getOrders()))"), java);
        assertTrue(java.contains("o_0.getName()"), java);
    }

    // ---- errors ------------------------------------------------------------------------------------

    private static void assertError(int line, String message, String body) {
        JxmlException e = assertThrows(JxmlException.class, () -> read(screen(body)));
        assertEquals(line, e.getLine(), e.getMessage());
        assertTrue(e.getMessage().startsWith("t.jxml:" + line + ": "), e.getMessage());
        assertTrue(e.getMessage().contains(message), e.getMessage());
    }

    @Test
    void misusedLoopsAndConditionsAreReportedWithTheirLine() {
        assertError(2, "<For> needs items", "<For var=\"o\"/>");
        assertError(3, "<For> needs var", "<Label text=\"x\"/>\n<For items=\"${orders}\"/>");
        assertError(2, "must be a binding", "<For items=\"orders\" var=\"o\"/>");
        assertError(2, "must be a class name", "<For items=\"${orders}\" var=\"o\" type=\"a-b\"/>");
        assertError(2, "has no attribute margin", "<For items=\"${orders}\" var=\"o\" margin=\"1\"/>");
        assertError(2, "<If> needs test", "<If/>");
        assertError(2, "<Else> must directly follow an <If>", "<Else/>");
        assertError(3, "<Else> must directly follow an <If>", "<Label text=\"x\"/>\n<Else/>");
        assertError(4, "<Else> must directly follow an <If>", "<If test=\"${admin}\"/>\n<Else/>\n<Else/>");
        assertError(3, "<Else> must directly follow an <If>", "<If test=\"${admin}\"/>\n<Label text=\"x\"/><Else/>");
        assertError(3, "<Else> has no attribute x", "<If test=\"${admin}\"/>\n<Else x=\"1\"/>");
        assertError(2, "only allowed where a tag takes many children", "<Scroll><For items=\"${orders}\" var=\"o\"/></Scroll>");
        assertError(2, "only allowed where a tag takes many children", "<Tabs><Tab title=\"t\"><If test=\"${admin}\"/></Tab></Tabs>");
        assertError(2, "only allowed in <If test>", "<Label text=\"${!admin}\"/>");
        assertError(2, "needs type=", "<For items=\"${orders}\" var=\"o\"><Label text=\"${o.name}\"/></For>");
        assertError(2, "needs type=", "<For items=\"${orders}\" var=\"o\"><Button onAction=\"#open(o)\"/></For>");
        assertError(2, "needs type=", "<For items=\"${orders}\" var=\"o\"><TextField text=\"@{o.name}\"/></For>");
        assertError(3, "is not a loop variable in scope", "<For items=\"${orders}\" var=\"o\" type=\"" + ORDER + "\">\n"
                + "<Button onAction=\"#open(p)\"/></For>");
        assertError(2, "is not a loop variable in scope", "<Button onAction=\"#open(o)\"/>");
        assertError(2, "must name a controller method", "<TextField onChange=\"setNote\"/>");
        assertError(2, "must name a controller method", "<Button onAction=\"#open(o\"/>");
        assertError(2, "exactly one element", "<For items=\"${orders}\" var=\"o\" key=\"${o}\">"
                + "<Label text=\"a\"/><Label text=\"b\"/></For>");
        assertError(2, "exactly one element", "<For items=\"${orders}\" var=\"o\" key=\"${o}\"/>");
        assertError(2, "already has a key", "<For items=\"${orders}\" var=\"o\" key=\"${o}\"><Label key=\"k\"/></For>");
    }

    @Test
    void misusedChangeAndTwoWayBindingsAreReportedWithTheirLine() {
        assertError(2, "record accessor", "<TextField text=\"@{customer.city()}\"/>");
        assertError(2, "cannot be bound two ways", "<Label text=\"@{note}\"/>");
        assertError(2, "cannot be bound two ways", "<ComboBox items=\"@{note}\"/>");
        assertError(2, "use selected=", "<CheckBox text=\"@{note}\"/>");
        assertError(2, "bad binding @{o}", "<For items=\"${orders}\" var=\"o\"><TextField text=\"@{o}\"/></For>");
        assertError(2, "bad binding", "<TextField text=\"@{a..b}\"/>");
        assertError(2, "both onChange and a @{...} binding", "<TextField text=\"@{note}\" onChange=\"#setNote\"/>");
        assertError(2, "has no attribute onChange", "<Label onChange=\"#setNote\"/>");
        assertError(2, "has no attribute onChange", "<Button onChange=\"#setNote\"/>");
    }

    @Test
    void controllerProblemsFoundWhileBuildingNameTheLine() {
        JxmlTemplate misspelled = read(screen("<TextField onChange=\"#setNot\"/>"));
        JxmlException e = assertThrows(JxmlException.class, () -> misspelled.build(new LanguageController()));
        assertTrue(e.getMessage().startsWith("t.jxml:2:") && e.getMessage().contains("setNot"), e.getMessage());
        JxmlTemplate notIterable = read(screen("<For items=\"${note}\" var=\"n\"/>"));
        JxmlException f = assertThrows(JxmlException.class, () -> notIterable.build(new LanguageController()));
        assertTrue(f.getMessage().startsWith("t.jxml:2:") && f.getMessage().contains("Iterable"), f.getMessage());
        JxmlTemplate wrongSetter = read(screen("<Slider value=\"@{customer.rating}\"/>"));
        JxmlException g = assertThrows(JxmlException.class,
                () -> change(wrongSetter.build(new LanguageController()).getChildren().get(0), Double.valueOf(1)));
        assertTrue(g.getMessage().startsWith("t.jxml:2:") && g.getMessage().contains("setRating"), g.getMessage());
    }
}
