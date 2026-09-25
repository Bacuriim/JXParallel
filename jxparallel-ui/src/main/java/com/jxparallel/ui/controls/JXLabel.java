package com.jxparallel.ui.controls;

import com.jxparallel.properties.JXStringProperty;
import com.jxparallel.ui.JXComponent;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

public final class JXLabel implements JXComponent {
    private final JXRenderMemo memo = new JXRenderMemo();
    private final JXStringProperty text;

    public JXLabel() {
        this("");
    }

    public JXLabel(String text) {
        this.text = new JXStringProperty(text == null ? "" : text);
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
    public JXElement render() {
        return memo.render(() -> JXElement.text(getText()),
                getText());
    }
}
