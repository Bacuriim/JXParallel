package com.jxparallel.javafx.controls;

import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;

public class JXRadioButton implements JXControl {
    private final RadioButton node = new RadioButton();

    public JXRadioButton() {
    }

    public JXRadioButton(String text) {
        node.setText(text == null ? "" : text);
    }

    public void setToggleGroup(ToggleGroup group) {
        node.setToggleGroup(group);
    }

    public boolean isSelected() {
        return node.isSelected();
    }

    public void setSelected(boolean selected) {
        node.setSelected(selected);
    }

    public RadioButton node() {
        return node;
    }
}
