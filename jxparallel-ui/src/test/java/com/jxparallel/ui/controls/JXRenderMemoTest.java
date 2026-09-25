package com.jxparallel.ui.controls;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.layout.JXPane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class JXRenderMemoTest {
    @Test
    void unchangedStateReturnsTheSameElement() {
        JXLabel label = new JXLabel("a");
        JXPane pane = new JXPane(8);
        pane.add(label);
        pane.add(new JXButton("Save"));

        assertSame(label.render(), label.render());
        assertSame(pane.render(), pane.render());
    }

    @Test
    void changedStateProducesANewElementWithTheNewValue() {
        JXLabel label = new JXLabel("a");
        JXPane pane = new JXPane(8);
        pane.add(label);
        JXElement before = pane.render();

        label.setText("b");
        JXElement after = pane.render();

        assertNotSame(before, after);
        assertEquals("b", after.getChildren().get(0).getProps().get("value"));
    }

    @Test
    void comboSeesItemChanges() {
        JXComboBox<String> combo = new JXComboBox<String>();
        combo.getItems().add("x");
        JXElement before = combo.render();

        combo.getItems().add("y");

        assertNotSame(before, combo.render());
    }
}
