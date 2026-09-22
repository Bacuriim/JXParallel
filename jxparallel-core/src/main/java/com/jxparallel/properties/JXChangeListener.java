package com.jxparallel.properties;

public interface JXChangeListener<T> {
    void changed(JXChangeEvent<T> event);
}
