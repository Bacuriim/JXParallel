package com.jxparallel.ui.controls;

import com.jxparallel.properties.JXBooleanProperty;
import com.jxparallel.properties.JXStringProperty;
import com.jxparallel.ui.JXComponent;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

public final class JXToggleButton implements JXComponent {
    private final JXRenderMemo memo = new JXRenderMemo();
    private final JXStringProperty text;
    private final JXBooleanProperty selected = new JXBooleanProperty(false);

    public JXToggleButton() {
        this("");
    }

    public JXToggleButton(String text) {
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

    public void fire() {
        setSelected(!isSelected());
    }

    @Override
    public JXElement render() {
        return memo.render(() -> JXElement.of("toggle", JXProps.builder()
                        .set("label", getText())
                        .set("selected", isSelected())
                        .build()),
                getText(), isSelected());
    }
}
