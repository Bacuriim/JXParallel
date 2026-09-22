package com.jxparallel.javafx.layout;

import javafx.scene.layout.GridPane;

public class JXGridPane {
    private final GridPane node = new GridPane();

    public GridPane node() {
        return node;
    }

    public double getHgap() {
        return node.getHgap();
    }

    public void setHgap(double value) {
        node.setHgap(value);
    }

    public double getVgap() {
        return node.getVgap();
    }

    public void setVgap(double value) {
        node.setVgap(value);
    }
}
