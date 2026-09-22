package com.jxparallel.javafx.controls;

import com.jxparallel.properties.JXStringProperty;
import javafx.scene.control.Label;

public class JXLabel implements JXControl {
    private final Label node = new Label();
    private final JXStringProperty text = new JXStringProperty("");

    public JXLabel() {
        text.addListener(event -> node.setText((String) event.getNewValue()));
        JXModernStyle.apply(node, JXVisualVariant.DEFAULT, JXVisualDensity.COMFORTABLE);
    }

    public JXLabel(String text) {
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
    public Label node() {
        return node;
    }
}
