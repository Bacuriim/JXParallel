package com.jxparallel.ui.controls;

import com.jxparallel.properties.JXStringProperty;
import com.jxparallel.ui.input.JXTextDocument;
import com.jxparallel.ui.JXComponent;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

public final class JXTextField implements JXComponent {
    private final JXRenderMemo memo = new JXRenderMemo();
    private final JXStringProperty text;
    private final JXStringProperty promptText;
    private final JXTextDocument document;

    public JXTextField() {
        this("");
    }

    public JXTextField(String text) {
        this.text = new JXStringProperty(text == null ? "" : text);
        this.promptText = new JXStringProperty("");
        this.document = new JXTextDocument(this.text.get());
        this.text.addListener(event -> document.setText(String.valueOf(event.getNewValue())));
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
        return promptText.get();
    }

    public void setPromptText(String value) {
        promptText.set(value == null ? "" : value);
    }

    public JXStringProperty promptTextProperty() {
        return promptText;
    }

    public JXTextDocument document() {
        return document;
    }

    @Override
    public JXElement render() {
        return memo.render(() -> JXElement.of("input", JXProps.builder()
                        .set("value", getText())
                        .set("placeholder", getPromptText())
                        .build()),
                getText(), getPromptText());
    }
}
