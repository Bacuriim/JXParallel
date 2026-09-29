package com.jxparallel.fx.nativeimpl;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jxparallel.fx.collections.FXCollections;
import com.jxparallel.fx.geometry.Insets;
import com.jxparallel.fx.geometry.Pos;
import com.jxparallel.fx.scene.control.*;
import com.jxparallel.fx.scene.control.cell.PropertyValueFactory;
import com.jxparallel.fx.scene.layout.*;
import com.jxparallel.fx.scene.text.Font;
import com.jxparallel.fx.scene.text.FontWeight;
import com.jxparallel.fx.util.StringConverter;
import com.jxparallel.ui.JXElement;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each control becomes the native element and props the renderer draws (JX API in, element out). */
class NativeControlsTest extends NativeTestSupport {
    public static class Person {
        private final String name;
        private final int age;

        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public com.jxparallel.fx.beans.property.IntegerProperty ageProperty() {
            return new com.jxparallel.fx.beans.property.SimpleIntegerProperty(age);
        }
    }

    private static JXElement element(Object jx) throws Exception {
        return fx(() -> NativeElements.toElement(model(jx)));
    }

    private static Map<String, Object> props(Object jx) throws Exception {
        return element(jx).getProps().asMap();
    }

    @Test
    void labeledControlsCarryTextFontAndState() throws Exception {
        Label label = fx(() -> {
            Label l = new Label("Nome");
            l.setFont(Font.font("System", FontWeight.BOLD, 14));
            l.setUnderline(true);
            l.setWrapText(true);
            l.setAlignment(Pos.CENTER_RIGHT);
            return l;
        });
        assertEquals("#text", element(label).getType());
        assertEquals("Nome", props(label).get("value"));
        assertEquals(14.0, props(label).get("fontSize"));
        assertEquals(true, props(label).get("bold"));
        assertEquals(true, props(label).get("underline"));
        assertEquals(true, props(label).get("wrapText"));
        assertEquals("CENTER_RIGHT", props(label).get("alignment"));

        CheckBox check = fx(() -> {
            CheckBox c = new CheckBox("Ativo");
            c.setSelected(true);
            return c;
        });
        assertEquals("checkbox", element(check).getType());
        assertEquals(true, props(check).get("checked"));
        assertEquals(false, props(check).get("indeterminate"));

        RadioButton radio = fx(() -> new RadioButton("A"));
        assertEquals("radio", element(radio).getType());
        assertEquals(false, props(radio).get("checked"));

        Hyperlink link = fx(() -> new Hyperlink("Abrir"));
        assertEquals("hyperlink", element(link).getType());
        assertEquals("Abrir", props(link).get("label"));

        ToggleButton toggle = fx(() -> {
            ToggleButton t = new ToggleButton("On");
            t.setSelected(true);
            return t;
        });
        assertEquals("toggle", element(toggle).getType());
        assertEquals(true, props(toggle).get("selected"));
    }

    @Test
    void labelWithGraphicIsACellWithTheGraphicAsChild() throws Exception {
        Label label = fx(() -> new Label("Com ícone", new Label("*")));
        JXElement e = element(label);
        assertEquals("cell", e.getType());
        assertEquals("Com ícone", e.getProps().get("value"));
        assertEquals(false, e.getProps().get("paintBackground"));
        assertEquals(1, e.getChildren().size());
        assertEquals("#text", e.getChildren().get(0).getType());
    }

    @Test
    void buttonsKnowTheirDefaultRoleAndGraphic() throws Exception {
        Button button = fx(() -> {
            Button b = new Button("OK", new Label("✓"));
            b.setDefaultButton(true);
            return b;
        });
        JXElement e = element(button);
        assertEquals("button", e.getType());
        assertEquals(true, e.getProps().get("defaultButton"));
        assertEquals(1, e.getChildren().size());
    }

    @Test
    void textInputsCarryTextPromptAlignmentAndColumns() throws Exception {
        TextField field = fx(() -> {
            TextField f = new TextField("Ada");
            f.setPromptText("Nome");
            f.setAlignment(Pos.CENTER_RIGHT);
            f.setPrefColumnCount(20);
            return f;
        });
        assertEquals("input", element(field).getType());
        assertEquals("Ada", props(field).get("value"));
        assertEquals("Nome", props(field).get("prompt"));
        assertEquals("RIGHT", props(field).get("textAlignment"));
        assertEquals(20.0, props(field).get("prefColumnCount"));
        assertNull(props(field).get("caret"), "no caret without focus");

        PasswordField password = fx(() -> new PasswordField());
        assertEquals("password", element(password).getType());

        TextArea area = fx(() -> {
            TextArea a = new TextArea("a\nb");
            a.setWrapText(true);
            a.setPrefRowCount(3);
            return a;
        });
        assertEquals("textarea", element(area).getType());
        assertEquals(true, props(area).get("wrapText"));
        assertEquals(3.0, props(area).get("prefRowCount"));
    }

    @Test
    void comboBoxShowsTheConverterTextOfItsValueAndIsReadOnlyByDefault() throws Exception {
        ComboBox<Integer> combo = fx(() -> {
            ComboBox<Integer> c = new ComboBox<>(FXCollections.observableArrayList(1, 2, 3));
            c.setConverter(new StringConverter<Integer>() {
                @Override
                public String toString(Integer value) {
                    return "#" + value;
                }

                @Override
                public Integer fromString(String s) {
                    return Integer.valueOf(s.substring(1));
                }
            });
            c.setValue(2);
            c.setPromptText("Escolha");
            return c;
        });
        Map<String, Object> p = props(combo);
        assertEquals("select", element(combo).getType());
        assertEquals("#2", p.get("value"));
        assertArrayEquals(new Object[]{"#1", "#2", "#3"}, (Object[]) p.get("options"));
        assertEquals("Escolha", p.get("prompt"));
        assertNull(p.get("editable"), "ComboBox is not editable by default");
        assertEquals(false, fx(() -> combo.isEditable()));
    }

    @Test
    void choiceBoxWithoutValueShowsNothing() throws Exception {
        ChoiceBox<String> choice = fx(() -> new ChoiceBox<>(FXCollections.observableArrayList("a", "b")));
        assertEquals("select", element(choice).getType());
        assertEquals("", props(choice).get("value"));
    }

    @Test
    void spinnerAndDatePickerShowTheirValueInTheEditor() throws Exception {
        Spinner<Integer> spinner = fx(() -> new Spinner<Integer>(0, 10, 7));
        assertEquals("spinner", element(spinner).getType());
        assertEquals("7", props(spinner).get("value"));

        DatePicker picker = fx(() -> {
            java.util.Locale.setDefault(java.util.Locale.Category.FORMAT, new java.util.Locale("pt", "BR"));
            return new DatePicker(LocalDate.of(2026, 9, 28));
        });
        assertEquals("datepicker", element(picker).getType());
        assertEquals("28/09/2026", props(picker).get("value"), "short format with a four digit year");
    }

    @Test
    void progressAndSliderValues() throws Exception {
        ProgressBar bar = fx(() -> new ProgressBar(0.25));
        assertEquals(0.25, props(bar).get("progress"));
        ProgressIndicator spinning = fx(() -> new ProgressIndicator());
        assertEquals("indicator", element(spinning).getType());
        assertEquals(-1.0, props(spinning).get("progress"), "indeterminate");
        Slider slider = fx(() -> new Slider(10, 20, 15));
        assertEquals(10.0, props(slider).get("min"));
        assertEquals(20.0, props(slider).get("max"));
        assertEquals(15.0, props(slider).get("value"));
    }

    @Test
    void containersKeepTheirLayoutProps() throws Exception {
        VBox box = fx(() -> {
            VBox b = new VBox(6);
            b.setAlignment(Pos.TOP_CENTER);
            b.setPadding(new Insets(1, 2, 3, 4));
            b.getChildren().add(new Label("a"));
            return b;
        });
        Map<String, Object> p = props(box);
        assertEquals("column", element(box).getType());
        assertEquals(6.0, p.get("gap"));
        assertEquals("TOP_CENTER", p.get("alignment"));
        assertArrayEquals(new double[]{1, 2, 3, 4}, (double[]) p.get("padding"));

        BorderPane border = fx(() -> {
            BorderPane b = new BorderPane();
            b.setTop(new Label("top"));
            b.setCenter(new Label("center"));
            return b;
        });
        JXElement e = element(border);
        assertEquals("border", e.getType());
        assertEquals("top", e.getChildren().get(0).getProps().get("position"));
        assertEquals("center", e.getChildren().get(1).getProps().get("position"));
    }

    @Test
    void gridPaneAddPlacesChildrenInCells() throws Exception {
        GridPane grid = fx(() -> {
            GridPane g = new GridPane();
            g.add(new Label("a"), 1, 2);
            g.add(new Label("b"), 0, 0, 2, 3);
            g.addRow(4, new Label("c"), new Label("d"));
            g.getColumnConstraints().add(new ColumnConstraints(50));
            return g;
        });
        JXElement e = element(grid);
        assertEquals("grid", e.getType());
        assertEquals(1, e.getChildren().get(0).getProps().get("column"));
        assertEquals(2, e.getChildren().get(0).getProps().get("row"));
        assertEquals(2, e.getChildren().get(1).getProps().get("columnSpan"));
        assertEquals(3, e.getChildren().get(1).getProps().get("rowSpan"));
        assertEquals(0, e.getChildren().get(2).getProps().get("column"));
        assertEquals(1, e.getChildren().get(3).getProps().get("column"), "addRow fills the next column");
        assertEquals(4, e.getChildren().get(3).getProps().get("row"));
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> columns = (java.util.List<Map<String, Object>>) e.getProps().get("columns");
        assertEquals(50.0, columns.get(0).get("prefWidth"));
        assertEquals(Double.NEGATIVE_INFINITY, columns.get(0).get("minWidth"), "a fixed width: min and max follow the pref");
        assertEquals(Double.NEGATIVE_INFINITY, columns.get(0).get("maxWidth"));
    }

    @Test
    void regionSizeShorthandsSetBothAxes() throws Exception {
        Pane pane = fx(() -> {
            Pane p = new Pane();
            p.setPrefSize(120, 80);
            p.setMinSize(10, 20);
            p.setMaxSize(300, 400);
            return p;
        });
        Map<String, Object> p = props(pane);
        assertEquals(120.0, p.get("prefWidth"));
        assertEquals(80.0, p.get("prefHeight"));
        assertEquals(10.0, p.get("minWidth"));
        assertEquals(20.0, p.get("minHeight"));
        assertEquals(300.0, p.get("maxWidth"));
        assertEquals(400.0, p.get("maxHeight"));
    }

    @Test
    void scrollPaneTabPaneAndTitledPaneHoldTheirContent() throws Exception {
        ScrollPane scroll = fx(() -> {
            ScrollPane s = new ScrollPane(new Label("content"));
            s.setFitToWidth(true);
            s.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
            return s;
        });
        JXElement e = element(scroll);
        assertEquals("scroll", e.getType());
        assertEquals(1, e.getChildren().size());
        assertEquals(true, e.getProps().get("fitToWidth"));
        assertEquals("ALWAYS", e.getProps().get("vbarPolicy"));

        TabPane tabs = fx(() -> new TabPane(new Tab("Um", new Label("1")), new Tab("Dois", new Label("2"))));
        JXElement t = element(tabs);
        assertEquals("tabs", t.getType());
        assertArrayEquals(new String[]{"Um", "Dois"}, (String[]) t.getProps().get("titles"));
        assertEquals(0, t.getProps().get("selected"));
        assertEquals("1", t.getChildren().get(0).getProps().get("value"), "only the selected tab's content");
        fx(() -> tabs.getSelectionModel().select(1));
        assertEquals("2", element(tabs).getChildren().get(0).getProps().get("value"));

        TitledPane titled = fx(() -> new TitledPane("Dados", new Label("x")));
        JXElement tp = element(titled);
        assertEquals("titled", tp.getType());
        assertEquals("Dados", tp.getProps().get("label"));
        assertEquals(true, tp.getProps().get("expanded"));
    }

    @Test
    void accordionOpensOnlyItsExpandedPane() throws Exception {
        TitledPane a = fx(() -> new TitledPane("A", new Label("a")));
        TitledPane b = fx(() -> new TitledPane("B", new Label("b")));
        Accordion accordion = fx(() -> {
            Accordion acc = new Accordion(a, b);
            acc.setExpandedPane(b);
            return acc;
        });
        JXElement e = element(accordion);
        assertEquals(false, e.getChildren().get(0).getProps().get("expanded"));
        assertEquals(true, e.getChildren().get(1).getProps().get("expanded"));
    }

    @Test
    void listViewBuildsCellsForItsItemsWithSelection() throws Exception {
        ListView<String> list = fx(() -> {
            ListView<String> l = new ListView<>(FXCollections.observableArrayList("a", "b", "c"));
            l.getSelectionModel().select(1);
            return l;
        });
        JXElement e = element(list);
        assertEquals("list", e.getType());
        assertEquals(3, e.getProps().get("itemCount"));
        assertEquals(3, e.getChildren().size());
        assertEquals("b", e.getChildren().get(1).getProps().get("value"));
        assertEquals(true, e.getChildren().get(1).getProps().get("selected"));
        assertEquals(true, e.getChildren().get(1).getProps().get("odd"));
        assertEquals(false, e.getChildren().get(0).getProps().get("selected"));
        fx(() -> list.getItems().add("d"));
        assertEquals(4, element(list).getChildren().size(), "a change of the items list is seen");
    }

    @Test
    void listViewCellFactoryRunsUpdateItemOfTheApplicationCell() throws Exception {
        ListView<String> list = fx(() -> {
            ListView<String> l = new ListView<>(FXCollections.observableArrayList("x", "y"));
            l.setCellFactory(v -> new ListCell<String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.toUpperCase() + getIndex());
                }
            });
            return l;
        });
        JXElement e = element(list);
        assertEquals("X0", e.getChildren().get(0).getProps().get("value"));
        assertEquals("Y1", e.getChildren().get(1).getProps().get("value"));
    }

    @Test
    void tableViewCellsComeFromPropertyValueFactoryAndLambdas() throws Exception {
        TableView<Person> table = fx(() -> {
            TableView<Person> t = new TableView<>(FXCollections.observableArrayList(new Person("Ada", 36), new Person("Alan", 41)));
            TableColumn<Person, String> name = new TableColumn<>("Nome");
            name.setCellValueFactory(new PropertyValueFactory<>("name"));
            TableColumn<Person, Number> age = new TableColumn<>("Idade");
            age.setCellValueFactory(c -> c.getValue().ageProperty());
            age.setPrefWidth(60);
            t.getColumns().add(name);
            t.getColumns().add(age);
            return t;
        });
        JXElement e = element(table);
        assertEquals("table", e.getType());
        assertArrayEquals(new String[]{"Nome", "Idade"}, (String[]) e.getProps().get("columns"));
        assertArrayEquals(new double[]{-1, 60}, (double[]) e.getProps().get("columnWidths"), "auto width, explicit width");
        JXElement row = e.getChildren().get(1);
        assertEquals("tablerow", row.getType());
        assertEquals("Alan", row.getChildren().get(0).getProps().get("value"));
        assertEquals("41", row.getChildren().get(1).getProps().get("value"));
        assertEquals(true, row.getProps().get("odd"));
    }

    @Test
    void tableCellFactoryGetsTheCellValue() throws Exception {
        TableView<Person> table = fx(() -> {
            TableView<Person> t = new TableView<>(FXCollections.observableArrayList(new Person("Ada", 36)));
            TableColumn<Person, String> name = new TableColumn<>("Nome");
            name.setCellValueFactory(new PropertyValueFactory<>("name"));
            name.setCellFactory(c -> new TableCell<Person, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : "[" + item + "]");
                }
            });
            t.getColumns().add(name);
            return t;
        });
        assertEquals("[Ada]", element(table).getChildren().get(0).getChildren().get(0).getProps().get("value"));
    }

    @Test
    void constrainedResizePolicyIsSeen() throws Exception {
        TableView<Person> table = fx(() -> {
            TableView<Person> t = new TableView<>();
            t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            return t;
        });
        assertEquals(true, props(table).get("constrained"));
    }

    @Test
    void paginationShowsThePageFromItsFactory() throws Exception {
        Pagination pages = fx(() -> {
            Pagination p = new Pagination(5, 2);
            p.setPageFactory(i -> new Label("page " + i));
            return p;
        });
        JXElement e = element(pages);
        assertEquals("pagination", e.getType());
        assertEquals(5, e.getProps().get("pageCount"));
        assertEquals(2, e.getProps().get("current"));
        assertEquals("page 2", e.getChildren().get(0).getProps().get("value"));
    }

    @Test
    void imageViewReadsThePixelsOnce() throws Exception {
        com.jxparallel.fx.scene.image.ImageView view = fx(() -> {
            com.jxparallel.fx.scene.image.WritableImage image = new com.jxparallel.fx.scene.image.WritableImage(3, 2);
            image.getPixelWriter().setArgb(1, 1, 0xFF112233);
            com.jxparallel.fx.scene.image.ImageView v = new com.jxparallel.fx.scene.image.ImageView(image);
            v.setFitWidth(30);
            v.setPreserveRatio(true);
            return v;
        });
        Map<String, Object> p = props(view);
        assertEquals("image", element(view).getType());
        assertEquals(3.0, p.get("imageWidth"));
        assertEquals(2.0, p.get("imageHeight"));
        assertEquals(0xFF112233, ((int[]) p.get("pixels"))[4]);
        assertSame(p.get("pixels"), props(view).get("pixels"), "cached");
        assertEquals(true, p.get("preserveRatio"));
        assertEquals(30.0, p.get("fitWidth"));
    }

    @Test
    void invisibleAndDisabledAndUnmanagedNodes() throws Exception {
        Label hidden = fx(() -> {
            Label l = new Label("h");
            l.setVisible(false);
            return l;
        });
        assertEquals(true, props(hidden).get("hidden"));
        Button disabled = fx(() -> {
            Button b = new Button("d");
            b.setDisable(true);
            return b;
        });
        assertEquals(true, props(disabled).get("disabled"));
        VBox box = fx(() -> {
            Label gone = new Label("gone");
            gone.setManaged(false);
            return new VBox(gone, new Label("kept"));
        });
        assertEquals(1, element(box).getChildren().size());
        assertEquals("kept", element(box).getChildren().get(0).getProps().get("value"));
    }

    @Test
    void elementsKnowTheirModel() throws Exception {
        Label label = fx(() -> new Label("m"));
        assertSame(model(label), props(label).get("model"));
    }

    @Test
    void blurEffectBecomesABlurRadius() throws Exception {
        Label label = fx(() -> {
            Label l = new Label("b");
            l.setEffect(new com.jxparallel.fx.scene.effect.GaussianBlur(6));
            l.setOpacity(0.5);
            return l;
        });
        assertEquals(6.0, props(label).get("blur"));
        assertEquals(0.5, props(label).get("opacity"));
    }

    @Test
    void unknownNodesAreEmptyPanesAndReported() throws Exception {
        MenuBar bar = fx(() -> new MenuBar());
        assertEquals("pane", element(bar).getType());
        assertTrue(NativeElements.unsupported().contains("javafx.scene.control.MenuBar"));
    }
}
