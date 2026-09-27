package com.jxparallel.fx.nativeimpl;

import java.util.HashMap;
import java.util.Map;

import com.jxparallel.fx.Fx;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

/**
 * What a JX node is in native mode (-Djx.backend=native): its JavaFX class, the values set on it
 * (kept in JX properties, so bindings and listeners work), the values layouts attach to it
 * (GridPane.columnIndex...) and its children. {@link NativeElements} turns it into the element tree
 * the native renderer draws.
 */
public final class NativeModel implements Fx.Owned {
    /** The JavaFX class this node stands for (javafx.scene.control.Label...). */
    public final Class<?> fxType;
    /** Bean values by property name: JX properties, or JX lists for list-valued ones. */
    final Map<String, Object> values = new HashMap<>();
    /** Values set by a layout's static setters, keyed "GridPane.columnIndex". */
    final Map<String, Object> constraints = new HashMap<>();
    /** Child models (JavaFX-side list: elements are models, the JX view converts them). */
    final ObservableList<Object> children = FXCollections.observableArrayList();
    NativeModel parent;
    private Object owner;

    NativeModel(Class<?> fxType) {
        this.fxType = fxType;
        children.addListener((ListChangeListener<Object>) change -> {
            while (change.next()) {
                for (Object removed : change.getRemoved()) {
                    if (removed instanceof NativeModel && ((NativeModel) removed).parent == this) {
                        ((NativeModel) removed).parent = null;
                    }
                }
                for (Object added : change.getAddedSubList()) {
                    if (added instanceof NativeModel) {
                        ((NativeModel) added).parent = this;
                    }
                }
            }
            Native.changed(this);
        });
    }

    @Override
    public Object jxOwner() {
        return owner;
    }

    @Override
    public void jxOwner(Object owner) {
        this.owner = owner;
    }

    @Override
    public Object callSuper(String method, Object[] args) {
        throw new UnsupportedOperationException("JX native: " + fxType.getSimpleName() + "." + method);
    }

    public NativeModel getParent() {
        return parent;
    }

    @Override
    public String toString() {
        return "Native" + fxType.getSimpleName() + values.keySet();
    }
}
