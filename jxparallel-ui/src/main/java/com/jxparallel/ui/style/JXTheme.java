package com.jxparallel.ui.style;

import java.awt.Color;

public final class JXTheme {
    private JXTheme() {}

    public static JXStyle clean() {
        return JXStyle.builder()
                .set("primary", new Color(45, 108, 223))
                .set("surface", Color.WHITE)
                .set("text", new Color(45, 45, 45))
                .set("gap", Integer.valueOf(8))
                .build();
    }
}
