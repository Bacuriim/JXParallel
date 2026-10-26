package com.jxparallel.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JXPropsTest {
    @Test
    void builtPropsNeverChangeWhenTheBuilderIsReused() {
        JXProps.Builder builder = JXProps.builder().set("a", 1).set("b", 2);
        JXProps first = builder.build();
        builder.set("a", 10).set("c", 3).set("b", null);
        JXProps second = builder.build();
        assertEquals(1, first.get("a"));
        assertEquals(2, first.get("b"));
        assertNull(first.get("c"));
        assertEquals(10, second.get("a"));
        assertNull(second.get("b"));
        assertEquals(3, second.get("c"));
        assertThrows(UnsupportedOperationException.class, () -> first.asMap().put("x", 1));
    }

    @Test
    void behavesLikeAnOrderedMap() {
        JXProps.Builder b = JXProps.builder();
        java.util.Map<String, Object> expected = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 20; i++) { // past the builder's first array
            b.set("k" + i, i);
            expected.put("k" + i, i);
        }
        b.set("k3", "three").set("k0", null).set("k19", null).set("absent", null);
        expected.put("k3", "three");
        expected.remove("k0");
        expected.remove("k19");
        java.util.Map<String, Object> map = b.build().asMap();
        assertEquals(expected, map);
        assertEquals(expected.hashCode(), map.hashCode());
        assertEquals(new java.util.ArrayList<>(expected.keySet()), new java.util.ArrayList<>(map.keySet()));
        assertEquals(new java.util.ArrayList<>(expected.entrySet()), new java.util.ArrayList<>(map.entrySet()));
        java.util.List<String> seen = new java.util.ArrayList<>();
        map.forEach((k, v) -> seen.add(k + "=" + v));
        assertEquals(18, seen.size(), "forEach stops at the last prop");
        assertEquals("k1=1", seen.get(0));
        assertEquals(true, map.containsKey("k1"), "the first key");
        assertEquals(false, map.isEmpty());
        assertEquals(18, map.keySet().size());
        assertEquals(18, map.entrySet().size());
        assertEquals(false, map.keySet().contains("k0"));
        assertEquals("k3=three", seen.get(2));
        assertEquals(18, map.size());
        assertEquals(true, map.containsKey("k18"));
        assertEquals(false, map.containsKey("k0"));
        assertEquals(true, map.keySet().contains("k5"));
        assertEquals(7, map.get(new String("k7")), "keys found by equality, not only identity");
        assertNull(map.get(null));
        java.util.Iterator<String> it = map.keySet().iterator();
        for (int i = 0; i < 18; i++) {
            it.next();
        }
        assertThrows(java.util.NoSuchElementException.class, it::next);
        assertThrows(UnsupportedOperationException.class, () -> map.keySet().iterator().remove());
    }

    @Test
    void removingAfterABuildLeavesTheBuiltPropsAlone() {
        JXProps.Builder b = JXProps.builder().set("a", 1).set("b", 2).set("c", 3);
        JXProps first = b.build();
        JXProps second = b.set("a", null).build();
        assertEquals(3, first.asMap().size());
        assertEquals(1, first.get("a"));
        assertEquals(java.util.Arrays.asList("b", "c"), new java.util.ArrayList<>(second.asMap().keySet()));
        assertSame(JXProps.empty(), JXProps.builder().set("x", 1).set("x", null).build());
        JXProps.Builder full = JXProps.builder();
        for (int i = 0; i < 8; i++) { // exactly the builder's first array
            full.set("p" + i, i);
        }
        full.set("p1", null).set("p7", null).set("p0", null);
        assertEquals(java.util.Arrays.asList("p2", "p3", "p4", "p5", "p6"), new java.util.ArrayList<>(full.build().asMap().keySet()));
        assertEquals(0, JXProps.empty().asMap().size());
        assertEquals(true, JXProps.empty().asMap().isEmpty());
    }

    @Test
    void ofIsTheSamePropsAsTheBuilderWithoutMakingOne() {
        JXProps built = JXProps.builder().set("gap", 2.0).set("fillWidth", Boolean.TRUE).set("alignment", "TOP_LEFT").build();
        JXProps of = JXProps.of("gap", 2.0, "fillWidth", Boolean.TRUE, "alignment", "TOP_LEFT");
        assertEquals(built, of);
        assertEquals(built.hashCode(), of.hashCode());
        assertEquals(3, of.size());
        assertEquals("gap", of.keyAt(0));
        assertEquals("TOP_LEFT", of.valueAt(2));
        assertSame(of, of.asMap());
        // a null value leaves the pair out, as set(name, null) does, and the others keep their order
        JXProps some = JXProps.of("a", 1, "b", null, "c", 3, "d", null);
        assertEquals(java.util.Arrays.asList("a", "c"), new java.util.ArrayList<>(some.keySet()));
        assertNull(some.get("b"));
        assertEquals(false, some.containsKey("d"));
        assertSame(JXProps.empty(), JXProps.of());
        assertSame(JXProps.empty(), JXProps.of("a", null));
        assertThrows(IllegalArgumentException.class, () -> JXProps.of("a"));
    }

    @Test
    void absentKeysAreAnsweredForEveryHashAndPresentOnesAreFound() {
        java.util.Map<String, Object> expected = new java.util.LinkedHashMap<>();
        JXProps.Builder b = JXProps.builder();
        for (int i = 0; i < 40; i++) { // more keys than mask bits: several share a bit
            String key = "key" + i;
            if (i % 2 == 0) {
                b.set(key, i);
                expected.put(key, i);
            }
        }
        JXProps props = b.build();
        for (int i = 0; i < 400; i++) {
            String key = "key" + i;
            assertEquals(expected.get(key), props.get(key), key);
            assertEquals(expected.containsKey(key), props.containsKey(key), key);
            assertEquals(expected.get(key), props.get(new String(key)), key + " by equality");
        }
        assertNull(props.get(null));
        assertNull(props.get(Integer.valueOf(3)));
        assertEquals(false, JXProps.empty().containsKey("a"));
    }

    @Test
    void emptyAndTypedReads() {
        assertSame(JXProps.empty(), JXProps.builder().build());
        JXProps p = JXProps.builder().set("flag", "true").set("n", 7).build();
        assertEquals(true, p.getBoolean("flag", false));
        assertEquals(false, p.getBoolean("missing", false));
        assertEquals("7", p.getString("n"));
        assertNull(p.getString("missing"));
        assertThrows(IllegalArgumentException.class, () -> JXProps.builder().set(" ", 1));
    }
}
