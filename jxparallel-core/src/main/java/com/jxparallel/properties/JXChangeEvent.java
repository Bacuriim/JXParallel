package com.jxparallel.properties;

public final class JXChangeEvent<T> {
    private final T oldValue;
    private final T newValue;

    public JXChangeEvent(T oldValue, T newValue) {
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public T getOldValue() {
        return oldValue;
    }

    public T getNewValue() {
        return newValue;
    }
}
