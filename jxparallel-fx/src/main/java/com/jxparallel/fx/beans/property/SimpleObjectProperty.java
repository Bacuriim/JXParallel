package com.jxparallel.fx.beans.property;

/**
 * JXParallel counterpart of {@link javafx.beans.property.SimpleObjectProperty}, written by hand
 * (scripts/make-lazy-properties.py; listed in mirrors/handwritten.txt so the generator leaves it).
 * Its JavaFX peer is made on first need: a listener, a binding, JavaFX reading it. Until then the
 * property holds its own value, so an application model with many rows costs one object per
 * property, as with JavaFX, instead of a JX object and its peer. A subclass gets its peer at once,
 * as it may override what the peer calls.
 */
@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class SimpleObjectProperty<T> extends com.jxparallel.fx.beans.property.ObjectPropertyBase<T> {
    /** False when a subclass constructor gave the peer (the wrap constructor). */
    private final boolean lazy;
    private Object bean;
    private String name;
    private T value;
    private Object peer;

    protected SimpleObjectProperty(com.jxparallel.fx.Fx.Wrap wrap, Object peer) {
        super(wrap, peer);
        this.lazy = false;
    }

    public SimpleObjectProperty() {
        this(null, "", null);
    }

    public SimpleObjectProperty(T initialValue) {
        this(null, "", initialValue);
    }

    public SimpleObjectProperty(java.lang.Object bean, java.lang.String name) {
        this(bean, name, null);
    }

    public SimpleObjectProperty(java.lang.Object bean, java.lang.String name, T initialValue) {
        super(com.jxparallel.fx.Fx.WRAP, null);
        this.lazy = true;
        this.bean = bean;
        this.name = name == null ? "" : name;
        this.value = initialValue;
        if (getClass() != SimpleObjectProperty.class) {
            fxPeer();
        }
    }

    /** No peer yet: the value is here. */
    private boolean unobserved() {
        return lazy && peer == null;
    }

    @Override
    public Object fxPeer() {
        if (!lazy) {
            return super.fxPeer();
        }
        if (peer == null) {
            peer = com.jxparallel.fx.Fx.own(new com.jxparallel.fx.peer.beans.property.SimpleObjectProperty(
                    com.jxparallel.fx.Fx.fx(bean), name, (T) com.jxparallel.fx.Fx.fx(value)), this);
        }
        return peer;
    }

    @Override
    public T get() {
        return unobserved() ? value : super.get();
    }

    @Override
    public void set(T v) {
        if (unobserved()) {
            value = v;
        } else {
            super.set(v);
        }
    }

    @Override
    public T getValue() {
        return unobserved() ? value : super.getValue();
    }

    @Override
    public void setValue(T v) {
        set(v);
    }

    @Override
    public boolean isBound() {
        return !unobserved() && super.isBound();
    }

    @Override
    public java.lang.Object getBean() {
        return lazy ? bean : (java.lang.Object) com.jxparallel.fx.Fx.jx(((javafx.beans.property.SimpleObjectProperty) fxPeer()).getBean());
    }

    @Override
    public java.lang.String getName() {
        return lazy ? name : ((javafx.beans.property.SimpleObjectProperty) fxPeer()).getName();
    }

    @Override
    public boolean equals(Object o) {
        return lazy ? o == this || (o != null && o == peer) : super.equals(o);
    }

    @Override
    public int hashCode() {
        return lazy ? System.identityHashCode(this) : super.hashCode();
    }
}
