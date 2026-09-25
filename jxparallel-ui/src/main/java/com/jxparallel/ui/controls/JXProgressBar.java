package com.jxparallel.ui.controls;

import com.jxparallel.properties.JXDoubleProperty;
import com.jxparallel.ui.JXComponent;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

public final class JXProgressBar implements JXComponent {
    private final JXRenderMemo memo = new JXRenderMemo();
    private final JXDoubleProperty progress = new JXDoubleProperty(0.0);

    public double getProgress() {
        return progress.getDouble();
    }

    public void setProgress(double value) {
        progress.set(Math.max(0.0, Math.min(1.0, value)));
    }

    public JXDoubleProperty progressProperty() {
        return progress;
    }

    @Override
    public JXElement render() {
        return memo.render(() -> JXElement.of("progress", JXProps.builder().set("progress", getProgress()).build()),
                getProgress());
    }
}
