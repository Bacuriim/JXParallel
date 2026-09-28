package com.jxparallel.fx.sun.scene.control.skin;

import com.jxparallel.fx.Fx;
import com.jxparallel.fx.scene.control.TitledPane;

/** JXParallel counterpart of JavaFX's internal {@code com.sun.javafx.scene.control.skin.TitledPaneSkin}. */
public class TitledPaneSkin extends SkinWrapper<TitledPane> {

    public TitledPaneSkin(TitledPane titledPane) {
        this(Fx.WRAP, new com.sun.javafx.scene.control.skin.TitledPaneSkin((javafx.scene.control.TitledPane) Fx.fx(titledPane)));
    }

    protected TitledPaneSkin(Fx.Wrap wrap, Object peer) {
        super(peer);
    }
}
