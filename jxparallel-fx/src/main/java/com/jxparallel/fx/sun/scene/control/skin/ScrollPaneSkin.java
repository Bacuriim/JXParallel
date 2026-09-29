package com.jxparallel.fx.sun.scene.control.skin;

import com.jxparallel.fx.Fx;
import com.jxparallel.fx.scene.control.ScrollPane;
import com.jxparallel.fx.scene.layout.StackPane;

/**
 * JXParallel counterpart of JavaFX's internal {@code com.sun.javafx.scene.control.skin.ScrollPaneSkin}.
 * Applications read its private {@code viewRect} by reflection (to turn off caching), so the field
 * exists here too and holds the JX counterpart of JavaFX's.
 */
public class ScrollPaneSkin extends SkinWrapper<ScrollPane> {
    @SuppressWarnings("unused")
    private final StackPane viewRect;

    public ScrollPaneSkin(ScrollPane scrollPane) {
        this(Fx.WRAP, Fx.NATIVE ? new NativeSkin(scrollPane)
                : new com.sun.javafx.scene.control.skin.ScrollPaneSkin((javafx.scene.control.ScrollPane) Fx.fx(scrollPane)));
    }

    protected ScrollPaneSkin(Fx.Wrap wrap, Object peer) {
        super(peer);
        // native mode draws the viewport itself: a detached pane takes what applications set on it (cache)
        this.viewRect = peer instanceof NativeSkin ? new StackPane() : (StackPane) Fx.jx(read(peer, "viewRect"));
    }
}
