package com.jxparallel.ui;

/**
 * A screen kept live, as a host sees it (JXForge's {@code JxmlLive} is one): the host mounts {@link #element()}, hands the
 * mounted root to {@link #attach}, and calls {@link #refresh} when the model behind the screen may have changed, on the
 * thread that owns the mounted tree. This is the interface through which the renderer (which does not depend on JXForge)
 * hosts a live screen; see {@code JXWindow.setLive} and {@code JXLiveHost}.
 */
public interface JXLiveSession {
    /** The element tree of the screen, which the host mounts. */
    JXElement element();

    /** Pairs the session with the mounted tree of {@link #element()}; false when it does not match (the session then only rebuilds). */
    boolean attach(JXLiveNode mounted);

    /**
     * Brings the mounted tree up to date. False when the root of the screen is another type now: the host must mount
     * {@link #renew()} instead. The host lays the tree out and paints afterwards.
     */
    boolean refresh();

    /** A new session for the same screen and model, built from the model as it is now (not yet attached). */
    JXLiveSession renew();
}
