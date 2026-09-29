package com.jxparallel.fx.beans.property;

/**
 * JXParallel counterpart of {@link javafx.beans.property.SimpleBooleanProperty}, written by hand
 * (scripts/make-lazy-properties.py; listed in mirrors/handwritten.txt so the generator leaves it).
 * Its JavaFX peer is made on first need: a listener, a binding, JavaFX reading it. Until then the
 * property holds its own value, so an application model with many rows costs one object per
 * property, as with JavaFX, instead of a JX object and its peer. A subclass gets its peer at once,
 * as it may override what the peer calls.
 */
@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class SimpleBooleanProperty extends com.jxparallel.fx.beans.property.BooleanPropertyBase {
    /** False when a subclass constructor gave the peer (the wrap constructor). */
    private final boolean lazy;
    private Object bean;
    private String name;
    private boolean value;
    private Object peer;

    protected SimpleBooleanProperty(com.jxparallel.fx.Fx.Wrap wrap, Object peer) {
        super(wrap, peer);
        this.lazy = false;
    }

    public SimpleBooleanProperty() {
        this(null, "", false);
    }

    public SimpleBooleanProperty(boolean initialValue) {
        this(null, "", initialValue);
    }

    public SimpleBooleanProperty(java.lang.Object bean, java.lang.String name) {
        this(bean, name, false);
    }

    public SimpleBooleanProperty(java.lang.Object bean, java.lang.String name, boolean initialValue) {
        super(com.jxparallel.fx.Fx.WRAP, null);
        this.lazy = true;
        this.bean = bean;
        this.name = name == null ? "" : name;
        this.value = initialValue;
        if (getClass() != SimpleBooleanProperty.class) {
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
            peer = com.jxparallel.fx.Fx.own(new com.jxparallel.fx.peer.beans.property.SimpleBooleanProperty(
                    com.jxparallel.fx.Fx.fx(bean), name, value), this);
        }
        return peer;
    }

    @Override
    public boolean get() {
        return unobserved() ? value : super.get();
    }

    @Override
    public void set(boolean v) {
        if (unobserved()) {
            value = v;
        } else {
            super.set(v);
        }
    }

    @Override
    public java.lang.Boolean getValue() {
        return unobserved() ? java.lang.Boolean.valueOf(value) : super.getValue();
    }

    @Override
    public void setValue(java.lang.Boolean v) {
        set(v == null ? false : v);
    }

    @Override
    public boolean isBound() {
        return !unobserved() && super.isBound();
    }

    @Override
    public java.lang.Object getBean() {
        return lazy ? bean : (java.lang.Object) com.jxparallel.fx.Fx.jx(((javafx.beans.property.SimpleBooleanProperty) fxPeer()).getBean());
    }

    @Override
    public java.lang.String getName() {
        return lazy ? name : ((javafx.beans.property.SimpleBooleanProperty) fxPeer()).getName();
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
