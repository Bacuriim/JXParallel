package com.jxparallel.javafx.layout;

import javafx.scene.Node;
import javafx.scene.layout.BorderPane;

public class JXBorderPane {
    private final BorderPane node = new BorderPane();

    public Node getTop() {
        return node.getTop();
    }

    public void setTop(Node value) {
        node.setTop(value);
    }

    public Node getCenter() {
        return node.getCenter();
    }

    public void setCenter(Node value) {
        node.setCenter(value);
    }

    public BorderPane node() {
        return node;
    }
}
