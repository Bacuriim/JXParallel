package com.jxparallel.ui;

import java.util.List;

/**
 * What a live-bound screen (JXForge's {@code JxmlLive}) needs from a mounted node, so that the module that generates
 * the screens does not depend on the one that renders them. A node mirrors one {@link JXElement}; a "live" element is
 * one built by a live session, whose props the session owns: the session changes a prop of the node in place
 * ({@link #setProp}), which is the same object as the prop of the element, so the element tree keeps describing what is
 * mounted and a later full reconcile compares against the right previous values.
 */
public interface JXLiveNode {
    /** {@link #setProp} result bit: the node's sizes or its placement in the parent may have changed. */
    int SET_RELAYOUT = 1;
    /** {@link #setProp} result bit: the node's grow priority in its parent changed. */
    int SET_PLACEMENT = 2;
    /** {@link #commit} result: nothing the parent has to do. */
    int UNCHANGED = 0;
    /** {@link #commit} result: something inside needs layout, this node's sizes and placement are the same. */
    int SUBTREE = 1;
    /** {@link #commit} result: this node's sizes or grow priority changed, the parent must lay out again. */
    int RESIZED = 2;
    /** {@link #commit} input bit: a child came back {@link #RESIZED} (or this node's own props need a relayout). */
    int RELAYOUT = 1;
    /** {@link #commit} input bit: a child came back {@link #SUBTREE}. */
    int CHILD_SUBTREE = 2;
    /** {@link #commit} input bit: the grow priority of this node changed. */
    int PLACEMENT = 4;

    /** The element this node was last given (mounted or reconciled). */
    JXElement getSource();

    /** The children, in the order of the source element's children. */
    List<? extends JXLiveNode> getLiveChildren();

    /**
     * Replaces the value of the prop {@code name}, in place, with {@code value}, both of which must be present
     * (non-null) and the prop must exist: a prop that appears or disappears is a different set of keys, which only a
     * reconcile does. Returns {@code -1} when it cannot be done in place, else the {@code SET_} bits. The node's layout
     * state is not settled yet: {@link #commit} does that once all the props of the refresh are in.
     */
    int setProp(String name, Object value);

    /**
     * Settles what {@link #setProp} and the children left, exactly as a reconcile does at the end of this node:
     * {@code bits} are {@link #RELAYOUT} (own props that change sizes, or a child that came back {@link #RESIZED}),
     * {@link #CHILD_SUBTREE} and {@link #PLACEMENT}. Returns {@link #UNCHANGED}, {@link #SUBTREE} or {@link #RESIZED}
     * for the parent.
     */
    int commit(int bits);

    /**
     * Reconciles this node against a rebuilt element of the same type, as the parent's reconcile would; returns what
     * the parent has to do ({@link #UNCHANGED}, {@link #SUBTREE}, {@link #RESIZED}) or {@code -1} when the type differs.
     */
    int reconcileLive(JXElement element);
}
