package com.jxparallel.javafx.controls;

import com.jxparallel.properties.JXBooleanProperty;
import com.jxparallel.properties.JXStringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;

public class JXButton implements JXControl {
    private final Button node = new Button();
    private final JXStringProperty text = new JXStringProperty("");
    private final JXBooleanProperty disabled = new JXBooleanProperty(false);
    private JXVisualVariant variant = JXVisualVariant.DEFAULT;
    private JXVisualDensity density = JXVisualDensity.COMFORTABLE;

    public JXButton() {
        text.addListener(event -> node.setText((String) event.getNewValue()));
        disabled.addListener(event -> node.setDisable(Boolean.TRUE.equals(event.getNewValue())));
        JXModernStyle.apply(node, variant, density);
        JXModernStyle.animateHover(node);
    }

    public JXButton(String text) {
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

    public boolean isDisable() {
        return disabled.getBoolean();
    }

    public boolean isDisabled() {
        return isDisable();
    }

    public void setDisable(boolean value) {
        disabled.set(value);
    }

    public JXBooleanProperty disabledProperty() {
        return disabled;
    }

    public void setOnAction(EventHandler<ActionEvent> handler) {
        node.setOnAction(handler);
    }

    public EventHandler<ActionEvent> getOnAction() {
        return node.getOnAction();
    }

    public JXVisualVariant getVariant() {
        return variant;
    }

    public void setVariant(JXVisualVariant value) {
        variant = value == null ? JXVisualVariant.DEFAULT : value;
        JXModernStyle.apply(node, variant, density);
    }

    public JXVisualDensity getDensity() {
        return density;
    }

    public void setDensity(JXVisualDensity value) {
        density = value == null ? JXVisualDensity.COMFORTABLE : value;
        JXModernStyle.apply(node, variant, density);
    }

    public void animateAppear() {
        JXModernStyle.animateAppear(node);
    }

    @Override
    public Button node() {
        return node;
    }
}
