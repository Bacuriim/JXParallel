package com.jxparallel.javafx.controls;

import com.jxparallel.properties.JXStringProperty;
import javafx.scene.control.TextField;

public class JXTextField implements JXControl {
    private final TextField node = new TextField();
    private final JXStringProperty text = new JXStringProperty("");
    private JXVisualDensity density = JXVisualDensity.COMFORTABLE;

    public JXTextField() {
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
        JXModernStyle.apply(node, JXVisualVariant.DEFAULT, density);
    }

    public JXTextField(String text) {
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

    public String getPromptText() {
        return node.getPromptText();
    }

    public void setPromptText(String value) {
        node.setPromptText(value == null ? "" : value);
    }

    public JXVisualDensity getDensity() {
        return density;
    }

    public void setDensity(JXVisualDensity value) {
        density = value == null ? JXVisualDensity.COMFORTABLE : value;
        JXModernStyle.apply(node, JXVisualVariant.DEFAULT, density);
    }

    @Override
    public TextField node() {
        return node;
    }
}
