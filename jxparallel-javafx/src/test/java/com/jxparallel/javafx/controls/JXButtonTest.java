package com.jxparallel.javafx.controls;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JXButtonTest {
    @Test
    void shouldExposeJavaFxLikeState() {
        assertEquals(String.class, method("getText").getReturnType());
        assertEquals(void.class, method("setText", String.class).getReturnType());
        assertEquals(void.class, method("setDisable", boolean.class).getReturnType());
        assertEquals(boolean.class, method("isDisabled").getReturnType());
    }

    private static Method method(String name, Class<?>... parameterTypes) {
        try {
            return JXButton.class.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }
}
