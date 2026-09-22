package com.jxparallel.javafx.controls;

import javafx.scene.control.ListView;

public class JXListView<T> implements JXControl {
    private final ListView<T> node = new ListView<T>();

    public ListView<T> node() {
        return node;
    }

    public javafx.collections.ObservableList<T> getItems() {
        return node.getItems();
    }

    public javafx.scene.control.MultipleSelectionModel<T> getSelectionModel() {
        return node.getSelectionModel();
    }

    public void setCellFactory(javafx.util.Callback<ListView<T>, javafx.scene.control.ListCell<T>> factory) {
        node.setCellFactory(factory);
    }

    public void setPrefHeight(double value) {
        node.setPrefHeight(value);
    }

    public void setPrefWidth(double value) {
        node.setPrefWidth(value);
    }
}
