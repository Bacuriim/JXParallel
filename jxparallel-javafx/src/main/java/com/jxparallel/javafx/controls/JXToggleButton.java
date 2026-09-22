package com.jxparallel.javafx.controls;

import javafx.scene.control.ToggleButton;

public class JXToggleButton implements JXControl {
    private final ToggleButton node = new ToggleButton();

    public JXToggleButton() {
        JXModernStyle.apply(node, JXVisualVariant.SECONDARY, JXVisualDensity.COMFORTABLE);
        JXModernStyle.animateHover(node);
    }

    public JXToggleButton(String text) {
        node.setText(text == null ? "" : text);
    }

    public String getText() {
        return node.getText();
    }

    public void setText(String text) {
        node.setText(text == null ? "" : text);
    }

    public boolean isSelected() {
        return node.isSelected();
    }

    public void setSelected(boolean selected) {
        node.setSelected(selected);
    }

    public ToggleButton node() {
        return node;
    }
}
