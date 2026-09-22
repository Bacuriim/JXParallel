package com.jxparallel.javafx.controls;

import javafx.scene.control.ProgressBar;

public class JXProgressBar implements JXControl {
    private final ProgressBar node = new ProgressBar();

    public JXProgressBar() {
        JXModernStyle.apply(node, JXVisualVariant.PRIMARY, JXVisualDensity.COMPACT);
    }

    public double getProgress() {
        return node.getProgress();
    }

    public void setProgress(double progress) {
        node.setProgress(progress);
    }

    public ProgressBar node() {
        return node;
    }
}
