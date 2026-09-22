package com.jxparallel.examples.nativeui;

import com.jxparallel.ui.controls.JXButton;
import com.jxparallel.ui.controls.JXLabel;
import com.jxparallel.ui.layout.JXPane;
import com.jxparallel.ui.native2d.JXWindow;

public final class NativeHelloApp {
    private NativeHelloApp() {
    }

    public static void main(String[] args) {
        JXButton button = new JXButton("Load");
        button.setOnAction(() -> System.out.println("Native action"));

        JXPane content = new JXPane(12);
        content.add(new JXLabel("JXParallel native UI"));
        content.add(button);

        JXWindow window = new JXWindow("JXParallel");
        window.setContent(content.render());
        window.show();
    }
}
