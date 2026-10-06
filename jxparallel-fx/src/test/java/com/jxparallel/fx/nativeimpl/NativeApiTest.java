package com.jxparallel.fx.nativeimpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.jxparallel.fx.collections.FXCollections;
import com.jxparallel.fx.event.ActionEvent;
import com.jxparallel.fx.event.EventHandler;
import com.jxparallel.fx.geometry.Bounds;
import com.jxparallel.fx.geometry.Point2D;
import com.jxparallel.fx.scene.Scene;
import com.jxparallel.fx.scene.control.*;
import com.jxparallel.fx.scene.control.cell.MapValueFactory;
import com.jxparallel.fx.scene.input.MouseEvent;
import com.jxparallel.fx.scene.layout.GridPane;
import com.jxparallel.fx.scene.layout.Pane;
import com.jxparallel.fx.scene.layout.VBox;
import com.jxparallel.fx.stage.Stage;
import com.jxparallel.ui.native2d.JXNativeNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The JavaFX API that application code calls directly, in native mode. */
class NativeApiTest extends NativeTestSupport {

    private static Object grid(Object node, String key) {
        return model(node).constraints.get("GridPane." + key);
    }

    @Test
    void gridPaneRowsColumnsAndConstraints() throws Exception {
        fx(() -> {
            GridPane grid = new GridPane();
            Label wide = new Label("wide");
            grid.add(wide, 0, 0, 2, 1);
            Label a = new Label("a");
            Label b = new Label("b");
            grid.addRow(0, a, b);
            assertEquals(2, grid(a, "columnIndex"), "after the two columns the wide label takes");
            assertEquals(3, grid(b, "columnIndex"));
            Label tall = new Label("tall");
            grid.add(tall, 5, 1, 1, 3);
            Label c = new Label("c");
            grid.addColumn(5, c);
            assertEquals(4, grid(c, "rowIndex"), "below the three rows of the tall label");
            Label d = new Label("d");
            grid.addRow(2, d);
            assertEquals(6, grid(d, "columnIndex"), "row 2 is crossed by the tall label in column 5");
            Label e = new Label("e");
            grid.addColumn(9, e);
            assertEquals(0, grid(e, "rowIndex"), "an empty column starts at row 0");
            Label f = new Label("f");
            GridPane.setConstraints(f, 3, 4);
            assertEquals(3, grid(f, "columnIndex"));
            assertEquals(4, grid(f, "rowIndex"));
            GridPane.setConstraints(f, 1, 2, 3, 4);
            assertEquals(3, grid(f, "columnSpan"));
            assertEquals(4, grid(f, "rowSpan"));
            GridPane.clearConstraints(f);
            assertNull(grid(f, "columnIndex"));
            assertNull(grid(f, "rowSpan"));
            assertEquals(7, grid.getChildren().size(), "f was only constrained, never added");
            return null;
        });
    }

    @Test
    void paginationChecksLikeJavaFx8() throws Exception {
        // the expected values are what JavaFX 8u51 does with the same calls
        fx(() -> {
            Pagination p = new Pagination();
            List<Number> seen = new ArrayList<>();
            p.currentPageIndexProperty().addListener((o, a, b) -> seen.add(b));
            p.setPageCount(1);
            p.setCurrentPageIndex(1);
            assertEquals(0, p.getCurrentPageIndex(), "the index stays within the pages");
            assertTrue(seen.isEmpty(), "listeners never see the index outside the pages (an application may load on each change)");
            p.setCurrentPageIndex(-3);
            assertEquals(0, p.getCurrentPageIndex());
            p.setPageCount(5);
            p.setCurrentPageIndex(4);
            p.setPageCount(2);
            assertEquals(4, p.getCurrentPageIndex(), "lowering the count keeps the index");
            p.setPageCount(0);
            assertEquals(2, p.getPageCount(), "a count below 1 is ignored");
            return null;
        });
    }

    @Test
    void addingAChildTwiceThrowsAndLeavesTheListAsJavaFxDoes() throws Exception {
        // an application that adds the same label again: JavaFX throws and the view never switches
        fx(() -> {
            Label error = new Label("erro");
            Label other = new Label("outro");
            VBox box = new VBox(error);
            try {
                box.getChildren().add(error);
                throw new AssertionError("no exception");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().startsWith("Children: duplicate children added"));
            }
            try {
                box.getChildren().addAll(other, other);
                throw new AssertionError("no exception");
            } catch (IllegalArgumentException expected) {
                // refused as a whole
            }
            assertEquals(Arrays.asList(error), new ArrayList<>(box.getChildren()));
            box.getChildren().setAll(other, error); // what is replaced may come back
            assertEquals(Arrays.asList(other, error), new ArrayList<>(box.getChildren()));
            box.getChildren().set(0, other); // the same child at its own place
            return null;
        });
    }

    @Test
    void aTabsDisableIsCopiedToItsContentLikeJavaFx8() throws Exception {
        fx(() -> {
            VBox content = new VBox();
            Tab tab = new Tab("t", content);
            new TabPane(tab, new Tab("other"));
            tab.setDisable(true);
            assertTrue(content.isDisable(), "Tab.updateDisabled sets the content's own disable");
            tab.setDisable(false);
            assertFalse(content.isDisable());
            content.setDisable(true);
            tab.setContent(new VBox());
            tab.setContent(content);
            assertFalse(content.isDisable(), "setting the content applies the tab's state");
            return null;
        });
    }

    @Test
    void spinnerEditorFollowsValueChangesOnlyLikeJavaFx8() throws Exception {
        // JavaFX 8u51: setValue to the same value leaves a cleared editor empty, also after showing
        Spinner<Integer> spinner = fx(() -> {
            Spinner<Integer> s = new Spinner<>(0, 100, 5);
            assertEquals("5", s.getEditor().getText());
            s.getValueFactory().setValue(0);
            assertEquals("0", s.getEditor().getText());
            s.getValueFactory().setValue(0);
            s.getEditor().clear();
            return s;
        });
        Stage stage = fx(() -> {
            Stage st = new Stage();
            st.setScene(new Scene(new VBox(spinner), 200, 100));
            st.show();
            return st;
        });
        assertEquals("", fx(() -> spinner.getEditor().getText()), "showing it does not refill the editor");
        fx(() -> {
            spinner.getValueFactory().setValue(7);
            assertEquals("7", spinner.getEditor().getText());
            stage.hide();
            return null;
        });
    }

    @Test
    void borderPaneRegionsAreItsChildren() throws Exception {
        fx(() -> {
            com.jxparallel.fx.scene.layout.BorderPane pane = new com.jxparallel.fx.scene.layout.BorderPane();
            Button a = new Button("a");
            Button b = new Button("b");
            pane.setCenter(a);
            pane.setTop(b);
            assertEquals(Arrays.asList(a, b), new ArrayList<>(pane.getChildrenUnmodifiable()));
            assertSame(pane, a.getParent());
            pane.setCenter(null);
            assertEquals(Arrays.asList(b), new ArrayList<>(pane.getChildrenUnmodifiable()));
            return null;
        });
    }

    @Test
    void comboSelectionIsTheNewValueInTheApplicationsValueListener() throws Exception {
        fx(() -> {
            ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList("a", "b"));
            List<Object> seen = new ArrayList<>();
            combo.valueProperty().addListener((o, before, after) -> seen.add(combo.getSelectionModel().getSelectedItem()));
            combo.setValue("b");
            assertEquals(Arrays.asList("b"), seen);
            return null;
        });
    }

    @Test
    void aSelectionModelTheApplicationSetsIsTheOneItGetsBack() throws Exception {
        fx(() -> {
            ListView<String> list = new ListView<>(FXCollections.observableArrayList("a", "b"));
            MultipleSelectionModel<String> none = new MultipleSelectionModel<String>() {
                @Override public com.jxparallel.fx.collections.ObservableList<Integer> getSelectedIndices() { return FXCollections.emptyObservableList(); }
                @Override public com.jxparallel.fx.collections.ObservableList<String> getSelectedItems() { return FXCollections.emptyObservableList(); }
                @Override public void selectIndices(int index, int... indices) { }
                @Override public void selectAll() { }
                @Override public void selectFirst() { }
                @Override public void selectLast() { }
                @Override public void clearAndSelect(int index) { }
                @Override public void select(int index) { }
                @Override public void select(String obj) { }
                @Override public void clearSelection(int index) { }
                @Override public void clearSelection() { }
                @Override public boolean isSelected(int index) { return false; }
                @Override public boolean isEmpty() { return true; }
                @Override public void selectPrevious() { }
                @Override public void selectNext() { }
            };
            list.setSelectionModel(none);
            assertSame(none, list.getSelectionModel());
            new Scene(new VBox(list), 200, 200).getRoot().layout(); // the cell factory runs and asks for it again
            assertSame(none, list.getSelectionModel());
            return null;
        });
    }

    @Test
    void columnCellDataAndTable() throws Exception {
        fx(() -> {
            @SuppressWarnings("rawtypes")
            TableView<Map> table = new TableView<>();
            @SuppressWarnings("rawtypes")
            TableColumn<Map, Object> name = new TableColumn<>("Nome");
            name.setCellValueFactory(new MapValueFactory<>("nome"));
            table.getColumns().add(name);
            Map<String, Object> row = new HashMap<>();
            row.put("nome", "Device");
            table.getItems().add(row);
            Stage stage = new Stage();
            // inside a TitledPane, as a typical record list
            stage.setScene(new Scene(new VBox(new TitledPane("Fichas", new VBox(table))), 200, 100));
            stage.show();
            assertSame(table, name.getTableView());
            assertEquals("Device", name.getCellData(0));
            assertEquals("Device", name.getCellData(row));
            assertNull(name.getCellData(5), "outside the items");
            stage.hide();
            return null;
        });
    }

    @Test
    void textInputMethods() throws Exception {
        fx(() -> {
            TextField f = new TextField("meio");
            f.appendText(" fim");
            assertEquals("meio fim", f.getText());
            f.insertText(0, "o ");
            assertEquals("o meio fim", f.getText());
            f.selectAll();
            assertEquals("o meio fim", f.getSelectedText());
            f.deselect();
            assertEquals("", f.getSelectedText());
            f.home();
            assertEquals(0, f.getCaretPosition());
            f.end();
            assertEquals(10, f.getCaretPosition());
            f.selectRange(2, 6);
            assertEquals("meio", f.getSelectedText());
            f.copy();
            f.cut();
            assertEquals("o  fim", f.getText());
            f.positionCaret(6);
            f.paste();
            assertEquals("o  fimmeio", f.getText());
            f.clear();
            assertEquals("", f.getText());
            return null;
        });
    }

    @Test
    void fireMethodsAndToggleGroups() throws Exception {
        fx(() -> {
            AtomicInteger actions = new AtomicInteger();
            EventHandler<ActionEvent> count = e -> actions.incrementAndGet();
            Button button = new Button("b");
            button.setOnAction(count);
            button.fire();
            Hyperlink link = new Hyperlink("l");
            link.setOnAction(count);
            link.fire();
            assertTrue(link.isVisited());
            CheckBox check = new CheckBox("c");
            check.setOnAction(count);
            check.fire();
            assertTrue(check.isSelected());
            ToggleButton toggle = new ToggleButton("t");
            toggle.fire();
            assertTrue(toggle.isSelected());
            ToggleGroup group = new ToggleGroup();
            RadioButton r1 = new RadioButton("1");
            RadioButton r2 = new RadioButton("2");
            r1.setToggleGroup(group);
            r2.setToggleGroup(group);
            r1.fire();
            assertTrue(r1.isSelected());
            r1.fire();
            assertTrue(r1.isSelected(), "a selected radio button stays selected");
            group.selectToggle(r2);
            assertTrue(r2.isSelected());
            assertFalse(r1.isSelected());
            assertSame(r2, group.getSelectedToggle());
            r2.setToggleGroup(null);
            r1.setSelected(true);
            assertTrue(r2.isSelected(), "a toggle that left the group is not cleared");
            assertEquals(3, actions.get());
            return null;
        });
    }

    @Test
    void spinnerStepsAndItsEditorFollowsAtOnce() throws Exception {
        fx(() -> {
            Spinner<Integer> spinner = new Spinner<>(0, 10, 5);
            spinner.increment();
            assertEquals(6, (int) spinner.getValue());
            assertEquals("6", spinner.getEditor().getText());
            spinner.increment(3);
            assertEquals(9, (int) spinner.getValue());
            spinner.decrement(2);
            assertEquals(7, (int) spinner.getValue());
            spinner.decrement();
            assertEquals(6, (int) spinner.valueProperty().getValue());
            assertEquals("6", spinner.getEditor().getText());
            return null;
        });
    }

    @Test
    void nodePropertiesRelocateAndHandlerRemoval() throws Exception {
        fx(() -> {
            Pane pane = new Pane();
            assertFalse(pane.hasProperties());
            pane.getProperties().put("key", "value");
            assertTrue(pane.hasProperties());
            assertEquals("value", pane.getProperties().get("key"));
            pane.relocate(12, 34);
            assertEquals(12.0, pane.getLayoutX());
            assertEquals(34.0, pane.getLayoutY());
            List<String> seen = new ArrayList<>();
            Button button = new Button("b");
            EventHandler<ActionEvent> handler = e -> seen.add("handler");
            EventHandler<ActionEvent> filter = e -> seen.add("filter");
            button.addEventHandler(ActionEvent.ACTION, handler);
            button.addEventFilter(ActionEvent.ACTION, filter);
            button.fire();
            button.removeEventHandler(ActionEvent.ACTION, handler);
            button.removeEventFilter(ActionEvent.ACTION, filter);
            button.fire();
            assertEquals(Arrays.asList("filter", "handler"), seen);
            return null;
        });
    }

    @Test
    void windowsShowHideSizeToSceneAndChangeScene() throws Exception {
        Stage stage = fx(() -> {
            Stage st = new Stage();
            st.initStyle(com.jxparallel.fx.stage.StageStyle.UTILITY);
            Pane content = new Pane();
            content.setPrefSize(321, 123);
            st.setScene(new Scene(new VBox(content)));
            st.show();
            return st;
        });
        assertTrue(fx(() -> stage.isShowing()));
        fx(() -> stage.sizeToScene());
        NativeScene s = fx(() -> NativeRuntime.sceneOf(model(stage)));
        assertEquals(321, s.width);
        assertEquals(123, s.height);
        Label other = fx(() -> new Label("outra cena"));
        fx(() -> stage.setScene(new Scene(new VBox(other), 200, 100)));
        JXNativeNode n = node(fx(() -> NativeRuntime.sceneOf(model(stage))), other);
        assertNotNull(n, "the new scene is drawn");
        fx(() -> stage.hide());
        assertFalse(fx(() -> stage.isShowing()));
        fx(() -> {
            javafx.application.Platform.runLater(() -> stage.close());
            stage.showAndWait();
            return null;
        });
        assertFalse(fx(() -> stage.isShowing()), "showAndWait returned when the stage closed");
    }

    @Test
    void geometryLookupAndChildren() throws Exception {
        Label[] label = new Label[1];
        VBox[] box = new VBox[1];
        NativeScene s = show(fx(() -> {
            label[0] = new Label("alvo");
            label[0].setId("alvo");
            box[0] = new VBox(label[0]);
            box[0].setPadding(new com.jxparallel.fx.geometry.Insets(10, 0, 0, 20));
            return new VBox(box[0]);
        }), 300, 200);
        JXNativeNode n = node(s, label[0]);
        fx(() -> {
            Bounds local = label[0].getBoundsInLocal();
            assertEquals(0.0, local.getMinX());
            assertEquals(n.getWidth(), local.getWidth(), 0.001);
            Bounds inParent = label[0].getBoundsInParent();
            assertEquals(20.0, inParent.getMinX(), 0.001);
            assertEquals(10.0, inParent.getMinY(), 0.001);
            Point2D local2 = label[0].sceneToLocal(25, 15);
            assertEquals(5.0, local2.getX(), 0.001);
            assertEquals(5.0, local2.getY(), 0.001);
            Point2D screen = label[0].localToScreen(1, 2);
            assertNotNull(screen);
            assertEquals(n.getHeight(), box[0].lookup("#alvo").getBoundsInLocal().getHeight(), 0.001);
            assertSame(label[0], box[0].lookup("#alvo"));
            assertNull(label[0].lookup(".nada"));
            assertEquals(1, box[0].getChildrenUnmodifiable().size());
            assertEquals(n.getHeight(), label[0].getHeight(), 0.001);
            assertEquals(n.getHeight(), label[0].heightProperty().get(), 0.001);
            return null;
        });
        close(s);
    }

    @Test
    void tooltipsAndContextMenusThroughTheApi() throws Exception {
        Label[] label = new Label[1];
        NativeScene s = show(fx(() -> {
            label[0] = new Label("dica");
            return new VBox(label[0]);
        }), 300, 200);
        Tooltip tip = fx(() -> new Tooltip("texto"));
        fx(() -> Tooltip.install(label[0], tip));
        assertSame(model(tip), fx(() -> NativeElements.model(model(label[0]).state.get("tooltip"))));
        fx(() -> Tooltip.uninstall(label[0], tip));
        assertNull(fx(() -> model(label[0]).state.get("tooltip")));
        ContextMenu menu = fx(() -> new ContextMenu(new MenuItem("um")));
        fx(() -> menu.show(label[0], 10, 10));
        assertEquals(1, (int) fx(() -> (int) s.overlays.stream().filter(o -> o.kind == NativeOverlay.Kind.MENU).count()));
        fx(() -> menu.hide());
        assertTrue(fx(() -> s.overlays.isEmpty()));
        close(s);
    }

    @Test
    void comboAndChoiceShowAndHide() throws Exception {
        ComboBox<String>[] combo = new ComboBox[1];
        ChoiceBox<String>[] choice = new ChoiceBox[1];
        NativeScene s = show(fx(() -> {
            combo[0] = new ComboBox<>(FXCollections.observableArrayList("a", "b"));
            choice[0] = new ChoiceBox<>(FXCollections.observableArrayList("x", "y"));
            return new VBox(combo[0], choice[0]);
        }), 300, 300);
        fx(() -> combo[0].show());
        assertTrue(fx(() -> combo[0].isShowing()));
        fx(() -> combo[0].hide());
        assertFalse(fx(() -> combo[0].isShowing()));
        fx(() -> choice[0].show());
        assertTrue(fx(() -> choice[0].isShowing()));
        fx(() -> choice[0].hide());
        assertFalse(fx(() -> choice[0].isShowing()));
        fx(() -> choice[0].getSelectionModel().select(1));
        assertEquals("y", fx(() -> choice[0].getValue()));
        close(s);
    }

    @Test
    void scrollToPutsTheItemAtTheTop() throws Exception {
        ListView<Integer>[] list = new ListView[1];
        TableView<Integer>[] table = new TableView[1];
        NativeScene s = show(fx(() -> {
            List<Integer> items = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                items.add(i);
            }
            list[0] = new ListView<>(FXCollections.observableArrayList(items));
            list[0].setPrefHeight(200);
            table[0] = new TableView<>(FXCollections.observableArrayList(items));
            table[0].getColumns().add(new TableColumn<>("n"));
            table[0].setPrefHeight(200);
            return new VBox(list[0], table[0]);
        }), 300, 500);
        fx(() -> list[0].scrollTo(50));
        fx(() -> table[0].scrollTo(40));
        assertEquals(50 * 23.0, fx(() -> NativeCells.number(model(list[0]).state.get("scrollY"), 0)), 0.001);
        assertEquals(50, node(s, list[0]).getProperty("first"));
        assertEquals(40 * 24.0, fx(() -> NativeCells.number(model(table[0]).state.get("scrollY"), 0)), 0.001);
        close(s);
    }

    @Test
    void cellsUpdateTheirIndexAndAreEmptyUntilFilled() throws Exception {
        fx(() -> {
            ListCell<String> cell = new ListCell<>();
            assertTrue(cell.isEmpty());
            cell.updateIndex(3);
            assertEquals(3, cell.getIndex());
            return null;
        });
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void mapValueFactoryReadsTheRowMap() throws Exception {
        TableView[] table = new TableView[1];
        NativeScene s = show(fx(() -> {
            Map<String, Object> row = new HashMap<>();
            row.put("nome", "Ana");
            table[0] = new TableView(FXCollections.observableArrayList(row, new HashMap<>()));
            TableColumn<Map, Object> name = new TableColumn<>("Nome");
            name.setCellValueFactory(new MapValueFactory<>("nome"));
            table[0].getColumns().add(name);
            return new VBox(table[0]);
        }), 300, 300);
        JXNativeNode t = node(s, table[0]);
        assertEquals("Ana", t.getChildren().get(0).getChildren().get(0).getProperty("value"));
        assertEquals("", String.valueOf(t.getChildren().get(1).getChildren().get(0).getProperty("value")).replace("null", ""),
                "a row without the key shows nothing");
        close(s);
    }

    @Test
    void notFocusTraversableNodesAreSkipped() throws Exception {
        Button[] b = new Button[3];
        NativeScene s = show(fx(() -> {
            for (int i = 0; i < 3; i++) {
                b[i] = new Button("b" + i);
            }
            b[1].setFocusTraversable(false);
            Label label = new Label("focusable label");
            label.setFocusTraversable(true);
            return new VBox(b[0], b[1], b[2], label);
        }), 300, 200);
        fx(() -> b[0].requestFocus());
        key(s, "TAB");
        assertTrue(fx(() -> b[2].isFocused()), "the button that is not focus traversable is skipped");
        key(s, "TAB");
        assertFalse(fx(() -> b[2].isFocused()), "a label asked to be traversable gets the focus");
        close(s);
    }

    @Test
    void mouseEventTypesStillMatchTheirSupertypes() {
        assertTrue(NativeEvents.matches(javafx.scene.input.MouseEvent.ANY, javafx.scene.input.MouseEvent.MOUSE_PRESSED));
        assertFalse(NativeEvents.matches(javafx.scene.input.MouseEvent.MOUSE_RELEASED, javafx.scene.input.MouseEvent.MOUSE_PRESSED));
        assertNotNull(MouseEvent.MOUSE_CLICKED);
    }
}
