package com.jxparallel.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class JXState<T> {
    private final List<Consumer<T>> listeners = new ArrayList<Consumer<T>>();
    private T value;

    public JXState(T initialValue) {
        value = initialValue;
    }

    public synchronized T get() {
        return value;
    }

    public void set(T nextValue) {
        List<Consumer<T>> snapshot;
        synchronized (this) {
            if (value == nextValue || (value != null && value.equals(nextValue))) {
                return;
            }
            value = nextValue;
            snapshot = new ArrayList<Consumer<T>>(listeners);
        }
        for (Consumer<T> listener : snapshot) {
            listener.accept(nextValue);
        }
    }

    public synchronized void subscribe(Consumer<T> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        listeners.add(listener);
    }
}
