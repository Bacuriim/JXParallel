package com.jxparallel.ui.accessibility;

/**
 * A change an assistive technology should hear of. Events are delivered in batches, in the order they happened, on the
 * display thread, after the frame that showed the change (see {@link JXAccessibilityAdapter}).
 */
public final class JXAccessibilityEvent {
    /** What happened. */
    public enum Kind {
        /** The keyboard focus moved to {@link #getNodeId()} (0: out of the window's content). */
        FOCUS_CHANGED,
        /** The text or number a node holds changed; {@link #getText()} is the new value (empty for a password). */
        VALUE_CHANGED,
        /** One of the node's states changed; {@link #getState()} says which, {@link #isStateOn()} to what. */
        STATE_CHANGED,
        /** The node's name changed; {@link #getText()} is the new name. */
        NAME_CHANGED,
        /** The selection of the node (a list, tab list or pagination) changed. */
        SELECTION_CHANGED,
        /** The children of the node were added, removed or reordered. */
        STRUCTURE_CHANGED,
        /** A live-region message from {@link JXAccessibility#announce}; {@link #getText()} is the text. */
        ANNOUNCEMENT
    }

    private final Kind kind;
    private final long nodeId;
    private final String text;
    private final JXAccessibleState state;
    private final boolean stateOn;
    private final boolean assertive;

    public JXAccessibilityEvent(Kind kind, long nodeId, String text, JXAccessibleState state, boolean stateOn, boolean assertive) {
        this.kind = kind;
        this.nodeId = nodeId;
        this.text = text == null ? "" : text;
        this.state = state;
        this.stateOn = stateOn;
        this.assertive = assertive;
    }

    public static JXAccessibilityEvent of(Kind kind, long nodeId, String text) {
        return new JXAccessibilityEvent(kind, nodeId, text, null, false, false);
    }

    public Kind getKind() {
        return kind;
    }

    /** The node the event is about; 0 for an announcement or a focus that left the content. */
    public long getNodeId() {
        return nodeId;
    }

    public String getText() {
        return text;
    }

    public JXAccessibleState getState() {
        return state;
    }

    public boolean isStateOn() {
        return stateOn;
    }

    /** For an announcement: it should interrupt what is being read. */
    public boolean isAssertive() {
        return assertive;
    }

    @Override
    public String toString() {
        return kind + "#" + nodeId + (state != null ? " " + state + "=" + stateOn : "") + (text.isEmpty() ? "" : " \"" + text + "\"");
    }
}
