package com.jxparallel.properties;

public final class JXListChangeEvent<T> {
    public enum Type {
        ADD,
        REMOVE,
        UPDATE
    }

    private final Type type;
    private final int index;
    private final T newValue;
    private final T oldValue;

    public JXListChangeEvent(Type type, int index, T newValue, T oldValue) {
        this.type = type;
        this.index = index;
        this.newValue = newValue;
        this.oldValue = oldValue;
    }

    public Type getType() {
        return type;
    }

    public int getIndex() {
        return index;
    }

    public T getNewValue() {
        return newValue;
    }

    public T getOldValue() {
        return oldValue;
    }
}
