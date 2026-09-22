package com.jxparallel.javafx.layout;

import javafx.scene.layout.VBox;

public class JXVBox {
    private final VBox node;

    public JXVBox() {
        this.node = new VBox();
    }

    public JXVBox(double spacing) {
        this.node = new VBox(spacing);
    }

    public VBox node() {
        return node;
    }

    public double getSpacing() {
        return node.getSpacing();
    }

    public void setSpacing(double spacing) {
        node.setSpacing(spacing);
    }
}
