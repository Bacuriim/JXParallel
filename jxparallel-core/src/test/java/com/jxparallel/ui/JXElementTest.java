package com.jxparallel.ui;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JXElementTest {
    @Test
    void childrenLeaveOutNullsAndKeepTheirOwnCopy() {
        JXElement a = JXElement.text("a");
        JXElement b = JXElement.text("b");
        JXElement[] given = {a, null, b};
        JXElement e = JXElement.of("column", JXProps.empty(), given);
        given[0] = b; // the caller reuses its array
        assertEquals(Arrays.asList(a, b), e.getChildren());
        assertThrows(UnsupportedOperationException.class, () -> e.getChildren().add(a));
        assertThrows(UnsupportedOperationException.class, () -> e.getChildren().set(0, b));
    }

    @Test
    void noChildren() {
        assertEquals("pane", JXElement.of("pane").getType());
        assertTrue(JXElement.of("pane").getChildren().isEmpty());
        assertTrue(JXElement.of("pane", null).getChildren().isEmpty());
        assertTrue(JXElement.of("pane", JXProps.empty(), (JXElement[]) null).getChildren().isEmpty());
        assertTrue(JXElement.of("pane", JXProps.empty(), null, null).getChildren().isEmpty());
        assertSame(JXProps.empty(), JXElement.of("pane", null).getProps());
        assertEquals("", JXElement.text(null).getProps().get("value"));
        assertThrows(IllegalArgumentException.class, () -> JXElement.of(" "));
    }
}
