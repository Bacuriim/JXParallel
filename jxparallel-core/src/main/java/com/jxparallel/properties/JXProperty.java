package com.jxparallel.properties;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class JXProperty<T> implements JXObservableValue<T> {
    private final List<JXChangeListener> listeners = new CopyOnWriteArrayList<JXChangeListener>();
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

    /**
     * Thread safe: concurrent sets are serialized with their notifications, so each change is
     * reported once and listeners see changes in order. Listeners run while this lock is held;
     * they must not wait for another thread that sets this property. {@link #get} never blocks.
     */
    // ponytail: one lock per property; two-way bindings set from two threads could deadlock
    // (lock order A->B vs B->A). None exist today; add a shared binding lock if they do.
    public synchronized void set(T newValue) {
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
        JXChangeListener listener = new WeakBinding<T>(this, source);
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
        listeners.add(listener);
    }

    @Override
    public void removeListener(JXChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    protected void notifyListeners(T oldValue, T newValue) {
        JXChangeEvent<T> event = new JXChangeEvent<T>(oldValue, newValue);
        for (JXChangeListener listener : listeners) {
            listener.changed(event);
        }
    }

    /**
     * Like JavaFX bindings: the source holds the bound property weakly, so a bound property that
     * nobody references can be collected. The dead listener removes itself on the next change.
     * Static on purpose: an anonymous class would capture the property strongly.
     */
    private static final class WeakBinding<T> implements JXChangeListener {
        private final WeakReference<JXProperty<T>> target;
        private final JXObservableValue<? extends T> source;

        WeakBinding(JXProperty<T> target, JXObservableValue<? extends T> source) {
            this.target = new WeakReference<JXProperty<T>>(target);
            this.source = source;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void changed(JXChangeEvent event) {
            JXProperty<T> property = target.get();
            if (property == null) {
                source.removeListener(this);
            } else {
                property.set((T) event.getNewValue());
            }
        }
    }
}
