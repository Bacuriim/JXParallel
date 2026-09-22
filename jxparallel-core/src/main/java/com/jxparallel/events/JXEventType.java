package com.jxparallel.events;

public final class JXEventType<T extends JXEvent> {
    private final String name;

    public JXEventType(String name) {
        this.name = name == null ? "" : name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JXEventType)) {
            return false;
        }
        JXEventType<?> that = (JXEventType<?>) other;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
