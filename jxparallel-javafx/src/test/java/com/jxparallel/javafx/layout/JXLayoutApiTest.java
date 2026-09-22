package com.jxparallel.javafx.layout;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JXLayoutApiTest {
    @Test
    void shouldExposeLayoutNodeAndSpacingApi() throws Exception {
        assertEquals("node", JXHBox.class.getMethod("node").getName());
        Method spacing = JXHBox.class.getMethod("setSpacing", double.class);
        assertEquals(void.class, spacing.getReturnType());
        assertEquals("node", JXVBox.class.getMethod("node").getName());
    }
}
