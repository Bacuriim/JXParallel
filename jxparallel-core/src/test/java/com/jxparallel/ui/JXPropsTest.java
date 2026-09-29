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
