package com.jxparallel.fx.nativeimpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jxparallel.fx.collections.FXCollections;
import com.jxparallel.fx.scene.control.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The selection models of native lists, tables, combo boxes and tab panes, through the public API. */
class NativeSelectionTest extends NativeTestSupport {

    private static List<Integer> indices(MultipleSelectionModel<?> sel) {
        return new ArrayList<>(sel.getSelectedIndices());
    }

    @Test
    void singleSelectionMovesAndClamps() throws Exception {
        fx(() -> {
            ListView<String> list = new ListView<>(FXCollections.observableArrayList("a", "b", "c", "d"));
            MultipleSelectionModel<String> sel = list.getSelectionModel();
            List<String> seen = new ArrayList<>();
            sel.selectedItemProperty().addListener((o, before, after) -> seen.add(String.valueOf(after)));
            assertTrue(sel.isEmpty());
            sel.selectPrevious();
            assertEquals(3, sel.getSelectedIndex(), "previous from nothing is the last, like JavaFX");
            sel.clearSelection();
            sel.selectNext();
            assertEquals(0, sel.getSelectedIndex(), "next from nothing is the first");
            sel.selectNext();
            assertEquals(Arrays.asList(1), indices(sel), "single mode keeps one index");
            sel.selectFirst();
            assertEquals("a", sel.getSelectedItem());
            sel.selectLast();
            sel.selectNext();
            assertEquals(3, sel.getSelectedIndex(), "stays on the last");
            sel.selectPrevious();
            assertEquals("c", sel.getSelectedItem());
            sel.select("d");
            assertEquals(3, sel.getSelectedIndex());
            sel.select(-1);
            sel.select(4);
            assertEquals(3, sel.getSelectedIndex(), "out of range indices are ignored");
            assertFalse(sel.isEmpty());
            sel.clearSelection(3);
            assertEquals(-1, sel.getSelectedIndex());
            assertNull(sel.getSelectedItem());
            assertTrue(sel.isEmpty());
            sel.select("elsewhere");
            assertEquals("elsewhere", sel.getSelectedItem(), "an item outside the list is still the selected item");
            assertEquals(-1, sel.getSelectedIndex());
            assertEquals(Arrays.asList("d", "null", "a", "b", "a", "d", "c", "d", "null", "elsewhere"), seen);
            return null;
        });
    }

    @Test
    void multipleSelectionAddsRemovesAndFallsBack() throws Exception {
        fx(() -> {
            ListView<String> list = new ListView<>(FXCollections.observableArrayList("a", "b", "c", "d"));
            MultipleSelectionModel<String> sel = list.getSelectionModel();
            sel.setSelectionMode(SelectionMode.MULTIPLE);
            sel.selectIndices(0, 2, 3);
            assertEquals(Arrays.asList(0, 2, 3), indices(sel));
            assertEquals(Arrays.asList("a", "c", "d"), new ArrayList<>(sel.getSelectedItems()));
            assertEquals(3, sel.getSelectedIndex(), "the last selected is the selected index");
            assertTrue(sel.isSelected(2));
            assertFalse(sel.isSelected(1));
            sel.clearSelection(3);
            assertEquals(2, sel.getSelectedIndex(), "falls back to the last one left");
            assertEquals("c", sel.getSelectedItem());
            sel.clearSelection(1);
            assertEquals(Arrays.asList(0, 2), indices(sel), "clearing an unselected index changes nothing");
            sel.clearSelection(0);
            assertEquals(2, sel.getSelectedIndex(), "clearing another index keeps the selected one");
            sel.selectAll();
            assertEquals(Arrays.asList(2, 0, 1, 3), indices(sel));
            sel.select(1);
            assertEquals(4, sel.getSelectedIndices().size(), "selecting a selected index adds nothing");
            sel.clearAndSelect(1);
            assertEquals(Arrays.asList(1), indices(sel));
            sel.selectNext();
            assertEquals(Arrays.asList(1, 2), indices(sel), "multiple mode keeps the previous");
            sel.clearSelection();
            assertTrue(sel.isEmpty());
            assertEquals(-1, sel.getSelectedIndex());
            return null;
        });
    }

    @Test
    void anEmptyListHasNothingToSelect() throws Exception {
        fx(() -> {
            ListView<String> list = new ListView<>();
            MultipleSelectionModel<String> sel = list.getSelectionModel();
            sel.selectFirst();
            sel.selectLast();
            sel.selectAll();
            assertTrue(sel.isEmpty());
            return null;
        });
    }

    @Test
    void tableSelectionIsRowBased() throws Exception {
        fx(() -> {
            TableView<String> table = new TableView<>(FXCollections.observableArrayList("a", "b", "c"));
            TableColumn<String, String> column = new TableColumn<>("x");
            table.getColumns().add(column);
            TableView.TableViewSelectionModel<String> sel = table.getSelectionModel();
            sel.selectFirst();
            assertEquals(0, sel.getSelectedIndex());
            assertEquals("a", sel.getSelectedItem());
            sel.selectBelowCell();
            assertEquals(1, sel.getSelectedIndex());
            sel.selectAboveCell();
            assertEquals(0, sel.getSelectedIndex());
            sel.selectRightCell();
            sel.selectLeftCell();
            assertEquals(0, sel.getSelectedIndex(), "cells sideways: rows stay");
            sel.selectLast();
            assertEquals("c", sel.getSelectedItem());
            sel.selectPrevious();
            assertEquals(1, sel.getSelectedIndex());
            sel.selectNext();
            assertEquals(2, sel.getSelectedIndex());
            sel.clearAndSelect(0, column);
            assertTrue(sel.isSelected(0, column));
            assertFalse(sel.isSelected(2));
            sel.clearSelection(0, column);
            assertTrue(sel.isEmpty());
            sel.select(1, column);
            assertEquals(1, sel.getSelectedIndex());
            sel.select("c");
            assertEquals(2, sel.getSelectedIndex());
            sel.clearSelection(2);
            sel.setSelectionMode(SelectionMode.MULTIPLE);
            sel.selectIndices(0, 2);
            assertEquals(Arrays.asList(0, 2), new ArrayList<>(sel.getSelectedIndices()));
            assertEquals(Arrays.asList("a", "c"), new ArrayList<>(sel.getSelectedItems()));
            sel.selectAll();
            assertEquals(3, sel.getSelectedIndices().size());
            sel.clearAndSelect(1);
            assertEquals(Arrays.asList(1), new ArrayList<>(sel.getSelectedIndices()));
            sel.clearSelection();
            assertTrue(sel.isEmpty());
            sel.select(0);
            sel.clearSelection(0);
            assertTrue(sel.isEmpty());
            assertTrue(sel.getSelectedCells().isEmpty(), "no cell selection");
            return null;
        });
    }

    @Test
    void comboSelectionFollowsTheValueBothWays() throws Exception {
        fx(() -> {
            ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList("a", "b", "c"));
            SingleSelectionModel<String> sel = combo.getSelectionModel();
            combo.setValue("b");
            assertEquals(1, sel.getSelectedIndex());
            sel.select(2);
            assertEquals("c", combo.getValue());
            sel.selectPrevious();
            assertEquals("b", combo.getValue());
            combo.setValue("outside");
            assertEquals(-1, sel.getSelectedIndex(), "a value outside the items selects no index");
            sel.select(5);
            assertEquals("outside", combo.getValue(), "an index past the items is ignored");
            sel.clearSelection();
            assertEquals("outside", combo.getValue(), "JavaFX keeps an item outside the list when clearing index -1");
            sel.select(0);
            sel.clearSelection();
            assertNull(combo.getValue());
            return null;
        });
    }

    @Test
    void tabPaneSelectionModel() throws Exception {
        fx(() -> {
            Tab a = new Tab("a");
            Tab b = new Tab("b");
            TabPane tabs = new TabPane(a, b);
            SingleSelectionModel<Tab> sel = tabs.getSelectionModel();
            assertEquals(0, sel.getSelectedIndex());
            sel.select(b);
            assertEquals(1, sel.getSelectedIndex());
            assertTrue(b.isSelected());
            assertFalse(a.isSelected());
            sel.selectFirst();
            assertTrue(a.isSelected());
            return null;
        });
    }
}
