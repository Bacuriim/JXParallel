package com.jxparallel.ui.controls;

import com.jxparallel.properties.JXStringProperty;
import com.jxparallel.ui.JXComponent;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

public final class JXTextArea implements JXComponent {
    private final JXStringProperty text = new JXStringProperty("");
    private final JXStringProperty promptText = new JXStringProperty("");

    public String getText() {
        return text.get();
    }

    public void setText(String value) {
        text.set(value == null ? "" : value);
    }

    public void setPromptText(String value) {
        promptText.set(value == null ? "" : value);
    }

    @Override
    public JXElement render() {
        return JXElement.of("textarea", JXProps.builder()
                .set("value", getText())
                .set("placeholder", promptText.get())
                .build());
    }
}
