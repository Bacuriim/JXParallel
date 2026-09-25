package com.jxparallel.properties;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class JXObservableList<E> extends AbstractList<E> {
    private final List<E> delegate = new ArrayList<E>();
    private final List<JXListChangeListener> listeners = Collections.synchronizedList(new ArrayList<JXListChangeListener>());

    public void addListener(JXListChangeListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        listeners.add(listener);
    }

    public void removeListener(JXListChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    @Override
    public boolean add(E value) {
        JXListChangeEvent<E> event;
        synchronized (this) {
            delegate.add(value);
            event = new JXListChangeEvent<E>(JXListChangeEvent.Type.ADD, delegate.size() - 1, value, null);
        }
        notifyListeners(event);
        return true;
    }

    @Override
    public synchronized E get(int index) {
        return delegate.get(index);
    }

    @Override
    public synchronized int size() {
        return delegate.size();
    }

    public synchronized List<E> snapshot() {
        return new ArrayList<E>(delegate);
    }

    @Override
    public synchronized Iterator<E> iterator() {
        return snapshot().iterator();
    }

    @Override
    public boolean addAll(java.util.Collection<? extends E> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (E value : values) {
            changed |= add(value);
        }
        return changed;
    }

    @Override
    public void clear() {
        while (size() > 0) {
            remove(size() - 1);
        }
    }

    @Override
    public E set(int index, E element) {
        E old;
        synchronized (this) {
            old = delegate.set(index, element);
        }
        notifyListeners(new JXListChangeEvent<E>(JXListChangeEvent.Type.UPDATE, index, element, old));
        return old;
    }

    @Override
    public E remove(int index) {
        E old;
        synchronized (this) {
            old = delegate.remove(index);
        }
        notifyListeners(new JXListChangeEvent<E>(JXListChangeEvent.Type.REMOVE, index, null, old));
        return old;
    }

    private void notifyListeners(JXListChangeEvent<E> event) {
        List<JXListChangeListener> snapshot;
        synchronized (listeners) {
            snapshot = new ArrayList<JXListChangeListener>(listeners);
        }
        for (JXListChangeListener listener : snapshot) {
            listener.changed(event);
        }
    }
}
