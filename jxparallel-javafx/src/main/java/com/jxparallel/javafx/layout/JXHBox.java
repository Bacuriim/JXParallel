package com.jxparallel.javafx.layout;

import javafx.scene.layout.HBox;

public class JXHBox {
    private final HBox node;

    public JXHBox() {
        this.node = new HBox();
    }

    public JXHBox(double spacing) {
        this.node = new HBox(spacing);
    }

    public HBox node() {
        return node;
    }

    public double getSpacing() {
        return node.getSpacing();
    }

    public void setSpacing(double spacing) {
        node.setSpacing(spacing);
    }
}
