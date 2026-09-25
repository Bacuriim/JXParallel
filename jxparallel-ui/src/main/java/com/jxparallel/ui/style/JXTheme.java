package com.jxparallel.ui.style;

public final class JXTheme {
    private JXTheme() {}

    /** Colors are ARGB ints (0xAARRGGBB). */
    public static JXStyle clean() {
        return JXStyle.builder()
                .set("primary", Integer.valueOf(0xFF2D6CDF))
                .set("surface", Integer.valueOf(0xFFFFFFFF))
                .set("text", Integer.valueOf(0xFF2D2D2D))
                .set("gap", Integer.valueOf(8))
                .build();
    }
}
