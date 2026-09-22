package com.jxparallel.ui.controls;

import com.jxparallel.properties.JXBooleanProperty;
import com.jxparallel.properties.JXStringProperty;
import com.jxparallel.ui.JXComponent;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

public final class JXCheckBox implements JXComponent {
    private final JXStringProperty text;
    private final JXBooleanProperty selected = new JXBooleanProperty(false);

    public JXCheckBox() {
        this("");
    }

    public JXCheckBox(String text) {
        this.text = new JXStringProperty(text == null ? "" : text);
    }

    public String getText() {
        return text.get();
    }

    public void setText(String value) {
        text.set(value == null ? "" : value);
    }

    public boolean isSelected() {
        return selected.getBoolean();
    }

    public void setSelected(boolean value) {
        selected.set(value);
    }

    public JXBooleanProperty selectedProperty() {
        return selected;
    }

    public void fire() {
        setSelected(!isSelected());
    }

    @Override
    public JXElement render() {
        return JXElement.of("checkbox", JXProps.builder()
                .set("label", getText())
                .set("checked", isSelected())
                .build());
    }
}
