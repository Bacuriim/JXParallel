package com.jxparallel.properties;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe observable list. Each change and its events happen under one write lock, so
 * listeners get events in index order even when several threads modify the list. Reads take only
 * a short lock and are not blocked by slow listeners. Listeners must not wait for another thread
 * that modifies this list.
 */
public class JXObservableList<E> extends AbstractList<E> {
    private final List<E> delegate = new ArrayList<E>();
    private final List<JXListChangeListener> listeners = new CopyOnWriteArrayList<JXListChangeListener>();
    private final Object writeLock = new Object();

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
        synchronized (writeLock) {
            int index;
            synchronized (this) {
                delegate.add(value);
                index = delegate.size() - 1;
            }
            notifyListeners(new JXListChangeEvent<E>(JXListChangeEvent.Type.ADD, index, value, null));
            return true;
        }
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
        List<E> added = new ArrayList<E>(values);
        synchronized (writeLock) {
            int start;
            synchronized (this) {
                start = delegate.size();
                delegate.addAll(added);
            }
            for (int i = 0; i < added.size(); i++) {
                notifyListeners(new JXListChangeEvent<E>(JXListChangeEvent.Type.ADD, start + i, added.get(i), null));
            }
            return true;
        }
    }

    /** Removes everything atomically, then reports one REMOVE per element from the last index down. */
    @Override
    public void clear() {
        synchronized (writeLock) {
            List<E> removed;
            synchronized (this) {
                removed = new ArrayList<E>(delegate);
                delegate.clear();
            }
            for (int i = removed.size() - 1; i >= 0; i--) {
                notifyListeners(new JXListChangeEvent<E>(JXListChangeEvent.Type.REMOVE, i, null, removed.get(i)));
            }
        }
    }

    @Override
    public E set(int index, E element) {
        synchronized (writeLock) {
            E old;
            synchronized (this) {
                old = delegate.set(index, element);
            }
            notifyListeners(new JXListChangeEvent<E>(JXListChangeEvent.Type.UPDATE, index, element, old));
            return old;
        }
    }

    @Override
    public E remove(int index) {
        synchronized (writeLock) {
            E old;
            synchronized (this) {
                old = delegate.remove(index);
            }
            notifyListeners(new JXListChangeEvent<E>(JXListChangeEvent.Type.REMOVE, index, null, old));
            return old;
        }
    }

    private void notifyListeners(JXListChangeEvent<E> event) {
        for (JXListChangeListener listener : listeners) {
            listener.changed(event);
        }
    }
}
