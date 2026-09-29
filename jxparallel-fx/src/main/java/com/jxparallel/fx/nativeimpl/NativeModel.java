package com.jxparallel.fx.nativeimpl;

import java.util.HashMap;
import java.util.Map;

import com.jxparallel.fx.Fx;
import com.jxparallel.ui.JXElement;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.EventDispatchChain;
import javafx.event.EventTarget;

/**
 * What a JX node is in native mode (-Djx.backend=native): its JavaFX class, the values set on it
 * (kept in JX properties, so bindings and listeners work), the values layouts attach to it
 * (GridPane.columnIndex...) and its children. {@link NativeElements} turns it into the element tree
 * the native renderer draws. It is the source and target of the JavaFX events the native runtime
 * delivers, so {@code event.getSource()} gives the JX node back.
 */
public final class NativeModel implements Fx.Owned, EventTarget {
    /** The JavaFX class this node stands for (javafx.scene.control.Label...). */
    public final Class<?> fxType;
    /** fxType's simple name, kept: Class.getSimpleName() is not cached on Java 8 and render paths ask often. */
    final String type;
    /** Bean values by property name: JX properties, or JX lists for list-valued ones. */
    final Map<String, Object> values = new HashMap<>();
    /** Values set by a layout's static setters, keyed "GridPane.columnIndex". */
    final Map<String, Object> constraints = new HashMap<>();
    /**
     * Runtime state that is not part of the API (caret, scroll offsets, cell pools). A write that
     * changes a value the element may show invalidates this node's element (see {@link StateMap}).
     */
    final Map<String, Object> state = new StateMap(this);
    /** Child models (JavaFX-side list: elements are models, the JX view converts them). */
    final ObservableList<Object> children = FXCollections.observableArrayList();
    NativeModel parent;
    private Object owner;

    // ---- the element last built for this node (NativeElements.toElement), reused while valid ----

    /** Bumped when this node, its state or something below it changed: its element must be rebuilt. */
    long version;
    JXElement element;
    long elementVersion = -1;
    long elementEpoch;
    NativeScene elementScene;
    Boolean elementExpanded;
    /** Focus-traversable nodes of the cached subtree, in order (the traversal a cache hit skips). */
    java.util.List<NativeModel> elementFocus;
    /** For table rows: what their cached element was built from (NativeCells.rowElement). */
    Object[] elementInputs;

    NativeModel(Class<?> fxType) {
        this.fxType = fxType;
        this.type = Native.simpleName(fxType);
        children.addListener((ListChangeListener<Object>) change -> {
            while (change.next()) {
                for (Object removed : change.getRemoved()) {
                    if (removed instanceof NativeModel && ((NativeModel) removed).parent == this) {
                        ((NativeModel) removed).parent = null;
                    }
                }
                for (Object added : change.getAddedSubList()) {
                    if (added instanceof NativeModel) {
                        NativeModel previous = ((NativeModel) added).parent;
                        if (previous != null && previous != this) {
                            // like JavaFX's Parent: a node has one parent, adding it elsewhere moves it
                            previous.children.remove(added);
                        }
                        ((NativeModel) added).parent = this;
                        // new ancestors: its CSS matches and inherited disabled state may differ
                        NativeElements.invalidateSubtree((NativeModel) added);
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
        if (is(javafx.animation.Animation.class)) {
            // an overridable Animation method (stop()) of a native transition: its JavaFX animation runs it
            String name = method.substring(0, method.indexOf('('));
            for (java.lang.reflect.Method m : javafx.animation.Animation.class.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                    try {
                        return m.invoke(NativeAnimations.of(this), args);
                    } catch (ReflectiveOperationException e) {
                        throw Fx.sneaky(e instanceof java.lang.reflect.InvocationTargetException ? e.getCause() : e);
                    }
                }
            }
        }
        throw new UnsupportedOperationException("JX native: " + type + "." + method);
    }

    @Override
    public EventDispatchChain buildEventDispatchChain(EventDispatchChain tail) {
        return tail; // the native runtime dispatches itself (NativeEvents)
    }

    public NativeModel getParent() {
        return parent;
    }

    /** True if this node's JavaFX class is {@code type} or a subclass of it. */
    boolean is(Class<?> type) {
        return type.isAssignableFrom(fxType);
    }

    @Override
    public String toString() {
        return "Native" + type + values.keySet();
    }

    /**
     * Node state whose changes invalidate the node's element. Layout results (x, y, width, height)
     * and the render parent are written on every frame and shown by no element, so they are quiet;
     * a write of an equal value changes nothing.
     */
    static final class StateMap extends HashMap<String, Object> {
        private static final java.util.Set<String> QUIET = new java.util.HashSet<>(java.util.Arrays.asList(
                "x", "y", "width", "height", "renderParent"));
        private final NativeModel owner;

        StateMap(NativeModel owner) {
            this.owner = owner;
        }

        @Override
        public Object put(String key, Object value) {
            Object old = super.put(key, value);
            if (!QUIET.contains(key) && !java.util.Objects.equals(old, value)) {
                NativeElements.invalidate(owner);
            }
            return old;
        }

        @Override
        public Object remove(Object key) {
            boolean had = containsKey(key);
            Object old = super.remove(key);
            if (had && !QUIET.contains(key)) {
                NativeElements.invalidate(owner);
            }
            return old;
        }
    }
}
