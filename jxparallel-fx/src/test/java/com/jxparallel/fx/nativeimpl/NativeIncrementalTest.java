package com.jxparallel.fx.nativeimpl;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

import com.jxparallel.fx.beans.property.SimpleStringProperty;
import com.jxparallel.fx.beans.property.StringProperty;
import com.jxparallel.fx.collections.FXCollections;
import com.jxparallel.fx.scene.control.*;
import com.jxparallel.fx.scene.control.cell.PropertyValueFactory;
import com.jxparallel.fx.scene.layout.HBox;
import com.jxparallel.fx.scene.layout.VBox;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.native2d.JXInputEvent;
import com.jxparallel.ui.native2d.JXNativeNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Incremental rendering: a frame rebuilds only the elements of nodes that changed (and their
 * ancestors), like JavaFX syncing its dirty nodes; everything a node shows still reaches the screen.
 */
class NativeIncrementalTest extends NativeTestSupport {

    private static JXElement element(Object jx) {
        return model(jx).element;
    }

    @Test
    void anUnchangedFrameReusesEveryElementAndAChangeRebuildsOnlyItsPath() throws Exception {
        Label[] l = new Label[3];
        VBox[] boxes = new VBox[2];
        NativeScene s = show(fx(() -> {
            l[0] = new Label("a");
            l[1] = new Label("b");
            l[2] = new Label("c");
            boxes[0] = new VBox(l[0], l[1]);
            boxes[1] = new VBox(l[2]);
            return new HBox(boxes[0], boxes[1]);
        }), 300, 100);
        settle();
        JXElement a = fx(() -> element(l[0]));
        JXElement b = fx(() -> element(l[1]));
        JXElement c = fx(() -> element(l[2]));
        JXElement left = fx(() -> element(boxes[0]));
        JXElement right = fx(() -> element(boxes[1]));
        fx(() -> s.render());
        assertSame(a, fx(() -> element(l[0])), "nothing changed: the same element");
        assertSame(left, fx(() -> element(boxes[0])));

        fx(() -> l[0].setText("changed"));
        settle();
        assertNotSame(a, fx(() -> element(l[0])), "the changed label is rebuilt");
        assertNotSame(left, fx(() -> element(boxes[0])), "and its parent, which holds its element");
        assertSame(b, fx(() -> element(l[1])), "its sibling is not");
        assertSame(c, fx(() -> element(l[2])), "nor another branch");
        assertSame(right, fx(() -> element(boxes[1])));
        assertEquals("changed", node(s, l[0]).getProperty("value"), "and the screen shows the change");
        close(s);
    }

    private static File sheet(String css) throws Exception {
        File file = File.createTempFile("jxinc", ".css");
        file.deleteOnExit();
        Files.write(file.toPath(), css.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    void aParentClassRestylesItsDescendants() throws Exception {
        File css = sheet(".on .label { -fx-text-fill: #ff0000; }");
        Label[] label = new Label[1];
        VBox[] box = new VBox[1];
        NativeScene s = show(fx(() -> {
            label[0] = new Label("x");
            box[0] = new VBox(new HBox(label[0]));
            box[0].getStylesheets().add(css.toURI().toString());
            return box[0];
        }), 300, 100);
        assertTrue(node(s, label[0]).getProperty("textFill") == null);
        fx(() -> box[0].getStyleClass().add("on"));
        settle();
        assertEquals(0xFFFF0000, node(s, label[0]).getProperty("textFill"), "a descendant selector follows the class");
        close(s);
    }

    @Test
    void hoveringAParentRestylesItsDescendantsAndLeavingRestoresThem() throws Exception {
        File css = sheet(".box:hover .label { -fx-text-fill: #00ff00; }");
        Label[] label = new Label[1];
        VBox[] box = new VBox[1];
        NativeScene s = show(fx(() -> {
            label[0] = new Label("x");
            box[0] = new VBox(label[0]);
            box[0].getStyleClass().add("box");
            box[0].setPrefSize(200, 60);
            VBox root = new VBox(box[0]);
            root.getStylesheets().add(css.toURI().toString());
            return root;
        }), 300, 200);
        JXNativeNode b = node(s, box[0]);
        move(s, b.getX() + 150, b.getY() + 40, false);
        settle();
        assertEquals(0xFF00FF00, node(s, label[0]).getProperty("textFill"));
        move(s, 290, 190, false);
        settle();
        assertTrue(node(s, label[0]).getProperty("textFill") == null, "back to the default");
        close(s);
    }

    @Test
    void aNodeMovedUnderAnotherParentTakesItsStyles() throws Exception {
        File css = sheet(".on .label { -fx-text-fill: #0000ff; }");
        Label[] label = new Label[1];
        VBox[] boxes = new VBox[2];
        NativeScene s = show(fx(() -> {
            label[0] = new Label("x");
            boxes[0] = new VBox(label[0]);
            boxes[1] = new VBox();
            boxes[1].getStyleClass().add("on");
            HBox root = new HBox(boxes[0], boxes[1]);
            root.getStylesheets().add(css.toURI().toString());
            return root;
        }), 300, 100);
        node(s, label[0]);
        fx(() -> boxes[1].getChildren().add(label[0]));
        settle();
        assertEquals(0xFF0000FF, node(s, label[0]).getProperty("textFill"));
        assertEquals(0, (int) fx(() -> boxes[0].getChildren().size()), "moved, not copied, like JavaFX");
        close(s);
    }

    @Test
    void disablingAParentRestylesTheDisabledNodesBelow() throws Exception {
        File css = sheet(".button:disabled { -fx-text-fill: #ff0000; }");
        Button[] button = new Button[1];
        VBox[] box = new VBox[1];
        NativeScene s = show(fx(() -> {
            button[0] = new Button("b");
            box[0] = new VBox(new HBox(button[0]));
            box[0].getStylesheets().add(css.toURI().toString());
            return box[0];
        }), 300, 100);
        assertTrue(node(s, button[0]).getProperty("textFill") == null);
        fx(() -> box[0].setDisable(true));
        settle();
        assertEquals(true, node(s, box[0]).getProperty("disabled"), "the disabled parent fades its subtree");
        assertEquals(0xFFFF0000, node(s, button[0]).getProperty("textFill"), ":disabled holds below it too");
        fx(() -> box[0].setDisable(false));
        settle();
        assertTrue(node(s, button[0]).getProperty("textFill") == null);
        close(s);
    }

    /** A row whose name changes after the table showed it. */
    public static class Item {
        private final StringProperty name = new SimpleStringProperty();

        Item(String n) {
            name.set(n);
        }

        public StringProperty nameProperty() {
            return name;
        }
    }

    @Test
    void aTableCellFollowsItsRowPropertyLikeJavaFx() throws Exception {
        TableView<Item>[] table = new TableView[1];
        Item[] item = new Item[1];
        NativeScene s = show(fx(() -> {
            item[0] = new Item("antes");
            table[0] = new TableView<>(FXCollections.observableArrayList(item[0], new Item("outro")));
            TableColumn<Item, Object> c = new TableColumn<>("Nome");
            c.setCellValueFactory(new PropertyValueFactory<>("name"));
            table[0].getColumns().add(c);
            return new VBox(table[0]);
        }), 300, 200);
        assertEquals("antes", node(s, table[0]).getChildren().get(0).getChildren().get(0).getProperty("value"));
        fx(() -> item[0].nameProperty().set("depois"));
        settle();
        assertEquals("depois", node(s, table[0]).getChildren().get(0).getChildren().get(0).getProperty("value"));
        assertEquals("outro", node(s, table[0]).getChildren().get(1).getChildren().get(0).getProperty("value"));
        close(s);
    }

    @Test
    void aCellPointingAtItsTableLeavesTheTableRowsAlone() throws Exception {
        TableView<Item>[] table = new TableView[1];
        NativeScene s = show(fx(() -> {
            table[0] = new TableView<>(FXCollections.observableArrayList(new Item("a"), new Item("b")));
            TableColumn<Item, Object> c = new TableColumn<>("Nome");
            c.setCellValueFactory(new PropertyValueFactory<>("name"));
            table[0].getColumns().add(c);
            return new VBox(table[0]);
        }), 300, 200);
        node(s, table[0]);
        NativeModel row = fx(() -> (NativeModel) ((java.util.Map<?, ?>) model(table[0]).state.get("cells")).get(0));
        long version = fx(() -> row.version);
        // a new cell refers to the table, as the runtime sets it: the table did not move, its rows stay built
        fx(() -> {
            Native.property(model(new TableRow<Item>()), "tableView", Object.class).setValue(model(table[0]));
            Native.property(model(new TableCell<Item, Object>()), "tableView", Object.class).setValue(model(table[0]));
            return null;
        });
        assertEquals(version, (long) fx(() -> row.version));
        fx(() -> table[0].getStyleClass().add("other")); // a class of the table still reaches its rows
        assertTrue(fx(() -> row.version) > version);
        close(s);
    }

    private static TableView<Item> table(int rows) {
        java.util.List<Item> items = new java.util.ArrayList<>();
        for (int i = 0; i < rows; i++) {
            items.add(new Item("r" + i));
        }
        TableView<Item> t = new TableView<>(FXCollections.observableArrayList(items));
        TableColumn<Item, Object> c = new TableColumn<>("Nome");
        c.setCellValueFactory(new PropertyValueFactory<>("name"));
        t.getColumns().add(c);
        return t;
    }

    @Test
    void aScrolledTableIsBuiltOnceNotAgainOnTheNextFrame() throws Exception {
        TableView<Item>[] table = new TableView[1];
        NativeScene s = show(fx(() -> {
            table[0] = table(500);
            return new VBox(table[0]);
        }), 300, 200);
        node(s, table[0]);
        fx(() -> {
            table[0].scrollTo(200);
            s.render(); // one frame: its cells scroll in (new index, item, selection)
            NativeModel t = model(table[0]);
            assertEquals(t.version, t.elementVersion, "setting up its cells did not make the table stale");
            return null;
        });
        settle();
        assertEquals("r200", node(s, table[0]).getChildren().get(0).getChildren().get(0).getProperty("value"));
        close(s);
    }

    @Test
    void aNumberPropertyReportsEveryChangeWithoutBeingRead() throws Exception {
        fx(() -> {
            NativeModel m = model(new Label("x"));
            javafx.beans.property.Property<Object> index = Native.property(m, "index", int.class);
            long v = m.version;
            index.setValue(1);
            long step = m.version - v;
            assertTrue(step > 0);
            index.setValue(2);
            assertEquals(v + 2 * step, m.version, "each change, although nobody read the value between them");
            index.setValue(2);
            assertEquals(v + 2 * step, m.version, "the same value again changes nothing");
            javafx.beans.property.Property<Object> flag = Native.property(m, "selected", boolean.class);
            flag.setValue(true);
            flag.setValue(false);
            assertTrue(m.version >= v + 4);
            return null;
        });
    }

    /** A value factory subclass that decorates the value: its call() is honoured, like JavaFX. */
    public static class Shouting extends PropertyValueFactory<Item, Object> {
        public Shouting() {
            super("name");
        }

        @Override
        public com.jxparallel.fx.beans.value.ObservableValue<Object> call(TableColumn.CellDataFeatures<Item, Object> f) {
            return new com.jxparallel.fx.beans.property.SimpleObjectProperty<>(f.getValue().nameProperty().get().toUpperCase());
        }
    }

    @Test
    void aValueFactorySubclassIsCalled() throws Exception {
        TableView<Item>[] table = new TableView[1];
        NativeScene s = show(fx(() -> {
            table[0] = table(3);
            ((TableColumn<Item, Object>) table[0].getColumns().get(0)).setCellValueFactory(new Shouting());
            return new VBox(table[0]);
        }), 300, 200);
        assertEquals("R1", node(s, table[0]).getChildren().get(1).getChildren().get(0).getProperty("value"));
        close(s);
    }

    @Test
    void oddAndEvenRowsInOneFrameGetTheirOwnStyles() throws Exception {
        File css = sheet(".table-row-cell:odd .table-cell { -fx-text-fill: #ff0000; }"
                + " .table-row-cell:selected .table-cell { -fx-text-fill: #0000ff; }");
        TableView<Item>[] table = new TableView[1];
        NativeScene s = show(fx(() -> {
            table[0] = table(6);
            table[0].getSelectionModel().select(2);
            VBox root = new VBox(table[0]);
            root.getStylesheets().add(css.toURI().toString());
            return root;
        }), 300, 300);
        JXNativeNode t = node(s, table[0]);
        assertTrue(t.getChildren().get(0).getChildren().get(0).getProperty("textFill") == null);
        assertEquals(0xFFFF0000, t.getChildren().get(1).getChildren().get(0).getProperty("textFill"));
        assertEquals(0xFF0000FF, t.getChildren().get(2).getChildren().get(0).getProperty("textFill"), "selected");
        assertEquals(0xFFFF0000, t.getChildren().get(3).getChildren().get(0).getProperty("textFill"));
        assertTrue(t.getChildren().get(4).getChildren().get(0).getProperty("textFill") == null);
        close(s);
    }

    @Test
    void switchingTabsKeepsEachFormBuiltAndItsNativeNodes() throws Exception {
        TabPane[] tabs = new TabPane[1];
        Label[] labels = new Label[2];
        NativeScene s = show(fx(() -> {
            labels[0] = new Label("um");
            labels[1] = new Label("dois");
            tabs[0] = new TabPane(new Tab("A", new VBox(labels[0], new Button("b"))), new Tab("B", new VBox(labels[1])));
            return new VBox(tabs[0]);
        }), 300, 200);
        JXNativeNode shown = node(s, labels[0]);
        JXElement built = fx(() -> element(labels[0]));
        long version = fx(() -> model(labels[0]).version);
        fx(() -> {
            tabs[0].getSelectionModel().select(1);
            return null;
        });
        settle();
        assertEquals("dois", node(s, labels[1]).getProperty("value"));
        assertEquals(version, (long) fx(() -> model(labels[0]).version), "leaving a tab does not touch its form");
        fx(() -> tabs[0].getTabs().get(0).getStyleClass().add("other"));
        assertEquals(version, (long) fx(() -> model(labels[0]).version), "nor does restyling the tab: it is not the form's CSS parent");
        fx(() -> {
            tabs[0].getSelectionModel().select(0);
            return null;
        });
        settle();
        assertSame(built, fx(() -> element(labels[0])), "coming back: the same element");
        assertSame(shown, node(s, labels[0]), "and the same native node");
        fx(() -> tabs[0].getTabs().get(0).setDisable(true));
        assertTrue(fx(() -> model(labels[0]).version) > version, "disabling a tab still reaches its form");
        close(s);
    }

    @Test
    void theOtherTabsAreBuiltWhenIdleSoTheFirstSwitchFindsThemReady() throws Exception {
        TabPane[] tabs = new TabPane[1];
        Label[] hidden = new Label[1];
        VBox[] content = new VBox[1];
        NativeScene s = show(fx(() -> {
            hidden[0] = new Label("depois");
            content[0] = new VBox(hidden[0]);
            tabs[0] = new TabPane(new Tab("A", new VBox(new Label("antes"))), new Tab("B", content[0]));
            return new VBox(tabs[0]);
        }), 300, 200);
        node(s, tabs[0]);
        assertTrue(fx(() -> model(hidden[0]).element) == null, "the first frame comes without the other tab");
        long until = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < until && fx(() -> model(hidden[0]).element) == null) {
            Thread.sleep(50);
            settle();
        }
        JXElement ahead = fx(() -> element(hidden[0]));
        assertTrue(ahead != null, "built once the window was idle");
        assertTrue(fx(() -> s.nodes.get(model(tabs[0])).holds(model(content[0]))), "its native nodes kept by the tab pane");
        fx(() -> {
            tabs[0].getSelectionModel().select(1);
            return null;
        });
        settle();
        assertSame(ahead, fx(() -> element(hidden[0])), "switching reuses what was built ahead");
        assertEquals("depois", node(s, hidden[0]).getProperty("value"));
        close(s);
    }

    @Test
    void aSpinnerShowsValuesSetOnItsFactory() throws Exception {
        Spinner<Integer>[] spinner = new Spinner[1];
        NativeScene s = show(fx(() -> {
            spinner[0] = new Spinner<>(0, 100, 5);
            return new VBox(spinner[0]);
        }), 300, 100);
        assertEquals("5", node(s, spinner[0]).getProperty("value"));
        fx(() -> spinner[0].getValueFactory().setValue(42));
        settle();
        assertEquals("42", node(s, spinner[0]).getProperty("value"));
        close(s);
    }

    @Test
    void tabTitlesComboEditorsAndFocusFollowTheirChanges() throws Exception {
        TabPane[] tabs = new TabPane[1];
        ComboBox<String>[] combo = new ComboBox[1];
        Button[] after = new Button[1];
        NativeScene s = show(fx(() -> {
            tabs[0] = new TabPane(new Tab("um", new Label("1")), new Tab("dois", new Label("2")));
            combo[0] = new ComboBox<>(FXCollections.observableArrayList("a"));
            combo[0].setEditable(true);
            after[0] = new Button("depois");
            return new VBox(tabs[0], combo[0], after[0]);
        }), 300, 300);
        fx(() -> tabs[0].getTabs().get(1).setText("dois!"));
        settle();
        assertEquals("dois!", ((String[]) node(s, tabs[0]).getProperty("titles"))[1], "an unselected tab's title");
        fx(() -> combo[0].requestFocus());
        type(s, "xyz");
        assertEquals("xyz", node(s, combo[0]).getProperty("value"), "the combo box shows its editor's text");
        assertEquals(3, node(s, combo[0]).getProperty("caret"));
        fx(() -> tabs[0].getTabs().get(0).setText("um!")); // an unrelated change, then Tab from the cached order
        key(s, "TAB");
        assertTrue(fx(() -> after[0].isFocused()), "the focus order survives cached subtrees");
        fx(() -> NativeEvents.handle(s, JXInputEvent.window(JXInputEvent.Kind.FOCUS_LOST, 0, 0)));
        assertTrue(node(s, after[0]).getProperty("focused") == null, "an unfocused window shows no focus");
        fx(() -> NativeEvents.handle(s, JXInputEvent.window(JXInputEvent.Kind.FOCUS_GAINED, 0, 0)));
        assertEquals(true, node(s, after[0]).getProperty("focused"));
        close(s);
    }

    @Test
    void stateWritesInvalidateUnlessQuietOrUnchanged() throws Exception {
        fx(() -> {
            NativeModel m = model(new Label("x"));
            long v = m.version;
            m.state.put("x", 3.0);
            m.state.put("renderParent", m);
            m.state.remove("renderParent");
            m.state.remove("x");
            assertEquals(v, m.version, "layout results and the render parent are quiet");
            m.state.remove("absent");
            assertEquals(v, m.version, "removing nothing changes nothing");
            m.state.put("caret", 1);
            assertEquals(v + 1, m.version);
            m.state.put("caret", 1);
            assertEquals(v + 1, m.version, "the same value again changes nothing");
            m.state.remove("caret");
            assertEquals(v + 2, m.version, "removing a shown value invalidates");
            return null;
        });
    }

    @Test
    void aReplacedValueFactoryNoLongerDrivesTheSpinner() throws Exception {
        Spinner<Integer>[] spinner = new Spinner[1];
        NativeScene s = show(fx(() -> {
            spinner[0] = new Spinner<>(0, 100, 5);
            return new VBox(spinner[0]);
        }), 300, 100);
        node(s, spinner[0]);
        SpinnerValueFactory<Integer> old = fx(() -> spinner[0].getValueFactory());
        fx(() -> spinner[0].setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 10, 7)));
        assertEquals("7", node(s, spinner[0]).getProperty("value"));
        long version = fx(() -> model(spinner[0]).version);
        fx(() -> old.setValue(99));
        assertEquals(version, (long) fx(() -> model(spinner[0]).version), "the old factory is not listened to any more");
        assertEquals("7", node(s, spinner[0]).getProperty("value"));
        close(s);
    }
}
