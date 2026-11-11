package com.jxparallel.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ListResourceBundle;
import java.util.ResourceBundle;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jxparallel.ui.JXElement;

/** FXML in, JXML out: the element tree, the report, the XSD, and the interpreter and compiler agreeing on the result. */
class FxmlToJxmlTest {
    private static Schema schema;

    @TempDir
    Path dir;

    @BeforeAll
    static void schema() throws Exception {
        schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new StreamSource(new ByteArrayInputStream(JxmlXsd.generate().getBytes(StandardCharsets.UTF_8))));
    }

    private static InputStream open(String name) {
        InputStream in = FxmlToJxmlTest.class.getResourceAsStream("/fxml/" + name);
        assertNotNull(in, name);
        return in;
    }

    private static FxmlToJxml.Result convert(String name, FxmlToJxml.Options options) throws IOException {
        InputStream in = open(name);
        try {
            return FxmlToJxml.convert(in, name, options);
        } finally {
            in.close();
        }
    }

    private static FxmlToJxml.Result convert(String name) throws IOException {
        return convert(name, new FxmlToJxml.Options());
    }

    private static JxmlParser.Node find(JxmlParser.Node node, String tag) {
        if (node.tag.equals(tag)) {
            return node;
        }
        for (JxmlParser.Node child : node.children) {
            JxmlParser.Node found = find(child, tag);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static void collect(JxmlParser.Node node, String tag, List<JxmlParser.Node> into) {
        if (node.tag.equals(tag)) {
            into.add(node);
        }
        for (JxmlParser.Node child : node.children) {
            collect(child, tag, into);
        }
    }

    private static List<JxmlParser.Node> all(JxmlParser.Node node, String tag) {
        List<JxmlParser.Node> out = new ArrayList<JxmlParser.Node>();
        collect(node, tag, out);
        return out;
    }

    private static String xsdError(String jxml) {
        try {
            schema.newValidator().validate(new StreamSource(new ByteArrayInputStream(jxml.getBytes(StandardCharsets.UTF_8))));
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private static boolean has(FxmlReport report, FxmlReport.Level level, String element, String attribute, String reasonPart) {
        for (FxmlReport.Entry e : report.entries(level)) {
            if ((element == null || e.element.equals(element)) && (attribute == null || attribute.equals(e.attribute))
                    && e.reason.contains(reasonPart)) {
                return true;
            }
        }
        return false;
    }

    // ---- the form screen -------------------------------------------------------------------------

    @Test
    void theFormBecomesTheJxmlTree() throws Exception {
        FxmlToJxml.Result result = convert("customer-form.fxml");
        JxmlParser.Node root = result.node();
        assertEquals("Border", root.tag);
        assertEquals("com.jxparallel.forge.FxmlFormController", result.controller());
        assertEquals("600.0", root.attrs.get("prefWidth"));
        assertEquals("600.0", root.attrs.get("minWidth")); // -Infinity: fixed to the preferred size
        assertFalse(root.attrs.containsKey("xmlns"));

        assertEquals("MenuBar", find(root, "MenuBar").tag);
        assertEquals("top", find(root, "MenuBar").attrs.get("slot"));
        assertEquals(1, all(root, "MenuSeparator").size());
        assertEquals("#save", all(root, "MenuItem").get(0).attrs.get("onAction"));

        JxmlParser.Node column = find(root, "Column");
        assertEquals("center", column.attrs.get("slot"));
        assertEquals("10.0", column.attrs.get("gap"));
        assertEquals("12.0", column.attrs.get("padding"));
        JxmlParser.Node title = find(root, "Label");
        assertEquals("Customer", title.attrs.get("text"));
        assertEquals("18.0", title.attrs.get("fontSize"));
        assertEquals("true", title.attrs.get("bold"));

        JxmlParser.Node grid = find(root, "Grid");
        assertEquals("10.0", grid.attrs.get("hgap"));
        JxmlParser.Node name = find(root, "TextField");
        assertEquals("${name}", name.attrs.get("text"));
        assertEquals("Full name", name.attrs.get("prompt"));
        assertEquals("1", name.attrs.get("column"));
        assertEquals("nameField", name.attrs.get("key"));
        assertEquals("always", name.attrs.get("hgrow")); // from the column constraint
        JxmlParser.Node combo = find(root, "ComboBox");
        assertEquals("Fortaleza, Recife", combo.attrs.get("items"));
        assertEquals("${city}", combo.attrs.get("value"));
        assertEquals("1", combo.attrs.get("row"));
        JxmlParser.Node spinner = find(root, "Spinner");
        assertEquals("10", spinner.attrs.get("value"));
        assertEquals("100", spinner.attrs.get("max"));
        assertEquals("${active}", find(root, "CheckBox").attrs.get("selected"));

        JxmlParser.Node row = find(root, "Row"); // the HBox of the radio buttons (the ToolBar is a Row alias with its own tag)
        assertEquals("CENTER_LEFT", row.attrs.get("alignment"));
        assertEquals("4.0 0 0 0", row.attrs.get("margin"));
        List<JxmlParser.Node> radios = all(root, "RadioButton");
        assertEquals("${kind}", radios.get(0).attrs.get("group"));
        assertEquals("person", radios.get(0).attrs.get("value"));
        assertEquals("company", radios.get(1).attrs.get("value"));
        JxmlParser.Node condition = find(root, "If");
        assertEquals("${canReset}", condition.attrs.get("test"));
        assertEquals("Hyperlink", condition.children.get(0).tag);
        assertEquals("#reset", condition.children.get(0).attrs.get("onAction"));
        JxmlParser.Node notes = find(root, "TextArea");
        assertEquals("true", notes.attrs.get("wrapText"));
        assertEquals("always", notes.attrs.get("vgrow"));

        JxmlParser.Node toolBar = find(root, "ToolBar");
        assertEquals("bottom", toolBar.attrs.get("slot"));
        List<JxmlParser.Node> buttons = all(toolBar, "Button");
        assertEquals("true", buttons.get(0).attrs.get("defaultButton"));
        assertEquals("${readOnly}", buttons.get(1).attrs.get("disabled"));
        JxmlParser.Node status = all(toolBar, "Label").get(0);
        assertEquals("#888888", status.attrs.get("textFill"));
        assertEquals("11", status.attrs.get("fontSize"));
        assertEquals("muted", status.attrs.get("style"));
        assertEquals("Style", root.children.get(0).tag);
        assertEquals("muted", root.children.get(0).attrs.get("name"));
    }

    @Test
    void theFormReportListsEveryFxIdAndWhatWasApproximated() throws Exception {
        FxmlReport report = convert("customer-form.fxml").report();
        List<String> ids = new ArrayList<String>();
        for (FxmlReport.Entry e : report.entries(FxmlReport.Level.NOTE)) {
            if ("fx:id".equals(e.attribute) && e.reason.startsWith("fx:id=")) {
                ids.add(e.reason.substring(7, e.reason.indexOf('"', 7)));
                assertTrue(e.reason.contains("controller field"), e.reason);
                assertEquals("customer-form.fxml", e.file);
                assertTrue(e.line > 0);
            }
        }
        assertEquals(java.util.Arrays.asList("nameField", "city", "limit", "active", "notes", "status"), ids);
        assertTrue(has(report, FxmlReport.Level.APPROXIMATE, "ColumnConstraints", null, "no column/row definitions"));
        assertTrue(has(report, FxmlReport.Level.APPROXIMATE, "Label", "styleClass", "muted"));
        assertTrue(has(report, FxmlReport.Level.APPROXIMATE, "Hyperlink", "visible", "<If"));
        assertTrue(has(report, FxmlReport.Level.NOTE, "MenuItem", "onAction", "no arguments"));
        assertTrue(report.hasLosses());
        assertTrue(report.entries(FxmlReport.Level.UNSUPPORTED).isEmpty(), report.toText());
    }

    // ---- the table screen ------------------------------------------------------------------------

    @Test
    void theTableBecomesAVirtualTableWithTheColumnsOfThePropertyValueFactories() throws Exception {
        FxmlToJxml.Result result = convert("orders-table.fxml");
        JxmlParser.Node table = find(result.node(), "TableView");
        assertEquals("${orders}", table.attrs.get("items"));
        assertEquals("row", table.attrs.get("var"));
        assertEquals("com.jxparallel.forge.FxmlOrder", table.attrs.get("type"));
        assertEquals("Id, Customer, Total", table.attrs.get("columns"));
        assertEquals("60.0, 220.0, 80.0", table.attrs.get("columnWidths"));
        assertEquals("true", table.attrs.get("constrained"));
        assertEquals("always", table.attrs.get("vgrow"));
        JxmlParser.Node row = table.children.get(0);
        assertEquals("TableRow", row.tag);
        assertEquals("${row.id}", row.children.get(0).attrs.get("text"));
        assertEquals("${row.total}", row.children.get(2).attrs.get("text"));
        assertTrue(result.report().entries(FxmlReport.Level.UNSUPPORTED).isEmpty(), result.report().toText());
    }

    @Test
    void aTableWithoutARowTypeIsReportedUnlessTheOptionsGiveOne() throws Exception {
        String fxml = "<VBox xmlns:fx='http://javafx.com/fxml/1' fx:controller='c.C'><TableView fx:id='t'><columns>"
                + "<TableColumn text='A' prefWidth='50'><cellValueFactory><PropertyValueFactory property='a'/></cellValueFactory>"
                + "</TableColumn></columns></TableView></VBox>";
        FxmlToJxml.Result bare = FxmlToJxml.convert(stream(fxml), "t.fxml", new FxmlToJxml.Options());
        assertTrue(has(bare.report(), FxmlReport.Level.UNSUPPORTED, "TableView", "fx:typeArguments", "row type"));
        assertNull(find(bare.node(), "TableView").attrs.get("type"));
        FxmlToJxml.Result typed = FxmlToJxml.convert(stream(fxml), "t.fxml", new FxmlToJxml.Options().itemType("t", "a.B"));
        assertEquals("a.B", find(typed.node(), "TableView").attrs.get("type"));
        assertFalse(has(typed.report(), FxmlReport.Level.UNSUPPORTED, "TableView", null, "row type"));
    }

    // ---- other panes and controls ----------------------------------------------------------------

    @Test
    void panesAndControlsMapToTheirTags() throws Exception {
        FxmlToJxml.Result result = convert("panes.fxml");
        JxmlParser.Node root = result.node();
        assertEquals("Column", root.tag);
        JxmlParser.Node split = find(root, "SplitPane");
        assertEquals("horizontal", split.attrs.get("orientation"));
        assertEquals("0.3", split.attrs.get("dividerPositions"));
        assertEquals(2, split.children.size());
        JxmlParser.Node anchor = split.children.get(0);
        assertEquals("Anchor", anchor.tag);
        JxmlParser.Node anchored = anchor.children.get(0);
        assertEquals("10.0", anchored.attrs.get("right"));
        assertEquals("10.0", anchored.attrs.get("top"));
        assertFalse(anchored.attrs.containsKey("left")); // the layoutX/Y of an anchored node do not count
        JxmlParser.Node image = find(root, "Image");
        assertEquals("logo.png", image.attrs.get("src"));
        assertEquals("true", image.attrs.get("preserveRatio"));
        assertEquals("32.0", image.attrs.get("fitWidth"));
        JxmlParser.Node over = all(root, "Label").get(0);
        assertEquals("LEFT", over.attrs.get("halignment"));
        assertEquals("TOP", over.attrs.get("valignment"));
        JxmlParser.Node tabs = find(root, "Tabs");
        assertEquals(2, tabs.children.size());
        assertEquals("First", tabs.children.get(0).attrs.get("title"));
        assertEquals("false", tabs.children.get(0).attrs.get("closable"));
        assertEquals("Flow", tabs.children.get(0).children.get(0).tag);
        assertEquals("vertical", find(root, "Separator").attrs.get("orientation"));
        assertEquals("0.5", find(root, "ProgressBar").attrs.get("progress"));
        assertEquals("3.0", find(root, "Slider").attrs.get("value"));
        assertEquals("Scroll", tabs.children.get(1).children.get(0).tag);
        assertEquals("true", find(root, "Scroll").attrs.get("fitToWidth"));
        assertEquals("2", find(root, "Tile").attrs.get("prefColumns"));
        assertNotNull(find(root, "PasswordField"));
        assertNotNull(find(root, "DatePicker"));
        assertEquals("true", find(root, "TitledPane").attrs.get("expanded"));
        assertNotNull(find(root, "Accordion"));
        JxmlParser.Node tree = find(root, "Tree");
        assertEquals(2, tree.children.size()); // showRoot=false: the children of the root
        assertEquals("alpha", tree.children.get(0).attrs.get("text"));
        assertNotNull(find(root, "ButtonBar"));
        assertNotNull(find(root, "ChoiceBox"));
        assertNotNull(find(root, "ProgressIndicator"));
        assertEquals("Pane", all(root, "Pane").get(0).tag); // the Region spacer
        assertEquals("always", all(root, "Pane").get(0).attrs.get("vgrow"));
        assertEquals("panes-part.jxml", find(root, "Include").attrs.get("src"));
        assertTrue(has(result.report(), FxmlReport.Level.UNSUPPORTED, "Button", "ButtonBar.buttonData", "no JXML equivalent"));
    }

    // ---- what cannot be converted ----------------------------------------------------------------

    @Test
    void whatCannotBeConvertedIsReportedWithFileLineElementAndAttribute() throws Exception {
        FxmlReport report = convert("unsupported.fxml").report();
        assertTrue(has(report, FxmlReport.Level.UNSUPPORTED, "Label", "text", "resource key %screen.title"));
        assertTrue(has(report, FxmlReport.Level.UNSUPPORTED, "Label", "text", "fx:id=\"nameField\"") == false
                && has(report, FxmlReport.Level.UNSUPPORTED, "Label", "text", "no node objects"));
        assertTrue(has(report, FxmlReport.Level.UNSUPPORTED, "Label", "text", "not a plain property path"));
        assertTrue(has(report, FxmlReport.Level.UNSUPPORTED, "Button", "onKeyPressed", "no JXML equivalent"));
        assertTrue(has(report, FxmlReport.Level.UNSUPPORTED, "Button", "managed", "managed"));
        assertTrue(has(report, FxmlReport.Level.UNSUPPORTED, "TextField", "editable", "no JXML equivalent"));
        assertTrue(has(report, FxmlReport.Level.UNSUPPORTED, "TextField", "onAction", "fires on Enter"));
        assertTrue(has(report, FxmlReport.Level.UNSUPPORTED, "Gauge", null, "custom class"));
        assertTrue(has(report, FxmlReport.Level.UNSUPPORTED, "Canvas", null, "no JXML tag"));
        assertTrue(has(report, FxmlReport.Level.UNSUPPORTED, "Button", "style: -fx-effect", "no JXML attribute"));
        for (FxmlReport.Entry e : report.entries()) {
            assertEquals("unsupported.fxml", e.file);
            assertTrue(e.line >= 6, e.toString());
        }
        FxmlReport.Entry canvas = null;
        for (FxmlReport.Entry e : report.entries()) {
            if (e.element.equals("Canvas")) {
                canvas = e;
            }
        }
        assertEquals(13, canvas.line);
        // what could be converted still was
        FxmlToJxml.Result result = convert("unsupported.fxml");
        assertEquals("#FF0000", all(result.node(), "Button").get(1).attrs.get("background"));
        assertEquals("%screen.title", all(result.node(), "Label").get(0).attrs.get("text"));
    }

    @Test
    void strictModeTurnsTheLossesIntoAFailureButNotTheNotes() throws Exception {
        FxmlConversionException e = assertThrows(FxmlConversionException.class,
                () -> convert("unsupported.fxml", new FxmlToJxml.Options().strict(true)));
        assertTrue(e.getMessage().contains("unsupported.fxml:13"), e.getMessage());
        assertFalse(e.getReport().entries().isEmpty());
        // fx:id notes alone do not fail
        FxmlToJxml.Result ok = convert("orders-table.fxml", new FxmlToJxml.Options().strict(true));
        assertFalse(ok.report().entries(FxmlReport.Level.NOTE).isEmpty());
        assertFalse(ok.report().hasLosses());
    }

    @Test
    void resourceKeysResolveThroughTheBundle() throws Exception {
        ResourceBundle bundle = new ListResourceBundle() {
            @Override
            protected Object[][] getContents() {
                return new Object[][] {{"screen.title", "Hello"}};
            }
        };
        FxmlToJxml.Result result = convert("unsupported.fxml", new FxmlToJxml.Options().bundle(bundle));
        assertEquals("Hello", all(result.node(), "Label").get(0).attrs.get("text"));
        assertFalse(has(result.report(), FxmlReport.Level.UNSUPPORTED, "Label", "text", "resource key"));
    }

    @Test
    void escapesAndLiteralsThatLookLikeBindingsStayLiteral() throws Exception {
        String fxml = "<VBox xmlns:fx='http://javafx.com/fxml/1'><Label text='\\%100'/><Label text='#1'/><Label text='\\\\x'/></VBox>";
        JxmlParser.Node root = FxmlToJxml.convert(stream(fxml), "e.fxml", new FxmlToJxml.Options()).node();
        assertEquals("%100", root.children.get(0).attrs.get("text"));
        assertEquals("\\#1", root.children.get(1).attrs.get("text")); // JXML: \# is the literal #1
        JxmlTemplate template = JxmlTemplate.read(stream(FxmlToJxml.convert(stream(fxml), "e.fxml", new FxmlToJxml.Options()).jxml()), "e.jxml");
        JXElement built = template.build(null);
        assertEquals("%100", built.getChildren().get(0).getProps().get("value"));
        assertEquals("#1", built.getChildren().get(1).getProps().get("value"));
    }

    // ---- fixtures from the other modules ---------------------------------------------------------

    @Test
    void sceneBuilderFixturesOfTheOtherModulesConvert() throws Exception {
        for (String name : new String[] {"screen01.fxml", "Handlers.fxml", "Inner.fxml", "InnerWithId.fxml", "Outer.fxml"}) {
            FxmlToJxml.Result result = convert(name);
            assertEquals(null, xsdError(result.jxml()), name + "\n" + result.jxml());
        }
        FxmlToJxml.Result bench = convert("screen01.fxml");
        assertEquals(3, all(bench.node(), "Tab").size());
        assertEquals(49, all(bench.node(), "Label").size());
    }

    @Test
    void smallerFeaturesMapAsDocumented() throws Exception {
        String fxml = "<VBox xmlns:fx='http://javafx.com/fxml/1' fx:controller='c.C' style='-fx-padding: 4 8; -fx-background-color: rgb(255,0,0)'>"
                + "<Label text='A' textFill='WHITE' opacity='0.5' layoutX='3'><tooltip><Tooltip text='Hint'/></tooltip></Label>"
                + "<Text text='T'/><ColorPicker/><ScrollBar max='5'/>"
                + "<MenuButton text='M'><items><MenuItem text='X' disable='true'/><CheckMenuItem text='Y'/></items></MenuButton>"
                + "<ComboBox onAction='#pick'/><Label onMouseClicked='#click' text='B' visible='false'/>"
                + "<HBox><padding><Insets topRightBottomLeft='5'/></padding><Label text='C' HBox.hgrow='SOMETIMES' minWidth='-1' maxWidth='Infinity'/></HBox>"
                + "</VBox>";
        FxmlToJxml.Result result = FxmlToJxml.convert(stream(fxml), "s.fxml", new FxmlToJxml.Options());
        JxmlParser.Node root = result.node();
        assertEquals("4 8", root.attrs.get("padding"));
        assertEquals("#FF0000", root.attrs.get("background"));
        JxmlParser.Node label = root.children.get(0);
        assertEquals("Hint", label.attrs.get("tooltip"));
        assertEquals("#FFFFFF", label.attrs.get("textFill"));
        assertEquals("0.5", label.attrs.get("opacity"));
        assertEquals("Label", root.children.get(1).tag); // Text
        assertEquals("ColorPicker", root.children.get(2).tag);
        assertEquals("5", root.children.get(3).attrs.get("max"));
        JxmlParser.Node menu = root.children.get(4);
        assertEquals("true", menu.children.get(0).attrs.get("disabled"));
        assertEquals("MenuItem", menu.children.get(1).tag);
        assertEquals("#pick", root.children.get(5).attrs.get("onChange"));
        JxmlParser.Node clicked = root.children.get(6);
        assertEquals("#click", clicked.attrs.get("onAction"));
        assertEquals("true", clicked.attrs.get("hidden"));
        JxmlParser.Node hbox = root.children.get(7);
        assertEquals("5", hbox.attrs.get("padding"));
        assertEquals("sometimes", hbox.children.get(0).attrs.get("hgrow"));
        assertFalse(hbox.children.get(0).attrs.containsKey("minWidth"));
        assertFalse(hbox.children.get(0).attrs.containsKey("maxWidth"));
        assertTrue(has(result.report(), FxmlReport.Level.APPROXIMATE, "ComboBox", "onAction", "onChange"));
        assertTrue(has(result.report(), FxmlReport.Level.APPROXIMATE, "Label", "onMouseClicked", "onAction"));
        assertTrue(has(result.report(), FxmlReport.Level.APPROXIMATE, "CheckMenuItem", null, "check state"));
        assertNull(xsdError(result.jxml()), result.jxml());
    }

    // ---- XSD, interpreter, compiler --------------------------------------------------------------

    @Test
    void theProducedJxmlValidatesAgainstTheGeneratedXsd() throws Exception {
        for (String name : new String[] {"customer-form.fxml", "orders-table.fxml", "panes.fxml", "panes-part.fxml"}) {
            String jxml = convert(name).jxml();
            assertNull(xsdError(jxml), name + "\n" + jxml);
        }
    }

    @Test
    void theTextAndTheInMemoryTreeAreTheSameScreen() throws Exception {
        for (String name : new String[] {"customer-form.fxml", "orders-table.fxml"}) {
            FxmlToJxml.Result result = convert(name);
            JxmlParser.Node reparsed = JxmlParser.parse(stream(result.jxml()), "x.jxml");
            assertEquals(describe(result.node()), describe(reparsed), name);
        }
    }

    private static String describe(JxmlParser.Node node) {
        StringBuilder out = new StringBuilder(node.tag).append(node.attrs).append('{');
        for (JxmlParser.Node child : node.children) {
            out.append(describe(child));
        }
        return out.append('}').toString();
    }

    @Test
    void theFormRunsInTheInterpreterAndTheGeneratedClassBuildsTheSameTree() throws Exception {
        InputStream in = open("customer-form.fxml");
        JxmlTemplate template = JxmlTemplate.readFxml(in, "customer-form.fxml");
        in.close();
        assertEquals("com.jxparallel.forge.FxmlFormController", template.getController());
        FxmlFormController controller = new FxmlFormController();
        JXElement built = template.build(controller);
        assertNotNull(built);
        assertEquals("Ada", find(built, "input").getProps().get("value"));
        List<JxmlView<Object>> views = new ViewCompiler(dir).views(template);
        assertEquals(3, views.size());
        for (JxmlView<Object> view : views) {
            assertNull(JxmlTrees.diff(built, view.build(controller)));
        }
        // the Save button works in the generated class: onAction calls controller.save()
        JXElement save = find(views.get(1).build(controller), "button");
        ((Runnable) save.getProps().get("onAction")).run();
        assertEquals(1, controller.saved);
        // and the interpreter's tree is the one of the JXML text read the usual way
        JxmlTemplate fromText = JxmlTemplate.read(stream(convert("customer-form.fxml").jxml()), "customer-form.jxml");
        assertNull(JxmlTrees.diff(built, fromText.build(controller)));
    }

    @Test
    void theTableRunsInTheInterpreterAndTheGeneratedClassBuildsTheSameTree() throws Exception {
        InputStream in = open("orders-table.fxml");
        JxmlTemplate template = JxmlTemplate.readFxml(in, "orders-table.fxml");
        in.close();
        FxmlTableController controller = new FxmlTableController();
        JXElement built = template.build(controller);
        JXElement table = find(built, "table");
        assertNotNull(table);
        assertEquals(2, table.getProps().get("itemCount"));
        assertArrayEqualsStrings(new String[] {"Id", "Customer", "Total"}, (String[]) table.getProps().get("columns"));
        for (JxmlView<Object> view : new ViewCompiler(dir).views(template)) {
            assertNull(JxmlTrees.diff(built, view.build(controller)));
        }
    }

    private static void assertArrayEqualsStrings(String[] expected, String[] actual) {
        assertEquals(java.util.Arrays.asList(expected), java.util.Arrays.asList(actual));
    }

    private static JXElement find(JXElement element, String type) {
        if (type.equals(element.getType())) {
            return element;
        }
        for (JXElement child : element.getChildren()) {
            JXElement found = find(child, type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    void anFxmlIncludeIsReadWithTheLoaderAndItsReportIsKept() throws Exception {
        FxmlToJxml.Options options = new FxmlToJxml.Options();
        InputStream in = open("panes.fxml");
        JxmlTemplate template = JxmlTemplate.readFxml(in, "fxml/panes.fxml",
                JxmlLoader.classpath(getClass().getClassLoader()), options);
        in.close();
        JXElement built = template.build(new FxmlFormController());
        assertNotNull(built);
        assertTrue(has(options.report(), FxmlReport.Level.UNSUPPORTED, "Button", "ButtonBar.buttonData", "no JXML equivalent"));
        assertTrue(JxmlTrees.diff(built, template.build(new FxmlFormController())) == null);
    }

    // ---- directories and the command line --------------------------------------------------------

    @Test
    void aDirectoryIsConvertedToJxmlFilesThatTheCompilerTakes() throws Exception {
        Path src = dir.resolve("src/screens");
        Files.createDirectories(src);
        for (String name : new String[] {"customer-form.fxml", "orders-table.fxml"}) {
            Files.copy(new File("src/test/resources/fxml/" + name).toPath(), src.resolve(name));
        }
        Path out = dir.resolve("out");
        FxmlReport report = FxmlToJxml.convertDirectory(src.toFile(), out.toFile(), new FxmlToJxml.Options());
        assertTrue(Files.isRegularFile(out.resolve("customer-form.jxml")));
        assertFalse(report.entries(FxmlReport.Level.NOTE).isEmpty());
        JXForge.Result compiled = JXForge.compileDirectory(out.toFile(), dir.resolve("gen").toFile(), "screens", "View", true);
        assertEquals(2, compiled.written.size());
        assertTrue(Files.isRegularFile(dir.resolve("gen/screens/CustomerFormView.java")));
        String java = new String(Files.readAllBytes(dir.resolve("gen/screens/OrdersTableView.java")), StandardCharsets.UTF_8);
        assertTrue(java.contains("getOrders()"), java);
    }

    @Test
    void includedFilesWithAnotherControllerAreReported() throws Exception {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.copy(new File("src/test/resources/fxml/Outer.fxml").toPath(), src.resolve("Outer.fxml"));
        Files.copy(new File("src/test/resources/fxml/Inner.fxml").toPath(), src.resolve("Inner.fxml"));
        Files.copy(new File("src/test/resources/fxml/InnerWithId.fxml").toPath(), src.resolve("InnerWithId.fxml"));
        FxmlReport report = FxmlToJxml.convertDirectory(src.toFile(), dir.resolve("o").toFile(), new FxmlToJxml.Options());
        assertTrue(has(report, FxmlReport.Level.UNSUPPORTED, "fx:include", "source", "InnerController"), report.toText());
        assertTrue(Files.isRegularFile(dir.resolve("o/Outer.jxml")));
        assertThrows(FxmlConversionException.class,
                () -> FxmlToJxml.convertDirectory(src.toFile(), dir.resolve("o2").toFile(), new FxmlToJxml.Options().strict(true)));
    }

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
