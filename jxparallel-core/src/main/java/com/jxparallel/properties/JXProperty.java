package com.jxparallel.properties;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JXProperty<T> implements JXObservableValue<T> {
    private final List<JXChangeListener> listeners = new ArrayList<JXChangeListener>();
    private volatile T value;
    private JXObservableValue<? extends T> boundSource;
    private JXChangeListener boundListener;

    public JXProperty() {
        this(null);
    }

    public JXProperty(T initialValue) {
        this.value = initialValue;
    }

    @Override
    public T getValue() {
        return value;
    }

    public T get() {
        return value;
    }

    public void set(T newValue) {
        T oldValue = this.value;
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        this.value = newValue;
        notifyListeners(oldValue, newValue);
    }

    public void bind(JXObservableValue<? extends T> source) {
        if (source == null) {
            throw new IllegalArgumentException("Source cannot be null");
        }
        unbind();
        set(source.getValue());
        JXChangeListener listener = new JXChangeListener() {
            @Override
            public void changed(JXChangeEvent event) {
                set((T) event.getNewValue());
            }
        };
        boundSource = source;
        boundListener = listener;
        source.addListener(listener);
    }

    public void unbind() {
        JXObservableValue<? extends T> source = boundSource;
        JXChangeListener listener = boundListener;
        boundSource = null;
        boundListener = null;
        if (source != null && listener != null) {
            source.removeListener(listener);
        }
    }

    @Override
    public void addListener(JXChangeListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(JXChangeListener listener) {
        if (listener == null) {
            return;
        }
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    protected void notifyListeners(T oldValue, T newValue) {
        JXChangeEvent<T> event = new JXChangeEvent<T>(oldValue, newValue);
        List<JXChangeListener> snapshot;
        synchronized (listeners) {
            snapshot = new ArrayList<JXChangeListener>(listeners);
        }
        for (JXChangeListener listener : snapshot) {
            listener.changed(event);
        }
    }
}
