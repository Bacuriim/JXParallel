package com.jxparallel.fx.beans.property;

/**
 * JXParallel counterpart of {@link javafx.beans.property.SimpleLongProperty}, written by hand
 * (scripts/make-lazy-properties.py; listed in mirrors/handwritten.txt so the generator leaves it).
 * Its JavaFX peer is made on first need: a listener, a binding, JavaFX reading it. Until then the
 * property holds its own value, so an application model with many rows costs one object per
 * property, as with JavaFX, instead of a JX object and its peer. A subclass gets its peer at once,
 * as it may override what the peer calls.
 */
@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class SimpleLongProperty extends com.jxparallel.fx.beans.property.LongPropertyBase {
    /** False when a subclass constructor gave the peer (the wrap constructor). */
    private final boolean lazy;
    private Object bean;
    private String name;
    private long value;
    private Object peer;

    protected SimpleLongProperty(com.jxparallel.fx.Fx.Wrap wrap, Object peer) {
        super(wrap, peer);
        this.lazy = false;
    }

    public SimpleLongProperty() {
        this(null, "", 0L);
    }

    public SimpleLongProperty(long initialValue) {
        this(null, "", initialValue);
    }

    public SimpleLongProperty(java.lang.Object bean, java.lang.String name) {
        this(bean, name, 0L);
    }

    public SimpleLongProperty(java.lang.Object bean, java.lang.String name, long initialValue) {
        super(com.jxparallel.fx.Fx.WRAP, null);
        this.lazy = true;
        this.bean = bean;
        this.name = name == null ? "" : name;
        this.value = initialValue;
        if (getClass() != SimpleLongProperty.class) {
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
            peer = com.jxparallel.fx.Fx.own(new com.jxparallel.fx.peer.beans.property.SimpleLongProperty(
                    com.jxparallel.fx.Fx.fx(bean), name, value), this);
        }
        return peer;
    }

    @Override
    public long get() {
        return unobserved() ? value : super.get();
    }

    @Override
    public void set(long v) {
        if (unobserved()) {
            value = v;
        } else {
            super.set(v);
        }
    }

    @Override
    public java.lang.Long getValue() {
        return unobserved() ? java.lang.Long.valueOf(value) : super.getValue();
    }

    @Override
    public void setValue(java.lang.Number v) {
        set(v == null ? 0L : v.longValue());
    }

    @Override
    public boolean isBound() {
        return !unobserved() && super.isBound();
    }

    @Override
    public java.lang.Object getBean() {
        return lazy ? bean : (java.lang.Object) com.jxparallel.fx.Fx.jx(((javafx.beans.property.SimpleLongProperty) fxPeer()).getBean());
    }

    @Override
    public java.lang.String getName() {
        return lazy ? name : ((javafx.beans.property.SimpleLongProperty) fxPeer()).getName();
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
