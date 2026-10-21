package com.jxparallel.forge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jxparallel.forge.samples.SampleController;
import com.jxparallel.ui.JXElement;

/**
 * Every tag of {@link JxmlSchema}, checked in tables that walk the schema and the sample screens in
 * {@code src/test/shared/samples} (which the renderer's mount tests in jxparallel-examples-native also use):
 * a tag or attribute without a sample, a prop without a declared type, or an error without a message fails here,
 * so a new tag cannot skip its tests.
 */
class JxmlTagsTest {
    private static final String CTRL = "com.jxparallel.forge.samples.SampleController";
    private static final String ROW = CTRL + ".Row";

    @TempDir
    Path dir;

    // ---- the samples ------------------------------------------------------------------------------

    static List<File> samples() {
        File[] files = new File("src/test/shared/samples").listFiles();
        assertTrue(files != null && files.length > 0, "samples are in src/test/shared/samples");
        List<File> out = new ArrayList<File>();
        for (File f : files) {
            if (f.getName().endsWith(".jxml")) {
                out.add(f);
            }
        }
        Collections.sort(out);
        return out;
    }

    private static String text(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    /** Controller states that take the branches of the samples both ways. */
    private static List<SampleController> states() {
        List<SampleController> out = new ArrayList<SampleController>();
        out.add(new SampleController());
        SampleController other = new SampleController();
        other.top = 7;
        other.limit = 0;
        other.page = 0;
        other.busy = true;
        other.on = false;
        other.open = false;
        other.hidden = true;
        other.choice = "a";
        other.alpha = 1;
        out.add(other);
        return out;
    }

    // ---- (a) interpreter and compiler build the same tree -------------------------------------------

    @Test
    void everySampleBuildsTheSameTreeInterpretedAndCompiled() throws Exception {
        ViewCompiler compiler = new ViewCompiler(dir);
        for (File sample : samples()) {
            List<JxmlView<Object>> views = compiler.views(text(sample), sample.getName());
            for (SampleController c : states()) {
                JXElement first = views.get(0).build(c);
                for (int i = 1; i < views.size(); i++) {
                    assertNull(JxmlTrees.diff(first, views.get(i).build(c)), sample.getName() + ", view " + i);
                }
            }
        }
    }

    // ---- the samples cover the schema ---------------------------------------------------------------

    private static void collect(JxmlParser.Node node, Map<String, Set<String>> into) {
        Set<String> attrs = into.get(node.tag);
        if (attrs == null) {
            attrs = new LinkedHashSet<String>();
            into.put(node.tag, attrs);
        }
        attrs.addAll(node.attrs.keySet());
        for (JxmlParser.Node child : node.children) {
            collect(child, into);
        }
    }

    @Test
    void everyTagAndAttributeAppearsInASample() throws Exception {
        Map<String, Set<String>> used = new LinkedHashMap<String, Set<String>>();
        for (File sample : samples()) {
            InputStream in = new FileInputStream(sample);
            try {
                collect(JxmlParser.parse(in, sample.getName()), used);
            } finally {
                in.close();
            }
        }
        Set<String> anywhere = new LinkedHashSet<String>();
        for (Set<String> attrs : used.values()) {
            anywhere.addAll(attrs);
        }
        List<String> missing = new ArrayList<String>();
        for (JxmlSchema.Tag tag : JxmlSchema.tags().values()) {
            if (!used.containsKey(tag.name)) {
                missing.add("<" + tag.name + "> is in no sample");
                continue;
            }
            if (tag.alias != null) {
                continue; // takes the attributes of the tag it renames, which have samples of their own
            }
            for (String attr : tag.attrs.keySet()) {
                if (!used.get(tag.name).contains(attr)) {
                    missing.add("<" + tag.name + "> " + attr + " is in no sample");
                }
            }
        }
        for (String attr : JxmlSchema.LAYOUT.keySet()) {
            if (!anywhere.contains(attr)) {
                missing.add("the layout attribute " + attr + " is in no sample");
            }
        }
        assertEquals(Collections.<String>emptyList(), missing);
    }

    // ---- (b) props have the Java types the schema declares ------------------------------------------

    private static Class<?>[] classes(AttrType type) {
        switch (type) {
            case STRING:
                return new Class<?>[] {String.class};
            case DOUBLE:
                return new Class<?>[] {Double.class};
            case INT:
            case COUNT:
            case COLOR:
                return new Class<?>[] {Integer.class};
            case BOOLEAN:
                return new Class<?>[] {Boolean.class};
            case INSETS:
                return new Class<?>[] {Double.class, double[].class};
            case STRINGS:
                return new Class<?>[] {String[].class};
            case DOUBLES:
                return new Class<?>[] {double[].class};
            case HANDLER:
                return new Class<?>[] {Runnable.class};
            case CHANGE:
                return new Class<?>[] {Consumer.class};
            case ELEMENT:
                return new Class<?>[] {JXElement.class};
            case PIXELS:
                return new Class<?>[] {int[].class};
            default:
                return new Class<?>[] {Object.class};
        }
    }

    /** The classes a prop of an element of this native type may have, or null if no tag declares it. */
    private static List<Class<?>> declared(String nativeType, String prop) {
        List<Class<?>> out = new ArrayList<Class<?>>();
        for (JxmlSchema.Tag tag : JxmlSchema.tags().values()) {
            if (!nativeType.equals(tag.nativeType)) {
                continue;
            }
            for (JxmlSchema.Attr attr : tag.attrs.values()) {
                if (attr.prop.equals(prop)) {
                    out.addAll(Arrays.asList(classes(attr.type)));
                }
            }
            if (tag.defaults.get(prop) != null) {
                out.add(tag.defaults.get(prop).getClass());
            }
        }
        for (JxmlSchema.Attr attr : JxmlSchema.LAYOUT.values()) {
            if (attr.prop.equals(prop)) {
                out.addAll(Arrays.asList(classes(attr.type)));
            }
        }
        if ("key".equals(prop)) {
            out.add(Object.class);
        } else if ("scrollY".equals(prop)) {
            out.add(Double.class); // derived by JxmlRuntime.finish for the virtual lists
        } else if ("deferredTabs".equals(prop)) {
            out.add(JXElement[].class);
        } else if ("titles".equals(prop)) {
            out.add(String[].class);
        } else if ("closable".equals(prop)) {
            out.add(boolean[].class);
        }
        return out.isEmpty() ? null : out;
    }

    private static void checkTypes(JXElement e, String where, List<String> problems) {
        for (Map.Entry<String, Object> prop : e.getProps().asMap().entrySet()) {
            List<Class<?>> allowed = declared(e.getType(), prop.getKey());
            if (allowed == null) {
                problems.add(where + e.getType() + "@" + prop.getKey() + ": no tag declares this prop");
                continue;
            }
            boolean ok = false;
            for (Class<?> c : allowed) {
                ok |= c.isInstance(prop.getValue());
            }
            if (!ok) {
                problems.add(where + e.getType() + "@" + prop.getKey() + ": " + prop.getValue().getClass().getSimpleName()
                        + " is none of " + allowed);
            }
        }
        for (JXElement child : e.getChildren()) {
            checkTypes(child, where + e.getType() + "/", problems);
        }
        Object deferred = e.getProps().get("deferredTabs");
        if (deferred instanceof JXElement[]) {
            for (JXElement d : (JXElement[]) deferred) {
                checkTypes(d, where + e.getType() + "/", problems);
            }
        }
    }

    @Test
    void everyPropHasADeclaredTypeAndTheBuiltValueHasIt() throws Exception {
        List<String> problems = new ArrayList<String>();
        for (File sample : samples()) {
            for (SampleController c : states()) {
                checkTypes(ViewCompiler.read(text(sample), sample.getName()).build(c), sample.getName() + ":", problems);
            }
        }
        assertEquals(Collections.<String>emptyList(), problems);
    }

    // ---- (d) errors ---------------------------------------------------------------------------------

    private static JxmlException error(String xml) {
        return assertThrows(JxmlException.class, () -> ViewCompiler.read(xml, "t.jxml"));
    }

    private static void assertError(int line, String message, String xml) {
        JxmlException e = error(xml);
        assertEquals(line, e.getLine(), e.getMessage());
        assertTrue(e.getMessage().startsWith("t.jxml:" + line + ": "), e.getMessage());
        assertTrue(e.getMessage().contains(message), e.getMessage());
    }

    /** A document with one element of the tag, written with {@code extra} attributes, that is otherwise valid. */
    private static String stub(String tag, String extra) {
        switch (tag) {
            case "ListView":
                return "<Column>\n<ListView items=\"${rows}\" var=\"r\" " + extra + "><ListItem/></ListView>\n</Column>";
            case "TableView":
                return "<Column>\n<TableView items=\"${rows}\" var=\"r\" " + extra + "><TableRow/></TableView>\n</Column>";
            case "Tab":
                return "<Column>\n<Tabs><Tab " + extra + "><Label/></Tab></Tabs>\n</Column>";
            case "For":
                return "<Column>\n<For items=\"${rows}\" var=\"r\" " + extra + "/>\n</Column>";
            case "If":
                return "<Column>\n<If test=\"${busy}\" " + extra + "/>\n</Column>";
            case "ListTile":
                return "<Column>\n<ListTile title=\"t\" " + extra + "/>\n</Column>";
            case "Expanded":
            case "Flexible":
                return "<Column>\n<" + tag + " " + extra + "><Label/></" + tag + ">\n</Column>";
            case "Icon":
                return "<Column>\n<Icon name=\"add\" " + extra + "/>\n</Column>";
            case "IconButton":
                return "<Column>\n<IconButton icon=\"add\" " + extra + "/>\n</Column>";
            case "Fab":
                return "<Column>\n<Fab icon=\"add\" " + extra + "/>\n</Column>";
            case "Badge":
                return "<Column>\n<Badge " + extra + "><Label/></Badge>\n</Column>";
            case "MenuItem":
                return "<Column>\n<MenuButton><MenuItem " + extra + "/></MenuButton>\n</Column>";
            case "Menu":
                return "<Column>\n<MenuBar><Menu " + extra + "/></MenuBar>\n</Column>";
            case "TreeItem":
                return "<Column>\n<Tree><TreeItem " + extra + "/></Tree>\n</Column>";
            default:
                return "<Column>\n<" + tag + " " + extra + "/>\n</Column>";
        }
    }

    @Test
    void everyTagRejectsAnUnknownAttributeWithItsLine() {
        for (JxmlSchema.Tag tag : JxmlSchema.tags().values()) {
            if ("Else".equals(tag.name)) {
                continue; // its only error is where it stands, tested with the language
            }
            JxmlException e = error(stub(tag.name, "bogus=\"1\""));
            assertEquals(2, e.getLine(), tag.name + ": " + e.getMessage());
            assertTrue(e.getMessage().contains("<" + tag.name + "> has no attribute bogus"), tag.name + ": " + e.getMessage());
        }
    }

    /** A literal that is not valid for the type, or null when every text is (a string, a handler checked elsewhere). */
    private static String badLiteral(AttrType type) {
        switch (type) {
            case DOUBLE:
            case INT:
            case DOUBLES:
                return "four";
            case BOOLEAN:
                return "yes";
            case INSETS:
                return "1 2 3";
            case COLOR:
                return "red";
            case HANDLER:
            case CHANGE:
                return "save";
            case COUNT:
            case PIXELS:
            case OBJECT:
            case ELEMENT:
                return "literal"; // bindings only
            default:
                return null;
        }
    }

    @Test
    void everyAttributeOfEveryTagRejectsAnInvalidLiteralWithItsLine() {
        int checked = 0;
        for (JxmlSchema.Tag tag : JxmlSchema.tags().values()) {
            if (tag.control || tag.alias != null || "Slot".equals(tag.name) || "Tab".equals(tag.name)) {
                continue; // loops and slots have their own errors; aliases take the attributes of the tag they rename
            }
            for (JxmlSchema.Attr attr : tag.attrs.values()) {
                String bad = badLiteral(attr.type);
                // the stub of a virtual list already writes items and var; rows is checked as the window of its loop
                if (bad == null || tag.virtual && ("items".equals(attr.name) || "var".equals(attr.name)
                        || "type".equals(attr.name) || "rows".equals(attr.name))) {
                    continue;
                }
                JxmlException e = error(stub(tag.name, attr.name + "=\"" + bad + "\""));
                assertEquals(2, e.getLine(), tag.name + " " + attr.name + ": " + e.getMessage());
                assertTrue(e.getMessage().toLowerCase().contains(attr.name.toLowerCase()),
                        tag.name + " " + attr.name + ": " + e.getMessage());
                checked++;
            }
        }
        for (JxmlSchema.Attr attr : JxmlSchema.LAYOUT.values()) { // the attributes every tag takes
            String bad = badLiteral(attr.type);
            if (bad != null) {
                JxmlException e = error(stub("Label", attr.name + "=\"" + bad + "\""));
                assertEquals(2, e.getLine(), attr.name + ": " + e.getMessage());
                assertTrue(e.getMessage().contains(attr.name), attr.name + ": " + e.getMessage());
                checked++;
            }
        }
        assertTrue(checked > 80, "checked " + checked);
    }

    @Test
    void newTagsReportTheirOwnMistakes() {
        // virtual lists
        assertError(2, "needs items=", "<Column>\n<ListView var=\"r\"><ListItem/></ListView></Column>");
        assertError(2, "needs var=", "<Column>\n<ListView items=\"${rows}\"><ListItem/></ListView></Column>");
        assertError(2, "needs var=", "<Column>\n<ListView items=\"${rows}\" var=\"1x\"><ListItem/></ListView></Column>");
        assertError(2, "takes one child, the template of a row", "<Column>\n<ListView items=\"${rows}\" var=\"r\"/></Column>");
        assertError(2, "takes one child, the template of a row",
                "<Column>\n<ListView items=\"${rows}\" var=\"r\"><ListItem/><ListItem/></ListView></Column>");
        assertError(3, "is one element, not <If>",
                "<Column>\n<ListView items=\"${rows}\" var=\"r\">\n<If test=\"${busy}\"/></ListView></Column>");
        assertError(3, "is one element, not <Slot>",
                "<Column>\n<ListView items=\"${rows}\" var=\"r\">\n<Slot content=\"${banner}\"/></ListView></Column>");
        assertError(2, "is not a valid count", "<Column>\n<ListView items=\"abc\" var=\"r\"><ListItem/></ListView></Column>");
        assertError(2, "cellHeight must be greater than 0",
                "<Column>\n<ListView items=\"${rows}\" var=\"r\" cellHeight=\"0\"><ListItem/></ListView></Column>");
        assertError(2, "rowHeight must be greater than 0",
                "<Column>\n<TableView items=\"${rows}\" var=\"r\" rowHeight=\"-3\"><TableRow/></TableView></Column>");
        // only the row height must be positive: the layout sizes of the list itself may be 0
        ViewCompiler.read("<Column>\n<ListView items=\"${rows}\" var=\"r\" minHeight=\"0\" prefHeight=\"0\" maxHeight=\"0\">"
                + "<ListItem/></ListView>\n<TableView items=\"${rows}\" var=\"r\" minHeight=\"0\"><TableRow/></TableView></Column>", "t.jxml");
        assertError(2, "first cannot be bound two ways",
                "<Column>\n<ListView items=\"${rows}\" var=\"r\" first=\"@{top}\"><ListItem/></ListView></Column>");
        assertError(3, "takes one <TableRow>",
                "<Column>\n<TableView items=\"${rows}\" var=\"r\">\n<ListItem/></TableView></Column>");
        assertError(2, "is not a valid doubles", "<Column>\n<TableView items=\"${rows}\" var=\"r\" columnWidths=\"a, b\">"
                + "<TableRow/></TableView></Column>");
        assertError(2, "the loop variable r is used with getters", "<Column>\n<ListView items=\"${rows}\" var=\"r\">"
                + "<ListItem text=\"${r.name}\"/></ListView></Column>");
        // window of a loop
        assertError(2, "is not a valid int", "<Column>\n<For items=\"${rows}\" var=\"r\" from=\"x\"/></Column>");
        // groups
        assertError(2, "with group needs value=", "<Column>\n<RadioButton group=\"${choice}\"/></Column>");
        assertError(2, "value only means something with group", "<Column>\n<RadioButton value=\"a\"/></Column>");
        assertError(2, "reports a choice only with a group", "<Column>\n<RadioButton onChange=\"#setChoice\"/></Column>");
        assertError(2, "has both group and selected", "<Column>\n<ToggleButton group=\"${choice}\" value=\"a\" selected=\"true\"/></Column>");
        assertError(2, "has both onAction and a group choice",
                "<Column>\n<RadioButton group=\"${choice}\" value=\"a\" onChange=\"#setChoice\" onAction=\"#save\"/></Column>");
        assertError(2, "selected cannot be bound two ways", "<Column>\n<RadioButton selected=\"@{on}\"/></Column>");
        // styles, images
        assertError(2, "is not a valid color: expected #RGB", "<Column>\n<Label textFill=\"red\"/></Column>");
        assertError(2, "is not a valid color", "<Column>\n<Label background=\"#12\"/></Column>");
        assertError(2, "is not a valid color", "<Column>\n<Label background=\"#GGGGGG\"/></Column>");
        assertError(2, "must be a binding", "<Column>\n<Image pixels=\"1,2,3\"/></Column>");
        assertError(2, "takes no children", "<Column>\n<Tooltip><Label/></Tooltip></Column>");
        assertError(2, "takes one child", "<Column>\n<TitledPane><Label/><Label/></TitledPane></Column>");
        assertError(2, "takes one child", "<Column>\n<Pagination><Label/><Label/></Pagination></Column>");
        assertError(2, "cannot be bound", "<Column>\n<Flow prefWrapLength=\"${gap}\"/></Column>");
    }

    @Test
    void compositesReportTheirOwnMistakes() {
        assertError(2, "<Spacer> takes no children", "<Column>\n<Spacer><Label/></Spacer></Column>");
        assertError(2, "<Expanded> takes one child", "<Column>\n<Expanded/></Column>");
        assertError(2, "<Flexible> takes one child", "<Column>\n<Flexible><Label/><Label/></Flexible></Column>");
        assertError(3, "needs an element as its child, not <If>", "<Column>\n<Expanded>\n<If test=\"${busy}\"/></Expanded></Column>");
        assertError(3, "needs an element as its child, not <Slot>",
                "<Column>\n<Expanded>\n<Slot content=\"${banner}\"/></Expanded></Column>");
        assertError(2, "margin is also written on its child", "<Column>\n<Expanded margin=\"2\"><Label margin=\"3\"/></Expanded></Column>");
        assertError(2, "<SizedBox> takes one child", "<Column>\n<SizedBox><Label/><Label/></SizedBox></Column>");
        assertError(2, "is not a valid double", "<Column>\n<SizedBox width=\"wide\"/></Column>");
        assertError(2, "<ListTile> needs title", "<Column>\n<ListTile/></Column>");
        assertError(3, "need slot=\"leading\" or slot=\"trailing\"", "<Column>\n<ListTile title=\"t\">\n<Label/></ListTile></Column>");
        assertError(3, "need slot=\"leading\" or slot=\"trailing\"",
                "<Column>\n<ListTile title=\"t\">\n<Label slot=\"top\"/></ListTile></Column>");
        assertError(3, "the leading of a <ListTile> is one element, not <For>",
                "<Column>\n<ListTile title=\"t\">\n<For slot=\"leading\" items=\"${rows}\" var=\"r\"/></ListTile></Column>");
        assertError(3, "has two children with slot=\"leading\"",
                "<Column>\n<ListTile title=\"t\"><Label slot=\"leading\"/>\n<Label slot=\"leading\"/></ListTile></Column>");
        assertError(2, "<Scaffold> has two children in slot center; the body is one element",
                "<Column>\n<Scaffold><Label/><Label/></Scaffold></Column>");
        assertError(2, "<Scaffold> has two children in slot top",
                "<Column>\n<Scaffold><AppBar/><AppBar/></Scaffold></Column>");
        assertError(3, "a <Scaffold> child is an element, not <If>", "<Column>\n<Scaffold>\n<If test=\"${busy}\"/></Scaffold></Column>");
        assertError(2, "<Center> has no attribute gap", "<Column>\n<Center gap=\"3\"/></Column>");
        assertError(2, "has no attribute title", "<Column>\n<Card title=\"t\"/></Column>");
        // an expansion is checked as the tags it becomes
        assertError(2, "is not a valid insets", "<Column>\n<Padding padding=\"1 2 3\"/></Column>");
        assertError(2, "is not a valid color", "<Column>\n<Card background=\"white\"/></Column>");
    }

    @Test
    void aBoundValueThatCannotBeConvertedIsReportedWithItsLine() throws Exception {
        JxmlTemplate list = ViewCompiler.read("<Column controller=\"" + CTRL + "\">\n<ListView items=\"${title}\" var=\"r\">"
                + "<ListItem/></ListView></Column>", "t.jxml");
        JxmlException e = assertThrows(JxmlException.class, () -> list.build(new SampleController()));
        assertTrue(e.getMessage().startsWith("t.jxml:2: ") && e.getMessage().contains("expected an Iterable or an array"),
                e.getMessage());
        JxmlTemplate image = ViewCompiler.read("<Image controller=\"" + CTRL + "\" pixels=\"${title}\"/>", "t.jxml");
        e = assertThrows(JxmlException.class, () -> image.build(new SampleController()));
        assertTrue(e.getMessage().startsWith("t.jxml:1: ") && e.getMessage().contains("must be an int[]"), e.getMessage());
        JxmlTemplate color = ViewCompiler.read("<Label controller=\"" + CTRL + "\" background=\"${title}\"/>", "t.jxml");
        e = assertThrows(JxmlException.class, () -> color.build(new SampleController()));
        assertTrue(e.getMessage().startsWith("t.jxml:1: ") && e.getMessage().contains("expected #RGB"), e.getMessage());
        JxmlTemplate height = ViewCompiler.read("<Column controller=\"" + CTRL + "\">\n<ListView items=\"${rows}\" var=\"r\" "
                + "cellHeight=\"${alpha}\" first=\"${top}\"><ListItem/></ListView></Column>", "t.jxml");
        SampleController flat = new SampleController();
        flat.alpha = 0;
        e = assertThrows(JxmlException.class, () -> height.build(flat));
        assertTrue(e.getMessage().startsWith("t.jxml:2: ") && e.getMessage().contains("cellHeight must be greater than 0"),
                e.getMessage());
    }

    // ---- values -------------------------------------------------------------------------------------

    @Test
    void colorsAreArgbIntegers() {
        assertEquals(Integer.valueOf(0xFFFFEEDD), JxmlValues.color("#FFEEDD"));
        assertEquals(Integer.valueOf(0xFFFFEEDD), JxmlValues.color("#fed"));
        assertEquals(Integer.valueOf(0x80112233), JxmlValues.color("#80112233"));
        assertEquals(Integer.valueOf(0x00112233), JxmlValues.color(" #00112233 "));
        assertEquals(Integer.valueOf(0xFF336699), JxmlValues.asColor("#336699"));
        assertEquals(Integer.valueOf(7), JxmlValues.asColor(Double.valueOf(7)));
        assertNull(JxmlValues.asColor(null));
        assertArrayEquals(new double[] {60, -1, 80.5}, JxmlValues.doubles("60, -1, 80.5"), 0);
        assertEquals(0, JxmlValues.doubles(" ").length);
        assertEquals(Integer.valueOf(0), JxmlValues.asCount(null));
        assertEquals(Integer.valueOf(3), JxmlValues.asCount(new int[3]));
        assertEquals(Integer.valueOf(2), JxmlValues.asCount(Arrays.asList("a", "b")));
        int[] pixels = {1, 2};
        assertSame(pixels, JxmlValues.asPixels(pixels));
    }

    @Test
    void aWindowOfItemsStartsWhereAskedAndEndsWhereTheListDoes() {
        List<String> all = Arrays.asList("a", "b", "c", "d", "e");
        assertEquals(Arrays.asList("c", "d"), toList(JxmlRuntime.<String>items(all, Integer.valueOf(2), Integer.valueOf(2))));
        assertEquals(Arrays.asList("d", "e"), toList(JxmlRuntime.<String>items(all, Integer.valueOf(3), Integer.valueOf(9))));
        assertEquals(Arrays.asList("a", "b"), toList(JxmlRuntime.<String>items(all, Integer.valueOf(-4), Integer.valueOf(2))));
        assertEquals(Collections.emptyList(), toList(JxmlRuntime.<String>items(all, Integer.valueOf(9), Integer.valueOf(2))));
        assertEquals(Collections.emptyList(), toList(JxmlRuntime.<String>items(all, null, Integer.valueOf(0))));
        assertEquals(all, toList(JxmlRuntime.<String>items(all, null, null)));
        assertEquals(Arrays.asList("b", "c"), toList(JxmlRuntime.<String>items(new String[] {"a", "b", "c", "d"}, Integer.valueOf(1),
                Integer.valueOf(2))));
        assertEquals(Collections.emptyList(), toList(JxmlRuntime.<String>items(null, Integer.valueOf(1), Integer.valueOf(2))));
        assertEquals(Arrays.asList("c"), toList(JxmlRuntime.<String>items(new java.util.LinkedHashSet<String>(all), Integer.valueOf(2),
                Integer.valueOf(1))));
    }

    private static <T> List<T> toList(Iterable<T> items) {
        List<T> out = new ArrayList<T>();
        for (T item : items) {
            out.add(item);
        }
        return out;
    }

    // ---- ListView and TableView ---------------------------------------------------------------------

    private JXElement agree(String xml, Consumer<SampleController> setup) throws Exception {
        List<JxmlView<Object>> views = new ViewCompiler(dir).views(xml, "t.jxml");
        SampleController c = new SampleController();
        setup.accept(c);
        JXElement first = views.get(0).build(c);
        for (int i = 1; i < views.size(); i++) {
            assertNull(JxmlTrees.diff(first, views.get(i).build(c)), "view " + i);
        }
        return first;
    }

    private static String list(String attrs) {
        return "<Column controller=\"" + CTRL + "\">\n<ListView items=\"${rows}\" var=\"r\" type=\"" + ROW + "\" key=\"${r.id}\" "
                + attrs + ">\n<ListItem text=\"${r.name}\" selected=\"${r.selected}\" onAction=\"#select(r)\"/>\n</ListView>\n</Column>";
    }

    @Test
    void aListBuildsOnlyTheRowsInItsWindowWithTheKeysOfTheirItems() throws Exception {
        JXElement list = agree(list("first=\"${top}\" rows=\"5\" cellHeight=\"20\""), c -> c.top = 10).getChildren().get(0);
        assertEquals("list", list.getType());
        assertEquals(Integer.valueOf(40), list.getProps().get("itemCount"));
        assertEquals(Integer.valueOf(10), list.getProps().get("first"));
        assertEquals(Double.valueOf(200), list.getProps().get("scrollY")); // first row at the top of the viewport
        assertEquals(Double.valueOf(20), list.getProps().get("cellHeight"));
        assertEquals(5, list.getChildren().size());
        for (int i = 0; i < 5; i++) {
            JXElement cell = list.getChildren().get(i);
            assertEquals("cell", cell.getType());
            assertEquals("Item " + (10 + i), cell.getProps().get("value"));
            assertEquals(Integer.valueOf(10 + i), cell.getProps().get("key"));
            assertEquals(Boolean.FALSE, cell.getProps().get("selected"));
            assertTrue(cell.getProps().get("onAction") instanceof Runnable);
        }
    }

    @Test
    void aListClampsFirstAndDefaultsItsWindowAndCellHeight() throws Exception {
        JXElement past = agree(list("first=\"${top}\""), c -> c.top = 100).getChildren().get(0);
        assertEquals(Integer.valueOf(39), past.getProps().get("first"));
        assertEquals(Double.valueOf(39 * 24), past.getProps().get("scrollY"));
        assertEquals(0, past.getChildren().size());
        JXElement before = agree(list("first=\"${top}\""), c -> c.top = -5).getChildren().get(0);
        assertEquals(Integer.valueOf(0), before.getProps().get("first"));
        assertEquals(Double.valueOf(0), before.getProps().get("scrollY"));
        assertEquals(JxmlTemplate.DEFAULT_ROWS, before.getChildren().size());
        assertEquals(Double.valueOf(24), before.getProps().get("cellHeight"));
        JXElement none = agree(list("rows=\"0\""), c -> { }).getChildren().get(0);
        assertEquals(0, none.getChildren().size());
        assertEquals(Integer.valueOf(40), none.getProps().get("itemCount"));
        JXElement empty = agree(list(""), c -> c.rows.clear()).getChildren().get(0);
        assertEquals(Integer.valueOf(0), empty.getProps().get("itemCount"));
        assertEquals(Integer.valueOf(0), empty.getProps().get("first"));
    }

    @Test
    void aListRowRunsItsHandlerWithItsItemAndScrollingKeepsTheKeysOfTheRowsThatStay() throws Exception {
        List<JxmlView<Object>> views = new ViewCompiler(dir).views(list("first=\"${top}\" rows=\"5\""), "t.jxml");
        for (JxmlView<Object> view : views) {
            SampleController c = new SampleController();
            c.top = 4;
            JXElement list = view.build(c).getChildren().get(0);
            ((Runnable) list.getChildren().get(1).getProps().get("onAction")).run();
            assertEquals(Arrays.asList("select Item 5"), c.calls);
            c.top = 5;
            JXElement scrolled = view.build(c).getChildren().get(0);
            // the key is the item's id (an Integer the item holds): the same instance, so the renderer keeps the node
            assertSame(list.getChildren().get(1).getProps().get("key"), scrolled.getChildren().get(0).getProps().get("key"));
            assertSame(list.getChildren().get(4).getProps().get("key"), scrolled.getChildren().get(3).getProps().get("key"));
        }
    }

    @Test
    void aTableHasColumnsAndRowsOfCells() throws Exception {
        String xml = "<TableView controller=\"" + CTRL + "\" items=\"${rows}\" var=\"r\" type=\"" + ROW + "\" key=\"${r.id}\" "
                + "first=\"3\" rows=\"4\" rowHeight=\"30\" columns=\"Id, Name\" columnWidths=\"${widths}\" constrained=\"true\">\n"
                + "<TableRow selected=\"${r.selected}\"><TableCell text=\"${r.id}\"/><TableCell text=\"${r.name}\"/></TableRow>\n"
                + "</TableView>";
        JXElement table = agree(xml, c -> { });
        assertEquals("table", table.getType());
        assertArrayEquals(new String[] {"Id", "Name"}, (String[]) table.getProps().get("columns"));
        assertArrayEquals(new double[] {60, -1, 80}, (double[]) table.getProps().get("columnWidths"), 0);
        assertEquals(Boolean.TRUE, table.getProps().get("constrained"));
        assertEquals(Integer.valueOf(40), table.getProps().get("itemCount"));
        assertEquals(Integer.valueOf(3), table.getProps().get("first"));
        assertEquals(Double.valueOf(90), table.getProps().get("scrollY"));
        assertEquals(Double.valueOf(30), table.getProps().get("rowHeight"));
        assertEquals(4, table.getChildren().size());
        JXElement row = table.getChildren().get(0);
        assertEquals("tablerow", row.getType());
        assertEquals(Integer.valueOf(3), row.getProps().get("key"));
        assertEquals(Boolean.TRUE, row.getProps().get("selected")); // row 3 is the selected one
        assertEquals(2, row.getChildren().size());
        JXElement cell = row.getChildren().get(1);
        assertEquals("cell", cell.getType());
        assertEquals("Item 3", cell.getProps().get("value"));
        // a table cell does not paint the list-cell background; the row does
        assertEquals(Boolean.TRUE, cell.getProps().get("tableCell"));
        assertEquals(Boolean.FALSE, cell.getProps().get("paintBackground"));
        assertArrayEquals(new double[] {2, 3, 2, 3}, (double[]) cell.getProps().get("padding"), 0);
        JXElement listCell = agree(list(""), c -> { }).getChildren().get(0).getChildren().get(0);
        assertNull(listCell.getProps().get("tableCell"));
        assertNull(listCell.getProps().get("paintBackground"));
    }

    @Test
    void aLoopWindowIsPartOfForItself() throws Exception {
        String xml = "<Column controller=\"" + CTRL + "\">\n<For items=\"${rows}\" var=\"r\" type=\"" + ROW
                + "\" from=\"${top}\" limit=\"2\" key=\"${r.id}\"><Label text=\"${r.name}\"/></For>\n</Column>";
        JXElement column = agree(xml, c -> c.top = 38);
        assertEquals(2, column.getChildren().size());
        assertEquals("Item 38", column.getChildren().get(0).getProps().get("value"));
        assertEquals("Item 39", column.getChildren().get(1).getProps().get("value"));
    }

    // ---- RadioButton and ToggleButton ---------------------------------------------------------------

    @Test
    void buttonsInAGroupAreSelectedByTheGroupAndChooseTheirValue() throws Exception {
        String xml = "<Column controller=\"" + CTRL + "\">\n<RadioButton text=\"A\" group=\"@{choice}\" value=\"a\"/>\n"
                + "<RadioButton text=\"B\" group=\"${choice}\" value=\"b\" onChange=\"#setChoice\"/>\n"
                + "<ToggleButton text=\"A\" group=\"@{choice}\" value=\"a\"/>\n<ToggleButton text=\"B\" group=\"${choice}\" value=\"b\"/>\n"
                + "<RadioButton text=\"alone\" selected=\"true\"/>\n</Column>";
        List<JxmlView<Object>> views = new ViewCompiler(dir).views(xml, "t.jxml");
        for (JxmlView<Object> view : views) {
            SampleController c = new SampleController(); // the group holds "b"
            JXElement column = view.build(c);
            List<JXElement> kids = column.getChildren();
            assertEquals("radio", kids.get(0).getType());
            assertEquals(Boolean.FALSE, kids.get(0).getProps().get("checked"));
            assertEquals(Boolean.TRUE, kids.get(1).getProps().get("checked"));
            assertEquals("toggle", kids.get(2).getType());
            assertEquals(Boolean.FALSE, kids.get(2).getProps().get("selected"));
            assertEquals(Boolean.TRUE, kids.get(3).getProps().get("selected"));
            assertEquals(Boolean.TRUE, kids.get(4).getProps().get("checked"));
            // what the group is made of does not reach the renderer
            for (String prop : new String[] {"group", "value", "onChange"}) {
                assertFalse(kids.get(0).getProps().asMap().containsKey(prop), prop);
            }
            ((Runnable) kids.get(0).getProps().get("onAction")).run(); // a click on A
            assertEquals("a", c.choice);
            JXElement again = view.build(c);
            assertEquals(Boolean.TRUE, again.getChildren().get(0).getProps().get("checked"));
            assertEquals(Boolean.FALSE, again.getChildren().get(1).getProps().get("checked"));
            assertEquals(Boolean.TRUE, again.getChildren().get(2).getProps().get("selected"));
            assertNull(kids.get(3).getProps().get("onAction")); // a group without a handler only shows the choice
            assertEquals("a", c.choice);
        }
    }

    // ---- composites ---------------------------------------------------------------------------------

    private static String shape(JXElement e) {
        StringBuilder s = new StringBuilder(e.getType());
        if (!e.getChildren().isEmpty()) {
            s.append('(');
            for (int i = 0; i < e.getChildren().size(); i++) {
                s.append(i == 0 ? "" : ",").append(shape(e.getChildren().get(i)));
            }
            s.append(')');
        }
        return s.toString();
    }

    private JXElement screen(String body) throws Exception {
        return agree("<Column controller=\"" + CTRL + "\">\n" + body + "\n</Column>", c -> { }).getChildren().get(0);
    }

    @Test
    void aliasesBecomeTheirPrimitiveWithTheirDefaults() throws Exception {
        JXElement center = screen("<Center><Label text=\"c\"/></Center>");
        assertEquals("stack(#text)", shape(center));
        assertEquals("CENTER", center.getProps().get("alignment"));
        assertEquals("always", center.getProps().get("hgrow"));
        assertEquals("always", center.getProps().get("vgrow"));
        JXElement align = screen("<Align alignment=\"BOTTOM_RIGHT\" hgrow=\"never\"><Label/></Align>");
        assertEquals("BOTTOM_RIGHT", align.getProps().get("alignment"));
        assertEquals("never", align.getProps().get("hgrow"));
        assertEquals("stack", screen("<Padding padding=\"8\"/>").getType());
        assertEquals(Double.valueOf(8), screen("<Padding padding=\"8\"/>").getProps().get("padding"));
        assertEquals("flow", screen("<Wrap hgap=\"3\"/>").getType());
        assertEquals("select", screen("<ChoiceBox items=\"A, B\"/>").getType());
        assertArrayEquals(new String[] {"A", "B"}, (String[]) screen("<ChoiceBox items=\"A, B\"/>").getProps().get("options"));
        assertEquals("separator", screen("<Divider/>").getType());
        assertEquals("vertical", screen("<Divider orientation=\"vertical\"/>").getProps().get("orientation"));
        JXElement card = screen("<Card><Label/><Label/></Card>");
        assertEquals("column(#text,#text)", shape(card));
        assertEquals(Integer.valueOf(0xFFFFFFFF), card.getProps().get("background"));
        assertEquals(Integer.valueOf(0xFFD9D9D9), card.getProps().get("borderColor"));
        assertEquals(Double.valueOf(6), card.getProps().get("borderRadius"));
        assertEquals(Double.valueOf(12), card.getProps().get("padding"));
        assertEquals(Boolean.TRUE, card.getProps().get("fillWidth"));
        assertEquals(Integer.valueOf(0xFF000000 | 0xAABBCC), screen("<Card background=\"#AABBCC\"/>").getProps().get("background"));
        assertEquals(Double.valueOf(0.5), screen("<Opacity opacity=\"0.5\"><Label/></Opacity>").getProps().get("opacity"));
        assertEquals(Boolean.TRUE, screen("<Visibility hidden=\"true\"><Label/></Visibility>").getProps().get("hidden"));
        JXElement positioned = screen("<Anchor><Positioned top=\"3\" left=\"4\"><Label/></Positioned></Anchor>").getChildren().get(0);
        assertEquals(Double.valueOf(3), positioned.getProps().get("topAnchor"));
        assertEquals(Double.valueOf(4), positioned.getProps().get("leftAnchor"));
    }

    @Test
    void growingCompositesSetThePriorityOfTheirChild() throws Exception {
        JXElement row = screen("<Row><Label/><Spacer/><Expanded><Button text=\"x\"/></Expanded>"
                + "<Flexible vgrow=\"never\"><Button text=\"y\"/></Flexible></Row>");
        assertEquals("row(#text,pane,button,button)", shape(row));
        assertEquals("always", row.getChildren().get(1).getProps().get("hgrow"));
        assertEquals("always", row.getChildren().get(1).getProps().get("vgrow"));
        JXElement expanded = row.getChildren().get(2);
        assertEquals("always", expanded.getProps().get("hgrow"));
        assertEquals("always", expanded.getProps().get("vgrow"));
        assertEquals("x", expanded.getProps().get("label"));
        JXElement flexible = row.getChildren().get(3);
        assertEquals("sometimes", flexible.getProps().get("hgrow"));
        assertEquals("never", flexible.getProps().get("vgrow"));
        // nested composites expand all the way down
        JXElement nested = screen("<Expanded><Card><Label/></Card></Expanded>");
        assertEquals("column(#text)", shape(nested));
        assertEquals("always", nested.getProps().get("hgrow"));
    }

    @Test
    void aSizedBoxIsExactlyThatSize() throws Exception {
        JXElement box = screen("<SizedBox width=\"${gap}\" height=\"20\"><Label/></SizedBox>");
        assertEquals("stack(#text)", shape(box));
        for (String prop : new String[] {"minWidth", "prefWidth", "maxWidth"}) {
            assertEquals(Double.valueOf(6), box.getProps().get(prop), prop);
        }
        for (String prop : new String[] {"minHeight", "prefHeight", "maxHeight"}) {
            assertEquals(Double.valueOf(20), box.getProps().get(prop), prop);
        }
        JXElement widthOnly = screen("<SizedBox width=\"30\"/>");
        assertNull(widthOnly.getProps().get("prefHeight"));
    }

    @Test
    void aListTileIsARowOfLeadingTextsAndTrailing() throws Exception {
        List<JxmlView<Object>> views = new ViewCompiler(dir).views("<Column controller=\"" + CTRL + "\">\n"
                + "<ListTile title=\"${title}\" subtitle=\"S\" onAction=\"#save\"><Label slot=\"leading\" text=\"L\"/>"
                + "<Button slot=\"trailing\" text=\"B\"/></ListTile>\n<ListTile title=\"plain\" gap=\"3\"/>\n</Column>", "t.jxml");
        for (JxmlView<Object> view : views) {
            SampleController c = new SampleController();
            JXElement tile = view.build(c).getChildren().get(0);
            assertEquals("row(#text,column(#text,#text),button)", shape(tile));
            assertEquals(Double.valueOf(12), tile.getProps().get("gap"));
            assertArrayEquals(new double[] {8, 12, 8, 12}, (double[]) tile.getProps().get("padding"), 0);
            assertEquals("CENTER_LEFT", tile.getProps().get("alignment"));
            assertNull(tile.getChildren().get(0).getProps().get("position")); // the slot only chose the place
            JXElement texts = tile.getChildren().get(1);
            assertEquals("always", texts.getProps().get("hgrow"));
            assertEquals(Boolean.TRUE, texts.getProps().get("mouseTransparent"));
            assertEquals("Sample", texts.getChildren().get(0).getProps().get("value"));
            assertEquals("S", texts.getChildren().get(1).getProps().get("value"));
            assertEquals(Double.valueOf(11), texts.getChildren().get(1).getProps().get("fontSize"));
            assertEquals(Boolean.TRUE, texts.getChildren().get(0).getProps().get("mouseTransparent"));
            ((Runnable) tile.getProps().get("onAction")).run();
            assertEquals(Arrays.asList("save"), c.calls);
            JXElement plain = view.build(c).getChildren().get(1);
            assertEquals("row(column(#text))", shape(plain));
            assertEquals(Double.valueOf(3), plain.getProps().get("gap"));
        }
    }

    @Test
    void aScaffoldPlacesItsAppBarBodyAndBottomBar() throws Exception {
        String xml = "<Scaffold controller=\"" + CTRL + "\" padding=\"2\">\n<BottomAppBar><Label text=\"s\"/></BottomAppBar>\n"
                + "<AppBar title=\"${title}\"><Button text=\"m\"/></AppBar>\n<Column><Label/></Column>\n</Scaffold>";
        JXElement border = agree(xml, c -> { });
        assertEquals("border(row(#text),row(#text,button),column(#text))", shape(border));
        assertEquals(Double.valueOf(2), border.getProps().get("padding"));
        assertEquals("bottom", border.getChildren().get(0).getProps().get("position"));
        assertEquals("top", border.getChildren().get(1).getProps().get("position"));
        assertEquals("center", border.getChildren().get(2).getProps().get("position"));
        JXElement title = border.getChildren().get(1).getChildren().get(0);
        assertEquals("Sample", title.getProps().get("value"));
        assertEquals(Boolean.TRUE, title.getProps().get("bold"));
        assertEquals("always", title.getProps().get("hgrow"));
        assertEquals(Integer.valueOf(0xFFEDEDED), border.getChildren().get(1).getProps().get("background"));
        // a child that writes its slot keeps it
        JXElement left = agree("<Scaffold controller=\"" + CTRL + "\"><Column slot=\"left\"/><Row/></Scaffold>", c -> { });
        assertEquals("left", left.getChildren().get(0).getProps().get("position"));
        assertEquals("center", left.getChildren().get(1).getProps().get("position"));
    }

    @Test
    void aNavigationBarSharesItsWidthAndABarIsARow() throws Exception {
        JXElement nav = screen("<NavigationBar><Button text=\"a\"/><Button text=\"b\" hgrow=\"never\"/><Label/></NavigationBar>");
        assertEquals("row(button,button,#text)", shape(nav));
        assertEquals("always", nav.getChildren().get(0).getProps().get("hgrow"));
        assertEquals("never", nav.getChildren().get(1).getProps().get("hgrow"));
        assertEquals("CENTER", nav.getProps().get("alignment"));
        assertEquals("row", screen("<ToolBar/>").getType());
        assertEquals("row", screen("<BottomBar/>").getType());
        JXElement rail = screen("<NavigationRail><Button/></NavigationRail>");
        assertEquals("column(button)", shape(rail));
        assertEquals("TOP_CENTER", rail.getProps().get("alignment"));
    }

    // ---- Column, and the new containers -------------------------------------------------------------

    @Test
    void aColumnHasGapPaddingAlignmentFillWidthAndGrowingChildren() throws Exception {
        JXElement column = agree("<Column controller=\"" + CTRL + "\" gap=\"${gap}\" padding=\"4 8\" alignment=\"TOP_CENTER\" "
                + "fillWidth=\"false\">\n<Label text=\"a\" vgrow=\"always\" hgrow=\"sometimes\"/>\n</Column>", c -> { });
        assertEquals("column", column.getType());
        assertEquals(Double.valueOf(6), column.getProps().get("gap"));
        assertArrayEquals(new double[] {4, 8, 4, 8}, (double[]) column.getProps().get("padding"), 0);
        assertEquals("TOP_CENTER", column.getProps().get("alignment"));
        assertEquals(Boolean.FALSE, column.getProps().get("fillWidth"));
        assertEquals("always", column.getChildren().get(0).getProps().get("vgrow"));
        assertEquals("sometimes", column.getChildren().get(0).getProps().get("hgrow"));
    }

    @Test
    void anchoredAndPositionedChildrenCarryTheirOffsets() throws Exception {
        JXElement anchor = screen("<Anchor><Label top=\"1\" right=\"2\" bottom=\"${gap}\" left=\"4\"/><Label x=\"5\" y=\"6\"/></Anchor>");
        JXElement label = anchor.getChildren().get(0);
        assertEquals(Double.valueOf(1), label.getProps().get("topAnchor"));
        assertEquals(Double.valueOf(2), label.getProps().get("rightAnchor"));
        assertEquals(Double.valueOf(6), label.getProps().get("bottomAnchor"));
        assertEquals(Double.valueOf(4), label.getProps().get("leftAnchor"));
        assertEquals(Double.valueOf(5), anchor.getChildren().get(1).getProps().get("layoutX"));
        assertEquals(Double.valueOf(6), anchor.getChildren().get(1).getProps().get("layoutY"));
    }

    @Test
    void anImageHandsOnTheSamePixelArray() throws Exception {
        List<JxmlView<Object>> views = new ViewCompiler(dir).views("<Image controller=\"" + CTRL
                + "\" pixels=\"${pixels}\" imageWidth=\"2\" imageHeight=\"2\"/>", "t.jxml");
        SampleController c = new SampleController();
        for (JxmlView<Object> view : views) {
            // the renderer tells a changed image by the identity of the array: building again must not copy it
            assertSame(c.pixels, view.build(c).getProps().get("pixels"));
        }
    }

    @Test
    void stylesAndVisibilityAttributesAreOnEveryTag() throws Exception {
        JXElement label = screen("<Label background=\"#112233\" backgroundRadius=\"3\" borderColor=\"${color}\" borderWidth=\"2\" "
                + "borderRadius=\"4\" opacity=\"0.25\" hidden=\"true\" mouseTransparent=\"true\"/>");
        assertEquals(Integer.valueOf(0xFF112233), label.getProps().get("background"));
        assertEquals(Integer.valueOf(0xFF336699), label.getProps().get("borderColor"));
        assertEquals(Double.valueOf(3), label.getProps().get("backgroundRadius"));
        assertEquals(Double.valueOf(2), label.getProps().get("borderWidth"));
        assertEquals(Double.valueOf(4), label.getProps().get("borderRadius"));
        assertEquals(Double.valueOf(0.25), label.getProps().get("opacity"));
        assertEquals(Boolean.TRUE, label.getProps().get("hidden"));
        assertEquals(Boolean.TRUE, label.getProps().get("mouseTransparent"));
    }
}
