package com.jxparallel.ui.controls;

import com.jxparallel.properties.JXBooleanProperty;
import com.jxparallel.properties.JXStringProperty;
import com.jxparallel.ui.JXComponent;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

public final class JXButton implements JXComponent {
    private final JXRenderMemo memo = new JXRenderMemo();
    private final JXStringProperty text;
    private final JXBooleanProperty disabled = new JXBooleanProperty(false);
    private Runnable onAction;

    public JXButton() {
        this("");
    }

    public JXButton(String text) {
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

    public boolean isDisable() {
        return disabled.getBoolean();
    }

    public void setDisable(boolean value) {
        disabled.set(value);
    }

    public JXBooleanProperty disabledProperty() {
        return disabled;
    }

    public Runnable getOnAction() {
        return onAction;
    }

    public void setOnAction(Runnable handler) {
        onAction = handler;
    }

    public void fire() {
        if (!isDisable() && onAction != null) {
            onAction.run();
        }
    }

    @Override
    public JXElement render() {
        return memo.render(() -> JXElement.of("button", JXProps.builder()
                        .set("label", getText())
                        .set("disabled", isDisable())
                        .set("onAction", onAction)
                        .build()),
                getText(), isDisable(), onAction);
    }
}
