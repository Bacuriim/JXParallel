package com.jxparallel.ui.layout;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

public final class JXLayouts {
    private JXLayouts() {
    }

    public static JXElement row(double gap, JXElement... children) {
        return container("row", gap, children);
    }

    public static JXElement column(double gap, JXElement... children) {
        return container("column", gap, children);
    }

    public static JXElement stack(JXElement... children) {
        return JXElement.of("stack", JXProps.empty(), children);
    }

    private static JXElement container(String type, double gap, JXElement... children) {
        return JXElement.of(type, JXProps.builder().set("gap", Math.max(0.0, gap)).build(), children);
    }
}
