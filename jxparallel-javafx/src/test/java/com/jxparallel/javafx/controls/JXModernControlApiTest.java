package com.jxparallel.javafx.controls;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JXModernControlApiTest {
    @Test
    void shouldKeepJavaFxLikeButtonMethodsAndAddVisualOptions() throws Exception {
        assertEquals(String.class, JXButton.class.getMethod("getText").getReturnType());
        assertEquals(void.class, JXButton.class.getMethod("setText", String.class).getReturnType());
        assertEquals(void.class, JXButton.class.getMethod("setOnAction", javafx.event.EventHandler.class).getReturnType());
        assertEquals(JXVisualVariant.class, JXButton.class.getMethod("getVariant").getReturnType());
        assertEquals(void.class, JXButton.class.getMethod("animateAppear").getReturnType());
        assertEquals(javafx.collections.ObservableList.class,
                JXButton.class.getMethod("getStyleClass").getReturnType());
    }

    @Test
    void shouldExposePromptTextLikeTextField() throws Exception {
        Method prompt = JXTextField.class.getMethod("setPromptText", String.class);
        assertEquals(void.class, prompt.getReturnType());
    }
}
