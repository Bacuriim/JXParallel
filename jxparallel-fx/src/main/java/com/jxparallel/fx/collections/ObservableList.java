package com.jxparallel.fx.collections;

import java.util.Collection;
import java.util.List;

import com.jxparallel.fx.beans.InvalidationListener;
import com.jxparallel.fx.beans.Observable;

/** JXParallel counterpart of {@link javafx.collections.ObservableList}. */
public interface ObservableList<E> extends List<E>, Observable {

    void addListener(ListChangeListener<? super E> listener);

    void removeListener(ListChangeListener<? super E> listener);

    @SuppressWarnings("unchecked")
    boolean addAll(E... elements);

    @SuppressWarnings("unchecked")
    boolean setAll(E... elements);

    boolean setAll(Collection<? extends E> col);

    @SuppressWarnings("unchecked")
    boolean removeAll(E... elements);

    @SuppressWarnings("unchecked")
    boolean retainAll(E... elements);

    void remove(int from, int to);

    /** A live view of the elements matching the predicate, like JavaFX's {@code filtered}. */
    ObservableList<E> filtered(java.util.function.Predicate<E> predicate);

    /** A live sorted view, like JavaFX's {@code sorted}. */
    ObservableList<E> sorted(java.util.Comparator<E> comparator);

    ObservableList<E> sorted();

    @Override
    void addListener(InvalidationListener listener);

    @Override
    void removeListener(InvalidationListener listener);
}
