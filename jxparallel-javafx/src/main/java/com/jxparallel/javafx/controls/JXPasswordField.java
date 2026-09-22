package com.jxparallel.javafx.controls;

import javafx.scene.control.PasswordField;

public class JXPasswordField implements JXControl {
    private final PasswordField node = new PasswordField();

    public String getText() {
        return node.getText();
    }

    public void setText(String text) {
        node.setText(text == null ? "" : text);
    }

    public PasswordField node() {
        return node;
    }
}
