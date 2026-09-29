"""Writes the hand-written Simple*Property classes of jxparallel-fx (listed in mirrors/handwritten.txt):
a JX property whose JavaFX peer is made on first need. Usage: python scripts/make-lazy-properties.py"""

# name, value type, JavaFX default, boxed type for getValue, setValue parameter, setValue body
TYPES = [
    ("String", "java.lang.String", "null", "java.lang.String", "java.lang.String", "set(v);"),
    ("Boolean", "boolean", "false", "java.lang.Boolean", "java.lang.Boolean", "set(v == null ? false : v);"),
    ("Integer", "int", "0", "java.lang.Integer", "java.lang.Number", "set(v == null ? 0 : v.intValue());"),
    ("Long", "long", "0L", "java.lang.Long", "java.lang.Number", "set(v == null ? 0L : v.longValue());"),
    ("Float", "float", "0.0f", "java.lang.Float", "java.lang.Number", "set(v == null ? 0.0f : v.floatValue());"),
    ("Double", "double", "0.0", "java.lang.Double", "java.lang.Number", "set(v == null ? 0.0 : v.doubleValue());"),
    ("Object", "T", "null", "T", "T", "set(v);"),
]

TEMPLATE = '''package com.jxparallel.fx.beans.property;

/**
 * JXParallel counterpart of {{@link javafx.beans.property.Simple{name}Property}}, written by hand
 * (scripts/make-lazy-properties.py; listed in mirrors/handwritten.txt so the generator leaves it).
 * Its JavaFX peer is made on first need: a listener, a binding, JavaFX reading it. Until then the
 * property holds its own value, so an application model with many rows costs one object per
 * property, as with JavaFX, instead of a JX object and its peer. A subclass gets its peer at once,
 * as it may override what the peer calls.
 */
@SuppressWarnings({{"unchecked", "rawtypes", "deprecation"}})
public class Simple{name}Property{tp} extends com.jxparallel.fx.beans.property.{name}PropertyBase{tp} {{
    /** False when a subclass constructor gave the peer (the wrap constructor). */
    private final boolean lazy;
    private Object bean;
    private String name;
    private {vt} value;
    private Object peer;

    protected Simple{name}Property(com.jxparallel.fx.Fx.Wrap wrap, Object peer) {{
        super(wrap, peer);
        this.lazy = false;
    }}

    public Simple{name}Property() {{
        this(null, "", {default});
    }}

    public Simple{name}Property({vt} initialValue) {{
        this(null, "", initialValue);
    }}

    public Simple{name}Property(java.lang.Object bean, java.lang.String name) {{
        this(bean, name, {default});
    }}

    public Simple{name}Property(java.lang.Object bean, java.lang.String name, {vt} initialValue) {{
        super(com.jxparallel.fx.Fx.WRAP, null);
        this.lazy = true;
        this.bean = bean;
        this.name = name == null ? "" : name;
        this.value = initialValue;
        if (getClass() != Simple{name}Property.class) {{
            fxPeer();
        }}
    }}

    /** No peer yet: the value is here. */
    private boolean unobserved() {{
        return lazy && peer == null;
    }}

    @Override
    public Object fxPeer() {{
        if (!lazy) {{
            return super.fxPeer();
        }}
        if (peer == null) {{
            peer = com.jxparallel.fx.Fx.own(new com.jxparallel.fx.peer.beans.property.Simple{name}Property(
                    com.jxparallel.fx.Fx.fx(bean), name, {to_fx}), this);
        }}
        return peer;
    }}

    @Override
    public {vt} get() {{
        return unobserved() ? value : super.get();
    }}

    @Override
    public void set({vt} v) {{
        if (unobserved()) {{
            value = v;
        }} else {{
            super.set(v);
        }}
    }}

    @Override
    public {boxed} getValue() {{
        return unobserved() ? {box_value} : super.getValue();
    }}

    @Override
    public void setValue({setparam} v) {{
        {setbody}
    }}

    @Override
    public boolean isBound() {{
        return !unobserved() && super.isBound();
    }}

    @Override
    public java.lang.Object getBean() {{
        return lazy ? bean : (java.lang.Object) com.jxparallel.fx.Fx.jx(((javafx.beans.property.Simple{name}Property) fxPeer()).getBean());
    }}

    @Override
    public java.lang.String getName() {{
        return lazy ? name : ((javafx.beans.property.Simple{name}Property) fxPeer()).getName();
    }}

    @Override
    public boolean equals(Object o) {{
        return lazy ? o == this || (o != null && o == peer) : super.equals(o);
    }}

    @Override
    public int hashCode() {{
        return lazy ? System.identityHashCode(this) : super.hashCode();
    }}
}}
'''

for name, vt, default, boxed, setparam, setbody in TYPES:
    obj = name == "Object"
    src = TEMPLATE.format(
        name=name, vt=vt, default=default, boxed=boxed, setparam=setparam, setbody=setbody,
        tp="<T>" if obj else "",
        to_fx="(T) com.jxparallel.fx.Fx.fx(value)" if obj else "value",
        box_value="value" if obj or name == "String" else boxed + ".valueOf(value)",
    )
    path = "jxparallel-fx/src/main/java/com/jxparallel/fx/beans/property/Simple%sProperty.java" % name
    open(path, "w", encoding="utf-8", newline="\n").write(src)
    print(path)
