package com.jxparallel.ui.controls;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

public final class JXControls {
    private JXControls() {
    }

    public static JXElement button(String label, Runnable action) {
        return JXElement.of("button", JXProps.builder()
                .set("label", label == null ? "" : label)
                .set("onAction", action)
                .build());
    }

    public static JXElement input(String value, String placeholder) {
        return JXElement.of("input", JXProps.builder()
                .set("value", value == null ? "" : value)
                .set("placeholder", placeholder == null ? "" : placeholder)
                .build());
    }

    public static JXElement checkbox(String label, boolean checked) {
        return JXElement.of("checkbox", JXProps.builder()
                .set("label", label == null ? "" : label)
                .set("checked", checked)
                .build());
    }

    public static JXElement select(String value, String... options) {
        return JXElement.of("select", JXProps.builder()
                .set("value", value == null ? "" : value)
                .set("options", options == null ? new String[0] : options.clone())
                .build());
    }
}
