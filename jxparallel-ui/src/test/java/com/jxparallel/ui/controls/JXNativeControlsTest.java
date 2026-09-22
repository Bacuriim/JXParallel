package com.jxparallel.ui.controls;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JXNativeControlsTest {
    @Test
    void shouldExposeButtonStateAndActionWithoutJavaFx() {
        JXButton button = new JXButton("Save");
        AtomicBoolean called = new AtomicBoolean(false);
        button.setOnAction(() -> called.set(true));

        button.fire();

        assertTrue(called.get());
        assertEquals("button", button.render().getType());
        button.setDisable(true);
        button.fire();
        assertTrue(button.isDisable());
    }

    @Test
    void shouldExposeLabelAndTextFieldProperties() {
        JXLabel label = new JXLabel("Name");
        JXTextField field = new JXTextField();
        field.setPromptText("Type a name");
        field.setText("Alice");

        assertEquals("Name", label.getText());
        assertEquals("Alice", field.getText());
        assertEquals("Type a name", field.getPromptText());
        assertFalse(field.render().getProps().getString("placeholder").isEmpty());
    }
}
