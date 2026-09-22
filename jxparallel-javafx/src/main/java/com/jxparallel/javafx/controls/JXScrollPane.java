package com.jxparallel.javafx.controls;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;

public class JXScrollPane implements JXControl {
    private final ScrollPane node = new ScrollPane();

    public Node getContent() {
        return node.getContent();
    }

    public void setContent(Node content) {
        node.setContent(content);
    }

    public ScrollPane node() {
        return node;
    }
}
