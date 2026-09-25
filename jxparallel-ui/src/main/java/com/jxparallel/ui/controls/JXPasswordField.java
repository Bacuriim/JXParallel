package com.jxparallel.ui.controls;

import com.jxparallel.properties.JXStringProperty;
import com.jxparallel.ui.JXComponent;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

public final class JXPasswordField implements JXComponent {
    private final JXRenderMemo memo = new JXRenderMemo();
    private final JXStringProperty text = new JXStringProperty("");

    public String getText() {
        return text.get();
    }

    public void setText(String value) {
        text.set(value == null ? "" : value);
    }

    @Override
    public JXElement render() {
        return memo.render(() -> JXElement.of("password", JXProps.builder().set("value", getText()).build()),
                getText());
    }
}
