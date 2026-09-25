package com.jxparallel.ui;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A value that notifies subscribers when it changes. Thread safe: concurrent {@link #set} calls
 * are serialized with their notifications, so the last value a subscriber receives is always
 * the current value. Subscribers run while {@code set} holds the lock; they must not wait for
 * another thread that sets this state. {@link #get} never blocks.
 */
public final class JXState<T> {
    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<Consumer<T>>();
    private volatile T value;

    public JXState(T initialValue) {
        value = initialValue;
    }

    public T get() {
        return value;
    }

    public synchronized void set(T nextValue) {
        if (value == nextValue || (value != null && value.equals(nextValue))) {
            return;
        }
        value = nextValue;
        for (Consumer<T> listener : listeners) {
            listener.accept(nextValue);
        }
    }

    public void subscribe(Consumer<T> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        listeners.add(listener);
    }
}
