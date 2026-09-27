package com.jxparallel.fx.collections;

import java.util.AbstractList;
import java.util.Collection;

import com.jxparallel.fx.Fx;
import com.jxparallel.fx.beans.InvalidationListener;

/** An {@link ObservableList} backed by a JavaFX list; elements read and write as JX objects. */
public final class JxObservableList<E> extends AbstractList<E> implements ObservableList<E>, Fx.Backed {
    private final javafx.collections.ObservableList<Object> fx;

    public JxObservableList(javafx.collections.ObservableList<Object> fx) {
        this.fx = fx;
    }

    @Override
    public Object fxPeer() {
        return fx;
    }

    @SuppressWarnings("unchecked")
    @Override
    public E get(int index) {
        return (E) Fx.jx(fx.get(index));
    }

    @Override
    public int size() {
        return fx.size();
    }

    @SuppressWarnings("unchecked")
    @Override
    public E set(int index, E element) {
        return (E) Fx.jx(fx.set(index, Fx.fx(element)));
    }

    @Override
    public void add(int index, E element) {
        fx.add(index, Fx.fx(element));
    }

    @Override
    public boolean add(E element) {
        return fx.add(Fx.fx(element));
    }

    @SuppressWarnings("unchecked")
    @Override
    public E remove(int index) {
        return (E) Fx.jx(fx.remove(index));
    }

    @Override
    public boolean remove(Object o) {
        return fx.remove(Fx.fx(o));
    }

    @Override
    public boolean contains(Object o) {
        return fx.contains(Fx.fx(o));
    }

    @Override
    public int indexOf(Object o) {
        return fx.indexOf(Fx.fx(o));
    }

    @Override
    public void clear() {
        fx.clear();
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        return fx.addAll(Fx.<Collection<Object>>fxCollection(c));
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        return fx.addAll(index, Fx.<Collection<Object>>fxCollection(c));
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return fx.removeAll(Fx.<Collection<Object>>fxCollection(c));
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return fx.retainAll(Fx.<Collection<Object>>fxCollection(c));
    }

    @SafeVarargs
    @Override
    public final boolean addAll(E... elements) {
        return fx.addAll(Fx.fxArray(elements, Object.class));
    }

    @SafeVarargs
    @Override
    public final boolean setAll(E... elements) {
        return fx.setAll(Fx.fxArray(elements, Object.class));
    }

    @Override
    public boolean setAll(Collection<? extends E> col) {
        return fx.setAll(Fx.<Collection<Object>>fxCollection(col));
    }

    @SafeVarargs
    @Override
    public final boolean removeAll(E... elements) {
        return fx.removeAll(Fx.fxArray(elements, Object.class));
    }

    @SafeVarargs
    @Override
    public final boolean retainAll(E... elements) {
        return fx.retainAll(Fx.fxArray(elements, Object.class));
    }

    @Override
    public void remove(int from, int to) {
        fx.remove(from, to);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public ObservableList<E> filtered(java.util.function.Predicate<E> predicate) {
        return new JxObservableList<>((javafx.collections.ObservableList) fx.filtered(o -> predicate.test((E) Fx.jx(o))));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public ObservableList<E> sorted(java.util.Comparator<E> comparator) {
        return new JxObservableList<>((javafx.collections.ObservableList) fx.sorted((a, b) -> comparator.compare((E) Fx.jx(a), (E) Fx.jx(b))));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public ObservableList<E> sorted() {
        return new JxObservableList<>((javafx.collections.ObservableList) fx.sorted());
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addListener(ListChangeListener<? super E> listener) {
        fx.addListener((javafx.collections.ListChangeListener<Object>) Fx.fx(listener));
    }

    @SuppressWarnings("unchecked")
    @Override
    public void removeListener(ListChangeListener<? super E> listener) {
        fx.removeListener((javafx.collections.ListChangeListener<Object>) Fx.fx(listener));
    }

    @Override
    public void addListener(InvalidationListener listener) {
        fx.addListener((javafx.beans.InvalidationListener) Fx.fx(listener));
    }

    @Override
    public void removeListener(InvalidationListener listener) {
        fx.removeListener((javafx.beans.InvalidationListener) Fx.fx(listener));
    }

    @Override
    public boolean equals(Object o) {
        return o == this || fx.equals(Fx.fx(o));
    }

    @Override
    public int hashCode() {
        return fx.hashCode();
    }
}
