package com.jxparallel.javafx.controls;

import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;
import javafx.collections.ObservableList;
import javafx.scene.layout.Region;

public interface JXControl {
    Node node();

    default ObservableList<String> getStyleClass() {
        return ((Control) node()).getStyleClass();
    }

    default String getId() {
        return node().getId();
    }

    default void setId(String id) {
        node().setId(id);
    }

    default boolean isVisible() {
        return node().isVisible();
    }

    default void setVisible(boolean value) {
        node().setVisible(value);
    }

    default boolean isManaged() {
        return node().isManaged();
    }

    default void setManaged(boolean value) {
        node().setManaged(value);
    }

    default Tooltip getTooltip() {
        return ((Control) node()).getTooltip();
    }

    default void setTooltip(Tooltip tooltip) {
        ((Control) node()).setTooltip(tooltip);
    }

    default void setPrefWidth(double value) {
        if (node() instanceof Region) {
            ((Region) node()).setPrefWidth(value);
        }
    }

    default void setPrefHeight(double value) {
        if (node() instanceof Region) {
            ((Region) node()).setPrefHeight(value);
        }
    }
}
