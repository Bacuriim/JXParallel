package com.jxparallel.fx.nativeimpl;

import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SingleSelectionModel;

/**
 * Selection models of native combo boxes, choice boxes, tab panes and list views, over the node's
 * item list. Like JavaFX's, a combo box keeps its selected item and its value in step.
 */
final class NativeSelection {
    private NativeSelection() {
    }

    /** JavaFX's ComboBox/ChoiceBox/TabPane selection: one item, synchronised with {@code value}. */
    static final class Single extends SingleSelectionModel<Object> {
        private final NativeModel model;
        private final String items;
        private boolean syncing;

        Single(NativeModel model, String items, boolean followsValue) {
            this.model = model;
            this.items = items;
            selectedIndexProperty().addListener(o -> Native.changed(model));
            if (followsValue) {
                selectedItemProperty().addListener((o, before, after) -> {
                    if (!syncing) {
                        syncing = true;
                        Native.property(model, "value", Object.class).setValue(after);
                        syncing = false;
                    }
                });
                Native.property(model, "value", Object.class).addListener((o, before, after) -> {
                    if (!syncing) {
                        syncing = true;
                        int index = Native.list(model, items).indexOf(after);
                        if (index < 0) {
                            // like ComboBox: a value outside the items selects no index but is the item
                            setSelectedIndex(-1);
                            setSelectedItem(after);
                        } else {
                            select(index);
                        }
                        syncing = false;
                    }
                });
            }
        }

        @Override
        protected Object getModelItem(int index) {
            List<Object> list = Native.list(model, items);
            return index >= 0 && index < list.size() ? list.get(index) : null;
        }

        @Override
        protected int getItemCount() {
            return Native.list(model, items).size();
        }
    }

    /** JavaFX's ListView selection: indices and items, single or multiple. */
    static final class Multiple extends MultipleSelectionModel<Object> {
        private final NativeModel model;
        private final String items;
        private final ObservableList<Integer> indices = FXCollections.observableArrayList();
        private final ObservableList<Object> selected = FXCollections.observableArrayList();

        Multiple(NativeModel model, String items) {
            this.model = model;
            this.items = items;
            indices.addListener((javafx.beans.InvalidationListener) o -> Native.changed(model));
        }

        private List<Object> all() {
            return Native.list(model, items);
        }

        @Override
        public ObservableList<Integer> getSelectedIndices() {
            return FXCollections.unmodifiableObservableList(indices);
        }

        @Override
        public ObservableList<Object> getSelectedItems() {
            return FXCollections.unmodifiableObservableList(selected);
        }

        @Override
        public void select(int index) {
            if (index < 0 || index >= all().size()) {
                return;
            }
            if (getSelectionMode() == SelectionMode.SINGLE) {
                indices.clear();
                selected.clear();
            }
            if (!indices.contains(index)) {
                indices.add(index);
                selected.add(all().get(index));
            }
            setSelectedIndex(index);
            setSelectedItem(all().get(index));
        }

        @Override
        public void select(Object item) {
            int index = all().indexOf(item);
            if (index >= 0) {
                select(index);
            } else {
                setSelectedItem(item);
            }
        }

        @Override
        public void clearAndSelect(int index) {
            clearSelection();
            select(index);
        }

        @Override
        public void clearSelection(int index) {
            int at = indices.indexOf(index);
            if (at >= 0) {
                indices.remove(at);
                selected.remove(at);
            }
            if (getSelectedIndex() == index) {
                int last = indices.isEmpty() ? -1 : indices.get(indices.size() - 1);
                setSelectedIndex(last);
                setSelectedItem(last < 0 ? null : all().get(last));
            }
        }

        @Override
        public void clearSelection() {
            indices.clear();
            selected.clear();
            setSelectedIndex(-1);
            setSelectedItem(null);
        }

        @Override
        public boolean isSelected(int index) {
            return indices.contains(index);
        }

        @Override
        public boolean isEmpty() {
            return indices.isEmpty();
        }

        @Override
        public void selectIndices(int index, int... more) {
            select(index);
            for (int i : more) {
                select(i);
            }
        }

        @Override
        public void selectAll() {
            for (int i = 0; i < all().size(); i++) {
                select(i);
            }
        }

        @Override
        public void selectFirst() {
            if (!all().isEmpty()) {
                select(0);
            }
        }

        @Override
        public void selectLast() {
            if (!all().isEmpty()) {
                select(all().size() - 1);
            }
        }

        @Override
        public void selectPrevious() {
            if (getSelectedIndex() == -1) {
                selectLast(); // like MultipleSelectionModelBase: from nothing, previous is the last
            } else if (getSelectedIndex() > 0) {
                select(getSelectedIndex() - 1);
            }
        }

        @Override
        public void selectNext() {
            if (getSelectedIndex() < all().size() - 1) {
                select(getSelectedIndex() + 1);
            }
        }

        /** Selects the range between the anchor and {@code index} (shift click), keeping others with {@code add}. */
        void selectRange(int anchor, int index, boolean add) {
            if (!add) {
                indices.clear();
                selected.clear();
            }
            int from = Math.min(anchor, index);
            int to = Math.max(anchor, index);
            for (int i = from; i <= to; i++) {
                if (i >= 0 && i < all().size() && !indices.contains(i)) {
                    indices.add(i);
                    selected.add(all().get(i));
                }
            }
            setSelectedIndex(index);
            setSelectedItem(index >= 0 && index < all().size() ? all().get(index) : null);
        }
    }

    /**
     * JavaFX's TableView selection (row based) over the native table's items. TableViewSelectionModel
     * requires a TableView, so a detached JavaFX one stands in; the rows come from the native items.
     */
    static final class TableSelection extends javafx.scene.control.TableView.TableViewSelectionModel<Object> {
        private final Multiple rows;
        private final ObservableList<javafx.scene.control.TablePosition> cells = FXCollections.observableArrayList();

        TableSelection(NativeModel table) {
            super(new javafx.scene.control.TableView<>());
            this.rows = new Multiple(table, "items");
            rows.selectedIndexProperty().addListener((o, a, b) -> setSelectedIndex(b.intValue()));
            rows.selectedItemProperty().addListener((o, a, b) -> setSelectedItem(b));
            selectionModeProperty().addListener((o, a, b) -> rows.setSelectionMode(b));
        }

        Multiple rows() {
            return rows;
        }

        @Override
        public ObservableList<javafx.scene.control.TablePosition> getSelectedCells() {
            return cells;
        }

        @Override
        public ObservableList<Integer> getSelectedIndices() {
            return rows.getSelectedIndices();
        }

        @Override
        public ObservableList<Object> getSelectedItems() {
            return rows.getSelectedItems();
        }

        @Override
        public boolean isSelected(int row, javafx.scene.control.TableColumn<Object, ?> column) {
            return rows.isSelected(row);
        }

        @Override
        public void select(int row, javafx.scene.control.TableColumn<Object, ?> column) {
            rows.select(row);
        }

        @Override
        public void clearAndSelect(int row, javafx.scene.control.TableColumn<Object, ?> column) {
            rows.clearAndSelect(row);
        }

        @Override
        public void clearSelection(int row, javafx.scene.control.TableColumn<Object, ?> column) {
            rows.clearSelection(row);
        }

        @Override
        public void selectLeftCell() {
        }

        @Override
        public void selectRightCell() {
        }

        @Override
        public void selectAboveCell() {
            rows.selectPrevious();
        }

        @Override
        public void selectBelowCell() {
            rows.selectNext();
        }

        @Override
        public void clearAndSelect(int row) {
            rows.clearAndSelect(row);
        }

        @Override
        public void select(int row) {
            rows.select(row);
        }

        @Override
        public void select(Object item) {
            rows.select(item);
        }

        @Override
        public void clearSelection(int index) {
            rows.clearSelection(index);
        }

        @Override
        public void clearSelection() {
            rows.clearSelection();
        }

        @Override
        public boolean isSelected(int index) {
            return rows.isSelected(index);
        }

        @Override
        public boolean isEmpty() {
            return rows.isEmpty();
        }

        @Override
        public void selectPrevious() {
            rows.selectPrevious();
        }

        @Override
        public void selectNext() {
            rows.selectNext();
        }

        @Override
        public void selectIndices(int index, int... more) {
            rows.selectIndices(index, more);
        }

        @Override
        public void selectAll() {
            rows.selectAll();
        }

        @Override
        public void selectFirst() {
            rows.selectFirst();
        }

        @Override
        public void selectLast() {
            rows.selectLast();
        }
    }
}
