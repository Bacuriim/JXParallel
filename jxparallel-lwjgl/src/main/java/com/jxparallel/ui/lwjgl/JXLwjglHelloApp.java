package com.jxparallel.ui.lwjgl;

import com.jxparallel.ui.controls.JXButton;
import com.jxparallel.ui.controls.JXLabel;
import com.jxparallel.ui.layout.JXPane;

public final class JXLwjglHelloApp {
    private JXLwjglHelloApp() {
    }

    public static void main(String[] args) {
        JXButton button = new JXButton("Load");
        button.setOnAction(() -> System.out.println("LWJGL action"));

        JXPane content = new JXPane(12);
        content.add(new JXLabel("JXParallel LWJGL/OpenGL"));
        content.add(button);

        JXLwjglWindow window = new JXLwjglWindow("JXParallel LWJGL");
        window.setContent(content.render());
        window.show();
    }
}
