package com.jxparallel.fx.beans.property;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jxparallel.fx.Fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Simple*Property makes its JavaFX peer on first need and behaves like JavaFX's before and after. */
class SimplePropertiesTest {

    private static Object peerField(Object property) throws Exception {
        for (Class<?> c = property.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getSimpleName().startsWith("Simple")) {
                Field f = c.getDeclaredField("peer");
                f.setAccessible(true);
                return f.get(property);
            }
        }
        throw new AssertionError("no Simple*Property class");
    }

    @Test
    void anUnobservedPropertyKeepsItsValueWithoutAPeer() throws Exception {
        Object bean = new Object();
        SimpleStringProperty s = new SimpleStringProperty(bean, null, "a");
        s.set("b");
        assertEquals("b", s.get());
        assertEquals("b", s.getValue());
        s.setValue("c");
        assertEquals("c", s.get());
        assertSame(bean, s.getBean());
        assertEquals("", s.getName(), "a null name is \"\", like JavaFX");
        assertFalse(s.isBound());
        assertNull(peerField(s), "no listener, no binding: no JavaFX object");

        SimpleIntegerProperty i = new SimpleIntegerProperty((Object) null, "n", 5);
        i.setValue(7.9);
        assertEquals(7, i.get());
        assertEquals(Integer.valueOf(7), i.getValue());
        i.setValue(null);
        assertEquals(0, i.get(), "null is 0, like JavaFX");
        SimpleBooleanProperty b = new SimpleBooleanProperty(true);
        b.setValue(null);
        assertFalse(b.get());
        SimpleLongProperty l = new SimpleLongProperty(3L);
        l.setValue(4.5);
        assertEquals(4L, l.get());
        assertEquals(Long.valueOf(4L), l.getValue());
        l.setValue(null);
        assertEquals(0L, l.get());
        SimpleFloatProperty f = new SimpleFloatProperty(1.5f);
        assertEquals(Float.valueOf(1.5f), f.getValue());
        f.setValue(null);
        assertEquals(0f, f.get());
        f.setValue(2);
        assertEquals(2f, f.get());
        SimpleDoubleProperty d = new SimpleDoubleProperty((Object) null, "d");
        assertEquals(0.0, d.get());
        d.setValue(2.5f);
        assertEquals(Double.valueOf(2.5), d.getValue());
        d.setValue(null);
        assertEquals(0.0, d.get());
        Object value = new Object();
        SimpleObjectProperty<Object> o = new SimpleObjectProperty<>(value);
        assertSame(value, o.get());
        assertSame(value, o.getValue());
        o.setValue(null);
        assertNull(o.get());
        for (Object p : new Object[]{i, b, l, f, d, o}) {
            assertNull(peerField(p), p.getClass().getSimpleName());
        }
        assertEquals("n", i.getName());
        assertNull(i.getBean());
        assertEquals("", new SimpleStringProperty().getName());
        assertNull(new SimpleStringProperty().get());
        assertEquals(0, new SimpleIntegerProperty().get());
        assertFalse(new SimpleBooleanProperty().get());
        assertEquals(0L, new SimpleLongProperty().get());
        assertEquals(0f, new SimpleFloatProperty().get());
        assertEquals(0.0, new SimpleDoubleProperty().get());
        assertNull(new SimpleObjectProperty<>().get());
    }

    @Test
    void aListenerMakesThePeerWithTheCurrentValueAndSeesEveryChange() throws Exception {
        SimpleIntegerProperty p = new SimpleIntegerProperty(this, "count", 3);
        int hash = p.hashCode();
        List<Object> seen = new ArrayList<>();
        p.addListener((obs, before, after) -> seen.add(before + "->" + after));
        Object peer = peerField(p);
        assertNotNull(peer);
        assertEquals(3, ((javafx.beans.property.SimpleIntegerProperty) peer).get(), "the value moved to the peer");
        assertEquals("count", ((javafx.beans.property.SimpleIntegerProperty) peer).getName());
        assertSame(p, Fx.jx(peer), "JavaFX's object leads back to the JX one");
        assertSame(peer, Fx.fx(p));
        p.set(4);
        p.setValue(5);
        assertEquals(java.util.Arrays.asList("3->4", "4->5"), seen);
        assertEquals(5, p.get());
        assertEquals(Integer.valueOf(5), p.getValue());
        assertEquals(hash, p.hashCode(), "the hash does not change when the peer appears");
        assertTrue(p.equals(peer));
        assertTrue(p.equals(p));
        assertFalse(p.equals(new SimpleIntegerProperty(5)));
        assertFalse(p.equals(null));
        assertSame(this, p.getBean());
    }

    @Test
    void bindingWorksBetweenUnobservedProperties() throws Exception {
        SimpleStringProperty a = new SimpleStringProperty("a");
        SimpleStringProperty b = new SimpleStringProperty("b");
        a.bind(b);
        assertTrue(a.isBound());
        assertEquals("b", a.get());
        b.set("c");
        assertEquals("c", a.get());
        assertEquals("c", a.getValue());
        assertThrows(RuntimeException.class, () -> a.set("x"), "a bound value cannot be set");
        a.unbind();
        a.set("x");
        assertEquals("x", a.get());
        assertEquals("c", b.get());

        SimpleObjectProperty<String> o = new SimpleObjectProperty<>("o");
        SimpleObjectProperty<String> q = new SimpleObjectProperty<>();
        q.bind(o);
        assertEquals("o", q.get());
        o.set("p");
        assertEquals("p", q.getValue());
        SimpleBooleanProperty flag = new SimpleBooleanProperty();
        SimpleBooleanProperty other = new SimpleBooleanProperty(true);
        flag.bindBidirectional(other);
        assertTrue(flag.get());
        flag.set(false);
        assertFalse(other.get());
        assertFalse(flag.getValue());
    }

    /** The same contract for one type: unobserved, observed, bound; a generated wrapper and a subclass. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void contract(Object bean, Property p, Property other, Property wrapper, Property subclass,
                                 Object v1, Object v2) throws Exception {
        String type = p.getClass().getSimpleName();
        assertSame(bean, p.getBean(), type);
        assertEquals("name", p.getName(), type);
        assertFalse(p.isBound(), type);
        assertEquals(v1, p.getValue(), type);
        assertNull(peerField(p), type);
        int hash = p.hashCode();
        assertEquals(System.identityHashCode(p), hash, type);
        assertTrue(p.equals(p));
        assertFalse(p.equals(other), type);
        assertFalse(p.equals(null), type);
        p.setValue(v2);
        assertEquals(v2, p.getValue(), type);
        assertNull(peerField(p), type);

        int[] calls = {0};
        p.addListener((com.jxparallel.fx.beans.InvalidationListener) o -> calls[0]++);
        Object peer = peerField(p);
        assertNotNull(peer, type);
        assertSame(peer, ((Fx.Backed) p).fxPeer(), type);
        assertSame(p, Fx.jx(peer), type);
        assertEquals(v2, ((javafx.beans.value.ObservableValue) peer).getValue(), type + ": the value moved to the peer");
        assertEquals(v2, p.getValue(), type);
        assertTrue(p.equals(peer), type);
        assertEquals(hash, p.hashCode(), type);
        assertSame(bean, p.getBean(), type);
        assertEquals("name", p.getName(), type);
        assertFalse(p.isBound(), type);
        p.setValue(v1);
        assertEquals(1, calls[0], type);

        other.setValue(v2);
        p.bind(other);
        assertTrue(p.isBound(), type);
        assertEquals(v2, p.getValue(), type);
        other.setValue(v1);
        assertEquals(v1, p.getValue(), type);
        p.unbind();

        assertSame(bean, wrapper.getBean(), type + " wrapper");
        assertEquals("name", wrapper.getName(), type + " wrapper");
        assertEquals(v1, wrapper.getValue(), type + " wrapper");
        assertFalse(wrapper.isBound(), type + " wrapper");
        Object wrapperPeer = ((Fx.Backed) wrapper).fxPeer();
        assertTrue(wrapper.equals(wrapperPeer), type + " wrapper");
        assertEquals(wrapperPeer.hashCode(), wrapper.hashCode(), type + " wrapper");
        wrapper.setValue(v2);
        assertEquals(v2, ((javafx.beans.value.ObservableValue) wrapperPeer).getValue(), type + " wrapper");

        assertNotNull(peerField(subclass), type + " subclass");
        assertEquals(v1, subclass.getValue(), type + " subclass");
        assertEquals(v1, ((javafx.beans.value.ObservableValue) peerField(subclass)).getValue(), type + " subclass");
        assertSame(bean, subclass.getBean(), type + " subclass");
    }

    @Test
    void everyTypeKeepsTheContract() throws Exception {
        Object bean = new Object();
        contract(bean, new SimpleStringProperty(bean, "name", "a"), new SimpleStringProperty(),
                new ReadOnlyStringWrapper(bean, "name", "a"), new SimpleStringProperty(bean, "name", "a") { }, "a", "b");
        contract(bean, new SimpleBooleanProperty(bean, "name", true), new SimpleBooleanProperty(),
                new ReadOnlyBooleanWrapper(bean, "name", true), new SimpleBooleanProperty(bean, "name", true) { }, true, false);
        contract(bean, new SimpleIntegerProperty(bean, "name", 1), new SimpleIntegerProperty(),
                new ReadOnlyIntegerWrapper(bean, "name", 1), new SimpleIntegerProperty(bean, "name", 1) { }, 1, 2);
        contract(bean, new SimpleLongProperty(bean, "name", 1L), new SimpleLongProperty(),
                new ReadOnlyLongWrapper(bean, "name", 1L), new SimpleLongProperty(bean, "name", 1L) { }, 1L, 2L);
        contract(bean, new SimpleFloatProperty(bean, "name", 1f), new SimpleFloatProperty(),
                new ReadOnlyFloatWrapper(bean, "name", 1f), new SimpleFloatProperty(bean, "name", 1f) { }, 1f, 2f);
        contract(bean, new SimpleDoubleProperty(bean, "name", 1.0), new SimpleDoubleProperty(),
                new ReadOnlyDoubleWrapper(bean, "name", 1.0), new SimpleDoubleProperty(bean, "name", 1.0) { }, 1.0, 2.0);
        contract(bean, new SimpleObjectProperty<>(bean, "name", "x"), new SimpleObjectProperty<>(),
                new ReadOnlyObjectWrapper<>(bean, "name", "x"), new SimpleObjectProperty<Object>(bean, "name", "x") { }, "x", "y");
    }

    /** A subclass may override what the peer calls: it gets its peer at once, as before. */
    static class Counted extends SimpleDoubleProperty {
        Counted() {
            super(1.0);
        }
    }

    @Test
    void aSubclassGetsItsPeerAtOnce() throws Exception {
        Counted c = new Counted();
        assertNotNull(peerField(c));
        assertEquals(1.0, c.get());
        c.set(2.0);
        assertEquals(2.0, ((javafx.beans.property.SimpleDoubleProperty) peerField(c)).get());
        assertTrue(c.equals(peerField(c)));
        assertEquals(System.identityHashCode(c), c.hashCode());
        ReadOnlyStringWrapper wrapper = new ReadOnlyStringWrapper("w"); // a generated subclass: wrap constructor
        assertEquals("w", wrapper.get());
        wrapper.set("v");
        assertEquals("v", wrapper.getReadOnlyProperty().get());
        assertEquals("", wrapper.getName());
        assertNull(wrapper.getBean());
        assertEquals(wrapper.fxPeer().hashCode(), wrapper.hashCode());
        assertTrue(wrapper.equals(wrapper.fxPeer()));
        assertFalse(wrapper.isBound());
    }
}
