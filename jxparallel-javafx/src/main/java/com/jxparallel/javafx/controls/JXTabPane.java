package com.jxparallel.javafx.controls;

import javafx.collections.ObservableList;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

public class JXTabPane implements JXControl {
    private final TabPane node = new TabPane();

    public JXTabPane() {
        JXModernStyle.apply(node, JXVisualVariant.DEFAULT, JXVisualDensity.COMFORTABLE);
    }

    public ObservableList<Tab> getTabs() {
        return node.getTabs();
    }

    public TabPane node() {
        return node;
    }
}
