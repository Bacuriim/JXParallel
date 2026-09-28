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
                        select(after);
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
            if (getSelectedIndex() > 0) {
                select(getSelectedIndex() - 1);
            }
        }

        @Override
        public void selectNext() {
            if (getSelectedIndex() < all().size() - 1) {
                select(getSelectedIndex() + 1);
            }
        }
    }
}
