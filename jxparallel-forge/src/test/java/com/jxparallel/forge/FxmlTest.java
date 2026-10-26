package com.jxparallel.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
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

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jxparallel.forge.samples.SampleController;
import com.jxparallel.ui.JXElement;

/**
 * FXML screens compiled through the same compiler as JXML: every row of the mapping, every error, the warnings, the
 * samples in {@code src/test/shared/samples-fxml} (which must show every mapped class and attribute), the 20 screens of
 * the FXML benchmark, the directory mode of {@link JXForge} and its {@code --fxml-to-jxml} option.
 */
class FxmlTest {
    private static final File SAMPLES = new File("src/test/shared/samples-fxml");
    private static final File CORPUS = new File("../jxparallel-examples/src/main/resources/fxmlbench");
    private static final String CTRL = "com.jxparallel.forge.samples.SampleController";

    @TempDir
    Path dir;

    // ---- helpers ---------------------------------------------------------------------------------

    private static List<File> samples() {
        File[] files = SAMPLES.listFiles();
        assertTrue(files != null && files.length > 0, "samples are in src/test/shared/samples-fxml");
        List<File> out = new ArrayList<File>();
        for (File f : files) {
            if (f.getName().endsWith(".fxml")) {
                out.add(f);
            }
        }
        Collections.sort(out);
        return out;
    }

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static JxmlTemplate read(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            return JxmlTemplate.read(in, file.getName(), JxmlLoader.directory(SAMPLES));
        } finally {
            in.close();
        }
    }

    private static final String HEAD = "<VBox xmlns:fx=\"http://javafx.com/fxml\" fx:controller=\"" + CTRL + "\">\n";

    /** A document whose root is a VBox with the body as children, so the body starts on line 2. */
    private static String doc(String body) {
        return HEAD + body + "\n</VBox>";
    }

    private static JxmlParser.Node translate(String xml, List<String> warnings) {
        return FxmlTranslator.translate(stream(xml), "t.fxml", warnings);
    }

    /** The translated children of the root of {@link #doc}, as {@code Tag{attr=value}[children]}. */
    private static String shapes(String body) {
        List<String> out = new ArrayList<String>();
        for (JxmlParser.Node child : translate(doc(body), null).children) {
            out.add(shape(child));
        }
        return String.join(" ", out);
    }

    private static String shape(JxmlParser.Node node) {
        StringBuilder s = new StringBuilder(node.tag);
        if (!node.attrs.isEmpty()) {
            List<String> attrs = new ArrayList<String>();
            for (Map.Entry<String, String> a : node.attrs.entrySet()) {
                attrs.add(a.getKey() + "=" + a.getValue());
            }
            s.append('{').append(String.join(",", attrs)).append('}');
        }
        if (!node.children.isEmpty()) {
            List<String> kids = new ArrayList<String>();
            for (JxmlParser.Node child : node.children) {
                kids.add(shape(child));
            }
            s.append('[').append(String.join(",", kids)).append(']');
        }
        return s.toString();
    }

    private static JxmlException error(String xml) {
        return assertThrows(JxmlException.class, () -> translate(xml, null));
    }

    /** The body (one line, so the problem is on line 2) is refused, naming the file, the line and these words. */
    private static void refused(String body, String... words) {
        JxmlException e = error(doc(body));
        assertEquals(2, e.getLine(), e.getMessage());
        assertTrue(e.getMessage().startsWith("t.fxml:2: "), e.getMessage());
        for (String word : words) {
            assertTrue(e.getMessage().contains(word), "\"" + word + "\" is not in: " + e.getMessage());
        }
    }

    // ---- the mapping: one row for each class ------------------------------------------------------------

    private static final String[][] ROWS = {
        {"<VBox spacing=\"4\" alignment=\"CENTER\" fillWidth=\"false\"/>", "Column{gap=4,alignment=CENTER,fillWidth=false}"},
        {"<HBox spacing=\"4\" alignment=\"CENTER_LEFT\" fillHeight=\"true\"/>", "Row{gap=4,alignment=CENTER_LEFT,fillHeight=true}"},
        {"<StackPane alignment=\"BOTTOM_RIGHT\"><children><Label text=\"a\"/></children></StackPane>",
            "Stack{alignment=BOTTOM_RIGHT}[Label{text=a}]"},
        {"<BorderPane padding=\"4\"><top><Label text=\"t\"/></top><bottom><Label text=\"b\"/></bottom><left><Label text=\"l\"/></left>"
            + "<right><Label text=\"r\"/></right><center><Label text=\"c\"/></center></BorderPane>",
            "Border{padding=4}[Label{text=t,slot=top},Label{text=b,slot=bottom},Label{text=l,slot=left},Label{text=r,slot=right},"
            + "Label{text=c,slot=center}]"},
        {"<GridPane hgap=\"8\" vgap=\"6\" alignment=\"CENTER\"><Label text=\"a\" GridPane.rowIndex=\"1\" GridPane.columnIndex=\"2\" "
            + "GridPane.rowSpan=\"3\" GridPane.columnSpan=\"4\" GridPane.hgrow=\"ALWAYS\" GridPane.vgrow=\"NEVER\" "
            + "GridPane.halignment=\"RIGHT\" GridPane.valignment=\"TOP\"/></GridPane>",
            "Grid{hgap=8,vgap=6,alignment=CENTER}[Label{text=a,row=1,column=2,rowSpan=3,columnSpan=4,hgrow=always,vgrow=never,"
            + "halignment=RIGHT,valignment=TOP}]"},
        {"<AnchorPane><Label text=\"a\" AnchorPane.topAnchor=\"1\" AnchorPane.rightAnchor=\"2\" AnchorPane.bottomAnchor=\"3\" "
            + "AnchorPane.leftAnchor=\"4\"/></AnchorPane>", "Anchor[Label{text=a,top=1,right=2,bottom=3,left=4}]"},
        {"<Pane padding=\"2\"><Label text=\"a\" layoutX=\"1\" layoutY=\"2\"/></Pane>", "Pane{padding=2}[Label{text=a,x=1,y=2}]"},
        {"<FlowPane hgap=\"4\" vgap=\"5\" alignment=\"TOP_LEFT\" orientation=\"VERTICAL\" prefWrapLength=\"200\" columnHalignment=\"LEFT\" "
            + "rowValignment=\"CENTER\"/>",
            "Flow{hgap=4,vgap=5,alignment=TOP_LEFT,orientation=vertical,prefWrapLength=200,columnHalignment=LEFT,rowValignment=CENTER}"},
        {"<TilePane hgap=\"4\" vgap=\"5\" alignment=\"TOP_LEFT\" orientation=\"HORIZONTAL\" prefColumns=\"2\" prefRows=\"3\" "
            + "prefTileWidth=\"60\" prefTileHeight=\"24\" tileAlignment=\"CENTER\"/>",
            "Tile{hgap=4,vgap=5,alignment=TOP_LEFT,orientation=horizontal,prefColumns=2,prefRows=3,prefTileWidth=60,prefTileHeight=24,"
            + "tileAlignment=CENTER}"},
        {"<ScrollPane fitToWidth=\"true\" fitToHeight=\"true\"><content><Label text=\"a\"/></content></ScrollPane>",
            "Scroll{fitToWidth=true,fitToHeight=true}[Label{text=a}]"},
        {"<SplitPane orientation=\"VERTICAL\" dividerPositions=\"0.5\"><items><Label text=\"a\"/><Label text=\"b\"/></items></SplitPane>",
            "SplitPane{orientation=vertical,dividerPositions=0.5}[Label{text=a},Label{text=b}]"},
        {"<TabPane tabClosingPolicy=\"UNAVAILABLE\"><Tab text=\"T\"><Label text=\"a\"/></Tab><Tab text=\"U\" closable=\"true\">"
            + "<content><Label text=\"b\"/></content></Tab></TabPane>",
            "Tabs[Tab{title=T,closable=false}[Label{text=a}],Tab{title=U,closable=true}[Label{text=b}]]"},
        {"<TabPane><tabs><Tab text=\"T\"><Label text=\"a\"/></Tab></tabs></TabPane>", "Tabs[Tab{title=T}[Label{text=a}]]"},
        {"<Accordion><panes><TitledPane text=\"One\" expanded=\"true\" collapsible=\"false\"><content><Label text=\"a\"/></content>"
            + "</TitledPane></panes></Accordion>",
            "Accordion[TitledPane{text=One,expanded=true,collapsible=false}[Label{text=a}]]"},
        {"<Accordion><TitledPane text=\"Two\"><Label text=\"b\"/></TitledPane></Accordion>", "Accordion[TitledPane{text=Two}[Label{text=b}]]"},
        {"<ToolBar><items><Button text=\"a\"/></items><Button text=\"b\"/></ToolBar>", "ToolBar[Button{text=a},Button{text=b}]"},
        {"<ButtonBar><buttons><Button text=\"a\"/></buttons><Button text=\"b\"/></ButtonBar>", "ButtonBar[Button{text=a},Button{text=b}]"},
        {"<Label text=\"a\" wrapText=\"true\" textFill=\"#112233\"/>", "Label{text=a,wrapText=true,textFill=#112233}"},
        {"<Button text=\"a\" onAction=\"#save\" defaultButton=\"true\" textFill=\"#112233\"><graphic><ImageView fitWidth=\"8\"/></graphic></Button>",
            "Button{text=a,onAction=#save,defaultButton=true,textFill=#112233}[Image{fitWidth=8}]"},
        {"<TextField text=\"a\" promptText=\"p\" prefColumnCount=\"20\"/>", "TextField{text=a,prompt=p,columns=20}"},
        {"<PasswordField text=\"a\" promptText=\"p\" prefColumnCount=\"6\"/>", "PasswordField{text=a,prompt=p,columns=6}"},
        {"<TextArea text=\"a\" promptText=\"p\" wrapText=\"true\" prefColumnCount=\"30\" prefRowCount=\"4\"/>",
            "TextArea{text=a,prompt=p,wrapText=true,columns=30,rows=4}"},
        {"<CheckBox text=\"a\" selected=\"TRUE\" onAction=\"#save\"/>", "CheckBox{text=a,selected=true,onAction=#save}"},
        {"<RadioButton text=\"a\" selected=\"false\" onAction=\"#save\"/>", "RadioButton{text=a,selected=false,onAction=#save}"},
        {"<ToggleButton text=\"a\" selected=\"true\" onAction=\"#save\"/>", "ToggleButton{text=a,selected=true,onAction=#save}"},
        {"<ComboBox promptText=\"p\" value=\"v\"/>", "ComboBox{prompt=p,value=v}"},
        {"<ChoiceBox value=\"v\"/>", "ChoiceBox{value=v}"},
        {"<Spinner min=\"0\" max=\"100\" initialValue=\"4\"/>", "Spinner{min=0,max=100,value=4}"},
        {"<DatePicker promptText=\"p\" value=\"2026-10-21\"/>", "DatePicker{prompt=p,value=2026-10-21}"},
        {"<ColorPicker value=\"#E91E63\"/>", "ColorPicker{value=#E91E63}"},
        {"<Slider min=\"0\" max=\"10\" value=\"3\" orientation=\"HORIZONTAL\"/>", "Slider{min=0,max=10,value=3}"},
        {"<ProgressBar progress=\"0.25\"/>", "ProgressBar{progress=0.25}"},
        {"<ProgressIndicator progress=\"-1\"/>", "ProgressIndicator{progress=-1}"},
        {"<Separator orientation=\"VERTICAL\"/>", "Separator{orientation=vertical}"},
        {"<Hyperlink text=\"a\" visited=\"true\" onAction=\"#save\"/>", "Hyperlink{text=a,visited=true,onAction=#save}"},
        {"<ImageView fitWidth=\"16\" fitHeight=\"8\" preserveRatio=\"true\"/>", "Image{fitWidth=16,fitHeight=8,preserveRatio=true}"},
        {"<Pagination pageCount=\"12\" currentPageIndex=\"1\" maxPageIndicatorCount=\"5\"/>",
            "Pagination{pageCount=12,current=1,maxPageIndicatorCount=5}"},
        {"<TextFlow><Text text=\"a\" fill=\"#336699\"/></TextFlow>", "TextFlow[Label{text=a,textFill=#336699}]"},
        {"<ListView fx:id=\"rows\" fixedCellSize=\"20\"/>", "ListView{cellHeight=20,items=${rows},var=item}[ListItem{text=${item}}]"},
        {"<TableView fx:id=\"rows\" fixedCellSize=\"22\"><columns><TableColumn text=\"Id\" prefWidth=\"60\"/><TableColumn text=\"Name\"/>"
            + "</columns></TableView>",
            "TableView{rowHeight=22,items=${rows},var=row,columns=Id, Name,columnWidths=60, -1}"
            + "[TableRow[TableCell{text=${row}},TableCell{text=${row}}]]"},
        {"<TableView fx:id=\"rows\"/>", "TableView{items=${rows},var=row}[TableRow]"},
        {"<TreeView><root><TreeItem value=\"src\" expanded=\"true\"><children><TreeItem value=\"main\"/></children>"
            + "<TreeItem value=\"test\" expanded=\"false\"/></TreeItem></root></TreeView>",
            "Tree[TreeItem{text=src,expanded=true}[TreeItem{text=main},TreeItem{text=test,expanded=false}]]"},
        {"<MenuBar><menus><Menu text=\"File\"><items><MenuItem text=\"New\" accelerator=\"Ctrl+N\" onAction=\"#save\"/>"
            + "<SeparatorMenuItem/></items><MenuItem text=\"Quit\"/></Menu></menus><Menu text=\"Edit\"/></MenuBar>",
            "MenuBar[Menu{text=File}[MenuItem{text=New,accelerator=Ctrl+N,onAction=#save},MenuItem{separator=true},MenuItem{text=Quit}],"
            + "Menu{text=Edit}]"},
        {"<MenuButton text=\"Actions\"><items><MenuItem text=\"Open\"/></items><MenuItem text=\"Quit\"/></MenuButton>",
            "MenuButton{text=Actions}[MenuItem{text=Open},MenuItem{text=Quit}]"},
        {"<StackPane><contextMenu><ContextMenu><items><MenuItem text=\"Cut\"/></items><SeparatorMenuItem/></ContextMenu></contextMenu>"
            + "<Label text=\"a\"/></StackPane>",
            "Stack[Label{text=a},ContextMenu[MenuItem{text=Cut},MenuItem{separator=true}]]"},
        {"<GridPane><columnConstraints><ColumnConstraints hgrow=\"ALWAYS\" halignment=\"CENTER\" prefWidth=\"80\" minWidth=\"10\"/>"
            + "</columnConstraints><rowConstraints><RowConstraints vgrow=\"SOMETIMES\" valignment=\"TOP\" prefHeight=\"30\"/></rowConstraints>"
            + "<Label text=\"a\"/><Label text=\"b\" GridPane.columnIndex=\"1\"/></GridPane>",
            "Grid[Label{text=a,hgrow=always,halignment=CENTER,prefWidth=80,minWidth=10,vgrow=sometimes,valignment=TOP,prefHeight=30},"
            + "Label{text=b,column=1,vgrow=sometimes,valignment=TOP,prefHeight=30}]"},
    };

    @Test
    void everyClassOfTheMappingHasARowThatTranslatesItAsDescribed() {
        Set<String> classes = new LinkedHashSet<String>();
        for (String[] row : ROWS) {
            assertEquals(row[1].replace(" ", ""), shapes(row[0]).replace(" ", ""), row[0]);
            for (java.util.regex.Matcher m = java.util.regex.Pattern.compile("<([A-Z][A-Za-z]*)[ />]").matcher(row[0]); m.find(); ) {
                classes.add(m.group(1));
            }
        }
        List<String> missing = new ArrayList<String>(FxmlTranslator.CLASSES.keySet());
        missing.removeAll(classes);
        assertEquals(Collections.<String>emptyList(), missing, "classes of the mapping without a row");
    }

    @Test
    void commonAttributesAreRenamedConvertedOrDropped() {
        assertEquals("Button{key=b,prefWidth=10,prefHeight=20,minWidth=1,minHeight=2,maxWidth=30,maxHeight=40,opacity=0.5,disabled=true,"
                        + "hidden=true,x=3,y=4,style=a b,padding=1 2 3 4}",
                shapes("<Button id=\"b\" prefWidth=\"10\" prefHeight=\"20\" minWidth=\"1\" minHeight=\"2\" maxWidth=\"30\" maxHeight=\"40\" "
                        + "opacity=\"0.5\" disable=\"true\" visible=\"false\" layoutX=\"3\" layoutY=\"4\" styleClass=\"a, b\" "
                        + "padding=\"1, 2, 3, 4\" mnemonicParsing=\"false\" managed=\"true\"/>"));
        // the defaults of JavaFX are not written
        assertEquals("Label", shapes("<Label prefWidth=\"USE_COMPUTED_SIZE\" prefHeight=\"-1.0\" minWidth=\"-Infinity\" maxWidth=\"Infinity\" "
                + "minHeight=\"-1\" maxHeight=\"-1\" visible=\"true\"/>"));
        assertEquals("Button{padding=8}", shapes("<Button padding=\"8\"/>"));
    }

    @Test
    void insetsElementsBecomePaddingAndMargin() {
        assertEquals("Column{padding=1 2 3 4}", shapes("<VBox><padding><Insets top=\"1\" right=\"2\" bottom=\"3\" left=\"4\"/></padding></VBox>"));
        assertEquals("Column{padding=0 0 3 4}", shapes("<VBox><padding><Insets bottom=\"3\" left=\"4\"/></padding></VBox>"));
        assertEquals("Row{padding=5 5 5 5}", shapes("<HBox><padding><Insets topRightBottomLeft=\"5\"/></padding></HBox>"));
        for (String owner : FxmlTranslator.MARGIN_OWNERS) {
            String button0 = "<Button text=\"a\"><" + owner + ".margin><Insets top=\"1\" right=\"2\" bottom=\"3\" left=\"4\"/>"
                    + "</" + owner + ".margin></Button>";
            String xml = "<" + owner + ">" + ("BorderPane".equals(owner) ? "<center>" + button0 + "</center>" : button0) + "</" + owner + ">";
            JxmlParser.Node button = translate(doc(xml), null).children.get(0).children.get(0);
            assertEquals("1 2 3 4", button.attrs.get("margin"), owner);
        }
    }

    @Test
    void aFullyQualifiedJavaFxClassIsTheSameAsTheImportedOne() {
        assertEquals("Label{text=a}", shapes("<javafx.scene.control.Label text=\"a\"/>"));
    }

    @Test
    void valuesAreEscapedForJxmlAndFxmlEscapesAreRemoved() {
        assertEquals("Label{text=\\${not a binding}}", shapes("<Label text=\"\\${not a binding}\"/>"));
        assertEquals("Label{text=\\@{x}}", shapes("<Label text=\"\\@{x}\"/>"));
        assertEquals("Label{text=#1}", shapes("<Label text=\"#1\"/>"));
        assertEquals("Label{text=\\\\x}", shapes("<Label text=\"\\\\x\"/>")); // FXML \\x is the text \x, which JXML writes \\x
    }

    @Test
    void anFxIncludeBecomesAnIncludeWithItsPlacement() {
        assertEquals("Grid[Include{src=parts/a.fxml,row=1,column=2}]",
                shapes("<GridPane><fx:include source=\"parts/a.fxml\" GridPane.rowIndex=\"1\" GridPane.columnIndex=\"2\"/></GridPane>"));
        assertEquals("Border[Include{src=a.fxml,slot=top}]", shapes("<BorderPane><top><fx:include source=\"a.fxml\"/></top></BorderPane>"));
    }

    @Test
    void theRootControllerBecomesTheControllerAttribute() {
        JxmlParser.Node root = translate(doc(""), null);
        assertEquals("Column", root.tag);
        assertEquals(CTRL, root.attrs.get("controller"));
        assertEquals("t.fxml", root.source);
        assertEquals(1, root.line);
    }

    // ---- what has no equivalent -------------------------------------------------------------------------

    @Test
    void whatJxmlCannotCarryOverIsRefusedWithItsFileAndLine() {
        refused("<fx:define><ToggleGroup fx:id=\"g\"/></fx:define>", "fx:define");
        refused("<fx:script source=\"x.js\"/>", "fx:script");
        refused("<fx:reference source=\"x\"/>", "fx:reference");
        refused("<fx:copy source=\"x\"/>", "fx:copy");
        refused("<Label fx:factory=\"create\"/>", "fx:factory", "Label");
        refused("<Label fx:value=\"x\"/>", "fx:value");
        refused("<Label fx:constant=\"X\"/>", "fx:constant");
        refused("<Label text=\"${name}\"/>", "expression", "text=\"${name}\"");
        refused("<Label text=\"$name\"/>", "reference", "$name");
        refused("<RadioButton toggleGroup=\"$group\"/>", "reference", "toggleGroup");
        refused("<Label textFill=\"%title\"/>", "resource key", "text attribute");
        refused("<Label text=\"%bad key\"/>", "resource key", "not a key");
        refused("<ImageView image=\"@icon.png\"/>", "location", "@icon.png");
        refused("<ComboBox><items><FXCollections fx:factory=\"observableArrayList\"/></items></ComboBox>", "items", "ComboBox");
        refused("<MyWidget/>", "MyWidget");
        refused("<com.acme.Widget/>", "custom class", "com.acme.Widget");
        refused("<Label><stylesheets><String fx:value=\"a.css\"/></stylesheets></Label>", "stylesheets");
        refused("<Label stylesheets=\"a.css\"/>", "stylesheets", "Label");
        refused("<GridPane><columnConstraints><ColumnConstraints percentWidth=\"50\"/></columnConstraints></GridPane>", "percentWidth", "ColumnConstraints");
        refused("<Label><contextMenu/></Label>", "contextMenu");
        refused("<BorderPane><Label text=\"x\"/></BorderPane>", "BorderPane", "top");
        refused("<Label>text</Label>", "text content");
    }

    @Test
    void anAttributeWithoutAnEquivalentNamesTheAttributeAndTheTag() {
        refused("<TextField disable=\"true\"/>", "disable", "TextField");
        refused("<TextField onAction=\"#x\"/>", "onAction", "TextField");
        refused("<Label onMouseClicked=\"#x\"/>", "onMouseClicked", "Label");
        refused("<Label style=\"-fx-text-fill: red\"/>", "style", "Label");
        refused("<Label bogus=\"1\"/>", "bogus", "Label");
        refused("<Label padding=\"3\"/>", "padding", "Label");
        refused("<TextField editable=\"false\"/>", "editable", "TextField");
        refused("<GridPane GridPane.fillWidth=\"true\"/>", "GridPane.fillWidth", "GridPane");
        refused("<Label GridPane.rowIndex=\"1\"/>", "GridPane.rowIndex", "needs a <GridPane> parent");
        refused("<HBox><Label VBox.vgrow=\"ALWAYS\"/></HBox>", "VBox.vgrow", "HBox");
        refused("<Label managed=\"false\"/>", "managed", "Label");
        refused("<Label mnemonicParsing=\"true\"/>", "mnemonicParsing");
        refused("<Label prefWidth=\"USE_PREF_SIZE\"/>", "USE_PREF_SIZE");
        refused("<Label visible=\"maybe\"/>", "visible");
        refused("<Button padding=\"1 2\"/>", "padding", "four");
        refused("<Slider orientation=\"VERTICAL\"/>", "orientation", "Slider");
        refused("<TabPane tabClosingPolicy=\"ALL_TABS\"/>", "tabClosingPolicy");
        refused("<Button onAction=\"save\"/>", "onAction", "#save");
        refused("<GridPane><Label GridPane.hgrow=\"FOREVER\"/></GridPane>", "GridPane.hgrow", "ALWAYS");
        refused("<Label fx:controller=\"x.Y\"/>", "fx:controller", "root");
        refused("<Label><VBox.padding/></Label>", "VBox.padding");
        refused("<Label><padding/></Label>", "Insets");
    }

    @Test
    void listsAndTablesNeedTheNameOfTheirRows() {
        refused("<ListView/>", "ListView", "fx:id");
        refused("<TableView/>", "TableView", "fx:id");
        refused("<TableView fx:id=\"r\"><columns><TableColumn><cellValueFactory><PropertyValueFactory property=\"x\"/></cellValueFactory>"
                + "</TableColumn></columns></TableView>", "cellValueFactory", "TableColumn");
        refused("<TableView fx:id=\"r\"><columns><TableColumn text=\"a\" minWidth=\"3\"/></columns></TableView>", "minWidth", "TableColumn");
        refused("<TableView fx:id=\"r\"><columns><TableColumn text=\"a, b\"/></columns></TableView>", "comma");
    }

    @Test
    void includesAreCheckedToo() {
        refused("<fx:include/>", "source");
        refused("<fx:include source=\"a.fxml\" resources=\"r\"/>", "resources");
        refused("<fx:include source=\"a.fxml\" GridPane.rowIndex=\"1\"/>", "GridPane.rowIndex", "GridPane");
    }

    @Test
    void theRootIsALayout() {
        JxmlException e = error("<fx:root type=\"javafx.scene.layout.VBox\"/>");
        assertEquals(1, e.getLine());
        assertTrue(e.getMessage().startsWith("t.fxml:1: ") && e.getMessage().contains("fx:root"), e.getMessage());
        e = error("<top/>");
        assertEquals(1, e.getLine());
    }

    @Test
    void fxRootIsRefusedWithTheCustomComponentPatternExplained() {
        JxmlException e = error("<fx:root type=\"javafx.scene.layout.VBox\" xmlns:fx=\"http://javafx.com/fxml\"/>");
        assertEquals(1, e.getLine());
        assertTrue(e.getMessage().startsWith("t.fxml:1: fx:root has no JXML equivalent"), e.getMessage());
        assertTrue(e.getMessage().contains("setRoot(this)") && e.getMessage().contains("javafx.scene.layout.VBox")
                && e.getMessage().contains("fx:controller"), e.getMessage());
        refused("<fx:root type=\"VBox\"/>", "fx:root"); // inside a screen it is still refused
    }

    @Test
    void aContextMenuBecomesTheLastChildOfAContainerAndMeansAPressOnIt() {
        assertEquals("Column[Label{text=a},ContextMenu{disabled=true}[MenuItem{text=Cut},Menu{text=More}[MenuItem{text=Deep}],MenuItem{text=Paste}]]",
                shapes("<VBox><contextMenu><ContextMenu disable=\"true\"><items><MenuItem text=\"Cut\"/><Menu text=\"More\"><MenuItem text=\"Deep\"/></Menu>"
                        + "</items><MenuItem text=\"Paste\"/></ContextMenu></contextMenu><Label text=\"a\"/></VBox>"));
        for (String owner : new String[] {"VBox", "HBox", "StackPane", "Pane", "AnchorPane", "FlowPane", "TilePane"}) {
            assertTrue(shapes("<" + owner + "><contextMenu><ContextMenu><MenuItem text=\"x\"/></ContextMenu></contextMenu><Label text=\"a\"/></" + owner + ">")
                    .endsWith("[Label{text=a},ContextMenu[MenuItem{text=x}]]"), owner);
        }
        // on the root of a screen too, and the line of its fx:id warning is the ContextMenu's
        List<String> warnings = new ArrayList<String>();
        JxmlParser.Node root = translate(HEAD + "<contextMenu>\n<ContextMenu fx:id=\"menu\"><MenuItem text=\"x\"/></ContextMenu>\n</contextMenu>\n</VBox>", warnings);
        assertEquals("ContextMenu", root.children.get(0).tag);
        assertEquals(3, root.children.get(0).line);
        assertTrue(warnings.get(0).startsWith("t.fxml:3: ") && warnings.get(0).contains("<ContextMenu>"), warnings.toString());
    }

    @Test
    void aContextMenuOnAControlThatHoldsNoChildrenIsRefusedWithFileAndLine() {
        for (String owner : new String[] {"Label", "Button", "TextField", "ListView fx:id=\"r\"", "TableView fx:id=\"r\"", "BorderPane", "GridPane",
                "ScrollPane", "TitledPane"}) {
            refused("<" + owner + "><contextMenu><ContextMenu><MenuItem text=\"x\"/></ContextMenu></contextMenu></" + owner.split(" ")[0] + ">",
                    "contextMenu", "no JXML equivalent", "secondary press on its parent", "StackPane");
        }
        refused("<VBox><contextMenu><Label/></contextMenu></VBox>", "contextMenu", "one <ContextMenu>");
        refused("<VBox><contextMenu><ContextMenu autoHide=\"true\"/></contextMenu></VBox>", "autoHide", "ContextMenu");
        // what the ContextMenu holds is checked by the JXML side, with the FXML line
        JxmlException e = assertThrows(JxmlException.class, () -> JxmlTemplate.read(stream(doc(
                "<VBox><contextMenu><ContextMenu><Label/></ContextMenu></contextMenu></VBox>")), "t.fxml"));
        assertTrue(e.getMessage().startsWith("t.fxml:2: ") && e.getMessage().contains("ContextMenu") && e.getMessage().contains("MenuItem"), e.getMessage());
    }

    @Test
    void aTooltipIsRefusedBecauseJxmlHasNoHoverToShowItOn() {
        refused("<Button text=\"a\"><tooltip><Tooltip text=\"hint\"/></tooltip></Button>", "tooltip", "Button", "no JXML equivalent", "hover");
        refused("<VBox><tooltip><Tooltip text=\"hint\"/></tooltip></VBox>", "tooltip", "VBox");
    }

    private static List<String> warningsOf(String body) {
        List<String> warnings = new ArrayList<String>();
        translate(doc(body), warnings);
        return warnings;
    }

    @Test
    void columnAndRowConstraintsAreWrittenOnTheCellsOfTheColumnAndTheColumnWins() {
        String grid = "<GridPane><columnConstraints><ColumnConstraints hgrow=\"ALWAYS\" prefWidth=\"90\"/><ColumnConstraints halignment=\"RIGHT\" minWidth=\"20\"/>"
                + "</columnConstraints><rowConstraints><RowConstraints/><RowConstraints vgrow=\"NEVER\" valignment=\"BOTTOM\"/></rowConstraints>"
                + "<Label text=\"a\" GridPane.hgrow=\"NEVER\" GridPane.halignment=\"LEFT\"/><Label text=\"b\" GridPane.columnIndex=\"1\" GridPane.halignment=\"CENTER\"/>"
                + "<Label text=\"c\" GridPane.columnIndex=\"1\" GridPane.rowIndex=\"1\"/><Label text=\"wide\" GridPane.rowIndex=\"2\" GridPane.columnSpan=\"2\"/></GridPane>";
        assertEquals("Grid[Label{text=a,hgrow=always,halignment=LEFT,prefWidth=90},Label{text=b,column=1,halignment=CENTER,minWidth=20},"
                + "Label{text=c,column=1,row=1,halignment=RIGHT,minWidth=20,vgrow=never,valignment=BOTTOM},Label{text=wide,row=2,columnSpan=2}]", shapes(grid));
        List<String> warnings = warningsOf(grid);
        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).startsWith("t.fxml:2: ") && warnings.get(0).contains("prefWidth=90") && warnings.get(0).contains("1 cell(s) of column 0"),
                warnings.get(0));
        assertTrue(warnings.get(1).contains("minWidth=20") && warnings.get(1).contains("2 cell(s) of column 1"), warnings.get(1));
    }

    @Test
    void constraintsThatTheCellsCannotSayAreRefusedWithTheirLine() {
        String cell = "<Label text=\"a\"/></GridPane>";
        refused("<GridPane><columnConstraints><ColumnConstraints percentWidth=\"30\"/></columnConstraints>" + cell, "percentWidth", "ColumnConstraints", "no JXML equivalent");
        refused("<GridPane><rowConstraints><RowConstraints percentHeight=\"30\"/></rowConstraints>" + cell, "percentHeight", "RowConstraints");
        refused("<GridPane><columnConstraints><ColumnConstraints maxWidth=\"50\"/></columnConstraints>" + cell, "maxWidth", "ColumnConstraints");
        refused("<GridPane><columnConstraints><ColumnConstraints fillWidth=\"false\"/></columnConstraints>" + cell, "fillWidth", "fill their column");
        refused("<GridPane><columnConstraints><ColumnConstraints minWidth=\"USE_PREF_SIZE\"/></columnConstraints>" + cell, "USE_PREF_SIZE");
        refused("<GridPane><columnConstraints><ColumnConstraints bogus=\"1\"/></columnConstraints>" + cell, "bogus", "ColumnConstraints");
        refused("<GridPane><columnConstraints><ColumnConstraints><Label/></ColumnConstraints></columnConstraints>" + cell, "takes no children");
        refused("<GridPane><columnConstraints><Label/></columnConstraints>" + cell, "not a <ColumnConstraints>");
        // a column that sets something but has no cell of its own (the spacer column of a form, or one only a spanning cell sits in)
        refused("<GridPane><columnConstraints><ColumnConstraints/><ColumnConstraints hgrow=\"ALWAYS\"/></columnConstraints>" + cell,
                "number 2 (column 1)", "hgrow", "no cell of the <GridPane> sits only in column 1");
        refused("<GridPane><rowConstraints><RowConstraints/><RowConstraints prefHeight=\"20\"/></rowConstraints><Label text=\"a\" GridPane.rowIndex=\"1\" "
                + "GridPane.rowSpan=\"2\"/></GridPane>", "number 2 (row 1)", "prefHeight");
        // what changes nothing is accepted: the defaults, a NEVER on an empty column, an empty constraint
        assertEquals("Grid[Label{text=a,hgrow=never}]", shapes("<GridPane><columnConstraints><ColumnConstraints percentWidth=\"-1\" fillWidth=\"true\" maxWidth=\"Infinity\" "
                + "hgrow=\"NEVER\"/><ColumnConstraints hgrow=\"NEVER\" minWidth=\"-1\"/></columnConstraints>" + cell));
    }

    @Test
    void aResourceKeyInATableColumnATabAPromptAndAMenuIsTheTranslation() {
        assertEquals("TableView{items=${r},var=row,columns=%{id.title}, Name, %{qty}}[TableRow[TableCell{text=${row}},TableCell{text=${row}},TableCell{text=${row}}]]",
                shapes("<TableView fx:id=\"r\"><columns><TableColumn text=\"%id.title\"/><TableColumn text=\"Name\"/><TableColumn text=\"%qty\"/></columns></TableView>"));
        assertEquals("Tabs[Tab{title=%{tab.one}}[Label{text=%{hello}}]]", shapes("<TabPane><Tab text=\"%tab.one\"><Label text=\"%hello\"/></Tab></TabPane>"));
        assertEquals("TextField{prompt=%{hint}}", shapes("<TextField promptText=\"%hint\"/>"));
        assertEquals("Menu{text=%{file}}[MenuItem{text=%{open}}]", shapes("<Menu text=\"%file\"><MenuItem text=\"%open\"/></Menu>"));
        assertEquals("TitledPane{text=%{section}}", shapes("<TitledPane text=\"%section\"/>"));
        assertEquals("Hyperlink{text=%{more}}", shapes("<Hyperlink text=\"%more\"/>"));
    }

    @Test
    void aResourceKeyWhereNoTextIsShownIsRefusedAndSaysWhy() {
        refused("<VBox><padding><Insets top=\"%gap\"/></padding></VBox>", "resource key", "top=\"%gap\"", "names no text in <Insets>");
        refused("<fx:include source=\"%file\"/>", "resource key", "source");
        refused("<fx:include source=\"a.fxml\" resources=\"msg\"/>", "resources", "src/main/l10n", "%key");
        refused("<TableView fx:id=\"r\"><columns><TableColumn text=\"a\" prefWidth=\"%w\"/></columns></TableView>", "resource key", "prefWidth");
        refused("<Button id=\"%x\"/>", "resource key", "is not one");
        refused("<MenuItem accelerator=\"%acc\"/>", "resource key", "is not one");
        refused("<TableView fx:id=\"r\"><columns><TableColumn text=\"%a b\"/></columns></TableView>", "not a key");
    }

    @Test
    void theHandlerWarningSaysExactlyWhichBridgeMethodToAdd() {
        List<String> warnings = warningsOf("<Button onAction=\"#save\"/>");
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("public void save() { /* body of save(ActionEvent) without the event */ }"), warnings.get(0));
        assertTrue(!warnings.get(0).contains("save(null)") || warnings.get(0).contains("would break"), warnings.get(0));
    }

    @Test
    void theErrorsOfTheJxmlSideAlsoPointAtTheFxmlLine() {
        JxmlException e = assertThrows(JxmlException.class, () -> JxmlTemplate.read(stream(doc("<Label styleClass=\"nope\"/>")), "t.fxml"));
        assertTrue(e.getMessage().startsWith("t.fxml:2: ") && e.getMessage().contains("unknown style"), e.getMessage());
        e = assertThrows(JxmlException.class, () -> JxmlTemplate.read(stream(doc("<TextField prefColumnCount=\"many\"/>")), "t.fxml"));
        assertTrue(e.getMessage().startsWith("t.fxml:2: ") && e.getMessage().contains("columns"), e.getMessage());
        e = assertThrows(JxmlException.class, () -> JxmlTemplate.read(stream(doc("<Label/>\n<Tooltip/>\n<TabPane>\n<Label/>\n</TabPane>")), "t.fxml"));
        assertTrue(e.getMessage().startsWith("t.fxml:"), e.getMessage());
        assertThrows(JxmlException.class, () -> translate("<VBox><", null)); // not XML at all
    }

    // ---- warnings -----------------------------------------------------------------------------------------

    @Test
    void everyFxIdIsAWarningWithItsLine() {
        List<String> warnings = new ArrayList<String>();
        translate(doc("<Label fx:id=\"status\" text=\"a\"/>\n<TextField fx:id=\"name\"/>\n<fx:include source=\"a.fxml\" fx:id=\"inc\"/>"), warnings);
        assertEquals(3, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).startsWith("t.fxml:2: ") && warnings.get(0).contains("fx:id=\"status\"") && warnings.get(0).contains("<Label>"),
                warnings.get(0));
        assertTrue(warnings.get(1).startsWith("t.fxml:3: ") && warnings.get(1).contains("fx:id=\"name\""), warnings.get(1));
        assertTrue(warnings.get(2).startsWith("t.fxml:4: ") && warnings.get(2).contains("inc"), warnings.get(2));
    }

    @Test
    void everyHandlerIsAWarningThatTheControllerMethodTakesNoArguments() {
        List<String> warnings = new ArrayList<String>();
        translate(doc("<Button onAction=\"#save\"/>\n<Button onAction=\"#save\"/>\n<MenuBar><Menu><MenuItem onAction=\"#open\"/></Menu></MenuBar>"),
                warnings);
        assertEquals(2, warnings.size(), warnings.toString()); // one for each handler, not for each use
        assertTrue(warnings.get(0).startsWith("t.fxml:2: ") && warnings.get(0).contains("#save") && warnings.get(0).contains("save()")
                && warnings.get(0).contains("save(ActionEvent)") && warnings.get(0).contains("no arguments"), warnings.get(0));
        assertTrue(warnings.get(1).startsWith("t.fxml:4: ") && warnings.get(1).contains("#open"), warnings.get(1));
    }

    @Test
    void listAndTableRowsAreWarnedAbout() {
        List<String> warnings = new ArrayList<String>();
        translate(doc("<ListView fx:id=\"rows\"/>\n<TableView fx:id=\"orders\"/>"), warnings);
        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("getRows()"), warnings.get(0));
        assertTrue(warnings.get(1).contains("getOrders()"), warnings.get(1));
    }

    @Test
    void theTemplateKeepsTheWarningsOfItselfAndOfItsIncludedFiles() throws Exception {
        JxmlTemplate layouts = read(new File(SAMPLES, "layouts.fxml"));
        String all = String.join("\n", layouts.getWarnings());
        assertTrue(all.contains("layouts.fxml:") && all.contains("fx:id=\"saveButton\""), all);
        assertTrue(all.contains("parts/header.fxml:") && all.contains("fx:id=\"partTitle\""), all); // from the included file
        assertTrue(all.contains("#save"), all);
        assertTrue(ViewCompiler.read("<Column/>", "t.jxml").getWarnings().isEmpty());
    }

    // ---- the samples ---------------------------------------------------------------------------------------

    private static List<SampleController> states() {
        List<SampleController> out = new ArrayList<SampleController>();
        out.add(new SampleController());
        SampleController other = new SampleController();
        other.rows.clear();
        other.busy = true;
        out.add(other);
        return out;
    }

    @Test
    void everySampleBuildsTheSameTreeInterpretedAndCompiled() throws Exception {
        ViewCompiler compiler = new ViewCompiler(dir);
        for (File sample : samples()) {
            List<JxmlView<Object>> views = compiler.views(read(sample));
            for (SampleController c : states()) {
                JXElement first = views.get(0).build(c);
                assertNotNull(first, sample.getName());
                for (int i = 1; i < views.size(); i++) {
                    assertNull(JxmlTrees.diff(first, views.get(i).build(c)), sample.getName() + ", view " + i);
                }
            }
        }
    }

    private static void collect(JxmlParser.Node node, JxmlParser.Node parent, Map<String, Set<String>> attrs,
                                Map<String, Set<String>> children) {
        Set<String> a = attrs.get(node.tag);
        if (a == null) {
            a = new LinkedHashSet<String>();
            attrs.put(node.tag, a);
        }
        a.addAll(node.attrs.keySet());
        if (parent != null) {
            Set<String> c = children.get(parent.tag);
            if (c == null) {
                c = new LinkedHashSet<String>();
                children.put(parent.tag, c);
            }
            c.add(node.tag);
        }
        for (JxmlParser.Node child : node.children) {
            collect(child, node, attrs, children);
        }
    }

    private static void collectFiles(File dir, List<File> out) {
        File[] files = dir.listFiles();
        Arrays.sort(files);
        for (File f : files) {
            if (f.isDirectory()) {
                collectFiles(f, out);
            } else if (f.getName().endsWith(".fxml")) {
                out.add(f);
            }
        }
    }

    @Test
    void everyMappedClassAttributeAndPropertyAppearsInASample() throws Exception {
        Map<String, Set<String>> attrs = new LinkedHashMap<String, Set<String>>();
        Map<String, Set<String>> children = new LinkedHashMap<String, Set<String>>();
        List<File> files = new ArrayList<File>();
        collectFiles(SAMPLES, files);
        for (File f : files) {
            InputStream in = new FileInputStream(f);
            try {
                collect(JxmlParser.parseXml(in, f.getName()), null, attrs, children);
            } finally {
                in.close();
            }
        }
        Set<String> anyAttr = new LinkedHashSet<String>();
        for (Set<String> a : attrs.values()) {
            anyAttr.addAll(a);
        }
        List<String> missing = new ArrayList<String>();
        for (FxmlTranslator.Spec spec : FxmlTranslator.CLASSES.values()) {
            if (!attrs.containsKey(spec.fxml)) {
                missing.add("<" + spec.fxml + "> is in no sample");
                continue;
            }
            for (String attr : spec.attrs.keySet()) {
                if (!attrs.get(spec.fxml).contains(attr)) {
                    missing.add("<" + spec.fxml + "> " + attr + " is in no sample");
                }
            }
            for (String prop : spec.props.keySet()) {
                if (prop.equals(spec.defaultProp)) {
                    continue; // the property of the children written directly, which the other forms show; wrapped, it is in the rows
                }
                Set<String> kids = children.get(spec.fxml);
                if (kids == null || !kids.contains(prop)) {
                    missing.add("<" + spec.fxml + "> <" + prop + "> is in no sample");
                }
            }
        }
        for (String other : FxmlTranslator.OTHER_CLASSES) {
            if (!attrs.containsKey(other)) {
                missing.add("<" + other + "> is in no sample");
            }
        }
        for (String attr : FxmlTranslator.COMMON.keySet()) {
            if (!anyAttr.contains(attr)) {
                missing.add("the common attribute " + attr + " is in no sample");
            }
        }
        for (String attr : FxmlTranslator.STATIC.keySet()) {
            if (!anyAttr.contains(attr)) {
                missing.add("the static attribute " + attr + " is in no sample");
            }
        }
        for (String name : new String[] {"fx:id", "fx:controller", "source"}) {
            if (!anyAttr.contains(name)) {
                missing.add(name + " is in no sample");
            }
        }
        if (!attrs.containsKey("fx:include")) {
            missing.add("<fx:include> is in no sample");
        }
        for (String owner : Arrays.asList("VBox")) {
            if (!attrs.containsKey(owner + ".margin")) {
                missing.add("<" + owner + ".margin> is in no sample");
            }
        }
        if (!attrs.containsKey("padding")) {
            missing.add("<padding> is in no sample");
        }
        assertEquals(Collections.<String>emptyList(), missing);
    }

    @Test
    void theSampleBuildsWhatTheMappingSays() throws Exception {
        JXElement layouts = read(new File(SAMPLES, "layouts.fxml")).build(new SampleController());
        assertEquals("border", layouts.getType());
        assertEquals(Double.valueOf(4), layouts.getProps().get("padding"));
        List<String> positions = new ArrayList<String>();
        for (JXElement child : layouts.getChildren()) {
            positions.add((String) child.getProps().get("position"));
        }
        assertEquals(Arrays.asList("top", "left", "center", "right", "bottom"), positions);
        JXElement tabs = read(new File(SAMPLES, "panes.fxml")).build(new SampleController()).getChildren().get(0);
        assertEquals("tabs", tabs.getType());
        assertArrayEquals(new boolean[] {false, false}, (boolean[]) tabs.getProps().get("closable"));
        JXElement grids = read(new File(SAMPLES, "grids.fxml")).build(new SampleController());
        JXElement label = grids.getChildren().get(0).getChildren().get(0).getChildren().get(0); // scroll > grid > label
        assertEquals(Integer.valueOf(0xFF1565C0), label.getProps().get("textFill")); // styleClass="note", from the included JXML part
        assertEquals("RIGHT", label.getProps().get("halignment"));
    }

    private static void assertArrayEquals(boolean[] expected, boolean[] actual) {
        assertTrue(Arrays.equals(expected, actual), Arrays.toString(actual));
    }

    // ---- the 20 screens of the FXML benchmark -----------------------------------------------------------------

    private static void javac(List<Path> sources, Path out, String classpath) {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertNotNull(javac, "tests need a JDK");
        List<String> args = new ArrayList<String>(Arrays.asList("-encoding", "UTF-8", "-source", "8", "-target", "8", "-nowarn",
                "-classpath", classpath, "-d", out.toString()));
        for (Path source : sources) {
            args.add(source.toString());
        }
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int status = javac.run(null, null, errors, args.toArray(new String[0]));
        assertEquals(0, status, new String(errors.toByteArray(), StandardCharsets.UTF_8));
    }

    private static String location(Class<?> type) throws Exception {
        return new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
    }

    @Test
    void theTwentyBenchmarkScreensTranslateCompileAndBuildAgainstAStubController() throws Exception {
        File[] files = CORPUS.listFiles((d, n) -> n.endsWith(".fxml"));
        assertNotNull(files, CORPUS.getAbsolutePath());
        Arrays.sort(files);
        assertEquals(20, files.length);
        Path stubs = dir.resolve("stubs");
        Path generated = dir.resolve("generated");
        Path classes = dir.resolve("classes");
        Files.createDirectories(classes);
        List<Path> stubSources = new ArrayList<Path>();
        List<Path> viewSources = new ArrayList<Path>();
        List<JxmlTemplate> templates = new ArrayList<JxmlTemplate>();
        List<String> viewNames = new ArrayList<String>();
        for (File file : files) {
            InputStream in = new FileInputStream(file);
            JxmlTemplate template;
            try {
                template = JxmlTemplate.read(in, file.getName(), JxmlLoader.directory(CORPUS));
            } finally {
                in.close();
            }
            templates.add(template);
            // the stub controller: the class the screen names, with the one handler the screens call and nothing else
            String controller = template.getController();
            assertNotNull(controller, file.getName());
            String pkg = controller.substring(0, controller.lastIndexOf('.'));
            String simple = controller.substring(controller.lastIndexOf('.') + 1);
            Path stub = stubs.resolve(controller.replace('.', '/') + ".java");
            Files.createDirectories(stub.getParent());
            Files.write(stub, ("package " + pkg + ";\npublic class " + simple + " {\n    public int calls;\n"
                    + "    public void onAction() { calls++; }\n}\n").getBytes(StandardCharsets.UTF_8));
            stubSources.add(stub);
            String name = JxmlCompiler.className(file.getName(), "View");
            viewNames.add(name);
            Path view = generated.resolve("gen/" + name + ".java");
            Files.createDirectories(view.getParent());
            Files.write(view, JxmlCompiler.compile(template, new JxmlCompiler.Options("gen", name, true)).getBytes(StandardCharsets.UTF_8));
            viewSources.add(view);
        }
        String base = location(JxmlView.class) + File.pathSeparator + location(JXElement.class);
        javac(stubSources, classes, base);
        javac(viewSources, classes, base + File.pathSeparator + classes);
        URLClassLoader loader = new URLClassLoader(new URL[] {classes.toUri().toURL()}, getClass().getClassLoader());
        for (int i = 0; i < files.length; i++) {
            String name = files[i].getName();
            Object controller = loader.loadClass(templates.get(i).getController()).getDeclaredConstructor().newInstance();
            @SuppressWarnings("unchecked")
            JxmlView<Object> compiled = (JxmlView<Object>) loader.loadClass("gen." + viewNames.get(i)).getDeclaredConstructor().newInstance();
            JXElement viaCompiler = compiled.build(controller);
            JXElement viaInterpreter = templates.get(i).build(controller);
            assertNull(JxmlTrees.diff(viaInterpreter, viaCompiler), name);
            assertEquals("border", viaCompiler.getType(), name);
            assertEquals(3, viaCompiler.getChildren().size(), name);
            assertEquals("top", viaCompiler.getChildren().get(0).getProps().get("position"), name);
            assertEquals("bottom", viaCompiler.getChildren().get(2).getProps().get("position"), name);
            // the handler of a button is the controller's method
            JXElement button = firstWith(viaCompiler, "onAction");
            assertNotNull(button, name);
            ((Runnable) button.getProps().get("onAction")).run();
            assertEquals(Integer.valueOf(1), controller.getClass().getField("calls").get(controller), name);
            // warnings: each fx:id of the file and its one handler
            int ids = 0;
            InputStream in = new FileInputStream(files[i]);
            try {
                ids = countIds(JxmlParser.parseXml(in, name));
            } finally {
                in.close();
            }
            assertEquals(ids + 1, templates.get(i).getWarnings().size(), name);
        }
    }

    private static JXElement firstWith(JXElement element, String prop) {
        if (element.getProps().get(prop) != null) {
            return element;
        }
        for (JXElement child : element.getChildren()) {
            JXElement found = firstWith(child, prop);
            if (found != null) {
                return found;
            }
        }
        Object deferred = element.getProps().get("deferredTabs");
        if (deferred instanceof JXElement[]) {
            for (JXElement child : (JXElement[]) deferred) {
                JXElement found = firstWith(child, prop);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static int countIds(JxmlParser.Node node) {
        int n = node.attrs.containsKey("fx:id") ? 1 : 0;
        for (JxmlParser.Node child : node.children) {
            n += countIds(child);
        }
        return n;
    }

    // ---- the directory mode ---------------------------------------------------------------------------------

    private void write(String name, String text) throws IOException {
        Path file = dir.resolve("src").resolve(name);
        Files.createDirectories(file.getParent());
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void aDirectoryWithFxmlAndJxmlFilesGetsOneViewForEach() throws Exception {
        write("order-form.fxml", "<VBox xmlns:fx=\"http://javafx.com/fxml\" fx:controller=\"" + CTRL + "\">\n"
                + "<Button fx:id=\"go\" text=\"Go\" onAction=\"#save\"/>\n<fx:include source=\"parts/footer.fxml\"/>\n</VBox>");
        write("parts/footer.fxml", "<HBox xmlns:fx=\"http://javafx.com/fxml\"><Label fx:id=\"foot\" text=\"Footer\"/></HBox>");
        write("home.jxml", "<Column controller=\"" + CTRL + "\"><Include src=\"order-form.fxml\"/></Column>");
        File src = dir.resolve("src").toFile();
        File out = dir.resolve("out").toFile();
        JXForge.Result first = JXForge.compileDirectory(src, out, "gen.views", "View", true);
        assertEquals(2, first.written.size(), first.written.toString());
        assertTrue(new File(out, "gen/views/OrderFormView.java").isFile());
        assertTrue(new File(out, "gen/views/HomeView.java").isFile());
        assertEquals(1, first.parts.size()); // the footer is a part of a view
        String warnings = String.join("\n", first.warnings);
        assertTrue(warnings.contains("order-form.fxml:2: ") && warnings.contains("fx:id=\"go\""), warnings);
        assertTrue(warnings.contains("parts/footer.fxml:1: ") && warnings.contains("fx:id=\"foot\""), warnings);
        assertTrue(warnings.contains("#save"), warnings);
        long distinct = first.warnings.stream().distinct().count();
        assertEquals(first.warnings.size(), distinct, "no warning twice");
        String java = new String(Files.readAllBytes(new File(out, "gen/views/OrderFormView.java").toPath()), StandardCharsets.UTF_8);
        assertTrue(java.contains("order-form.fxml:2"), "the generated code names the FXML line");
        JXForge.Result second = JXForge.compileDirectory(src, out, "gen.views", "View", true);
        assertEquals(0, second.written.size());
        assertEquals(2, second.upToDate.size());
        assertEquals(first.warnings, second.warnings, "the warnings of up to date screens are replayed");
    }

    @Test
    void anFxmlFileAndAJxmlFileOfTheSameNameShareAClassName() throws Exception {
        write("a.fxml", "<VBox xmlns:fx=\"http://javafx.com/fxml\" fx:controller=\"" + CTRL + "\"/>");
        write("a.jxml", "<Column controller=\"" + CTRL + "\"/>");
        JxmlException e = assertThrows(JxmlException.class,
                () -> JXForge.compileDirectory(dir.resolve("src").toFile(), dir.resolve("out").toFile(), "gen", "View", true));
        assertTrue(e.getMessage().contains("same class name AView"), e.getMessage());
        assertEquals("AView", JxmlCompiler.className("a.fxml", "View"));
        assertEquals("Screen01View", JxmlCompiler.className("screen01.fxml", "View"));
    }

    @Test
    void anFxmlErrorInADirectoryNamesTheFileAndLine() throws Exception {
        write("views/bad.fxml", "<VBox xmlns:fx=\"http://javafx.com/fxml\" fx:controller=\"" + CTRL + "\">\n<Label text=\"${x}\"/>\n</VBox>");
        JxmlException e = assertThrows(JxmlException.class,
                () -> JXForge.compileDirectory(dir.resolve("src").toFile(), dir.resolve("out").toFile(), "gen", "View", true));
        assertTrue(e.getMessage().startsWith("views/bad.fxml:2: "), e.getMessage());
    }

    // ---- --fxml-to-jxml ---------------------------------------------------------------------------------------------

    @Test
    void theTranslatedTreeIsPrintedAsJxml() throws Exception {
        Path fxml = dir.resolve("form.fxml");
        Files.write(fxml, ("<?xml version=\"1.0\"?>\n<VBox xmlns:fx=\"http://javafx.com/fxml\" fx:controller=\"" + CTRL + "\" spacing=\"4\">\n"
                + "<Label text=\"a &amp; b\"/>\n<Button fx:id=\"go\" text=\"Go\" onAction=\"#save\"/>\n"
                + "<fx:include source=\"parts/footer.fxml\"/>\n</VBox>").getBytes(StandardCharsets.UTF_8));
        List<String> warnings = new ArrayList<String>();
        String jxml = JXForge.fxmlToJxml(fxml.toFile(), warnings);
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Column controller=\"" + CTRL + "\" gap=\"4\">\n"
                + "  <Label text=\"a &amp; b\"/>\n"
                + "  <Button text=\"Go\" onAction=\"#save\"/>\n"
                + "  <Include src=\"parts/footer.jxml\"/>\n"
                + "</Column>\n", jxml);
        assertEquals(2, warnings.size(), warnings.toString()); // the fx:id and the handler
        // the printed text is JXML: it reads back as the same tree
        assertEquals(jxml, FxmlTranslator.print(JxmlParser.parse(stream(jxml), "form.jxml")));
    }

    @Test
    void everySamplePrintsAsJxmlThatBuildsTheSameTree() throws Exception {
        for (String name : new String[] {"panes", "menus", "lists"}) {
            File fxml = new File(SAMPLES, name + ".fxml");
            String jxml = JXForge.fxmlToJxml(fxml, null);
            Path file = dir.resolve(name + ".jxml");
            Files.write(file, jxml.getBytes(StandardCharsets.UTF_8));
            JxmlTemplate fromJxml = JxmlTemplate.read(file.toFile());
            JxmlTemplate fromFxml = read(fxml);
            for (SampleController c : states()) {
                assertNull(JxmlTrees.diff(fromFxml.build(c), fromJxml.build(c)), name);
            }
        }
    }

    @Test
    void theCommandLineWritesTheJxmlFileAndTheWarningsGoToTheConsole() throws Exception {
        File fxml = new File(SAMPLES, "menus.fxml");
        Path out = dir.resolve("menus.jxml");
        JXForge.main(new String[] {"--fxml-to-jxml", fxml.getPath(), out.toString()});
        assertEquals(JXForge.fxmlToJxml(fxml, null), new String(Files.readAllBytes(out), StandardCharsets.UTF_8));
        java.io.PrintStream previous = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(captured, true, "UTF-8"));
        try {
            JXForge.main(new String[] {"--fxml-to-jxml", fxml.getPath()});
        } finally {
            System.setOut(previous);
        }
        assertEquals(JXForge.fxmlToJxml(fxml, null), new String(captured.toByteArray(), StandardCharsets.UTF_8));
        assertFalse(captured.toString("UTF-8").contains("warning"));
    }
}
