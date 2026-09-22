package com.jxparallel.javafx.controls;

import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;

public class JXComboBox<T> implements JXControl {
    private final ComboBox<T> node = new ComboBox<T>();

    public JXComboBox() {
        JXModernStyle.apply(node, JXVisualVariant.DEFAULT, JXVisualDensity.COMFORTABLE);
    }

    public ObservableList<T> getItems() {
        return node.getItems();
    }

    public javafx.scene.control.SingleSelectionModel<T> getSelectionModel() {
        return node.getSelectionModel();
    }

    public T getValue() {
        return node.getValue();
    }

    public void setValue(T value) {
        node.setValue(value);
    }

    public ComboBox<T> node() {
        return node;
    }
}
