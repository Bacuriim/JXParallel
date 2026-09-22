package com.jxparallel.events;

public class JXEvent {
    private final Object source;

    public JXEvent(Object source) {
        this.source = source;
    }

    public Object getSource() {
        return source;
    }
}
