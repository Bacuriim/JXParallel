package com.jxparallel.fx.nativeimpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jxparallel.fx.beans.property.SimpleStringProperty;
import com.jxparallel.fx.beans.property.StringProperty;
import com.jxparallel.fx.collections.FXCollections;
import com.jxparallel.fx.collections.ObservableList;
import com.jxparallel.fx.scene.control.*;
import com.jxparallel.fx.scene.control.cell.PropertyValueFactory;
import com.jxparallel.fx.scene.layout.VBox;
import com.jxparallel.fx.util.StringConverter;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.native2d.JXNativeNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cells: value factories, reuse of cells and updateItem, row factories, nested columns, combo texts and popups. */
class NativeCellsTest extends NativeTestSupport {

    /** Bean-style row: a property accessor, a getter, a boolean "is" getter. */
    public static class Row {
        private final StringProperty name = new SimpleStringProperty();
        private final int age;
        private final boolean active;

        public Row(String name, int age, boolean active) {
            this.name.set(name);
            this.age = age;
            this.active = active;
        }

        public StringProperty nameProperty() {
            return name;
        }

        public int getAge() {
            return age;
        }

        public boolean isActive() {
            return active;
        }

        @Override
        public String toString() {
            return "row " + name.get();
        }
    }

    private static <T> TableColumn<T, Object> column(String title, String property) {
        TableColumn<T, Object> c = new TableColumn<>(title);
        c.setCellValueFactory(new PropertyValueFactory<>(property));
        return c;
    }

    private static List<Object> rowValues(JXNativeNode row) {
        List<Object> out = new ArrayList<>();
        for (JXNativeNode cell : row.getChildren()) {
            out.add(cell.getProperty("value"));
        }
        return out;
    }

    @Test
    void propertyValueFactoryUsesPropertiesGettersAndIsGetters() throws Exception {
        TableView<Row>[] table = new TableView[1];
        NativeScene s = show(fx(() -> {
            table[0] = new TableView<>(FXCollections.observableArrayList(new Row("Ana", 30, true)));
            table[0].getColumns().addAll(Arrays.asList(column("Nome", "name"), column("Idade", "age"),
                    column("Ativo", "active"), column("Nada", "missing"), column("Vazio", "")));
            return new VBox(table[0]);
        }), 600, 200);
        JXNativeNode t = node(s, table[0]);
        List<Object> values = rowValues(t.getChildren().get(0));
        assertEquals(Arrays.asList("Ana", "30", "true"), values.subList(0, 3));
        assertTrue(values.get(3) == null || "".equals(values.get(3)), "an unknown property shows nothing: " + values.get(3));
        assertTrue(values.get(4) == null || "".equals(values.get(4)));
        close(s);
    }

    @Test
    void nestedColumnsGiveTheirLeavesAndHiddenColumnsAreSkipped() throws Exception {
        TableView<Row>[] table = new TableView[1];
        NativeScene s = show(fx(() -> {
            table[0] = new TableView<>(FXCollections.observableArrayList(new Row("Bia", 20, false)));
            TableColumn<Row, Object> group = new TableColumn<>("Pessoa");
            group.getColumns().addAll(Arrays.asList(column("Nome", "name"), column("Idade", "age")));
            TableColumn<Row, Object> hidden = column("Oculta", "active");
            hidden.setVisible(false);
            table[0].getColumns().addAll(Arrays.asList(group, hidden));
            return new VBox(table[0]);
        }), 600, 200);
        JXNativeNode t = node(s, table[0]);
        assertEquals(Arrays.asList("Nome", "Idade"), Arrays.asList((String[]) t.getProperty("columns")));
        assertEquals(Arrays.asList("Bia", "20"), rowValues(t.getChildren().get(0)));
        close(s);
    }

    @Test
    void cellsAreReusedAndUpdatedOnlyWhenTheirItemChanges() throws Exception {
        List<String> updates = new ArrayList<>();
        ListView<String>[] list = new ListView[1];
        ObservableList<String>[] items = new ObservableList[1];
        NativeScene s = show(fx(() -> {
            items[0] = FXCollections.observableArrayList("a", "b");
            list[0] = new ListView<>(items[0]);
            list[0].setCellFactory(v -> new ListCell<String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    updates.add(item);
                    setText(empty || item == null ? null : item.toUpperCase());
                }
            });
            return new VBox(list[0]);
        }), 300, 200);
        JXNativeNode n = node(s, list[0]);
        assertEquals("A", n.getChildren().get(0).getProperty("value"));
        int before = updates.size();
        fx(() -> s.render());
        fx(() -> s.render());
        assertEquals(before, updates.size(), "rendering again does not call updateItem");
        fx(() -> items[0].set(1, "c"));
        settle();
        assertEquals("C", node(s, list[0]).getChildren().get(1).getProperty("value"));
        assertTrue(updates.contains("c"));
        close(s);
    }

    @Test
    void aRowFactoryGetsEachRowItemAndAFailingFactoryFallsBackToDefaultCells() throws Exception {
        List<Object> rowItems = new ArrayList<>();
        TableView<Row>[] table = new TableView[1];
        ListView<String>[] list = new ListView[1];
        Thread.UncaughtExceptionHandler handler = fx(() -> Thread.currentThread().getUncaughtExceptionHandler());
        List<Throwable> reported = new ArrayList<>();
        fx(() -> Thread.currentThread().setUncaughtExceptionHandler((t, e) -> reported.add(e)));
        try {
            NativeScene s = show(fx(() -> {
                table[0] = new TableView<>(FXCollections.observableArrayList(new Row("Ana", 1, true), new Row("Bia", 2, true)));
                table[0].getColumns().add(column("Nome", "name"));
                table[0].setRowFactory(tv -> new TableRow<Row>() {
                    @Override
                    protected void updateItem(Row item, boolean empty) {
                        super.updateItem(item, empty);
                        rowItems.add(item);
                    }
                });
                list[0] = new ListView<>(FXCollections.observableArrayList("x"));
                list[0].setCellFactory(v -> {
                    throw new IllegalStateException("factory failed");
                });
                return new VBox(table[0], list[0]);
            }), 400, 400);
            node(s, table[0]);
            assertEquals(2, rowItems.size());
            assertEquals("Ana", ((Row) rowItems.get(0)).nameProperty().get());
            assertEquals("x", node(s, list[0]).getChildren().get(0).getProperty("value"), "the default cell shows the item");
            assertEquals("factory failed", reported.get(0).getMessage());
            close(s);
        } finally {
            fx(() -> Thread.currentThread().setUncaughtExceptionHandler(handler));
        }
    }

    @Test
    void nodeItemsAreShownAsGraphicsAndFixedCellSizesApply() throws Exception {
        ListView<Object>[] list = new ListView[1];
        Label[] item = new Label[1];
        NativeScene s = show(fx(() -> {
            item[0] = new Label("dentro");
            list[0] = new ListView<>(FXCollections.observableArrayList(item[0], "texto"));
            list[0].setFixedCellSize(40);
            return new VBox(list[0]);
        }), 300, 300);
        JXNativeNode n = node(s, list[0]);
        assertEquals(40.0, n.getProperty("cellHeight"));
        assertEquals("dentro", n.getChildren().get(0).getChildren().get(0).getProperty("value"), "the node is the cell's graphic");
        assertEquals(40, n.getChildren().get(1).getY() - n.getChildren().get(0).getY());
        close(s);
    }

    @Test
    void comboTextsComeFromTheCellFactoryThenTheConverterThenToString() throws Exception {
        fx(() -> {
            ComboBox<Row> combo = new ComboBox<>(FXCollections.observableArrayList(new Row("Ana", 1, true)));
            NativeModel m = model(combo);
            Row ana = combo.getItems().get(0);
            assertEquals("row Ana", NativeCells.display(m, ana));
            assertEquals("", NativeCells.display(m, null));
            combo.setConverter(new StringConverter<Row>() {
                @Override
                public String toString(Row r) {
                    return r.nameProperty().get().toLowerCase();
                }

                @Override
                public Row fromString(String s) {
                    return null;
                }
            });
            assertEquals("ana", NativeCells.display(m, ana));
            combo.setCellFactory(v -> new ListCell<Row>() {
                @Override
                protected void updateItem(Row item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : "cell " + item.getAge());
                }
            });
            assertEquals("cell 1", NativeCells.display(m, ana), "the cell factory wins, as in JavaFX's button cell");
            combo.setCellFactory(v -> new ListCell<Row>() {
                @Override
                protected void updateItem(Row item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : "other " + item.getAge());
                }
            });
            assertEquals("other 1", NativeCells.display(m, ana), "a new factory clears the cached texts");
            return null;
        });
        fx(() -> {
            ComboBox<Row> combo = new ComboBox<>();
            combo.setConverter(new StringConverter<Row>() {
                @Override
                public String toString(Row r) {
                    throw new IllegalStateException("converter failed");
                }

                @Override
                public Row fromString(String s) {
                    return null;
                }
            });
            Thread.UncaughtExceptionHandler handler = Thread.currentThread().getUncaughtExceptionHandler();
            List<Throwable> reported = new ArrayList<>();
            Thread.currentThread().setUncaughtExceptionHandler((t, e) -> reported.add(e));
            try {
                assertEquals("row Bia", NativeCells.display(model(combo), new Row("Bia", 2, true)), "a failing converter falls back");
                assertEquals(1, reported.size());
            } finally {
                Thread.currentThread().setUncaughtExceptionHandler(handler);
            }
            return null;
        });
    }

    @Test
    void comboPopupShowsTheVisibleRowsAndClampsItsScroll() throws Exception {
        fx(() -> {
            List<String> items = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                items.add("item " + i);
            }
            ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(items));
            combo.setVisibleRowCount(5);
            combo.setValue("item 12");
            NativeModel m = model(combo);
            m.state.put("popupScrollY", 10 * 23.0 + 5);
            m.state.put("popupHover", 11);
            JXElement popup = NativeCells.comboPopup(m, null, 120);
            assertEquals(10, popup.getProps().get("first"));
            assertEquals(6, popup.getChildren().size(), "the five rows plus the one partly shown");
            JXElement twelve = popup.getChildren().get(2);
            assertEquals("item 12", twelve.getProps().get("value"));
            assertEquals(true, twelve.getProps().get("selected"));
            assertEquals(true, popup.getChildren().get(1).getProps().get("hover"));
            assertEquals(5 * 23.0 + 2, popup.getProps().get("prefHeight"));
            m.state.put("popupScrollY", 10000.0);
            NativeCells.comboPopup(m, null, 120);
            assertEquals(25 * 23.0, m.state.get("popupScrollY"), "at most the last page");
            m.state.put("popupScrollY", -50.0);
            NativeCells.comboPopup(m, null, 120);
            assertEquals(0.0, m.state.get("popupScrollY"));
            ComboBox<String> few = new ComboBox<>(FXCollections.observableArrayList("a", "b"));
            JXElement small = NativeCells.comboPopup(model(few), null, 120);
            assertEquals(2 * 23.0 + 2, small.getProps().get("prefHeight"), "no taller than its items");
            assertEquals(false, small.getChildren().get(0).getProps().get("selected"));
            return null;
        });
    }

    @Test
    void aListScrolledPastItsEndIsClamped() throws Exception {
        ListView<String>[] list = new ListView[1];
        NativeScene s = show(fx(() -> {
            list[0] = new ListView<>(FXCollections.observableArrayList("a", "b", "c"));
            list[0].setPrefHeight(100);
            return new VBox(list[0]);
        }), 300, 300);
        node(s, list[0]);
        fx(() -> {
            model(list[0]).state.put("scrollY", 999.0);
            s.render();
            return null;
        });
        assertEquals(0.0, fx(() -> NativeCells.number(model(list[0]).state.get("scrollY"), -1)), 0.001, "three rows fit");
        assertNull(fx(() -> NativeCells.selection(model(new ListView<String>()))), "no selection model before use");
        close(s);
    }

    @Test
    void scrollingRecyclesCellsLikeAVirtualFlow() throws Exception {
        TableView<Row>[] table = new TableView[1];
        List<Integer> updates = new ArrayList<>();
        NativeScene s = show(fx(() -> {
            List<Row> rows = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                rows.add(new Row("n" + i, i, i % 2 == 0));
            }
            table[0] = new TableView<>(FXCollections.observableArrayList(rows));
            table[0].getColumns().add(column("Nome", "name"));
            table[0].setRowFactory(tv -> new TableRow<Row>() {
                @Override
                protected void updateItem(Row item, boolean empty) {
                    super.updateItem(item, empty);
                    updates.add(item == null ? -1 : item.getAge());
                }
            });
            table[0].setPrefHeight(200);
            return new VBox(table[0]);
        }), 300, 300);
        java.util.Set<Object> cells = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (int i = 0; i < 40; i++) {
            int index = i * 20;
            fx(() -> table[0].scrollTo(index));
            JXNativeNode t = node(s, table[0]);
            for (JXNativeNode row : t.getChildren()) {
                cells.add(row.getProperty("model"));
                for (JXNativeNode cell : row.getChildren()) {
                    cells.add(cell.getProperty("model"));
                }
            }
            assertEquals("n" + index, t.getChildren().get(0).getChildren().get(0).getProperty("value"), "the rows show the new items");
        }
        int visible = node(s, table[0]).getChildren().size();
        assertTrue(cells.size() <= 4 * visible, "40 pages shown with " + cells.size() + " row and cell objects, "
                + visible + " rows visible: they are reused, not created per row shown");
        assertTrue(updates.contains(780), "reused rows run updateItem with their new item");
        close(s);
    }
}
