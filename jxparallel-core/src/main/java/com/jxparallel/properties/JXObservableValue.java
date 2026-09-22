package com.jxparallel.properties;

public interface JXObservableValue<T> {
    T getValue();

    void addListener(JXChangeListener listener);

    void removeListener(JXChangeListener listener);
}
