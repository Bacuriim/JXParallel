package com.jxparallel.javafx.controls;

import com.jxparallel.properties.JXStringProperty;
import javafx.scene.control.TextArea;

public class JXTextArea implements JXControl {
    private final TextArea node = new TextArea();
    private final JXStringProperty text = new JXStringProperty("");

    public JXTextArea() {
        text.addListener(event -> {
            String newValue = (String) event.getNewValue();
            if (!newValue.equals(node.getText())) {
                node.setText(newValue);
            }
        });
        node.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.equals(text.get())) {
                text.set(newValue);
            }
        });
        JXModernStyle.apply(node, JXVisualVariant.DEFAULT, JXVisualDensity.COMFORTABLE);
    }

    public JXTextArea(String text) {
        this();
        setText(text);
    }

    public String getText() {
        return text.get();
    }

    public void setText(String value) {
        text.set(value == null ? "" : value);
    }

    public JXStringProperty textProperty() {
        return text;
    }

    @Override
    public TextArea node() {
        return node;
    }
}
