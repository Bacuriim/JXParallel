package com.jxparallel.javafx.controls;

import javafx.scene.control.CheckBox;

public class JXCheckBox implements JXControl {
    private final CheckBox node = new CheckBox();

    public JXCheckBox() {
        JXModernStyle.apply(node, JXVisualVariant.DEFAULT, JXVisualDensity.COMFORTABLE);
        JXModernStyle.animateHover(node);
    }

    public JXCheckBox(String text) {
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

    public CheckBox node() {
        return node;
    }
}
