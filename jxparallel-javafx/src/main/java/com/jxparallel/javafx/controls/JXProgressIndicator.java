package com.jxparallel.javafx.controls;

import javafx.scene.control.ProgressIndicator;

public class JXProgressIndicator implements JXControl {
    private final ProgressIndicator node = new ProgressIndicator();

    public JXProgressIndicator() {
        JXModernStyle.apply(node, JXVisualVariant.PRIMARY, JXVisualDensity.COMPACT);
    }

    public double getProgress() {
        return node.getProgress();
    }

    public void setProgress(double progress) {
        node.setProgress(progress);
    }

    public ProgressIndicator node() {
        return node;
    }
}
