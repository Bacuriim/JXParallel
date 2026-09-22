package com.jxparallel.ui.controls;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JXAdditionalNativeControlsTest {
    @Test
    void shouldToggleAndClampControlState() {
        JXCheckBox checkBox = new JXCheckBox("Accept");
        checkBox.fire();
        assertTrue(checkBox.isSelected());

        JXProgressBar progress = new JXProgressBar();
        progress.setProgress(2.0);
        assertEquals(1.0, progress.getProgress());

        JXSlider slider = new JXSlider();
        slider.setValue(500.0);
        assertEquals(slider.getMax(), slider.getValue());
    }

    @Test
    void shouldSelectComboBoxItem() {
        JXComboBox<String> combo = new JXComboBox<String>();
        combo.getItems().add("A");
        combo.getItems().add("B");
        combo.select(1);

        assertEquals(1, combo.getSelectedIndex());
        assertEquals("B", combo.getValue());
    }
}
